# -*- coding: utf-8 -*-
"""
线上书店后端 —— 简历能力点全量冒烟测试（针对 8080 主实例）

覆盖：
  R1 分页 + 条件筛选（关键词 / 分类 / 价格区间）
  R2 权限控制（Spring Security：匿名 401、普通用户 403、管理员放行、方法级 @PreAuthorize）
  R3 文件上传（ObjectStorage 抽象 + 四道校验）
  R4 未登录购物车合并（数量累加 / 库存收敛 / 下架跳过）
  R5 订单取消 + 库存回滚（含重复取消幂等拒）
  R6 全局异常处理 + 统一返回结构
  R7 索引优化（价格筛选正确性 + SQL 走索引的行为一致性）

注意：口令以字面量写在脚本文件里（不写命令行），否则会被运行环境改写。
"""
import json
import os
import subprocess
import urllib.request
import urllib.error
import uuid
import io

# 后端地址与演示账号均可用环境变量覆盖，便于切换实例 / 真机联调
BASE = os.environ.get("BOOKSTORE_BASE", "http://127.0.0.1:8080")
REDIS_CLI = os.environ.get("BOOKSTORE_REDIS_CLI", "D:/redis5/redis-cli.exe")

ADMIN_USER = os.environ.get("BOOKSTORE_ADMIN_USER", "admin")
ADMIN_PWD = os.environ.get("BOOKSTORE_ADMIN_PWD", "123456")
USER_PHONE = os.environ.get("BOOKSTORE_USER_PHONE", "13800138000")
USER_PWD = os.environ.get("BOOKSTORE_USER_PWD", "123456")

# 关闭代理，避免走环境里的 HTTP_PROXY 导致 502
_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

passed = []
failed = []


def check(ok, msg):
    (passed if ok else failed).append(msg)
    print(("  [PASS] " if ok else "  [FAIL] ") + msg)


def req(method, path, body=None, token=None, raw=None, content_type=None, expect_http=200):
    url = BASE + path
    data = None
    headers = {"Accept": "application/json"}
    if raw is not None:
        data = raw
        headers["Content-Type"] = content_type
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with _opener.open(request, timeout=20) as resp:
            status = resp.status
            text = resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        status = e.code
        text = e.read().decode("utf-8", "replace")
    try:
        payload = json.loads(text)
    except Exception:
        payload = {"_raw": text}
    return status, payload


def jreq(method, path, body=None, token=None):
    return req(method, path, body=body, token=token)


def cart_items(token):
    """购物车返回 CartListVO：条目在 data.list 里"""
    st, js = jreq("GET", "/api/cart/list", token=token)
    if js.get("code") != 200:
        return []
    return (js.get("data") or {}).get("list") or []


def clear_cart(token):
    for it in cart_items(token):
        jreq("DELETE", "/api/cart/%s" % it.get("id"), token=token)


def make_png():
    """手工生成一个合法的 1x1 透明 PNG，避免因图片本身非法而误判上传接口。"""
    import struct
    import zlib

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0)
    idat = zlib.compress(b"\x00\x00\x00\x00\x00")
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", idat) + chunk(b"IEND", b""))


def redis_get(key):
    """读取一个 Redis 字符串键；仅用于取回图形验证码明文（见 fill_captcha）"""
    try:
        out = subprocess.run([REDIS_CLI, "-h", "127.0.0.1", "-p", "6379", "get", key],
                             capture_output=True, timeout=5)
        return out.stdout.decode("utf-8", "replace").strip()
    except Exception:
        return ""


def fill_captcha(path, payload):
    """APP 用户登录已强制图形验证码，这里补上 captchaKey + captchaCode。

    验证码是图片，脚本无法自动识别；本地 E2E 直接从 Redis 读回本次验证码明文。
    这只是测试侧取数手段，不依赖任何服务端测试后门，也不改动应用代码。
    """
    if "/user/login" not in path:
        return payload
    _, js = jreq("GET", "/api/captcha")
    key = (js.get("data") or {}).get("captchaKey")
    if key:
        code = redis_get("bookstore:captcha:" + key)
        if code:
            return dict(payload, captchaKey=key, captchaCode=code)
    return payload


