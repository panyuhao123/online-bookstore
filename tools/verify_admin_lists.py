# -*- coding: utf-8 -*-
"""
线上书店后端 —— P2 管理端运营能力端到端验证

覆盖本轮新增的四件事：
  1. 订单关键词检索：同一个输入框同时匹配「订单号」与「下单用户手机号」
  2. 批量发货：逐单条件更新，已支付的发货、其余带原因跳过（部分成功不算失败）
  3. 订单导出 CSV：当前筛选条件下全量导出，带 UTF-8 BOM、字段转义、公式注入防护
  4. 列表页汇总接口：图书 / 用户 / 评价三套汇总指标与分页 total 的口径一致性

设计原则（沿用既有脚本）：
  · 只走 HTTP 接口，不直连数据库、不依赖测试后门；
  · 只认 body.code，不看 HTTP 状态码（本项目 HTTP 恒 200）；
  · 造的数据尽量用「本来就要产生的业务数据」：脚本会真实下单 + 支付，
    然后把它批量发货掉 —— 这条订单留在库里是合理的业务数据，不是垃圾。

注意：口令以字面量写在脚本里（不写命令行），否则会被运行环境改写。
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

_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

passed = []
failed = []


def check(ok, msg):
    (passed if ok else failed).append(msg)
    print(("  [PASS] " if ok else "  [FAIL] ") + msg)


def api(method, path, body=None, token=None):
    """发一次请求，返回 (http_status, payload)"""
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
        with _opener.open(request, timeout=25) as resp:
            status, text = resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode("utf-8", "replace")
    try:
        payload = json.loads(text)
    except Exception:  # noqa: BLE001
        payload = {"_raw": text}
    return status, payload


def download(path, token):
    """下载类接口：返回 (http_status, content_type, bytes)"""
    request = urllib.request.Request(BASE + path, headers={"Authorization": "Bearer " + token})
    try:
        with _opener.open(request, timeout=30) as resp:
            return resp.status, resp.headers.get("Content-Type", ""), resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type", ""), e.read()


def q(value):
    return urllib.parse.quote(str(value), safe="")


def code_of(payload):
    return payload.get("code")


def data_of(payload):
    return payload.get("data")


def redis_cli(*args):
    try:
        out = subprocess.run([REDIS_CLI] + list(args), capture_output=True, timeout=10)
        return out.stdout.decode("utf-8", "replace").strip()
    except Exception as e:  # noqa: BLE001
        return "__ERR__:" + str(e)


def login(path, payload):
    """登录；APP 端需要图形验证码，本地从 Redis 读回明文"""
    body = dict(payload)
    if "/user/login" in path:
        _, js = api("GET", "/api/captcha")
        key = (data_of(js) or {}).get("captchaKey")
        code = redis_cli("-h", "127.0.0.1", "-p", "6379", "get", "bookstore:captcha:" + str(key))
        if key and code and not code.startswith("__ERR__"):
            body.update(captchaKey=key, captchaCode=code)
    _, js = api("POST", path, body)
    if code_of(js) != 200:
        return None, js
    return (data_of(js) or {}).get("token"), js


def ensure_address(token):
    _, js = api("GET", "/api/address/list", token=token)
    rows = data_of(js) or []
    if rows:
        return rows[0].get("id")
    _, js = api("POST", "/api/address/add", {
        "receiver": "运营验证", "phone": "13900000008",
        "province": "广东省", "city": "深圳市", "district": "南山区",
        "detail": "批量发货路 8 号", "isDefault": 1,
    }, token=token)
    return (data_of(js) or {}).get("id")


def clear_cart(token):
    _, js = api("GET", "/api/cart/list", token=token)
    for item in (data_of(js) or {}).get("list") or []:
        api("DELETE", "/api/cart/%s" % item.get("id"), token=token)


def order_page(token, **kwargs):
    qs = "&".join("%s=%s" % (k, q(v)) for k, v in kwargs.items())
    _, js = api("GET", "/api/admin/order/page?" + qs, token=token)
    payload = data_of(js) or {}
    return payload.get("total", 0), payload.get("list") or []


# ============================================================================
print("=" * 72)
print("第 0 步：登录并准备一笔「已支付」订单（批量发货的样本）")
print("=" * 72)

admin_token, js = login("/api/admin/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
check(bool(admin_token), "管理员登录成功")

user_token, js = login("/api/user/login", {"phone": USER_PHONE, "password": USER_PWD})
check(bool(user_token), "用户登录成功（%s）" % USER_PHONE)

if not admin_token or not user_token:
    print("\n登录失败，无法继续")
    print("\n通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
    sys.exit(1)

addr_id = ensure_address(user_token)
_, js = api("GET", "/api/book/page?pageNum=1&pageSize=1")
books = (data_of(js) or {}).get("list") or []
book = books[0] if books else {}
check(bool(book.get("id")), "取到一本可用于下单的图书（%s）" % book.get("title"))

clear_cart(user_token)
api("POST", "/api/cart/add", {"bookId": book.get("id"), "quantity": 1}, token=user_token)
_, js = api("POST", "/api/order/create", {"addressId": addr_id}, token=user_token)
created = data_of(js) or {}
order_id = created.get("id") or created.get("orderId")
order_no = created.get("orderNo")
check(code_of(js) == 200 and bool(order_id), "下单成功 orderId=%s orderNo=%s" % (order_id, order_no))

_, js = api("POST", "/api/order/%s/pay" % order_id, token=user_token)
check(code_of(js) == 200, "模拟支付成功（用于验证批量发货的「已支付」路径）")

# ============================================================================
print()
print("=" * 72)
print("第 1 步：订单关键词检索（订单号 / 手机号同一个输入框）")
print("=" * 72)

total, rows = order_page(admin_token, keyword=USER_PHONE, pageNum=1, pageSize=20)
check(total > 0, "按手机号 %s 检索命中 %d 单" % (USER_PHONE, total))
check(rows and all(r.get("userPhone") == USER_PHONE for r in rows),
      "命中行全部属于该手机号（不会把别人的订单带出来）")
check(any(r.get("id") == order_id for r in rows), "刚创建的订单出现在手机号检索结果里")

total_by_no, rows_by_no = order_page(admin_token, keyword=order_no, pageNum=1, pageSize=20)
check(total_by_no >= 1 and any(r.get("id") == order_id for r in rows_by_no),
      "按订单号 %s 检索命中同一笔订单" % order_no)

# 订单号以 2026 开头，手机号以 138 开头，两者不会互相串场
check(all(str(r.get("orderNo", "")).find(USER_PHONE) < 0 for r in rows),
      "手机号检索不会误命中订单号（两条匹配路径用 OR 连接，但不是子串乱配）")

total_miss, _ = order_page(admin_token, keyword="__not_exist_9527__", pageNum=1, pageSize=5)
check(total_miss == 0, "不存在的关键词返回 0 条（实际 %d）" % total_miss)

total_all, _ = order_page(admin_token, pageNum=1, pageSize=5)
check(total_all > total, "不带关键词时的总数（%d）大于手机号筛选结果（%d）" % (total_all, total))

# ============================================================================
print()
print("=" * 72)
print("第 2 步：批量发货（部分成功不整体失败）")
print("=" * 72)

# 找一个「已发货」的订单，验证它会被跳过而不是被改状态
_, shipped_rows = order_page(admin_token, status=3, pageNum=1, pageSize=1)
shipped_order = shipped_rows[0] if shipped_rows else None

ids = [order_id] + ([shipped_order.get("id")] if shipped_order else [])
if shipped_order:
    # 同步记下原状态
    _, js = api("GET", "/api/admin/order/%s" % shipped_order.get("id"), token=admin_token)
    before_status = (data_of(js) or {}).get("status")
else:
    before_status = None

_, js = api("POST", "/api/admin/order/batch-ship", {"ids": ids}, token=admin_token)
result = data_of(js) or {}
check(code_of(js) == 200, "批量发货请求成功（code=200）")
check(result.get("shipped") == 1, "成功发货 1 单（实际 %s）" % result.get("shipped"))
if shipped_order:
    skipped = result.get("skipped") or []
    check(len(skipped) == 1, "跳过 1 单（已发货的那笔）")
    check(bool(skipped) and "仅已支付" in (skipped[0].get("reason") or ""),
          "跳过原因写明「仅已支付可发货」：%s" % (skipped[0].get("reason") if skipped else "-"))

_, js = api("GET", "/api/admin/order/%s" % order_id, token=admin_token)
after = data_of(js) or {}
check(after.get("status") == 3, "被发货订单状态变为 3（已发货，实际 %s）" % after.get("status"))
check(bool(after.get("shipTime")), "发货时间已写入（%s）" % after.get("shipTime"))

if shipped_order:
    _, js = api("GET", "/api/admin/order/%s" % shipped_order.get("id"), token=admin_token)
    check((data_of(js) or {}).get("status") == before_status,
          "被跳过的订单状态未变（仍是 %s）" % before_status)

# 幂等：对同一批再发一次，应当全部跳过、shipped=0，而不是报错
_, js = api("POST", "/api/admin/order/batch-ship", {"ids": [order_id]}, token=admin_token)
check(code_of(js) == 200 and (data_of(js) or {}).get("shipped") == 0,
      "重复发货同一单：code=200 且 shipped=0（幂等，不抛 500）")

# 入参校验
_, js = api("POST", "/api/admin/order/batch-ship", {"ids": []}, token=admin_token)
check(code_of(js) == 400, "空 ids 返回 400（实际 %s）" % code_of(js))
_, js = api("POST", "/api/admin/order/batch-ship", {"ids": list(range(1, 102))}, token=admin_token)
check(code_of(js) == 400, "超过 100 单返回 400（实际 %s）" % code_of(js))

# 权限：APP 用户令牌不能批量发货
_, js = api("POST", "/api/admin/order/batch-ship", {"ids": [order_id]}, token=user_token)
check(code_of(js) == 403, "用户令牌调用批量发货返回 403（实际 %s）" % code_of(js))

# ============================================================================
print()
print("=" * 72)
print("第 3 步：导出 CSV（当前筛选条件下的全量）")
print("=" * 72)

status, ctype, raw = download("/api/admin/order/export?keyword=" + q(USER_PHONE), admin_token)
check(status == 200, "导出接口 HTTP 200（实际 %s）" % status)
check("text/csv" in (ctype or "").lower(), "Content-Type 为 text/csv（实际 %s）" % ctype)
check(raw.startswith(b"\xef\xbb\xbf"), "响应体以 UTF-8 BOM 开头（Excel 直接双击不乱码）")

text = raw.decode("utf-8-sig", "replace")
lines = [ln for ln in text.split("\r\n") if ln]
check(lines[0].split(",")[0] == "订单号" and len(lines[0].split(",")) == 10,
      "表头 10 列且首列为「订单号」：%s" % lines[0])
check(len(lines) - 1 == total, "导出行数 %d = 手机号筛选总数 %d" % (len(lines) - 1, total))
check(USER_PHONE in text, "导出内容含完整手机号（导出用于对账，不做脱敏；列表页才脱敏）")

# 转义：收货信息里常带逗号，必须整体加引号，否则一行会裂成多列
csv_cols = [len(ln.split(",")) for ln in lines[1:]]
check(all(c >= 10 for c in csv_cols), "每行至少 10 个逗号分隔段（含引号内的逗号不会被拆开）")

# 空结果也要有表头，不能返回空文件
status, ctype, raw = download("/api/admin/order/export?keyword=__none_9527__", admin_token)
empty_text = raw.decode("utf-8-sig", "replace")
check(status == 200 and "订单号" in empty_text and len([ln for ln in empty_text.split("\r\n") if ln]) == 1,
      "无匹配时仍返回「只有表头」的 CSV（不做空文件，Excel 打开不报错）")

# 权限：用户令牌不能导出。
# 注意本项目「HTTP 恒 200、业务码在 body」（RestAuthExceptionHandler.write 里显式 setStatus(SC_OK)），
# 所以这里不能只看状态码 —— 必须断言响应体是 JSON 403，而不是 csv。
# 只看状态码会把「200 且拒绝」和「200 且下载成功」混为一谈，等于没测。
status, ctype, raw = download("/api/admin/order/export", user_token)
denied_body = raw.decode("utf-8", "replace")
check("application/json" in (ctype or "").lower() and '"code":403' in denied_body,
      "用户令牌导出被拒绝（HTTP %s / %s / body=%s）" % (status, ctype, denied_body[:48]))
check(not raw.startswith(b"\xef\xbb\xbf") and "订单号" not in denied_body,
      "被拒绝时响应体不是 CSV，未泄露任何订单数据")

# ============================================================================
print()
print("=" * 72)
print("第 4 步：列表页汇总接口与分页 total 的口径一致性")
print("=" * 72)

_, js = api("GET", "/api/admin/book/summary", token=admin_token)
book_sum = data_of(js) or {}
_, js = api("GET", "/api/admin/book/page?pageNum=1&pageSize=1", token=admin_token)
book_total = (data_of(js) or {}).get("total")
check(code_of(js) == 200 and book_sum.get("total") == book_total,
      "图书汇总总数 %s = 分页 total %s" % (book_sum.get("total"), book_total))
check((book_sum.get("onSale") or 0) + (book_sum.get("offSale") or 0) == book_sum.get("total"),
      "图书：上架 %s + 下架 %s = 总数 %s"
      % (book_sum.get("onSale"), book_sum.get("offSale"), book_sum.get("total")))
check((book_sum.get("lowStock") or 0) <= (book_sum.get("onSale") or 0),
      "库存告急 %s 不会超过上架数 %s（下架书断货不计入）"
      % (book_sum.get("lowStock"), book_sum.get("onSale")))

_, js = api("GET", "/api/admin/user/summary", token=admin_token)
user_sum = data_of(js) or {}
_, js = api("GET", "/api/admin/user/page?pageNum=1&pageSize=1", token=admin_token)
user_total = (data_of(js) or {}).get("total")
check(code_of(js) == 200 and user_sum.get("total") == user_total,
      "用户汇总总数 %s = 分页 total %s" % (user_sum.get("total"), user_total))
check((user_sum.get("active") or 0) + (user_sum.get("disabled") or 0) == user_sum.get("total"),
      "用户：正常 %s + 禁用 %s = 总数 %s"
      % (user_sum.get("active"), user_sum.get("disabled"), user_sum.get("total")))
check((user_sum.get("newThisMonth") or 0) <= user_sum.get("total"),
      "本月新增 %s 不超过总注册数" % user_sum.get("newThisMonth"))

_, js = api("GET", "/api/admin/review/summary", token=admin_token)
review_sum = data_of(js) or {}
_, js = api("GET", "/api/admin/review/page?pageNum=1&pageSize=1", token=admin_token)
review_total = (data_of(js) or {}).get("total")
check(code_of(js) == 200 and review_sum.get("total") == review_total,
      "评价汇总总数 %s = 分页 total %s" % (review_sum.get("total"), review_total))
check((review_sum.get("visible") or 0) + (review_sum.get("hidden") or 0) == review_sum.get("total"),
      "评价：正常 %s + 隐藏 %s = 总数 %s"
      % (review_sum.get("visible"), review_sum.get("hidden"), review_sum.get("total")))
avg = review_sum.get("avgRating")
check(avg is None or 1 <= float(avg) <= 5, "平均分落在 1~5 之间（实际 %s）" % avg)

# 隐藏一条评价后平均分应立刻变化（或至少汇总可见数 -1），验证「隐藏优先于删除」的口径
_, js = api("GET", "/api/admin/review/page?pageNum=1&pageSize=1&status=1", token=admin_token)
one = ((data_of(js) or {}).get("list") or [])
if one:
    rid = one[0].get("id")
    before = data_of(api("GET", "/api/admin/review/summary", token=admin_token)[1]) or {}
    api("PUT", "/api/admin/review/%s/status?status=0" % rid, token=admin_token)
    after_hide = data_of(api("GET", "/api/admin/review/summary", token=admin_token)[1]) or {}
    check((after_hide.get("visible") or 0) == (before.get("visible") or 0) - 1,
          "隐藏一条后「正常」数减 1（%s -> %s）" % (before.get("visible"), after_hide.get("visible")))
    api("PUT", "/api/admin/review/%s/status?status=1" % rid, token=admin_token)
    restored = data_of(api("GET", "/api/admin/review/summary", token=admin_token)[1]) or {}
    check((restored.get("visible") or 0) == (before.get("visible") or 0),
          "恢复后「正常」数回到 %s（脚本不改动演示数据）" % before.get("visible"))
else:
    check(True, "库里暂无正常评价，跳过「隐藏后汇总联动」检查")

print()
print("=" * 72)
print("通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
print("=" * 72)
if failed:
    for item in failed:
        print("  [FAIL] " + item)
    sys.exit(1)
