# -*- coding: utf-8 -*-
"""
线上书店后端 —— 缓存「事务提交后失效」验证（批 6）

背景：
  带 @Transactional 的写操作（下单、改书、改分类、加购…）同时带 @CacheEvict。
  Spring 默认在「方法返回后、事务提交前」执行清理，于是存在这样一个窗口：
      T1 扣完库存 → 清空 book 缓存（尚未提交）
      T2 读图书详情 → 未命中 → 查库（看不到 T1 未提交的扣减）→ 把旧值回填缓存
      T1 提交
  结果缓存里是旧值，且要等 TTL（默认 30 分钟）才过期。
  批 6 把清理动作推迟到「事务提交成功之后」，本脚本验证该改动。

验证目标：
  A. 形态：/actuator/info 能反映当前缓存清理时机（可回退开关真的生效）；
  B. 正常路径：写操作成功提交后，相关缓存被清理，后续读到的是新值；
  C. 失败路径：事务回滚时不清理缓存（数据没变，缓存本来与库一致）；
  D. 不回归：购物车 Cache-Aside 的「写失效」仍然生效；
  E. 对照形态：以 CACHE_EVICT_AFTER_COMMIT=false 启动的实例显示旧行为且功能正常。

方法学说明（重要）：
  本脚本验证的是「行为」，事务提交「时刻」的先后顺序由单元测试
  TransactionAwareCacheManagerTest 精确覆盖（提交前不清 / 提交后清 / 回滚不清 /
  提交后失败不上抛）。端到端无法稳定复现微秒级的时序窗口，因此不在此重复造断言。

⚠️ 本脚本会：
   · 临时修改一本图书的价格再改回原值（即使中途异常也会在退出时还原）；
   · 触发一次必然失败的下单（cartIds 不存在，不会产生订单数据）；
   · 临时往购物车加一条记录，随后删除（净变化为零）。

用法：
  python tools/verify_cache_commit.py
  python tools/verify_cache_commit.py --legacy-base=http://127.0.0.1:8083
       （传入 --legacy-base 时，额外校验一个以
         CACHE_EVICT_AFTER_COMMIT=false 启动的对照实例。）

注意：口令以字面量写在脚本文件里（不写命令行），否则会被运行环境改写。
"""
import atexit
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("BOOKSTORE_BASE", "http://127.0.0.1:8080")
REDIS_CLI = os.environ.get("BOOKSTORE_REDIS_CLI", "D:/redis5/redis-cli.exe")

ADMIN_USER = os.environ.get("BOOKSTORE_ADMIN_USER", "admin")
ADMIN_PWD = os.environ.get("BOOKSTORE_ADMIN_PWD", "123456")
USER_PHONE = os.environ.get("BOOKSTORE_USER_PHONE", "13800138000")
USER_PWD = os.environ.get("BOOKSTORE_USER_PWD", "123456")

# 与 application.yml 中 bookstore.cache 的默认值保持一致
CACHE_PREFIX = "bookstore:"
CACHE_TTL_MINUTES = int(os.environ.get("BOOKSTORE_CACHE_TTL_MINUTES", "30"))
HOT_LIMIT = 6

# 必然不存在的购物车条目 ID，用于构造「下单事务回滚」
GHOST_CART_ID = 999999999

_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

passed = []
failed = []

# 退出时还原用的现场
_pending_restore = {}


def check(ok, msg):
    (passed if ok else failed).append(msg)
    print(("  [PASS] " if ok else "  [FAIL] ") + msg)


# ---------------------------------------------------------------- HTTP

def req(method, path, body=None, token=None, base=None):
    """发一次请求，返回解析后的 JSON。HTTP 错误也把响应体读回来（契约是 HTTP 恒 200）。"""
    root = (base or BASE).rstrip("/")
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(root + path, data=data, headers=headers, method=method)
    try:
        with _opener.open(request, timeout=20) as resp:
            text = resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", "replace")
    try:
        return json.loads(text)
    except Exception:
        return {"_raw": text}


# ---------------------------------------------------------------- Redis

def redis(*args):
    """执行一条 redis-cli 命令，返回去掉首尾空白的字符串输出"""
    try:
        out = subprocess.run([REDIS_CLI, "-h", "127.0.0.1", "-p", "6379"] + list(args),
                             capture_output=True, timeout=8)
        if out.returncode != 0:
            raise RuntimeError(out.stderr.decode("utf-8", "replace").strip())
        return out.stdout.decode("utf-8", "replace").strip()
    except Exception as e:
        print("  [ERROR] redis-cli 执行失败：%s" % e)
        raise


