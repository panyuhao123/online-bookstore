# -*- coding: utf-8 -*-
"""
线上书店后端 —— 第二批简历能力点冒烟测试（针对 8080 主实例）

覆盖：
  B1 支付抽象层：预支付单结构、模拟渠道落状态、订单归属与状态校验
  B2 购物车 Redis 缓存：Cache-Aside 写失效一致性、短 TTL、条目级失效
  B3 评价模块：购买资格四道校验、星级校验、匿名处理、汇总与分布、图书评分回填
  B4 管理端评价管理：隐藏即刷新聚合与缓存、幂等、筛选、操作日志留痕

注意：口令以字面量写在脚本文件里（不写命令行），否则会被运行环境改写。
"""
import io
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

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


def req(method, path, body=None, token=None):
    url = BASE + path
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
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


def q(value):
    """查询串里的中文要先编码，否则 urllib 会抛 UnicodeEncodeError"""
    return urllib.parse.quote(str(value), safe="")


def redis_cli(*args):
    try:
        out = subprocess.run([REDIS_CLI] + list(args), capture_output=True, timeout=10)
        return out.stdout.decode("utf-8", "replace").strip()
    except Exception as e:  # noqa: BLE001
        return "__ERR__:" + str(e)


def fill_captcha(path, payload):
    """APP 用户登录已强制图形验证码，这里补上 captchaKey + captchaCode。

    验证码是图片，脚本无法自动识别；本地 E2E 直接从 Redis 读回本次验证码明文。
    这只是测试侧取数手段，不依赖任何服务端测试后门，也不改动应用代码。
    """
    if "/user/login" not in path:
        return payload
    _, js = req("GET", "/api/captcha")
    key = (js.get("data") or {}).get("captchaKey")
    if key:
        code = redis_cli("-h", "127.0.0.1", "-p", "6379", "get", "bookstore:captcha:" + key)
        if code and not code.startswith("__ERR__"):
            return dict(payload, captchaKey=key, captchaCode=code)
    return payload


def login(path, payload):
    st, js = req("POST", path, fill_captcha(path, payload))
    return (js.get("data", {}) or {}).get("token") if js.get("code") == 200 else None, js


def cart_items(token):
    st, js = req("GET", "/api/cart/list", token=token)
    if js.get("code") != 200:
        return []
    return (js.get("data") or {}).get("list") or []


def clear_cart(token):
    for it in cart_items(token):
        req("DELETE", "/api/cart/%s" % it.get("id"), token=token)


def ensure_address(token):
    st, js = req("GET", "/api/address/list", token=token)
    rows = js.get("data") or []
    if rows:
        return rows[0].get("id")
    st, js = req("POST", "/api/address/add", {
        "receiver": "评价测试", "phone": "13900000002",
        "province": "广东省", "city": "深圳市", "district": "南山区",
        "detail": "采购路 8 号", "isDefault": 1,
    }, token=token)
    return (js.get("data") or {}).get("id")


def create_order(token, addr_id, book_id, qty=1):
    """下单（走真实链路：加购 → 勾选 → 提交），返回 (orderId, orderNo)"""
    clear_cart(token)
    req("POST", "/api/cart/add", {"bookId": book_id, "quantity": qty}, token=token)
    items = [c for c in cart_items(token) if c.get("bookId") == book_id]
    if not items:
        return None, None
    req("PUT", "/api/cart/%s" % items[0].get("id"),
        {"quantity": qty, "checked": 1}, token=token)
    st, js = req("POST", "/api/order/create", {"addressId": addr_id}, token=token)
    if js.get("code") != 200:
        print("    下单失败：%s" % js.get("message"))
        return None, None
    d = js.get("data") or {}
    return d.get("id") or d.get("orderId"), d.get("orderNo")


def complete_order(user_token, admin_token, addr_id, book_id, qty=1):
    """完整生命周期：下单 → 支付 → 管理员发货 → 用户确认收货（状态 4）"""
    oid, ono = create_order(user_token, addr_id, book_id, qty)
    if not oid:
        return None, None
    st, js = req("POST", "/api/order/%s/pay" % oid, token=user_token)
    if js.get("code") != 200:
        print("    支付失败：%s" % js.get("message"))
        return oid, ono
    st, js = req("POST", "/api/admin/order/%s/ship" % oid, token=admin_token)
    if js.get("code") != 200:
        print("    发货失败：%s" % js.get("message"))
        return oid, ono
    st, js = req("POST", "/api/order/%s/confirm" % oid, token=user_token)
    if js.get("code") != 200:
        print("    确认收货失败：%s" % js.get("message"))
    return oid, ono