def login(path, payload):
    st, js = jreq("POST", path, fill_captcha(path, payload))
    return js.get("data", {}).get("token") if js.get("code") == 200 else None, js


print("=" * 68)
print("第 0 步：数据库连通性与统一返回结构（R6）")
print("=" * 68)
st, js = jreq("GET", "/api/category/list")
check(st == 200, "HTTP 状态码恒为 200（实测 %s）" % st)
check(js.get("code") == 200, "分类接口 code=200（数据库连通；注意不能用 /health 判断）")
cats = js.get("data") or []
check(len(cats) > 0, "分类数据非空（%d 个分类）" % len(cats))
for k in ("code", "message", "data", "timestamp"):
    check(k in js, "统一返回结构含字段 %s" % k)
check("requestId" in js, "统一返回结构含 requestId（链路追踪）")

# D-10：分类书目数（只统计上架图书，用于前台「共 N 本 / 该分类 N 本」）
check(all("bookCount" in c for c in cats), "每个分类都返回 bookCount 字段")
check(all(isinstance(c.get("bookCount"), int) for c in cats),
      "bookCount 为整数而非 null（前端拿 null 会渲染出「共 null 本」）")
st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=5")
on_sale_total = (js.get("data") or {}).get("total")
sum_count = sum(int(c.get("bookCount") or 0) for c in cats)
check(sum_count == on_sale_total,
      "各分类 bookCount 之和 == 上架图书总数（%d vs %d）" % (sum_count, on_sale_total))
check(all(int(c.get("bookCount") or 0) >= 0 for c in cats),
      "bookCount 无负数（统计口径未算错）")
# 抽一个分类核对：该分类下能查到的上架图书数应与其 bookCount 相等
if cats:
    c0 = cats[0]
    st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=1&categoryId=%s" % c0.get("id"))
    cat_total = (js.get("data") or {}).get("total")
    check(cat_total == c0.get("bookCount"),
          "分类「%s」bookCount=%s 与实际可查图书数 %s 一致"
          % (c0.get("name"), c0.get("bookCount"), cat_total))

print()
print("=" * 68)
print("第 1 步：分页 + 条件筛选（R1）")
print("=" * 68)
st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=5")
d = js.get("data") or {}
check(js.get("code") == 200, "图书分页接口可用")
check(d.get("pageNum") == 1 and d.get("pageSize") == 5, "分页参数生效 pageNum=1 pageSize=5")
check(len(d.get("list") or []) == 5, "首页返回 5 条（实际 %d 条）" % len(d.get("list") or []))
check(d.get("pages", 0) >= 1, "总页数计算正确 pages=%s" % d.get("pages"))

# 关键词筛选
st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=50&keyword=Java")
d = js.get("data") or {}
kw_books = d.get("list") or []
hit_kw = all(("java" in (b.get("title") or "").lower() or "java" in (b.get("author") or "").lower())
             for b in kw_books) if kw_books else False
check(js.get("code") == 200 and len(kw_books) > 0, "关键词搜索「Java」命中 %d 条" % len(kw_books))
check(hit_kw, "关键词命中的记录标题/作者均含关键字（无脏数据）")

# 分类筛选
if cats:
    cid = cats[0].get("id")
    st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=50&categoryId=%s" % cid)
    d = js.get("data") or {}
    lst = d.get("list") or []
    check(len(lst) > 0, "按分类 %s 筛选命中 %d 条" % (cats[0].get("name"), len(lst)))
    check(all(b.get("categoryId") == cid for b in lst), "分类筛选结果全部属于该分类")

# 超大 pageSize 收敛
st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=9999")
d = js.get("data") or {}
check(d.get("pageSize") == 50, "超大 pageSize 被收敛为 50（防拖库）")

print()
print("=" * 68)
print("第 2 步：价格区间筛选（R1 + R7）")
print("=" * 68)
st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=50&minPrice=30&maxPrice=60")
d = js.get("data") or {}
lst = d.get("list") or []
inrange = all(30 <= float(b.get("price")) <= 60 for b in lst) if lst else False
check(js.get("code") == 200 and len(lst) > 0, "价格区间 30~60 命中 %d 条" % len(lst))
check(inrange, "返回结果价格全部落在 [30, 60] 区间")

