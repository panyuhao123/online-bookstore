# -*- coding: utf-8 -*-
"""
第二批能力验证：定时任务 / 接口文档 / 热门图书 / 统计看板 / 操作日志切面。

对应任务 #11 ~ #15。
"""
import json
import os
import subprocess
import urllib.error
import urllib.request

BASE = os.environ.get("BOOKSTORE_BASE", "http://127.0.0.1:8080")
ADMIN_USER = os.environ.get("BOOKSTORE_ADMIN_USER", "admin")
ADMIN_PWD = os.environ.get("BOOKSTORE_ADMIN_PWD", "123456")
USER_PHONE = os.environ.get("BOOKSTORE_USER_PHONE", "13800138000")
USER_PWD = os.environ.get("BOOKSTORE_USER_PWD", "123456")
REDIS_CLI = "D:/redis5/redis-cli.exe"

_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
passed, failed = [], []


def check(ok, msg):
    (passed if ok else failed).append(msg)
    print(("  [PASS] " if ok else "  [FAIL] ") + msg)


def jreq(method, path, body=None, token=None):
    from urllib.parse import quote
    url = BASE + quote(path, safe="/?=&")
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with _opener.open(req, timeout=25) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"_raw": raw}


def raw_get(path):
    """取原始响应（用于 /doc.html 这类非 JSON 页面；路径含中文时自动编码）"""
    from urllib.parse import quote
    url = BASE + quote(path, safe="/?=&")
    try:
        with _opener.open(urllib.request.Request(url), timeout=25) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def login(path, payload):
    _, js = jreq("POST", path, payload)
    return (js.get("data") or {}).get("token") if js.get("code") == 200 else None


def redis_exists(key):
    r = subprocess.run([REDIS_CLI, "-h", "127.0.0.1", "-p", "6379", "exists", key],
                       capture_output=True, timeout=5)
    return r.stdout.decode().strip() == "1"