print("=" * 70)
print("第 0 步：准备数据（登录 / 地址 / 目标图书）")
print("=" * 70)
admin_token, js = login("/api/admin/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
check(bool(admin_token), "管理员登录成功")
user_token, js = login("/api/user/login", {"phone": USER_PHONE, "password": USER_PWD})
check(bool(user_token), "用户登录成功")

st, js = req("GET", "/api/user/info", token=user_token)
me = js.get("data") or {}
user_id = me.get("id")
check(bool(user_id), "取得当前用户 ID=%s（昵称=%s）" % (user_id, me.get("nickname")))

addr_id = ensure_address(user_token)
check(bool(addr_id), "取得收货地址 id=%s" % addr_id)

st, js = req("GET", "/api/book/page?pageNum=1&pageSize=1")
target = ((js.get("data") or {}).get("list") or [{}])[0]
book_id = target.get("id")
book_title = target.get("title")
check(bool(book_id), "选定测试图书 id=%s 《%s》" % (book_id, book_title))

# Redis 可用性预检（缓存断言依赖它）
probe = redis_cli("PING")
check(probe == "PONG", "Redis 可用（redis-cli PING -> %s）" % probe)
if probe != "PONG":
    print("\nRedis 不可用，缓存相关断言无法进行，提前退出。")
    sys.exit(1)

print()
print("=" * 70)
print("第 1 步：支付抽象层（B1）")
print("=" * 70)
oid, ono = create_order(user_token, addr_id, book_id, 1)
check(bool(oid), "下单成功 orderId=%s orderNo=%s" % (oid, ono))

st, js = req("GET", "/api/order/%s" % oid, token=user_token)
before = js.get("data") or {}
check(before.get("status") == 1, "新订单状态为 1（待支付）")

st, js = req("POST", "/api/order/%s/pay" % oid, token=user_token)
check(js.get("code") == 200, "发起支付返回 code=200（message=%s）" % js.get("message"))
pre = js.get("data") or {}
for k in ("payType", "orderNo", "amount", "subject", "paid", "expireSeconds"):
    check(k in pre, "预支付单含字段 %s（值=%s）" % (k, pre.get(k)))
check(pre.get("payType") == "mock", "默认走模拟支付渠道 payType=mock")
check(pre.get("paid") is True, "模拟渠道当场完成支付 paid=true")
check(pre.get("payUrl") is None, "模拟渠道不返回收银台地址 payUrl=null")
check(pre.get("orderNo") == ono, "预支付单订单号与下单一致")
check(abs(float(pre.get("amount")) - float(before.get("totalAmount"))) < 0.001,
      "预支付单金额与订单金额一致（%s）" % pre.get("amount"))

st, js = req("GET", "/api/order/%s" % oid, token=user_token)
check((js.get("data") or {}).get("status") == 2, "支付后订单状态流转为 2（已支付）")

st, js = req("POST", "/api/order/%s/pay" % oid, token=user_token)
check(js.get("code") in (409, 422), "重复支付被拒（code=%s message=%s）" % (js.get("code"), js.get("message")))

st, js = req("POST", "/api/order/99999999/pay", token=user_token)
check(js.get("code") == 404, "支付不存在的订单返回 404（code=%s）" % js.get("code"))

st, js = req("POST", "/api/order/%s/pay" % oid)
check(js.get("code") == 401, "无令牌发起支付返回 401（code=%s）" % js.get("code"))

# 模拟渠道不支持异步通知：必须以纯文本 failure 回复，而不是 500
# 注意本脚本的 req() 默认带 Accept: application/json —— 这正是会让
# produces=text/plain 的映射提前抛 406 的场景，用来防止该缺陷回归
st, js = req("POST", "/api/order/notify?trade_status=TRADE_SUCCESS&out_trade_no=x")
body = (js.get("_raw") or "").strip()
check(st == 200 and body == "failure",
      "模拟渠道收到异步通知时回复纯文本 failure（HTTP=%s body=%r），不制造假的支付成功"
      % (st, js.get("_raw")))
st, js = req("POST", "/api/order/notify?out_trade_no=x")
check(st == 200 and (js.get("_raw") or "").strip() == "failure",
      "缺少签名等参数的回调同样回复 failure（不抛 500，避免网关无限重推）")

print()
print("=" * 70)
print("第 2 步：购物车 Redis 缓存（B2，Cache-Aside 写失效）")
print("=" * 70)
cart_key = "bookstore:cart::%s" % user_id
redis_cli("DEL", cart_key)
clear_cart(user_token)

st, js = req("POST", "/api/cart/add", {"bookId": book_id, "quantity": 2}, token=user_token)
check(js.get("code") == 200, "加购成功（数量 2）")

st, js = req("GET", "/api/cart/list", token=user_token)
items = [c for c in cart_items(user_token) if c.get("bookId") == book_id]
check(len(items) == 1 and items[0].get("quantity") == 2, "首次查询购物车命中目标商品且数量为 2")
cart_id = items[0].get("id")

raw = redis_cli("GET", cart_key)
check(raw not in ("", "(nil)") and not raw.startswith("__ERR__"),
      "查询后购物车已写入 Redis（key=%s）" % cart_key)
check(('"list"' in raw) if raw else False, "缓存值是可读 JSON（含 list 字段，便于 redis-cli 排查）")
ttl = redis_cli("TTL", cart_key)
check(ttl.isdigit() and 0 < int(ttl) <= 300,
      "购物车缓存 TTL=%s 秒（独立短 TTL，≤5 分钟）" % ttl)
print("        缓存片段：%s" % (raw[:110] + "..." if len(raw) > 110 else raw))

# 关键一致性断言：修改数量后必须立刻失效，不能读到旧值
st, js = req("PUT", "/api/cart/%s" % cart_id, {"quantity": 5}, token=user_token)
check(js.get("code") == 200, "修改购物车数量为 5")
items = [c for c in cart_items(user_token) if c.get("bookId") == book_id]
check(bool(items) and items[0].get("quantity") == 5,
      "写后立即读到新数量 5（写失效生效，未读到脏缓存）")
raw2 = redis_cli("GET", cart_key)
check(("5" in raw2) if raw2 else False, "缓存内容已刷新为最新数量")

# 条目级失效：删除后 key 被清掉，再次查询才重新回填
st, js = req("DELETE", "/api/cart/%s" % cart_id, token=user_token)
check(js.get("code") == 200, "删除购物车条目成功")
exists = redis_cli("EXISTS", cart_key)
check(exists == "0", "删除后购物车缓存 key 被清掉（EXISTS=%s）" % exists)
items = cart_items(user_token)
check(len(items) == 0, "再次查询购物车为空（%d 条）" % len(items))
exists = redis_cli("EXISTS", cart_key)
check(exists == "1", "空购物车也被正常回填缓存（EXISTS=%s，避免缓存穿透）" % exists)

print()
print("=" * 70)
print("第 3 步：评价模块 —— 购买资格校验（B3）")
print("=" * 70)
# 先清掉这本书的历史评价，保证计数断言可重复执行
st, js = req("GET", "/api/admin/review/page?bookId=%s&pageSize=50" % book_id, token=admin_token)
old = ((js.get("data") or {}).get("list") or [])
for r in old:
    req("DELETE", "/api/admin/review/%s" % r.get("id"), token=admin_token)
check(True, "清理该书历史评价 %d 条（保证脚本可重复执行）" % len(old))

st, js = req("GET", "/api/review/book/%s" % book_id)
check(st == 200 and js.get("code") == 200, "匿名可读图书评价列表（无需令牌）")
st, js = req("GET", "/api/review/book/%s/summary" % book_id)
check(js.get("code") == 200, "匿名可读评分汇总（无需令牌）")
d = js.get("data") or {}
check(d.get("reviewCount") == 0 and d.get("avgRating") is None,
      "无评价时 avgRating=null、reviewCount=0（不误导性地显示 0 分）")
check(len(d.get("distribution") or []) == 5, "评分分布固定返回 5 个星级")

# 未完成订单不能评价
pending_oid, _ = create_order(user_token, addr_id, book_id, 1)
st, js = req("POST", "/api/review", {"orderId": pending_oid, "bookId": book_id,
                                    "rating": 5, "content": "未完成订单"}, token=user_token)
check(js.get("code") == 422, "待支付订单不能评价（code=%s message=%s）" % (js.get("code"), js.get("message")))

# 不存在的订单
st, js = req("POST", "/api/review", {"orderId": 99999999, "bookId": book_id,
                                    "rating": 5, "content": "x"}, token=user_token)
check(js.get("code") == 404, "评价不存在的订单返回 404（code=%s）" % js.get("code"))

# 未登录
st, js = req("POST", "/api/review", {"orderId": pending_oid, "bookId": book_id, "rating": 5})
check(js.get("code") == 401, "未登录不能发表评价（code=%s）" % js.get("code"))

# 星级越界
st, js = req("POST", "/api/review", {"orderId": pending_oid, "bookId": book_id, "rating": 6},
             token=user_token)
check(js.get("code") == 400, "星级 6 被参数校验拦下（code=%s）" % js.get("code"))
st, js = req("POST", "/api/review", {"orderId": pending_oid, "bookId": book_id, "rating": 0},
             token=user_token)
check(js.get("code") == 400, "星级 0 被参数校验拦下（code=%s）" % js.get("code"))
st, js = req("POST", "/api/review", {"bookId": book_id, "rating": 5}, token=user_token)
check(js.get("code") == 400, "缺少 orderId 被参数校验拦下（code=%s）" % js.get("code"))

print()
print("=" * 70)
print("第 4 步：评价模块 —— 正常评价与聚合（B3）")
print("=" * 70)
oid_a, ono_a = complete_order(user_token, admin_token, addr_id, book_id, 1)
st, js = req("GET", "/api/order/%s" % oid_a, token=user_token)
check((js.get("data") or {}).get("status") == 4, "订单 A 已流转到 4（已完成），具备评价资格")

st, js = req("POST", "/api/review", {"orderId": oid_a, "bookId": book_id, "rating": 5,
                                    "content": "内容扎实，讲解清晰，值得推荐。",
                                    "anonymous": False}, token=user_token)
check(js.get("code") == 200, "发表 5 星评价成功（message=%s）" % js.get("message"))

st, js = req("POST", "/api/review", {"orderId": oid_a, "bookId": book_id, "rating": 4,
                                    "content": "重复评价"}, token=user_token)
check(js.get("code") == 409, "同一订单同一本书重复评价被拒（code=%s message=%s）"
      % (js.get("code"), js.get("message")))

# 订单里没有这本书 → 用已完成订单 A 去评价另一本书
st, js = req("GET", "/api/book/page?pageNum=1&pageSize=2")
other = [b for b in ((js.get("data") or {}).get("list") or []) if b.get("id") != book_id]
if other:
    st, js = req("POST", "/api/review", {"orderId": oid_a, "bookId": other[0].get("id"),
                                        "rating": 5, "content": "越界评价"}, token=user_token)
    check(js.get("code") == 422,
          "订单里不含该书时拒绝评价（code=%s message=%s）" % (js.get("code"), js.get("message")))

# 第二笔订单：3 星 + 匿名 —— 用于验证平均分与匿名替换
oid_b, ono_b = complete_order(user_token, admin_token, addr_id, book_id, 1)
st, js = req("POST", "/api/review", {"orderId": oid_b, "bookId": book_id, "rating": 3,
                                    "content": "整体还行，排版一般，希望再版改进。",
                                    "anonymous": True}, token=user_token)
check(js.get("code") == 200, "发表 3 星匿名评价成功")

st, js = req("GET", "/api/review/book/%s/summary" % book_id)
d = js.get("data") or {}
check(d.get("reviewCount") == 2, "评价数聚合正确 reviewCount=%s" % d.get("reviewCount"))
check(str(d.get("avgRating")) == "4.0",
      "平均分聚合正确（(5+3)/2 = %s，保留一位小数）" % d.get("avgRating"))
dist = d.get("distribution") or []
check([x.get("rating") for x in dist] == [5, 4, 3, 2, 1], "分布按 5→1 星降序返回")
cnt = {x.get("rating"): x.get("count") for x in dist}
check(cnt.get(5) == 1 and cnt.get(3) == 1 and cnt.get(4) == 0 and cnt.get(2) == 0 and cnt.get(1) == 0,
      "各星级分布计数正确（5星1条、3星1条、其余为0）")

st, js = req("GET", "/api/review/book/%s?pageNum=1&pageSize=10" % book_id)
lst = (js.get("data") or {}).get("list") or []
check(len(lst) == 2, "评价列表返回 2 条")
nick = {r.get("rating"): r.get("nickname") for r in lst}
check(nick.get(3) == "匿名用户", "匿名评价在服务端替换为「匿名用户」（实测=%s）" % nick.get(3))
check(bool(nick.get(5)) and nick.get(5) != "匿名用户", "非匿名评价展示真实昵称（%s）" % nick.get(5))
check(all(r.get("anonymous") is not None for r in lst), "列表带出 anonymous 标记供前端展示")

st, js = req("GET", "/api/review/my?pageNum=1&pageSize=10", token=user_token)
d = js.get("data") or {}
check(d.get("total") == 2, "我的评价共 2 条（total=%s）" % d.get("total"))
check(all(r.get("bookTitle") for r in (d.get("list") or [])), "我的评价带出图书标题（避免二次请求）")

st, js = req("GET", "/api/review/order/%s/items" % oid_a, token=user_token)
items = js.get("data") or []
check(js.get("code") == 200 and len(items) >= 1, "订单可评价项查询可用（%d 条）" % len(items))
if items:
    check(items[0].get("reviewed") is True, "已评价条目 reviewed=true")
    check(items[0].get("rating") == 5, "已评价条目回显星级 rating=%s" % items[0].get("rating"))
    check(bool(items[0].get("reviewId")), "已评价条目带出 reviewId（便于跳转查看）")

st, js = req("GET", "/api/review/order/%s/items" % oid_b, token=user_token)
items = js.get("data") or []
if items:
    check(items[0].get("reviewed") is True, "订单 B 的条目同样识别为已评价")

print()
print("=" * 70)
print("第 5 步：评分回填到图书列表 / 详情 / 热门榜（B3）")
print("=" * 70)
st, js = req("GET", "/api/book/%s" % book_id)
d = js.get("data") or {}
check(str(d.get("avgRating")) == "4.0", "图书详情回填平均分 avgRating=%s" % d.get("avgRating"))
check(d.get("reviewCount") == 2, "图书详情回填评价数 reviewCount=%s" % d.get("reviewCount"))

st, js = req("GET", "/api/book/page?pageNum=1&pageSize=50")
lst = (js.get("data") or {}).get("list") or []
hit = [b for b in lst if b.get("id") == book_id]
check(bool(hit), "列表中能找到目标图书")
if hit:
    check(str(hit[0].get("avgRating")) == "4.0", "列表页回填平均分 avgRating=%s" % hit[0].get("avgRating"))
others = [b for b in lst if b.get("id") != book_id]
if others:
    check(all(b.get("avgRating") is None for b in others),
          "无评价图书 avgRating 为 null（不显示假分数）")
    check(all(b.get("reviewCount") == 0 for b in others),
          "无评价图书 reviewCount 显式为 0（前端无需判空）")

st, js = req("GET", "/api/book/hot?limit=6")
hot = js.get("data") or []
check(len(hot) == 6, "热门榜返回 6 条")
check(all(b.get("reviewCount") is not None for b in hot), "热门榜条目均带评分字段（无缺失）")

print()
print("=" * 70)
print("第 6 步：管理端评价管理（B4）")
print("=" * 70)
st, js = req("GET", "/api/admin/review/page?pageNum=1&pageSize=20", token=user_token)
check(js.get("code") == 403, "普通用户访问管理端评价接口返回 403（code=%s）" % js.get("code"))

st, js = req("GET", "/api/admin/review/page?bookId=%s&pageNum=1&pageSize=20" % book_id,
             token=admin_token)
d = js.get("data") or {}
rows = d.get("list") or []
check(d.get("total") == 2, "管理端评价分页可用（total=%s）" % d.get("total"))
check(all(r.get("bookTitle") for r in rows), "管理端带出图书标题")
check(all(r.get("orderNo") for r in rows), "管理端带出订单号（可溯源到真实购买）")
check(all(r.get("userId") is not None for r in rows), "管理端带出评价人 ID（不做匿名处理）")
by_rating = {r.get("rating"): r for r in rows}

st, js = req("GET", "/api/admin/review/page?bookId=%s&rating=5&pageNum=1&pageSize=20" % book_id,
             token=admin_token)
rows5 = (js.get("data") or {}).get("list") or []
check(bool(rows5) and all(r.get("rating") == 5 for r in rows5),
      "按星级筛选生效（5 星命中 %d 条）" % len(rows5))

st, js = req("GET", "/api/admin/review/page?bookId=%s&status=0&pageNum=1&pageSize=20" % book_id,
             token=admin_token)
check(((js.get("data") or {}).get("total") or 0) == 0, "按状态筛选生效（隐藏数为 0）")

st, js = req("GET", "/api/admin/review/page?keyword=%s&pageNum=1&pageSize=20" % q("排版一般"),
             token=admin_token)
check(((js.get("data") or {}).get("total") or 0) >= 1,
      "关键词搜索命中评价内容（total=%s）" % ((js.get("data") or {}).get("total")))

# 隐藏 5 星评价 → 平均分应立即变为 3.0，且图书详情缓存被刷新
rid5 = (by_rating.get(5) or {}).get("id")
check(bool(rid5), "定位到 5 星评价 id=%s" % rid5)
st, js = req("PUT", "/api/admin/review/%s/status?status=0" % rid5, token=admin_token)
check(js.get("code") == 200, "隐藏违规评价成功")

st, js = req("GET", "/api/review/book/%s/summary" % book_id)
d = js.get("data") or {}
check(d.get("reviewCount") == 1, "隐藏后评价数降为 1（reviewCount=%s）" % d.get("reviewCount"))
check(str(d.get("avgRating")) == "3.0", "隐藏后平均分重算为 3.0（实测 %s）" % d.get("avgRating"))

st, js = req("GET", "/api/book/%s" % book_id)
d = js.get("data") or {}
check(str(d.get("avgRating")) == "3.0" and d.get("reviewCount") == 1,
      "图书详情缓存被同步刷新（avgRating=%s reviewCount=%s）" % (d.get("avgRating"), d.get("reviewCount")))

st, js = req("GET", "/api/review/book/%s?pageNum=1&pageSize=10" % book_id)
lst = (js.get("data") or {}).get("list") or []
check(len(lst) == 1, "隐藏评价不再出现在前台列表（%d 条）" % len(lst))

st, js = req("PUT", "/api/admin/review/%s/status?status=0" % rid5, token=admin_token)
check(js.get("code") == 200, "重复隐藏为幂等操作（code=%s，不报错）" % js.get("code"))

st, js = req("PUT", "/api/admin/review/%s/status?status=2" % rid5, token=admin_token)
check(js.get("code") == 400, "非法状态值 2 被拒（code=%s message=%s）" % (js.get("code"), js.get("message")))

st, js = req("GET", "/api/admin/review/page?bookId=%s&status=0&pageNum=1&pageSize=20" % book_id,
             token=admin_token)
check(((js.get("data") or {}).get("total") or 0) == 1, "隐藏后的评价可在「已隐藏」筛选中查到（留痕）")

# 操作日志：管理动作必须留痕
st, js = req("GET", "/api/admin/log/page?module=%s&pageSize=20" % q("评价管理"), token=admin_token)
d = js.get("data") or {}
logs = d.get("list") or []
check(d.get("total", 0) >= 1, "评价管理操作已写入操作日志（%s 条）" % d.get("total"))
check(any(x.get("success") for x in logs), "日志中有成功记录（隐藏 / 恢复 / 删除）")
check(any(not x.get("success") for x in logs),
      "非法状态的失败调用同样被记录（审计不遗漏失败操作）")
check(any(x.get("action") == "变更评价状态" for x in logs), "日志动作文案正确（变更评价状态）")
if logs:
    check(bool(logs[0].get("adminName")), "日志记录管理员标识（%s）" % logs[0].get("adminName"))
    check(all(x.get("module") == "评价管理" for x in logs), "模块筛选精确，未混入其他模块日志")

# 恢复隐藏 + 彻底删除
st, js = req("PUT", "/api/admin/review/%s/status?status=1" % rid5, token=admin_token)
check(js.get("code") == 200, "恢复已隐藏的评价")
st, js = req("GET", "/api/review/book/%s/summary" % book_id)
check((js.get("data") or {}).get("reviewCount") == 2, "恢复后评价数回到 2")

st, js = req("DELETE", "/api/admin/review/%s" % rid5, token=admin_token)
check(js.get("code") == 200, "彻底删除评价成功")
st, js = req("GET", "/api/review/book/%s/summary" % book_id)
check((js.get("data") or {}).get("reviewCount") == 1, "删除后评价数降为 1")

st, js = req("DELETE", "/api/admin/review/99999999", token=admin_token)
check(js.get("code") == 404, "删除不存在的评价返回 404（code=%s）" % js.get("code"))

print()
print("=" * 70)
print("清理：删除本次测试产生的评价，避免污染演示数据")
print("=" * 70)
st, js = req("GET", "/api/admin/review/page?bookId=%s&pageSize=50" % book_id, token=admin_token)
left = ((js.get("data") or {}).get("list") or [])
for r in left:
    req("DELETE", "/api/admin/review/%s" % r.get("id"), token=admin_token)
check(True, "清理剩余测试评价 %d 条，目标图书评分已复位" % len(left))

print()
print("=" * 70)
print("结果汇总：通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
print("=" * 70)
for m in failed:
    print("  [FAIL] " + m)
sys.exit(1 if failed else 0)