def keys(pattern):
    """列出匹配的缓存键。

    这里用 KEYS 而不是 SCAN：本脚本只跑在演示/开发环境的 Redis 上（键数量在两位数），
    不构成生产环境那种阻塞风险；而实测 redis-cli 的 --scan 在当前 Windows 环境下返回空结果，
    会造成「缓存根本没生效」的误判（比 KEYS 的代价大得多）。
    """
    out = redis("keys", pattern)
    return [k for k in out.splitlines() if k.strip()]


def book_keys(book_id):
    return keys(CACHE_PREFIX + "book::" + str(book_id))


def hot_keys():
    return keys(CACHE_PREFIX + "hotBook::*")


def clear_book_keys(book_id):
    found = book_keys(book_id) + hot_keys()
    if found:
        redis("del", *found)


# ---------------------------------------------------------------- 登录

def captcha_credentials():
    """取一次图形验证码（APP 登录必需），验证码明文临时存放在 Redis 里"""
    js = req("GET", "/api/captcha")
    key = (js.get("data") or {}).get("captchaKey")
    if not key:
        return None
    code = redis("get", CACHE_PREFIX + "captcha:" + key)
    if not code or code.startswith("__ERR__"):
        return None
    return {"captchaKey": key, "captchaCode": code}


def admin_login():
    js = req("POST", "/api/admin/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
    token = (js.get("data") or {}).get("token")
    if not token:
        raise RuntimeError("管理员登录失败：%s" % js)
    return token


def user_login():
    body = {"phone": USER_PHONE, "password": USER_PWD}
    extra = captcha_credentials()
    if extra:
        body.update(extra)
    js = req("POST", "/api/user/login", body)
    token = (js.get("data") or {}).get("token")
    if not token:
        raise RuntimeError("APP 用户登录失败：%s" % js)
    return token


# ---------------------------------------------------------------- 业务辅助

def pick_book(token=None, exclude_ids=()):
    """取一本有库存、且不在排除列表里的书"""
    js = req("GET", "/api/book/page?pageNum=1&pageSize=20")
    for item in (js.get("data") or {}).get("list") or []:
        if item.get("id") in exclude_ids:
            continue
        if (item.get("stock") or 0) > 5:
            return item
    raise RuntimeError("演示数据里找不到有库存的图书，无法继续验证")


def first_address(token):
    js = req("GET", "/api/address/list", token=token)
    data = js.get("data")
    items = data if isinstance(data, list) else (data or {}).get("list") or []
    if not items:
        raise RuntimeError("演示账号没有收货地址，无法构造下单")
    return items[0].get("id")


def admin_book_detail(book_id, token):
    js = req("GET", "/api/admin/book/%d" % book_id, token=token)
    return js.get("data") or {}


def update_book_price(book_id, token, detail, price):
    """按管理端接口原样回填字段，只改价格"""
    body = {
        "categoryId": detail.get("categoryId"),
        "title": detail.get("title"),
        "author": detail.get("author"),
        "publisher": detail.get("publisher"),
        "price": price,
        "stock": detail.get("stock"),
        "coverUrl": detail.get("coverUrl"),
        "description": detail.get("description"),
        "status": detail.get("status"),
    }
    # 封面是完整 URL（http://.../files/xxx），后端只接受相对路径，这里转回相对形式
    cover = body.get("coverUrl") or ""
    marker = "/files/"
    if marker in cover:
        body["coverUrl"] = cover[cover.index(marker):]
    return req("PUT", "/api/admin/book/%d" % book_id, body, token=token)


def price_of(book_id):
    js = req("GET", "/api/book/%d" % book_id)
    return (js.get("data") or {}).get("price")


def same_price(a, b):
    try:
        return abs(float(a) - float(b)) < 0.001
    except (TypeError, ValueError):
        return False


# ---------------------------------------------------------------- A. 形态

def verify_form():
    print("\n## A. 形态确认（/actuator/info）")
    js = req("GET", "/actuator/info")
    info = js.get("bookstore") or {}
    check(bool(info), "/actuator/info 暴露 bookstore 运行形态")
    check(info.get("cacheEnabled") is True, "缓存已启用（cacheEnabled=true）")
    check(info.get("cacheEvictAfterCommit") is True,
          "缓存清理时机 = 事务提交后（cacheEvictAfterCommit=true，即批 6 的新行为）")
    check(info.get("cacheTtlMinutes") == CACHE_TTL_MINUTES,
          "缓存 TTL 与配置一致（%d 分钟）" % CACHE_TTL_MINUTES)


# ---------------------------------------------------------------- B. 正常路径

def verify_commit_path(token, book):
    print("\n## B. 正常路径：写操作提交后清理缓存，且读到新值")
    book_id = book["id"]
    origin_price = book["price"]

    clear_book_keys(book_id)
    check(not book_keys(book_id), "起点：book 缓存为空（先清场，结论才可信）")

    req("GET", "/api/book/%d" % book_id)
    req("GET", "/api/book/%d" % book_id)
    cache_built = bool(book_keys(book_id))
    check(cache_built, "读图书详情后 Redis 中出现 book 缓存键")

    req("GET", "/api/book/hot?limit=%d" % HOT_LIMIT)
    hot_built = bool(hot_keys())
    check(hot_built, "读热门榜后 Redis 中出现 hotBook 缓存键")

    # 管理端改价：一次真实的「带事务的写操作」
    detail = admin_book_detail(book_id, token)
    new_price = round(float(origin_price) + 1.00, 2)
    _pending_restore["book_id"] = book_id
    _pending_restore["token"] = token
    _pending_restore["detail"] = detail
    _pending_restore["origin_price"] = origin_price

    js = update_book_price(book_id, token, detail, new_price)
    check(js.get("code") == 200, "管理端修改图书价格成功（code=200）")

    # 带上前置条件：若缓存压根没建立起来，这几条也要判失败，而不是「空 == 被清理」地假通过
    check(cache_built and not book_keys(book_id), "事务提交后 book 缓存被清理")
    check(hot_built and not hot_keys(), "事务提交后 hotBook 缓存被清理")

    got = price_of(book_id)
    check(same_price(got, new_price),
          "清理后重新读到的是新价格（期望 %s，实际 %s）" % (new_price, got))

    # 还原
    restore_book_price()
    got = price_of(book_id)
    check(same_price(got, origin_price),
          "价格已还原为原值（%s）" % origin_price)


def restore_book_price():
    """把被改动的图书价格还原（脚本异常退出时也会执行）"""
    if not _pending_restore.get("book_id"):
        return
    try:
        update_book_price(_pending_restore["book_id"], _pending_restore["token"],
                          _pending_restore["detail"], _pending_restore["origin_price"])
    except Exception as e:
        print("  [WARN] 价格还原失败，请手工检查图书 id=%s：%s"
              % (_pending_restore.get("book_id"), e))
    finally:
        _pending_restore.pop("book_id", None)


# ---------------------------------------------------------------- C. 失败路径

def verify_rollback_path(user_token):
    print("\n## C. 失败路径：事务回滚时不清理缓存")
    book = pick_book()
    book_id = book["id"]

    clear_book_keys(book_id)
    req("GET", "/api/book/%d" % book_id)
    req("GET", "/api/book/hot?limit=%d" % HOT_LIMIT)
    cache_built = bool(book_keys(book_id))
    hot_built = bool(hot_keys())
    check(cache_built and hot_built, "先建立两级缓存作为观察对象")

    address_id = first_address(user_token)
    js = req("POST", "/api/order/create",
             {"addressId": address_id, "cartIds": [GHOST_CART_ID]}, token=user_token)
    check(js.get("code") != 200,
          "用不存在的购物车条目下单失败（code=%s，事务将回滚）" % js.get("code"))

    check(cache_built and bool(book_keys(book_id)),
          "事务回滚后 book 缓存仍在 —— 回滚说明数据没变，缓存本来与库一致，无需清理")
    check(hot_built and bool(hot_keys()), "事务回滚后 hotBook 缓存仍在")


# ---------------------------------------------------------------- D. 购物车写失效

def verify_cart_cache(user_token):
    print("\n## D. 不回归：购物车 Cache-Aside 的写失效仍然生效")
    req("GET", "/api/cart/list", token=user_token)
    cart_built = bool(keys(CACHE_PREFIX + "cart::*"))
    check(cart_built, "读购物车后 Redis 中出现 cart 缓存键")

    # 找一本不在购物车里的书，加一条再删掉（净变化为零）
    js = req("GET", "/api/cart/list", token=user_token)
    in_cart = {item.get("bookId") for item in ((js.get("data") or {}).get("list") or [])}
    book = pick_book(exclude_ids=in_cart)
    book_id = book["id"]

    js = req("POST", "/api/cart/add", {"bookId": book_id, "quantity": 1}, token=user_token)
    check(js.get("code") == 200, "加购成功（一次带事务的写操作）")

    after_add = keys(CACHE_PREFIX + "cart::*")
    check(cart_built and not after_add, "加购提交后 cart 缓存被清理（写失效生效）")

    # 清场：删掉刚加的那条
    js = req("GET", "/api/cart/list", token=user_token)
    target = None
    for item in ((js.get("data") or {}).get("list") or []):
        if item.get("bookId") == book_id:
            target = item.get("id")
            break
    if target:
        js = req("DELETE", "/api/cart/%s" % target, token=user_token)
        check(js.get("code") == 200, "已删除临时加入的购物车条目（净变化为零）")
    else:
        check(False, "未能在购物车中找到刚加入的条目，无法清场")


# ---------------------------------------------------------------- E. 对照形态

def verify_legacy_form(legacy_base):
    print("\n## E. 对照形态：CACHE_EVICT_AFTER_COMMIT=false 的实例（可回退性）")
    js = req("GET", "/actuator/info", base=legacy_base)
    info = js.get("bookstore") or {}
    check(info.get("cacheEvictAfterCommit") is False,
          "对照实例显示旧行为（cacheEvictAfterCommit=false）—— 说明开关真的生效、可回退")
    check(info.get("cacheEnabled") is True, "对照实例缓存仍处于启用状态")

    js = req("GET", "/api/category/list", base=legacy_base)
    check(js.get("code") == 200, "对照实例业务接口正常（开关只影响清理时机，不影响可用性）")

    token = admin_login()
    book = pick_book()
    book_id = book["id"]
    req("GET", "/api/book/%d" % book_id, base=legacy_base)
    detail = admin_book_detail(book_id, token)
    new_price = round(float(book["price"]) + 1.00, 2)
    try:
        js = req("PUT", "/api/admin/book/%d" % book_id,
                 {
                     "categoryId": detail.get("categoryId"), "title": detail.get("title"),
                     "author": detail.get("author"), "publisher": detail.get("publisher"),
                     "price": new_price, "stock": detail.get("stock"),
                     "coverUrl": detail.get("coverUrl"), "description": detail.get("description"),
                     "status": detail.get("status"),
                 }, token=token)
        check(js.get("code") == 200, "对照实例上修改图书成功")
        check(not book_keys(book_id), "对照实例上缓存同样被清理（旧时机也能清干净，只是时机不同）")
    finally:
        update_book_price(book_id, token, detail, book["price"])


# ---------------------------------------------------------------- 主流程

def main():
    legacy_base = None
    for arg in sys.argv[1:]:
        if arg.startswith("--legacy-base="):
            legacy_base = arg.split("=", 1)[1]

    print("=" * 72)
    print("缓存「事务提交后失效」验证  BASE=%s" % BASE)
    print("=" * 72)

    # 前置：用「查库」的接口判断后端与数据库是否就绪（不要用 /health，它不查库）
    js = req("GET", "/api/category/list")
    if js.get("code") != 200:
        print("[ERROR] 后端接口不可用，请先启动后端（默认 http://127.0.0.1:8080）")
        return 2
    try:
        if "PONG" not in redis("ping").upper():
            raise RuntimeError("no pong")
    except Exception as e:
        print("[ERROR] Redis 不可用（%s），本验证依赖 Redis 中的缓存键" % e)
        return 2

    try:
        admin_token = admin_login()
        user_token = user_login()
    except Exception as e:
        print("[ERROR] 登录失败：%s" % e)
        return 2

    atexit.register(restore_book_price)
    try:
        verify_form()
        verify_commit_path(admin_token, pick_book())
        verify_rollback_path(user_token)
        verify_cart_cache(user_token)
        if legacy_base:
            verify_legacy_form(legacy_base)
    finally:
        restore_book_price()

    print("\n" + "=" * 72)
    print("通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
    if failed:
        print("失败清单：")
        for item in failed:
            print("  - " + item)
    print("=" * 72)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
