# -*- coding: utf-8 -*-
"""
线上书店后端 —— W1 新增模块端到端验证

覆盖本轮落地的 5 个后端模块，以及订单的扩展字段：
  S4 首页轮播图：匿名可见、管理端 CRUD、下架后即时从前台消失（顺带验证缓存清理链路）
  S5 免运费规则：规则读取、结算预览三档口径、后台改门槛即时生效、下单金额与预览一致
  S1 书单：创建 / 批量加书去重 / 详情 / 公开与私密可见性 / 重名冲突 / 删除
  S2 浏览历史：记录幂等、未登录期间本地记录合并、单条删除与清空
  N7 到货提醒：缺货订阅、有货拒绝、重复订阅冲突、管理端看板与标记已通知、取消幂等
  N3 订单时间线：下单写运费 → 发货写 ship_time → 确认收货写 finish_time

设计原则
--------
· 只走 HTTP 公开接口，不直连数据库、不依赖任何测试后门；
· 每个模块结束时把自己造的数据清理干净（轮播图删除、配置项还原、书单删除、历史清空、
  订阅取消），保证脚本可重复执行，也不给演示库留垃圾数据；
· 金额比较一律走 Decimal，不用浮点相等（0.1+0.2 != 0.3 这类问题在钱上不能容忍）；
· 只认 body.code，不看 HTTP 状态码 —— 本项目约定 HTTP 恒 200，业务码在 body 里。

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
from decimal import Decimal

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


def api(method, path, body=None, token=None):
    """发一次请求，返回 (http_status, payload)。

    payload 解析不出 JSON 时给出 {'_raw': 原文}，这样断言失败时能看到服务端到底回了什么。
    """
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


def q(value):
    """查询串里的中文要先编码，否则 urllib 会抛 UnicodeEncodeError"""
    return urllib.parse.quote(str(value), safe="")


def code_of(payload):
    return payload.get("code")


def msg_of(payload):
    return payload.get("message")


def data_of(payload):
    return payload.get("data")


def money(value):
    return Decimal(str(value)).quantize(Decimal("0.01"))


def redis_cli(*args):
    try:
        out = subprocess.run([REDIS_CLI] + list(args), capture_output=True, timeout=10)
        return out.stdout.decode("utf-8", "replace").strip()
    except Exception as e:  # noqa: BLE001
        return "__ERR__:" + str(e)


def fill_captcha(path, payload):
    """APP 用户登录已强制图形验证码，这里补上 captchaKey + captchaCode。

    验证码是图片，脚本无法自动识别；本地 E2E 直接从 Redis 读回本次验证码明文。
    这只是测试侧取数手段，不依赖任何服务端后门，也不改动应用代码。
    """
    if "/user/login" not in path:
        return payload
    _, js = api("GET", "/api/captcha")
    key = (data_of(js) or {}).get("captchaKey")
    if key:
        code = redis_cli("-h", "127.0.0.1", "-p", "6379", "get", "bookstore:captcha:" + key)
        if code and not code.startswith("__ERR__"):
            return dict(payload, captchaKey=key, captchaCode=code)
    return payload


def login(path, payload):
    _, js = api("POST", path, fill_captcha(path, payload))
    if code_of(js) != 200:
        return None, js
    return (data_of(js) or {}).get("token"), js


# ---------------------------------------------------------------- 通用业务助手

def book_page(**kwargs):
    qs = "&".join("%s=%s" % (k, q(v)) for k, v in kwargs.items())
    _, js = api("GET", "/api/book/page?" + qs)
    return (data_of(js) or {}).get("list") or []


def cart_items(token):
    _, js = api("GET", "/api/cart/list", token=token)
    return (data_of(js) or {}).get("list") or []


def clear_cart(token):
    for item in cart_items(token):
        api("DELETE", "/api/cart/%s" % item.get("id"), token=token)


def ensure_address(token):
    _, js = api("GET", "/api/address/list", token=token)
    rows = data_of(js) or []
    if rows:
        return rows[0].get("id")
    _, js = api("POST", "/api/address/add", {
        "receiver": "模块测试", "phone": "13900000009",
        "province": "广东省", "city": "深圳市", "district": "南山区",
        "detail": "验证路 9 号", "isDefault": 1,
    }, token=token)
    return (data_of(js) or {}).get("id")


def fill_cart(token, book_id, qty=1):
    clear_cart(token)
    api("POST", "/api/cart/add", {"bookId": book_id, "quantity": qty}, token=token)


def settle(token, cart_ids=None):
    body = {"cartIds": cart_ids} if cart_ids else None
    _, js = api("POST", "/api/order/settle-preview", body, token=token)
    return code_of(js), (data_of(js) or {})


def order_of(token, order_id):
    _, js = api("GET", "/api/order/%s" % order_id, token=token)
    return data_of(js) or {}


def banner_list(token=None):
    _, js = api("GET", "/api/banner/list", token=token)
    return code_of(js), (data_of(js) or [])


def admin_banner_list(token):
    _, js = api("GET", "/api/admin/banner/list", token=token)
    return code_of(js), (data_of(js) or [])


def plaza(token=None):
    _, js = api("GET", "/api/booklist/page?pageNum=1&pageSize=50", token=token)
    return code_of(js), (data_of(js) or {})


def history_page(token):
    _, js = api("GET", "/api/history/page?pageNum=1&pageSize=50", token=token)
    return code_of(js), (data_of(js) or {})


# ============================================================================
print("=" * 72)
print("第 0 步：准备数据（登录 / 地址 / 选书）")
print("=" * 72)

admin_token, js = login("/api/admin/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
check(bool(admin_token), "管理员登录成功")

user_token, js = login("/api/user/login", {"phone": USER_PHONE, "password": USER_PWD})
check(bool(user_token), "用户登录成功（%s）" % USER_PHONE)

if not admin_token or not user_token:
    print("\n登录失败，无法继续（管理员响应：%s）" % msg_of(js))
    print("\n通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
    sys.exit(1)

addr_id = ensure_address(user_token)
check(bool(addr_id), "取得收货地址 id=%s" % addr_id)

# 便宜且有货的书：用于「未达包邮门槛」分支
_cheap_pool = book_page(minPrice=1, maxPrice=90, pageSize=50, sort="price_asc")
cheap = next((b for b in _cheap_pool if (b.get("stock") or 0) > 0), None)
# 贵重且有货的书：用于「达到包邮门槛」分支
_rich_pool = book_page(minPrice=99, pageSize=50, sort="price_desc")
rich = next((b for b in _rich_pool if (b.get("stock") or 0) > 0), None)
# 缺货书（价格升序第一本，本库最便宜的恰好缺货）：用于到货提醒
_oos_pool = book_page(pageSize=50, sort="price_asc")
oos = next((b for b in _oos_pool if (b.get("stock") or 0) == 0 and b.get("status") == 1), None)

check(cheap is not None, "选定「便宜有货」图书：%s"
      % (cheap and "id=%s 《%s》 ￥%s 库存 %s" % (cheap.get("id"), cheap.get("title"),
                                                 cheap.get("price"), cheap.get("stock"))))
check(rich is not None, "选定「贵重有货」图书：%s"
      % (rich and "id=%s 《%s》 ￥%s 库存 %s" % (rich.get("id"), rich.get("title"),
                                                 rich.get("price"), rich.get("stock"))))
check(oos is not None, "选定「缺货在售」图书：%s"
      % (oos and "id=%s 《%s》 ￥%s 库存 %s" % (oos.get("id"), oos.get("title"),
                                               oos.get("price"), oos.get("stock"))))

if not cheap or not rich or not oos:
    print("\n缺少必要的测试数据（需要有货/缺货图书），提前退出。")
    print("\n通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
    sys.exit(1)

cheap_id, rich_id, oos_id = cheap.get("id"), rich.get("id"), oos.get("id")

# ============================================================================
print()
print("=" * 72)
print("第 1 步：首页轮播图（S4）")
print("=" * 72)

rule_code, anon_banners = banner_list()
check(rule_code == 200 and isinstance(anon_banners, list) and len(anon_banners) > 0,
      "匿名 GET /api/banner/list 返回 code=200 且非空（%d 张）" % len(anon_banners))
check(all(b.get("status") == 1 for b in anon_banners),
      "前台列表只含上架轮播图（status 全为 1）")
sorts = [b.get("sort") for b in anon_banners]
check(sorts == sorted(sorts), "前台列表按排序值升序返回：%s" % sorts)
check(all(b.get("title") for b in anon_banners), "每张轮播图都有标题（前端可直接渲染）")

st, js = api("GET", "/api/admin/banner/list")
check(code_of(js) == 401, "匿名访问管理端轮播图列表被拒（code=%s，期望 401）" % code_of(js))

admin_code, admin_banners = admin_banner_list(admin_token)
check(admin_code == 200, "管理员可读取管理端列表（含已下架，共 %d 张）" % len(admin_banners))

# ---- 新增 → 前台立即可见（同时验证 @CacheEvict 在事务提交后真的清了缓存）----
created = None
if len(admin_banners) < 8:
    st, js = api("POST", "/api/admin/banner", {
        "title": "端到端测试位",
        "subTitle": "由 verify_extended_features 创建，用完即删",
        "bgColor": "linear-gradient(135deg,#123456,#654321)",
        "linkUrl": "/books?sort=sales",
        "status": 1,
    }, token=admin_token)
    created = data_of(js) or {}
    check(code_of(js) == 200 and created.get("id"),
          "管理员新增轮播图成功 id=%s sort=%s" % (created.get("id"), created.get("sort")))

    _, fresh = banner_list()
    check(any(b.get("id") == created.get("id") for b in fresh),
          "新增后前台立刻可见（说明写操作的后置缓存清理生效，没有读到旧缓存）")

    # ---- 下架 → 前台消失、后台仍在 ----
    st, js = api("PUT", "/api/admin/banner/%s/status?status=0" % created.get("id"), token=admin_token)
    check(code_of(js) == 200, "下架轮播图返回 code=200")
    _, fresh = banner_list()
    check(not any(b.get("id") == created.get("id") for b in fresh),
          "下架后前台列表不再包含该轮播图")
    _, still = admin_banner_list(admin_token)
    check(any(b.get("id") == created.get("id") for b in still),
          "下架后管理端列表仍能看到它（下架 ≠ 删除）")

    st, js = api("PUT", "/api/admin/banner/%s/status?status=9" % created.get("id"), token=admin_token)
    check(code_of(js) == 400, "非法状态值被拒（code=%s，期望 400）" % code_of(js))

    # ---- 恢复上架 → 删除 ----
    api("PUT", "/api/admin/banner/%s/status?status=1" % created.get("id"), token=admin_token)
    st, js = api("DELETE", "/api/admin/banner/%s" % created.get("id"), token=admin_token)
    check(code_of(js) == 200, "删除轮播图返回 code=200")
    _, fresh = banner_list()
    _, still = admin_banner_list(admin_token)
    check(not any(b.get("id") == created.get("id") for b in fresh),
          "删除后前台列表已无该轮播图")
    check(not any(b.get("id") == created.get("id") for b in still),
          "删除后管理端列表也已无该轮播图")
    st, js = api("DELETE", "/api/admin/banner/%s" % created.get("id"), token=admin_token)
    check(code_of(js) == 404, "重复删除返回 404（code=%s）" % code_of(js))
else:
    print("  [SKIP] 轮播图已达上限 8 张，跳过新增/下架/删除分支")

st, js = api("POST", "/api/admin/banner", {
    "title": "非法跳转",
    "linkUrl": "javascript:alert(1)",
}, token=admin_token)
check(code_of(js) == 400, "轮播图跳转地址只允许站内路径或 http(s)（非法值被拒 code=%s）" % code_of(js))

st, js = api("POST", "/api/admin/banner", {
    "title": "非法背景",
    "bgColor": "url(evil.png);color:red",
}, token=admin_token)
check(code_of(js) == 400, "轮播图背景只允许颜色或渐变（注入式取值被拒 code=%s）" % code_of(js))

# ============================================================================
print()
print("=" * 72)
print("第 2 步：免运费规则（S5）")
print("=" * 72)

st, js = api("GET", "/api/order/freight-rule")
rule = data_of(js) or {}
check(code_of(js) == 200, "匿名 GET /api/order/freight-rule 可访问（code=200）")
check(rule.get("enabled") is True, "运费开关为开启（enabled=%s）" % rule.get("enabled"))
check(money(rule.get("freeThreshold")) == money(99), "包邮门槛为 99.00（实际 %s）" % rule.get("freeThreshold"))
check(money(rule.get("baseFee")) == money(8), "基础运费为 8.00（实际 %s）" % rule.get("baseFee"))
check(rule.get("tip") == "满 99 元包邮", "文案为「满 99 元包邮」（实际「%s」）" % rule.get("tip"))

st, js = api("POST", "/api/order/settle-preview", None)
check(code_of(js) == 401, "未登录不能看结算预览（code=%s，期望 401）" % code_of(js))

# ---- 空购物车：不是错误，返回按规则算出的运费 ----
clear_cart(user_token)
code, empty = settle(user_token)
check(code == 200, "空购物车结算预览返回 code=200（空态不是错误）")
check(money(empty.get("goodsAmount")) == money(0), "空购物车商品金额 0.00（实际 %s）" % empty.get("goodsAmount"))
check(money(empty.get("freightAmount")) == money(8), "空购物车运费 8.00（实际 %s）" % empty.get("freightAmount"))
check(money(empty.get("payableAmount")) == money(8), "空购物车应付 = 0 + 运费（实际 %s）" % empty.get("payableAmount"))
check(empty.get("totalQuantity") == 0, "空购物车件数 0（实际 %s）" % empty.get("totalQuantity"))

# ---- 未达门槛：收运费，且给出差额 ----
fill_cart(user_token, cheap_id, 1)
code, low = settle(user_token)
cheap_price = money(cheap.get("price"))
check(code == 200 and money(low.get("goodsAmount")) == cheap_price,
      "未达门槛：商品金额 = 书价 %s" % cheap_price)
check(money(low.get("freightAmount")) == money(8), "未达门槛：运费 8.00（实际 %s）" % low.get("freightAmount"))
check(money(low.get("payableAmount")) == cheap_price + money(8),
      "未达门槛：应付 = 商品 %s + 运费 8.00 = %s（实际 %s）"
      % (cheap_price, cheap_price + money(8), low.get("payableAmount")))
check(low.get("freeShipping") is False, "未达门槛：freeShipping=false")
check(money(low.get("amountToFreeShipping")) == money(99) - cheap_price,
      "未达门槛：提示再买 %s 元包邮（实际 %s）"
      % (money(99) - cheap_price, low.get("amountToFreeShipping")))
check(low.get("totalQuantity") == 1, "未达门槛：件数 1（实际 %s）" % low.get("totalQuantity"))

# ---- 达到门槛：包邮，差额归零 ----
fill_cart(user_token, rich_id, 1)
code, high = settle(user_token)
rich_price = money(rich.get("price"))
check(code == 200 and money(high.get("goodsAmount")) == rich_price,
      "达门槛：商品金额 = 书价 %s" % rich_price)
check(money(high.get("freightAmount")) == money(0), "达门槛：运费 0.00（实际 %s）" % high.get("freightAmount"))
check(high.get("freeShipping") is True, "达门槛：freeShipping=true")
check(money(high.get("payableAmount")) == rich_price,
      "达门槛：应付 = 商品金额（实际 %s）" % high.get("payableAmount"))
check(money(high.get("amountToFreeShipping")) == money(0),
      "达门槛：差额归零（实际 %s）" % high.get("amountToFreeShipping"))

# ---- 后台改门槛：前台立刻跟随（配置项刻意不做缓存）----
st, js = api("PUT", "/api/admin/config/freight.free-threshold",
             {"configValue": "500"}, token=admin_token)
check(code_of(js) == 200, "管理端修改包邮门槛成功（新值 %s）"
      % ((data_of(js) or {}).get("configValue")))
_, js = api("GET", "/api/order/freight-rule")
moved = data_of(js) or {}
check(money(moved.get("freeThreshold")) == money(500),
      "改门槛后前台门槛立刻变为 500.00（实际 %s）" % moved.get("freeThreshold"))
check(moved.get("tip") == "满 500 元包邮", "文案同步更新为「满 500 元包邮」（实际「%s」）" % moved.get("tip"))

code, after = settle(user_token)
check(money(after.get("freightAmount")) == money(8),
      "门槛提到 500 后，原本包邮的 %s 元订单开始收运费 8.00（实际 %s）"
      % (rich_price, after.get("freightAmount")))

st, js = api("PUT", "/api/admin/config/freight.free-threshold",
             {"configValue": "99"}, token=admin_token)
check(code_of(js) == 200, "门槛已还原为 99")
code, restored = settle(user_token)
check(money(restored.get("freightAmount")) == money(0),
      "还原门槛后重新包邮（运费 %s）" % restored.get("freightAmount"))

st, js = api("PUT", "/api/admin/config/freight.no-such-key",
             {"configValue": "1"}, token=admin_token)
check(code_of(js) == 404, "改不存在的配置键返回 404（不做「顺手创建」兜底，code=%s）" % code_of(js))

st, js = api("PUT", "/api/admin/config/freight.base-fee",
             {"configValue": ""}, token=admin_token)
check(code_of(js) == 400, "配置值留空被参数校验拒绝（code=%s，期望 400）" % code_of(js))

st, js = api("PUT", "/api/admin/config/freight.base-fee", {"configValue": "1"})
check(code_of(js) == 401, "匿名修改配置被拒（code=%s，期望 401）" % code_of(js))

st, js = api("GET", "/api/admin/config/list", token=admin_token)
rows = data_of(js) or []
check(code_of(js) == 200 and len(rows) >= 3,
      "管理端配置列表返回 %d 项（含说明与 editable 标记）" % len(rows))
check(all("description" in r and "editable" in r for r in rows),
      "配置列表暴露说明与可改性，后台才能正确置灰只读项")

# ============================================================================
print()
print("=" * 72)
print("第 3 步：书单（S1）")
print("=" * 72)

code, page = plaza()
check(code == 200 and isinstance(page.get("list"), list),
      "匿名 GET /api/booklist/page 可访问（书单广场，共 %s 条）" % page.get("total"))

st, js = api("POST", "/api/booklist", {"name": "匿名不该能建", "isPublic": 1})
check(code_of(js) == 401, "匿名单建书单被拒（code=%s，期望 401）" % code_of(js))

st, js = api("POST", "/api/booklist", {"name": "", "isPublic": 1}, token=user_token)
check(code_of(js) == 400, "书单名为空被参数校验拒绝（code=%s，期望 400）" % code_of(js))

list_name = "端到端书单"
st, js = api("POST", "/api/booklist", {
    "name": list_name, "description": "由 verify_extended_features 创建", "isPublic": 1,
}, token=user_token)
mine = data_of(js) or {}
list_id = mine.get("id")
check(code_of(js) == 200 and bool(list_id), "创建公开书单成功 id=%s name=%s" % (list_id, mine.get("name")))
check(mine.get("isPublic") == 1, "新书单为公开（isPublic=1）")
check(mine.get("bookCount") == 0, "新书单收录数为 0（实际 %s）" % mine.get("bookCount"))

st, js = api("POST", "/api/booklist", {"name": list_name, "isPublic": 1}, token=user_token)
check(code_of(js) == 409, "同一用户重名书单返回 409（code=%s）" % code_of(js))

st, js = api("GET", "/api/booklist/my?pageNum=1&pageSize=50", token=user_token)
mine_page = data_of(js) or {}
check(code_of(js) == 200, "我的书单列表可读（共 %s 个）" % mine_page.get("total"))
check(any(b.get("id") == list_id for b in (mine_page.get("list") or [])),
      "我的书单里能查到刚建的书单")
check(any(b.get("mine") is True for b in (mine_page.get("list") or [])),
      "自己创建的书单被标记 mine=true（前端据此决定是否显示编辑入口）")

code, page = plaza()
check(any(b.get("id") == list_id for b in (page.get("list") or [])),
      "公开书单出现在匿名可见的书单广场")

st, js = api("POST", "/api/booklist/%s/books" % list_id,
             {"bookIds": [cheap_id, rich_id]}, token=user_token)
add1 = data_of(js) or {}
check(code_of(js) == 200 and add1.get("added") == 2 and add1.get("total") == 2,
      "批量加书 2 本全部新增（added=%s total=%s）" % (add1.get("added"), add1.get("total")))

st, js = api("POST", "/api/booklist/%s/books" % list_id,
             {"bookIds": [cheap_id, rich_id]}, token=user_token)
add2 = data_of(js) or {}
check(code_of(js) == 200 and add2.get("added") == 0 and add2.get("total") == 2,
      "重复加同样的书不产生重复条目（added=%s，书单内是集合语义）" % add2.get("added"))

st, js = api("POST", "/api/booklist/%s/books" % list_id, {"bookIds": []}, token=user_token)
check(code_of(js) == 400, "空图书列表被拒（code=%s，期望 400）" % code_of(js))

st, js = api("GET", "/api/booklist/detail/%s" % list_id, token=user_token)
detail = data_of(js) or {}
books = detail.get("books") or []
check(code_of(js) == 200 and len(books) == 2, "书单详情含 %d 本书" % len(books))
check(len(set(b.get("id") for b in books)) == len(books), "书单详情无重复图书")
check(detail.get("bookCount") == 2, "详情里的收录数与实际条目一致（%s）" % detail.get("bookCount"))

st, js = api("GET", "/api/booklist/detail/%s" % list_id)
check(code_of(js) == 200, "公开书单允许匿名查看详情（code=200）")

st, js = api("PUT", "/api/booklist/%s" % list_id, {
    "name": list_name + "（改名）", "description": "改过简介", "isPublic": 1,
}, token=user_token)
check(code_of(js) == 200 and (data_of(js) or {}).get("name") == list_name + "（改名）",
      "改名成功（新名 %s）" % (data_of(js) or {}).get("name"))

st, js = api("PUT", "/api/booklist/%s" % list_id, {
    "name": list_name + "（改名）", "description": "改过简介", "isPublic": 0,
}, token=user_token)
check(code_of(js) == 200 and (data_of(js) or {}).get("isPublic") == 0,
      "切换为私密成功（isPublic=0）")

st, js = api("GET", "/api/booklist/detail/%s" % list_id)
check(code_of(js) == 403, "私密书单对匿名访问返回 403（不是 404 —— 语义是「别人的私密内容」）"
      "（code=%s）" % code_of(js))

code, page = plaza()
check(not any(b.get("id") == list_id for b in (page.get("list") or [])),
      "私密书单不出现在匿名可见的广场")

code, page = plaza(user_token)
check(any(b.get("id") == list_id and b.get("mine") is True for b in (page.get("list") or [])),
      "带登录态看广场时，自己的私密书单会一并返回并标记 mine=true")

st, js = api("GET", "/api/booklist/detail/99999999")
check(code_of(js) == 404, "不存在的书单详情返回 404（code=%s）" % code_of(js))

st, js = api("DELETE", "/api/booklist/%s/books/%s" % (list_id, cheap_id), token=user_token)
check(code_of(js) == 200, "从书单移出图书成功")
st, js = api("GET", "/api/booklist/detail/%s" % list_id, token=user_token)
check(len((data_of(js) or {}).get("books") or []) == 1, "移出后书单只剩 1 本书")

st, js = api("DELETE", "/api/booklist/%s" % list_id, token=user_token)
check(code_of(js) == 200, "删除书单成功")
st, js = api("GET", "/api/booklist/detail/%s" % list_id, token=user_token)
check(code_of(js) == 404, "删除后详情返回 404（code=%s）" % code_of(js))

# 清理：删掉重名冲突测试可能残留的同名书单（正常情况下只留了上面这一个，已删除）

# ============================================================================
print()
print("=" * 72)
print("第 4 步：浏览历史（S2）")
print("=" * 72)

st, js = api("POST", "/api/history", {"bookId": cheap_id})
check(code_of(js) == 401, "未登录不能记录浏览历史（code=%s，期望 401）" % code_of(js))

api("DELETE", "/api/history", token=user_token)  # 先清空，保证断言基线为 0
code, hist = history_page(user_token)
check(code == 200 and hist.get("total") == 0, "清空后浏览历史为空（total=%s）" % hist.get("total"))

st, js = api("POST", "/api/history", {"bookId": cheap_id}, token=user_token)
check(code_of(js) == 200 and (data_of(js) or {}).get("recorded") is True,
      "记录一次浏览返回 recorded=true")
st, js = api("POST", "/api/history", {"bookId": cheap_id}, token=user_token)
check(code_of(js) == 200 and (data_of(js) or {}).get("recorded") is True,
      "重复浏览同一本书返回 recorded=true（幂等）")
code, hist = history_page(user_token)
check(hist.get("total") == 1, "重复浏览只更新浏览时间，不新增记录行（total=%s）" % hist.get("total"))

st, js = api("POST", "/api/history", {"bookId": 99999999}, token=user_token)
check(code_of(js) == 200 and (data_of(js) or {}).get("recorded") is False,
      "浏览不存在的图书不报错、返回 recorded=false（埋点不该打断浏览）")

st, js = api("POST", "/api/history/merge",
             {"bookIds": [cheap_id, rich_id, oos_id]}, token=user_token)
merged = data_of(js) or {}
check(code_of(js) == 200 and merged.get("added") == 2 and merged.get("total") == 3,
      "合并未登录期间的本地记录：3 条里已存在的 1 条不重复计入（added=%s total=%s）"
      % (merged.get("added"), merged.get("total")))
code, hist = history_page(user_token)
rows = hist.get("list") or []
check(hist.get("total") == 3, "合并后历史共 3 条（total=%s）" % hist.get("total"))
check(len({r.get("bookId") for r in rows}) == len(rows), "浏览历史按书去重，无重复行")
# 提交顺序即时间先后：第 0 条最新。因此 rich（第 1 条）应排在 oos（第 2 条）之前。
ids = [r.get("bookId") for r in rows]
check(rich_id in ids and oos_id in ids and ids.index(rich_id) < ids.index(oos_id),
      "按提交顺序还原先后关系（列表中靠前者更新）：rich 位于 oos 之前，实际 %s" % ids)
check(cheap_id in ids, "已存在的记录合并后仍在列表中（合并不丢数据）")
check(all(r.get("title") and r.get("price") is not None for r in rows),
      "历史条目带图书快照字段（标题/价格），前端可直接渲染")

st, js = api("POST", "/api/history/merge", {"bookIds": []}, token=user_token)
check(code_of(js) == 400, "空的合并请求被参数校验拒绝（code=%s，期望 400）" % code_of(js))

st, js = api("DELETE", "/api/history/%s" % rich_id, token=user_token)
check(code_of(js) == 200, "删除单条浏览记录成功")
code, hist = history_page(user_token)
check(hist.get("total") == 2, "单删后剩余 2 条（total=%s）" % hist.get("total"))

st, js = api("DELETE", "/api/history", token=user_token)
check(code_of(js) == 200, "清空浏览历史成功")
code, hist = history_page(user_token)
check(hist.get("total") == 0, "清空后历史为 0（total=%s）" % hist.get("total"))

st, js = api("GET", "/api/history/page")
check(code_of(js) == 401, "未登录不能查看浏览历史（code=%s，期望 401）" % code_of(js))

# ============================================================================
print()
print("=" * 72)
print("第 5 步：到货提醒（N7）")
print("=" * 72)

st, js = api("POST", "/api/stock-notice", {"bookId": oos_id})
check(code_of(js) == 401, "未登录不能订阅到货提醒（code=%s，期望 401）" % code_of(js))

st, js = api("POST", "/api/stock-notice", {"bookId": 99999999}, token=user_token)
check(code_of(js) == 404, "订阅不存在的图书返回 404（code=%s）" % code_of(js))

st, js = api("POST", "/api/stock-notice", {"bookId": rich_id}, token=user_token)
check(code_of(js) == 422, "对「有货」图书订阅返回 422（现在就能买，订阅没意义，code=%s）" % code_of(js))

api("DELETE", "/api/stock-notice/%s" % oos_id, token=user_token)  # 幂等清一遍，避免上轮残留
st, js = api("POST", "/api/stock-notice", {"bookId": oos_id}, token=user_token)
notice = data_of(js) or {}
check(code_of(js) == 200 and notice.get("bookId") == oos_id,
      "对缺货图书订阅成功 bookId=%s" % notice.get("bookId"))
check(notice.get("status") == 0, "新订阅状态为 0（待到货通知，实际 %s）" % notice.get("status"))
check(notice.get("statusText") == "待到货通知",
      "状态文案为「待到货通知」（实际「%s」）" % notice.get("statusText"))
check(notice.get("bookTitle") == oos.get("title"), "订阅记录带图书标题（%s）" % notice.get("bookTitle"))

st, js = api("POST", "/api/stock-notice", {"bookId": oos_id}, token=user_token)
check(code_of(js) == 409, "重复订阅同一本书返回 409（code=%s）" % code_of(js))

st, js = api("GET", "/api/stock-notice/my?pageNum=1&pageSize=50", token=user_token)
mine_notices = data_of(js) or {}
check(code_of(js) == 200, "我的订阅列表可读（共 %s 条）" % mine_notices.get("total"))
check(any(n.get("bookId") == oos_id for n in (mine_notices.get("list") or [])),
      "我的订阅列表含刚订阅的图书")

st, js = api("GET", "/api/admin/stock-notice/summary")
check(code_of(js) == 401, "匿名不能看管理端到货提醒看板（code=%s，期望 401）" % code_of(js))

st, js = api("GET", "/api/admin/stock-notice/summary?keyword=%s&onlyWaiting=true&pageNum=1&pageSize=10"
             % q(oos.get("title")), token=admin_token)
summary = data_of(js) or {}
rows = summary.get("list") or []
row = next((r for r in rows if r.get("bookId") == oos_id), None)
check(code_of(js) == 200, "管理端看板可读（命中 %s 条）" % summary.get("total"))
check(row is not None, "看板按图书聚合，能查到《%s》" % oos.get("title"))
check(row and (row.get("waitingCount") or 0) >= 1,
      "该书等待人数 >= 1（实际 %s）" % (row and row.get("waitingCount")))
check(row and row.get("bookTitle") == oos.get("title"), "看板行带书名，运营可直接按书名补货")

st, js = api("PUT", "/api/admin/stock-notice/%s/notify" % oos_id, token=admin_token)
check(code_of(js) == 200 and (data_of(js) or {}).get("notified", 0) >= 1,
      "标记已通知返回通知人数 %s" % (data_of(js) or {}).get("notified"))
st, js = api("PUT", "/api/admin/stock-notice/%s/notify" % oos_id, token=admin_token)
check(code_of(js) == 200 and (data_of(js) or {}).get("notified") == 0,
      "重复标记幂等：第二次通知 0 人（不报错，实际 %s）" % (data_of(js) or {}).get("notified"))

st, js = api("GET", "/api/stock-notice/my?pageNum=1&pageSize=50", token=user_token)
rows = (data_of(js) or {}).get("list") or []
mine_row = next((n for n in rows if n.get("bookId") == oos_id), None)
check(mine_row is not None and mine_row.get("status") == 1,
      "用户侧订阅状态变为 1 已通知（实际 %s）" % (mine_row and mine_row.get("status")))
check(mine_row is not None and mine_row.get("statusText") == "已通知",
      "状态文案为「已通知」（实际「%s」）" % (mine_row and mine_row.get("statusText")))

st, js = api("GET", "/api/admin/stock-notice/summary?keyword=%s&onlyWaiting=false&pageNum=1&pageSize=10"
             % q(oos.get("title")), token=admin_token)
rows = (data_of(js) or {}).get("list") or []
row = next((r for r in rows if r.get("bookId") == oos_id), None)
check(row is not None and (row.get("waitingCount") or 0) == 0,
      "通知后等待人数归零（实际 %s）" % (row and row.get("waitingCount")))
check(row is not None and (row.get("notifiedCount") or 0) >= 1,
      "已通知人数被保留（运营要能回答「补货后通知了多少人」，实际 %s）"
      % (row and row.get("notifiedCount")))

st, js = api("DELETE", "/api/stock-notice/%s" % oos_id, token=user_token)
check(code_of(js) == 200, "取消订阅返回 code=200")
st, js = api("DELETE", "/api/stock-notice/%s" % oos_id, token=user_token)
check(code_of(js) == 200, "重复取消订阅幂等（code=200，不报错）")

st, js = api("GET", "/api/stock-notice/my?pageNum=1&pageSize=50", token=user_token)
rows = (data_of(js) or {}).get("list") or []
check(not any(n.get("bookId") == oos_id for n in rows), "取消后订阅列表不再包含该书（已取消不给用户看）")

st, js = api("POST", "/api/stock-notice", {"bookId": oos_id}, token=user_token)
check(code_of(js) == 200 and (data_of(js) or {}).get("status") == 0,
      "取消后可重新订阅（复用原记录置回待通知，status=0）")
api("DELETE", "/api/stock-notice/%s" % oos_id, token=user_token)  # 收尾：不留订阅

# ============================================================================
print()
print("=" * 72)
print("第 6 步：订单时间线与运费落库（N3 + S5）")
print("=" * 72)

# 用「未达门槛」的书下单，保证运费非 0，能真正验证运费被写进订单
fill_cart(user_token, cheap_id, 1)
code, preview = settle(user_token)
check(code == 200 and money(preview.get("freightAmount")) == money(8),
      "下单前预览：商品 %s 元、运费 %s 元" % (preview.get("goodsAmount"), preview.get("freightAmount")))

st, js = api("POST", "/api/order/create", {"addressId": addr_id}, token=user_token)
new_order = data_of(js) or {}
order_id = new_order.get("id") or new_order.get("orderId")
check(code_of(js) == 200 and bool(order_id),
      "下单成功 orderId=%s orderNo=%s" % (order_id, new_order.get("orderNo")))

st, js = api("GET", "/api/order/%s" % order_id, token=user_token)
order = data_of(js) or {}
order_freight = money(order.get("freightAmount"))
check(order_freight == money(preview.get("freightAmount")),
      "订单落库运费 %s = 预览运费 %s（预览与下单同一段逻辑，不会「看着免运费、付款多收 8 元」）"
      % (order_freight, money(preview.get("freightAmount"))))
check(money(order.get("payableAmount")) == money(order.get("totalAmount")) + order_freight,
      "应付金额 = 商品小计 %s + 运费 %s = %s（实际 %s）"
      % (order.get("totalAmount"), order_freight,
         money(order.get("totalAmount")) + order_freight, order.get("payableAmount")))
check(order.get("status") == 1, "新订单状态 1（待支付）")
check(order.get("shipTime") is None, "待支付订单发货时间为空")
check(order.get("finishTime") is None, "待支付订单完成时间为空")
check(order.get("payTime") is None, "待支付订单支付时间为空")

st, js = api("POST", "/api/order/%s/pay" % order_id, token=user_token)
pay = data_of(js) or {}
check(code_of(js) == 200, "发起支付成功（渠道 %s）" % pay.get("payType"))
order = order_of(user_token, order_id)
check(order.get("status") == 2, "支付后订单状态 2（已支付，实际 %s）" % order.get("status"))
check(bool(order.get("payTime")), "支付时间已写入（%s）" % order.get("payTime"))

st, js = api("POST", "/api/admin/order/%s/ship" % order_id, token=admin_token)
check(code_of(js) == 200, "管理员发货成功")
order = order_of(user_token, order_id)
check(order.get("status") == 3, "发货后订单状态 3（已发货，实际 %s）" % order.get("status"))
check(bool(order.get("shipTime")), "发货时间已写入 ship_time=%s（时间线第二个节点的真实时刻）"
      % order.get("shipTime"))

st, js = api("POST", "/api/order/%s/confirm" % order_id, token=user_token)
check(code_of(js) == 200, "用户确认收货成功")
order = order_of(user_token, order_id)
check(order.get("status") == 4, "确认收货后订单状态 4（已完成，实际 %s）" % order.get("status"))
check(bool(order.get("finishTime")), "完成时间已写入 finish_time=%s" % order.get("finishTime"))
check(bool(order.get("shipTime")) and bool(order.get("finishTime"))
      and order.get("shipTime") <= order.get("finishTime"),
      "时间线自洽：发货时间 %s ≤ 完成时间 %s" % (order.get("shipTime"), order.get("finishTime")))
check(bool(order.get("payTime")) and order.get("payTime") <= order.get("shipTime"),
      "时间线自洽：支付时间 %s ≤ 发货时间 %s" % (order.get("payTime"), order.get("shipTime")))

st, js = api("GET", "/api/admin/order/page?pageNum=1&pageSize=5", token=admin_token)
rows = (data_of(js) or {}).get("list") or []
admin_row = next((r for r in rows if r.get("id") == order_id), None)
check(code_of(js) == 200, "管理端订单列表可读")
check(admin_row is not None and "freightAmount" in admin_row,
      "管理端订单也带运费字段（后台对账需要看到运费，不是只算商品金额）")
check(admin_row is not None and bool(admin_row.get("shipTime")),
      "管理端订单带发货时间（%s）" % (admin_row and admin_row.get("shipTime")))

# 收尾：购物车清空
clear_cart(user_token)

# ============================================================================
print()
print("=" * 72)
print("通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
if failed:
    print("失败清单：")
    for item in failed:
        print("  - " + item)
print("=" * 72)

sys.exit(1 if failed else 0)