st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=50&minPrice=100")
d = js.get("data") or {}
lst = d.get("list") or []
check(all(float(b.get("price")) >= 100 for b in lst), "仅传 minPrice=100 时结果均 ≥100（%d 条）" % len(lst))

st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=50&maxPrice=40")
d = js.get("data") or {}
lst = d.get("list") or []
check(all(float(b.get("price")) <= 40 for b in lst), "仅传 maxPrice=40 时结果均 ≤40（%d 条）" % len(lst))

# 区间写反 -> 自动交换
st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=50&minPrice=60&maxPrice=30")
d1 = js.get("data") or {}
st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=50&minPrice=30&maxPrice=60")
d2 = js.get("data") or {}
check(d1.get("total") == d2.get("total") and d1.get("total") is not None,
      "区间写反自动交换（60~30 与 30~60 结果一致，total=%s）" % d2.get("total"))

# 负数忽略
st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=50&minPrice=-100&maxPrice=40")
d = js.get("data") or {}
lst = d.get("list") or []
check(all(float(b.get("price")) <= 40 for b in lst), "负数下限被忽略（结果均 ≤40）")

print()
print("=" * 68)
print("第 3 步：权限控制 Spring Security（R2）")
print("=" * 68)
st, js = jreq("GET", "/api/user/info")
check(js.get("code") == 401, "匿名访问 /api/user/info → code 401（实际 %s）" % js.get("code"))
check(st == 200, "401 时 HTTP 仍为 200（契约一致）")

st, js = jreq("GET", "/api/user/info", token="invalid.token.value")
check(js.get("code") == 401, "伪造令牌被拒 401")