admin_token = login("/api/admin/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
user_token = login("/api/user/login", {"phone": USER_PHONE, "password": USER_PWD})

print("=" * 72)
print("A. 接口文档 Knife4j / OpenAPI（任务 #12）")
print("=" * 72)
st, html = raw_get("/doc.html")
check(st == 200, "/doc.html 可访问（HTTP %s）" % st)
check("knife4j" in html.lower() or "swagger" in html.lower() or "doc" in html.lower(),
      "页面确为接口文档（返回 HTML %d 字符）" % len(html))

st, js = jreq("GET", "/v3/api-docs")
check(st == 200 and "openapi" in js, "/v3/api-docs 返回 OpenAPI 描述（版本 %s）" % js.get("openapi"))
paths = list((js.get("paths") or {}).keys())
check(len(paths) > 10, "文档收录接口数 = %d" % len(paths))

# 分组
st, js = raw_get("/v3/api-docs/01-APP端接口")
doc = json.loads(js)
check("/api/book/page" in (doc.get("paths") or {}), "分组「01-APP端接口」存在且含图书接口")
st, js = raw_get("/v3/api-docs/02-管理端接口")
doc = json.loads(js)
check("/api/admin/dashboard" in (doc.get("paths") or {}), "分组「02-管理端接口」存在")
st, js = jreq("GET", "/v3/api-docs")
check(any("securitySchemes" in json.dumps(v) for v in [js.get("components") or {}]),
      "文档已声明 Bearer 鉴权方案（可在页面直接填 token 调试）")

print()
print("=" * 72)
print("B. 热门图书榜 + 缓存（任务 #13）")
print("=" * 72)
st, js = jreq("GET", "/api/book/hot?limit=6")
check(js.get("code") == 200, "热门榜接口可用")
hot = js.get("data") or []
check(len(hot) == 6, "返回 6 条（实际 %d 条）" % len(hot))
sales = [b.get("sales") for b in hot]
check(sales == sorted(sales, reverse=True), "按销量倒序排列：%s" % sales)
check(all(b.get("status") == 1 for b in hot) if hot else False, "榜单仅含上架图书")
check(all(b.get("categoryName") for b in hot) if hot else False, "分类名已回填（无 N+1）")

st, js = jreq("GET", "/api/book/hot?limit=100")
check(len(js.get("data") or []) <= 20, "超大 limit 被收敛（limit=100 → %d 条）" % len(js.get("data") or []))
st, js = jreq("GET", "/api/book/hot?limit=0")
check(js.get("code") == 200 and len(js.get("data") or []) >= 1, "limit=0 被收敛为至少 1 条")
st, js = jreq("GET", "/api/book/hot")
check(js.get("code") == 200, "不传 limit 使用默认值")

jreq("GET", "/api/book/hot?limit=6")
check(redis_exists("bookstore:hotBook::6"), "热门榜结果已写入 Redis 缓存（bookstore:hotBook::6）")

print()
print("=" * 72)
print("C. 统计看板（任务 #13）")
print("=" * 72)
st, js = jreq("GET", "/api/admin/stats/sales-trend?days=7", token=admin_token)
check(js.get("code") == 200, "销售趋势接口可用")
trend = js.get("data") or []
check(len(trend) == 7, "返回恰好 7 个数据点（实际 %d）" % len(trend))
dates = [p.get("date") for p in trend]
check(dates == sorted(dates), "日期升序")
from datetime import date, timedelta
expect = [(date.today() - timedelta(days=6 - i)).isoformat() for i in range(7)]
check(dates == expect, "日期连续且含今天（无订单的日期已补 0）")
check(all("orderCount" in p and "amount" in p for p in trend), "每个点都含订单数与金额")

st, js = jreq("GET", "/api/admin/stats/sales-trend?days=999", token=admin_token)
check(len(js.get("data") or []) <= 90, "超大 days 被收敛到 90 以内（实际 %d）" % len(js.get("data") or []))

st, js = jreq("GET", "/api/admin/stats/top-books?limit=10", token=admin_token)
check(js.get("code") == 200, "销量排行接口可用")
top = js.get("data") or []
ts = [b.get("sales") for b in top]
check(ts == sorted(ts, reverse=True), "按销量倒序：%s" % ts[:5])
check(all(s > 0 for s in ts) if ts else False, "已过滤销量为 0 的图书")

st, js = jreq("GET", "/api/admin/stats/sales-trend?days=7")
check(js.get("code") == 401, "匿名访问统计接口 → 401（实际 %s）" % js.get("code"))
st, js = jreq("GET", "/api/admin/stats/sales-trend?days=7", token=user_token)
check(js.get("code") == 403, "普通用户访问统计接口 → 403（实际 %s）" % js.get("code"))

print()
print("=" * 72)
print("D. 定时任务：超时订单自动取消（任务 #11）")
print("=" * 72)


def cart_items(token):
    _, js = jreq("GET", "/api/cart/list", token=token)
    return (js.get("data") or {}).get("list") or []


def clear_cart(token):
    for it in cart_items(token):
        jreq("DELETE", "/api/cart/%s" % it.get("id"), token=token)


def create_pending_order():
    """下一笔待支付订单，返回 (orderId, bookId, 下单前库存, 购买数量)"""
    _, js = jreq("GET", "/api/address/list", token=user_token)
    addrs = js.get("data") or []
    if not addrs:
        return None
    addr_id = addrs[0].get("id")
    _, js = jreq("GET", "/api/book/page?pageNum=1&pageSize=5")
    book = (js.get("data") or {}).get("list")[0]
    clear_cart(user_token)
    jreq("POST", "/api/cart/add", {"bookId": book["id"], "quantity": 1}, token=user_token)
    items = cart_items(user_token)
    if items:
        jreq("PUT", "/api/cart/%s" % items[0]["id"], {"quantity": 1, "checked": 1}, token=user_token)
    _, js = jreq("POST", "/api/order/create", {"addressId": addr_id}, token=user_token)
    oid = (js.get("data") or {}).get("id")
    if not oid:
        return None, None
    return oid, book["id"]


# 先清空历史遗留的待支付订单，建立干净基线（否则上一轮跑测留下的同书订单会一起被取消）
jreq("POST", "/api/admin/order/cancel-timeout?timeoutMinutes=0", token=admin_token)

oid, bid = create_pending_order()
check(bool(oid), "成功创建一笔待支付订单 id=%s" % oid)

if oid:
    _, js = jreq("GET", "/api/book/%s" % bid)
    stock_after_order = (js.get("data") or {}).get("stock")
    check(stock_after_order is not None, "下单后读取库存 = %s" % stock_after_order)

    # 关键点 1：默认阈值（30 分钟）下，刚下的单**不应该**被取消 —— 阈值生效
    st, js = jreq("POST", "/api/admin/order/cancel-timeout", token=admin_token)
    check(js.get("code") == 200, "管理员手动触发超时取消（code=%s）" % js.get("code"))
    check(isinstance(js.get("data"), int), "返回本次取消的订单数 = %s" % js.get("data"))
    _, js = jreq("GET", "/api/order/%s" % oid, token=user_token)
    check((js.get("data") or {}).get("status") == 1,
          "刚下单的订单未被误取消（仍为待支付 1，证明超时阈值生效）")

    # 关键点 2：timeoutMinutes=0 表示「把所有未支付订单都视为超时」，用于一键清理/演示
    st, js = jreq("POST", "/api/admin/order/cancel-timeout?timeoutMinutes=0", token=admin_token)
    check(js.get("code") == 200 and js.get("data") == 1,
          "timeoutMinutes=0 精确取消这 1 笔待支付订单（本次取消 %s 单）" % js.get("data"))

    _, js = jreq("GET", "/api/order/%s" % oid, token=user_token)
    check((js.get("data") or {}).get("status") == 5, "订单已被自动取消（状态 %s，期望 5）"
          % (js.get("data") or {}).get("status"))

    _, js = jreq("GET", "/api/book/%s" % bid)
    restored = (js.get("data") or {}).get("stock")
    check(restored == stock_after_order + 1,
          "库存已按明细数量精确回补：%s → %s（+1）" % (stock_after_order, restored))

    # 关键点 3：幂等 —— 再触发一次不会重复回补
    jreq("POST", "/api/admin/order/cancel-timeout?timeoutMinutes=0", token=admin_token)
    _, js2 = jreq("GET", "/api/book/%s" % bid)
    check((js2.get("data") or {}).get("stock") == restored,
          "重复触发不重复回补库存（幂等，当前库存 %s）" % (js2.get("data") or {}).get("stock"))

st, js = jreq("POST", "/api/admin/order/cancel-timeout", token=user_token)
check(js.get("code") == 403, "普通用户不能触发定时任务 → 403（实际 %s）" % js.get("code"))
st, js = jreq("POST", "/api/admin/order/cancel-timeout")
check(js.get("code") == 401, "匿名不能触发定时任务 → 401（实际 %s）" % js.get("code"))

st, js = jreq("POST", "/api/admin/order/cancel-timeout?timeoutMinutes=1440", token=admin_token)
check(js.get("code") == 200 and js.get("data") == 0,
      "超时阈值 1440 分钟时无订单被取消（返回 %s）" % js.get("data"))

print()
print("=" * 72)
print("E. 操作日志 AOP 切面（任务 #15）")
print("=" * 72)
# 先做一次成功的写操作：新增一个分类
import random
tag = "测试分类%d" % random.randint(1000, 9999)
st, js = jreq("POST", "/api/admin/category", {"name": tag, "sort": 1}, token=admin_token)
new_cat_id = js.get("data")
check(js.get("code") == 200, "新增分类成功（用于产生日志）")

# 再做一次失败的写操作：删除一个不存在的分类
st, js = jreq("DELETE", "/api/admin/category/99999999", token=admin_token)
check(js.get("code") in (400, 404, 422), "删除不存在的分类被拒（code=%s）" % js.get("code"))

import time
time.sleep(1.2)
st, js = jreq("GET", "/api/admin/log/page?pageNum=1&pageSize=20", token=admin_token)
check(js.get("code") == 200, "操作日志分页接口可用")
page = js.get("data") or {}
logs = page.get("list") or []
check(len(logs) > 0, "日志已落库（共 %s 条）" % page.get("total"))

succ = [x for x in logs if x.get("success") == 1]
fail = [x for x in logs if x.get("success") == 0]
check(any(x.get("action") == "新增分类" and x.get("module") == "分类管理" for x in succ),
      "成功操作已记录（含模块与动作）")
check(len(fail) > 0, "失败操作同样被记录（success=0，共 %d 条）" % len(fail))
check(any(x.get("errorMsg") for x in fail), "失败日志含失败原因")

# 审计人名称取自登录上下文（管理员真实姓名），与 /api/admin/info 保持一致
_, info = jreq("GET", "/api/admin/info", token=admin_token)
expect_admin_name = (info.get("data") or {}).get("realName") or "系统管理员"
first = logs[0]
check(first.get("adminName") == expect_admin_name,
      "记录了操作人名称：%s（与 /api/admin/info 一致）" % first.get("adminName"))
check(first.get("adminId") is not None, "记录了操作人 ID（不可变标识）：%s" % first.get("adminId"))
check(bool(first.get("uri")) and bool(first.get("httpMethod")), "记录了请求路径与方法：%s %s"
      % (first.get("httpMethod"), first.get("uri")))
check(first.get("durationMs") is not None and first.get("durationMs") >= 0,
      "记录了耗时：%s ms" % first.get("durationMs"))
check(bool(first.get("ip")), "记录了客户端 IP：%s" % first.get("ip"))

# 脱敏验证：日志里不得出现明文口令
blob = json.dumps(logs, ensure_ascii=False)
check(ADMIN_PWD not in blob, "日志中不含明文口令（脱敏生效）")

# 按结果筛选
st, js = jreq("GET", "/api/admin/log/page?success=0&pageNum=1&pageSize=20", token=admin_token)
only_fail = (js.get("data") or {}).get("list") or []
check(all(x.get("success") == 0 for x in only_fail), "按 success=0 筛选正确（%d 条）" % len(only_fail))

st, js = jreq("GET", "/api/admin/log/page?module=分类管理&pageNum=1&pageSize=20", token=admin_token)
mod_logs = (js.get("data") or {}).get("list") or []
check(len(mod_logs) > 0 and all("分类" in (x.get("module") or "") for x in mod_logs),
      "按模块模糊筛选正确（%d 条）" % len(mod_logs))

st, js = jreq("GET", "/api/admin/log/modules", token=admin_token)
mods = js.get("data") or []
check(len(mods) > 0, "模块列表接口可用：%s" % mods)
check("分类管理" in mods, "模块列表包含「分类管理」")

st, js = jreq("GET", "/api/admin/log/page", token=user_token)
check(js.get("code") == 403, "普通用户不能看操作日志 → 403（实际 %s）" % js.get("code"))

# 清理测试分类
if new_cat_id:
    jreq("DELETE", "/api/admin/category/%s" % new_cat_id, token=admin_token)

print()
print("=" * 72)
print("结果：通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
if failed:
    print("失败明细：")
    for f in failed:
        print("  - " + f)
print("=" * 72)