admin_token, js = login("/api/admin/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
check(admin_token is not None, "管理员登录成功")

user_token, js = login("/api/user/login", {"phone": USER_PHONE, "password": USER_PWD})
check(user_token is not None, "普通用户登录成功")

if admin_token and user_token:
    st, js = jreq("GET", "/api/admin/dashboard", token=admin_token)
    check(js.get("code") == 200, "管理员访问 /api/admin/dashboard → 200")
    dd = js.get("data") or {}
    check("bookCount" in dd or "userCount" in dd, "总览统计返回业务字段")

    st, js = jreq("GET", "/api/admin/dashboard", token=user_token)
    check(js.get("code") == 403, "普通用户访问管理端 → 403（%s）" % js.get("code"))
    check(st == 200, "403 时 HTTP 仍为 200")

    st, js = jreq("GET", "/api/admin/book/page?pageNum=1&pageSize=5", token=user_token)
    check(js.get("code") == 403, "普通用户访问管理端图书列表 → 403")

    st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=5", token=None)
    check(js.get("code") == 200, "匿名可浏览图书（白名单放行）")

    st, js = jreq("GET", "/api/cart/list", token=user_token)
    check(js.get("code") == 200, "普通用户可访问自己的购物车（业务放行）")

print()
print("=" * 68)
print("第 4 步：文件上传（R3）")
print("=" * 68)
if admin_token:
    png = make_png()
    boundary = "----bookstore" + uuid.uuid4().hex
    body = io.BytesIO()
    body.write(("--%s\r\n" % boundary).encode())
    body.write(b'Content-Disposition: form-data; name="file"; filename="smoke.png"\r\n')
    body.write(b"Content-Type: image/png\r\n\r\n")
    body.write(png)
    body.write(("\r\n--%s--\r\n" % boundary).encode())
    ctype = "multipart/form-data; boundary=%s" % boundary

    st, js = req("POST", "/api/admin/upload", raw=body.getvalue(), content_type=ctype, token=admin_token)
    check(js.get("code") == 200, "管理员上传 PNG 成功（code=%s）" % js.get("code"))
    up = js.get("data") or {}
    url = up.get("url")
    check(bool(url), "返回封面对外 URL：%s" % url)
    check("storage" in up, "返回当前存储实现类型 storage=%s" % up.get("storage"))

    if url:
        st2, _ = req("GET", url)
        check(st2 == 200, "上传后的封面可直接访问（HTTP %s）" % st2)

    # 非图片文件应被拒
    bad = io.BytesIO()
    b2 = "----bad" + uuid.uuid4().hex
    bad.write(("--%s\r\n" % b2).encode())
    bad.write(b'Content-Disposition: form-data; name="file"; filename="evil.txt"\r\n')
    bad.write(b"Content-Type: text/plain\r\n\r\nhello world")
    bad.write(("\r\n--%s--\r\n" % b2).encode())
    st, js = req("POST", "/api/admin/upload", raw=bad.getvalue(),
                 content_type="multipart/form-data; boundary=%s" % b2, token=admin_token)
    check(js.get("code") == 400, "非图片文件被拒 400（实际 %s）" % js.get("code"))

    # 普通用户不能上传
    st, js = req("POST", "/api/admin/upload", raw=body.getvalue(), content_type=ctype, token=user_token)
    check(js.get("code") == 403, "普通用户上传被拒 403（实际 %s）" % js.get("code"))

    # 匿名不能上传
    st, js = req("POST", "/api/admin/upload", raw=body.getvalue(), content_type=ctype)
    check(js.get("code") == 401, "匿名上传被拒 401（实际 %s）" % js.get("code"))

print()
print("=" * 68)
print("第 5 步：购物车合并（R4）")
print("=" * 68)
if user_token:
    clear_cart(user_token)

    bks = []
    st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=10", token=user_token)
    bks = (js.get("data") or {}).get("list") or []

    if len(bks) >= 2:
        a, b = bks[0], bks[1]
        # 先加 2 件 a
        jreq("POST", "/api/cart/add", {"bookId": a.get("id"), "quantity": 2}, token=user_token)
        # 再合并本地购物车：a 3 件 + b 2 件 -> a 累计 5 件（累加而非覆盖）
        st, js = jreq("POST", "/api/cart/merge", {"items": [
            {"bookId": a.get("id"), "quantity": 3},
            {"bookId": b.get("id"), "quantity": 2}
        ]}, token=user_token)
        check(js.get("code") == 200, "登录后合并本地购物车成功")
        res = js.get("data") or {}
        cart = cart_items(user_token)
        amt = {c.get("bookId"): c.get("quantity") for c in cart}
        check(amt.get(a.get("id")) == 5, "同商品数量累加：2 + 3 = %s（期望 5）" % amt.get(a.get("id")))
        check(amt.get(b.get("id")) == 2, "新商品数量正确并入：%s（期望 2）" % amt.get(b.get("id")))

        # 合并不存在的商品 -> 跳过并说明原因
        st, js = jreq("POST", "/api/cart/merge", {"items": [
            {"bookId": 999999, "quantity": 1},
            {"bookId": b.get("id"), "quantity": 1}
        ]}, token=user_token)
        res = js.get("data") or {}
        skipped = res.get("skipped") or []
        check(len(skipped) >= 1, "不存在的商品被跳过并在 skipped 中说明原因")

        # 参数校验
        st, js = jreq("POST", "/api/cart/merge", {"items": []}, token=user_token)
        check(js.get("code") == 400, "空列表被拒 400（实际 %s）" % js.get("code"))
        st, js = jreq("POST", "/api/cart/merge", {"items": [{"bookId": a.get("id"), "quantity": 0}]}, token=user_token)
        check(js.get("code") == 400, "数量为 0 被拒 400（实际 %s）" % js.get("code"))
        st, js = jreq("POST", "/api/cart/merge", {"items": [{"bookId": a.get("id"), "quantity": 100}]}, token=user_token)
        check(js.get("code") == 400, "数量超 99 被拒 400（实际 %s）" % js.get("code"))
        st, js = jreq("POST", "/api/cart/merge", {"items": [{"bookId": 1, "quantity": 1}] * 51}, token=user_token)
        check(js.get("code") == 400, "超过 50 条被拒 400（实际 %s）" % js.get("code"))
        st, js = jreq("POST", "/api/cart/merge", {"items": [{"bookId": a.get("id"), "quantity": 1}]})
        check(js.get("code") == 401, "未登录合并被拒 401（实际 %s）" % js.get("code"))

        # 清空收尾
        clear_cart(user_token)

print()
print("=" * 68)
print("第 6 步：订单创建 / 取消 / 库存回滚（R5）")
print("=" * 68)
if user_token:
    # 确保有默认地址
    st, js = jreq("GET", "/api/address/list", token=user_token)
    addrs = js.get("data") or []
    addr_id = addrs[0].get("id") if addrs else None
    if not addr_id:
        st, js = jreq("POST", "/api/address/add", {
            "receiver": "冒烟测试", "phone": "13900000001",
            "province": "广东省", "city": "深圳市", "district": "南山区",
            "detail": "测试路 1 号", "isDefault": 1
        }, token=user_token)
        addr_id = (js.get("data") or {}).get("id")
    check(bool(addr_id), "获取到收货地址（id=%s）" % addr_id)

    st, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=10")
    bks = (js.get("data") or {}).get("list") or []
    if addr_id and bks:
        target = bks[0]
        bid = target.get("id")
        stock_before = target.get("stock")
        qty = 2

        # 下单单据来自「购物车已勾选条目」：先加购并勾选
        clear_cart(user_token)
        jreq("POST", "/api/cart/add", {"bookId": bid, "quantity": qty}, token=user_token)
        items = cart_items(user_token)
        chk = [c for c in items if c.get("bookId") == bid]
        check(len(chk) == 1, "加购后购物车中存在目标商品")
        if chk:
            jreq("PUT", "/api/cart/%s" % chk[0].get("id"),
                 {"quantity": qty, "checked": 1}, token=user_token)
            items = cart_items(user_token)
            chk = [c for c in items if c.get("bookId") == bid]
            check(bool(chk and chk[0].get("checked") == 1), "购物车条目已勾选（结算前置条件）")

        st, js = jreq("POST", "/api/order/create", {"addressId": addr_id}, token=user_token)
        check(js.get("code") == 200, "下单成功（code=%s，message=%s）" % (js.get("code"), js.get("message")))
        order = js.get("data") or {}
        oid = order.get("id") or order.get("orderId")
        check(bool(oid), "返回订单 id=%s" % oid)

        st, js = jreq("GET", "/api/book/%s" % bid)
        stock_after = (js.get("data") or {}).get("stock")
        check(stock_after == stock_before - qty,
              "下单后库存扣减正确：%s → %s（-%d）" % (stock_before, stock_after, qty))

        if oid:
            st, js = jreq("POST", "/api/order/%s/cancel" % oid, token=user_token)
            check(js.get("code") == 200, "取消待支付订单成功（code=%s）" % js.get("code"))

            st, js = jreq("GET", "/api/book/%s" % bid)
            stock_restored = (js.get("data") or {}).get("stock")
            check(stock_restored == stock_before,
                  "取消后库存回滚正确：%s → %s（恢复原值 %s）" % (stock_after, stock_restored, stock_before))

            st, js = jreq("GET", "/api/order/%s" % oid, token=user_token)
            check((js.get("data") or {}).get("status") == 5, "订单状态变为 5（已取消）")

            st, js = jreq("POST", "/api/order/%s/cancel" % oid, token=user_token)
            check(js.get("code") in (400, 409, 422), "重复取消被拒（code=%s）" % js.get("code"))

            st, js = jreq("GET", "/api/book/%s" % bid)
            check((js.get("data") or {}).get("stock") == stock_before,
                  "重复取消未造成库存多加（幂等性成立）")

print()
print("=" * 68)
print("第 7 步：全局异常处理（R6）")
print("=" * 68)
st, js = jreq("GET", "/api/book/999999999")
check(js.get("code") in (400, 404, 422, 500), "不存在的图书返回业务错误码（%s）而非堆栈" % js.get("code"))
check("java" not in json.dumps(js).lower() or "Exception" not in json.dumps(js),
      "异常响应不含 Java 堆栈信息（已脱敏）")
st, js = jreq("POST", "/api/user/register", {"phone": "123", "password": "1", "nickname": ""})
check(js.get("code") == 400, "参数校验失败返回 400（实际 %s）" % js.get("code"))
check(bool(js.get("message")), "错误信息可读：%s" % js.get("message"))

print()
print("=" * 68)
print("结果：通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
if failed:
    print("失败明细：")
    for f in failed:
        print("  - " + f)
print("=" * 68)
