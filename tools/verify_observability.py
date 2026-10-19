# -*- coding: utf-8 -*-
"""
线上书店后端 —— 可观测性验证（批 5）

验证目标：
  1. 健康检查：/actuator/health 免登录可访问，status=UP，并逐项列出依赖组件的状态
     （db / redis / 自定义的缓存组件 bookstoreCache）；
  2. 运行信息：/actuator/info 免登录可访问，输出关键开关（支付渠道、存储方式、
     登录锁定、令牌吊销、埋点开关……）与构建信息，便于部署自证；
     同时确认「绝不出现在信息端点里的敏感值」（密钥/口令/私钥）；
  3. 鉴权边界（本批最核心的安全点）：
       匿名           → 401（未登录）
       APP 用户令牌    → 403（已登录但非管理员，不能看运维指标）
       ADMIN 令牌     → 200（可取指标）
     actuator 的响应也遵循项目「HTTP 恒 200，成败看 body.code」的统一约定；
  4. 最小暴露：未纳入暴露清单的端点（env / beans / heapdump）即使带着 ADMIN 令牌
     也返回 404 —— 说明「不是靠权限挡住，而是压根没开」；
  5. 业务埋点真的在计数：登录成功/失败、管理员登录失败、令牌吊销、下单、支付
     各自触发一次后，对应 Micrometer 计数器 +1；
  6. 内置自动指标 http.server.requests 可查（框架自带的接口调用量与耗时，
     与业务埋点互补，不必自己重造轮子）。

⚠️ 本脚本会：① 故意输错密码 1~2 次（结束后清理 Redis 计数键）；
   ② 用一个演示账号真实下一单并完成模拟支付（会在库中留下 1 条已完成支付订单）。
   不留残余的 Redis 键；订单数据可用 db/mock_dataset.sql 或 db/seed.sql 重置。

用法：
   python tools/verify_observability.py
   python tools/verify_observability.py --prod-base=http://127.0.0.1:8081
   python tools/verify_observability.py --prod-base=http://127.0.0.1:8081 --json-log=bookstore-server/logs/bookstore-json.log
     （--prod-base 额外验证 prod profile 形态：只暴露 health、隐藏依赖明细、
       关闭接口文档入口；该实例需以 --spring.profiles.active=local,prod 启动。
       --json-log 额外校验结构化日志文件：逐行合法 JSON、字段齐全、MDC 链路 ID 落盘。）

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

FAIL_KEY_USER = "bookstore:login:fail:user:" + USER_PHONE
LOCK_KEY_USER = "bookstore:login:lock:user:" + USER_PHONE
FAIL_KEY_ADMIN = "bookstore:login:fail:admin:" + ADMIN_USER
LOCK_KEY_ADMIN = "bookstore:login:lock:admin:" + ADMIN_USER

_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

passed = []
failed = []


def check(ok, msg):
    (passed if ok else failed).append(msg)
    print(("  [PASS] " if ok else "  [FAIL] ") + msg)


# ---------------------------------------------------------------- HTTP

def call(method, path, body=None, token=None, base=None):
    """发一次请求，返回 (http_status, 解析后的 JSON)。HTTP 错误也会把响应体读回来。"""
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request((base or BASE) + path, data=data, headers=headers, method=method)
    try:
        with _opener.open(request, timeout=20) as resp:
            status, text = resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode("utf-8", "replace")
    try:
        return status, json.loads(text)
    except Exception:
        return status, {"_raw": text}


def is_denied(js):
    """判断响应是否是「被 Spring Security 挡下来」的统一返回体（code=401/403）"""
    return js.get("code") in (401, 403)


# ---------------------------------------------------------------- Redis

def redis(*args):
    out = subprocess.run([REDIS_CLI, "-h", "127.0.0.1", "-p", "6379"] + list(args),
                         capture_output=True, timeout=5)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.decode("utf-8", "replace").strip())
    return out.stdout.decode("utf-8", "replace").strip()


def clear_login_keys():
    for key in (FAIL_KEY_USER, LOCK_KEY_USER, FAIL_KEY_ADMIN, LOCK_KEY_ADMIN):
        try:
            redis("del", key)
        except Exception:
            pass


# ---------------------------------------------------------------- 业务辅助

def captcha():
    """取一次图形验证码（APP 登录必需）"""
    _, js = call("GET", "/api/captcha")
    key = (js.get("data") or {}).get("captchaKey")
    if not key:
        return None
    code = redis("get", "bookstore:captcha:" + key)
    if not code or code.startswith("__ERR__"):
        return None
    return {"captchaKey": key, "captchaCode": code}


def user_login(phone, password):
    payload = {"phone": phone, "password": password}
    cred = captcha()
    if cred:
        payload.update(cred)
    return call("POST", "/api/user/login", payload)


def admin_login(password):
    return call("POST", "/api/admin/login", {"username": ADMIN_USER, "password": password})


def admin_token():
    _, js = admin_login(ADMIN_PWD)
    token = (js.get("data") or {}).get("token")
    if not token:
        raise RuntimeError("无法取得管理员令牌：%s" % js.get("message"))
    return token


def user_token():
    _, js = user_login(USER_PHONE, USER_PWD)
    token = (js.get("data") or {}).get("token")
    if not token:
        raise RuntimeError("无法取得用户令牌：%s" % js.get("message"))
    return token


# ---------------------------------------------------------------- 指标读取

def metric_count(name, tags=None):
    """读一个计数器的当前值；指标不存在（404）或没有该标签组合时返回 0.0"""
    path = "/actuator/metrics/" + name
    if tags:
        path += "?" + "&".join("tag=%s:%s" % (k, v) for k, v in tags.items())
    status, js = call("GET", path, token=ADMIN_TOKEN)
    if status != 200 or not isinstance(js.get("measurements"), list):
        return 0.0
    for m in js["measurements"]:
        if m.get("statistic") == "COUNT":
            return float(m.get("value") or 0)
    return 0.0


def login(base, path, body):
    """在指定 base 上登录，返回 (body.code, token)"""
    _, js = call("POST", path, body, base=base)
    return js.get("code"), (js.get("data") or {}).get("token")


ADMIN_TOKEN = None  # main() 里赋值


# ---------------------------------------------------------------- 各段验证

def verify_health():
    print("\n[1] 健康检查 /actuator/health（免登录）")
    status, js = call("GET", "/actuator/health")
    check(status == 200, "HTTP 状态 200（实际 %s）" % status)
    check(js.get("status") == "UP", "整体 status=UP（实际 %s）" % js.get("status"))

    comps = js.get("components") or {}
    check("db" in comps, "包含数据库组件 db")
    check((comps.get("db") or {}).get("status") == "UP",
          "数据库组件为 UP（实际 %s）" % (comps.get("db") or {}).get("status"))
    check("redis" in comps, "包含缓存组件 redis")
    check((comps.get("redis") or {}).get("status") == "UP",
          "缓存组件为 UP（实际 %s）" % (comps.get("redis") or {}).get("status"))

    cache = comps.get("bookstoreCache")
    check(cache is not None, "包含自定义缓存健康组件 bookstoreCache")
    if cache:
        check(cache.get("status") == "UP", "缓存组件恒为 UP（熔断已由业务层降级兜底，见下方明细）")
        details = cache.get("details") or {}
        for key in ("enabled", "circuitOpen", "degraded"):
            check(key in details, "缓存组件明细包含 %s（实际 %s）" % (key, details.get(key)))


def verify_info():
    print("\n[2] 运行信息 /actuator/info（免登录）")
    status, js = call("GET", "/actuator/info")
    check(status == 200, "HTTP 状态 200（实际 %s）" % status)

    bs = js.get("bookstore")
    check(isinstance(bs, dict), "/actuator/info 含 bookstore 段（本项目的运行开关摘要）")
    if not isinstance(bs, dict):
        return
    for key in ("profiles", "payType", "storageType", "jwtExpireHours",
                "tokenRevocationEnabled", "loginGuardEnabled", "loginGuardMaxFailures",
                "cacheEnabled", "metricsEnabled"):
        check(key in bs, "bookstore 段包含 %s（实际 %s）" % (key, bs.get(key)))
    check(bs.get("metricsEnabled") is True, "埋点开关 metricsEnabled=true（默认开启）")
    check(bs.get("payType") == "mock", "支付渠道为 mock（模拟支付）")
    check(bs.get("loginGuardEnabled") is True, "登录失败锁定处于开启状态")

    check("build" in js, "含 build 段（由 build-info 目标生成，可核对部署版本）")
    check("java" in js, "含 java 段（Java 运行时版本）")

    # 反向断言：信息端点绝不能变成「配置泄露」的入口
    text = json.dumps(js, ensure_ascii=False).lower()
    for secret_word in ("secret", "password", "privatekey", "private_key"):
        check(secret_word not in text,
              "/actuator/info 未泄露含「%s」的字段（信息端点只报开关，不报凭证）" % secret_word)


def verify_auth_boundary():
    print("\n[3] 鉴权边界（本批重点：运维指标不能裸奔）")

    # 匿名
    status, js = call("GET", "/actuator/metrics")
    check(is_denied(js) and js.get("code") == 401,
          "匿名访问 /actuator/metrics → body.code=401（实际 code=%s）" % js.get("code"))
    check(status == 200, "actuator 也遵循「HTTP 恒 200」约定（实际 %s）" % status)

    # APP 用户令牌（非管理员）
    ut = None
    try:
        ut = user_token()
    except Exception as e:
        print("  [WARN] %s" % e)
    if ut:
        _, js_user = call("GET", "/actuator/metrics", token=ut)
        check(js_user.get("code") == 403,
              "普通用户令牌访问 /actuator/metrics → 403（已登录但无权限，实际 code=%s）"
              % js_user.get("code"))

    # ADMIN 令牌
    status, js_admin = call("GET", "/actuator/metrics", token=ADMIN_TOKEN)
    check(status == 200 and not is_denied(js_admin), "ADMIN 令牌可读取 /actuator/metrics")
    names = js_admin.get("names") or []
    check("http.server.requests" in names,
          "指标列表中含框架自带的 http.server.requests（接口调用量与耗时）")


def verify_least_exposure():
    print("\n[4] 最小暴露：未纳入清单的端点对 ADMIN 也是 404")
    # 匿名访问未暴露端点：被安全层挡在门外（401）
    _, js_anon = call("GET", "/actuator/env")
    check(js_anon.get("code") == 401, "匿名访问 /actuator/env → 401（实际 code=%s）"
          % js_anon.get("code"))
    # 带 ADMIN 令牌：权限已足够，但端点根本没开 → 404（不是靠权限，而是压根不暴露）
    for ep in ("env", "beans", "heapdump", "threaddump"):
        status, _ = call("GET", "/actuator/" + ep, token=ADMIN_TOKEN)
        check(status == 404, "/actuator/%s 未暴露 → 404（实际 %s）" % (ep, status))


def verify_metrics_counting():
    print("\n[5] 业务埋点真的在计数")

    clear_login_keys()

    # 5.1 登录失败
    tags_fail = {"scope": "user", "result": "failure"}
    before = metric_count("bookstore.login.attempt", tags_fail)
    user_login(USER_PHONE, USER_PWD + "-wrong-metric")
    after = metric_count("bookstore.login.attempt", tags_fail)
    check(after == before + 1,
          "一次密码错误 → bookstore.login.attempt{scope=user,result=failure} +1（%g→%g）"
          % (before, after))

    # 5.2 登录成功
    clear_login_keys()
    tags_ok = {"scope": "user", "result": "success"}
    before = metric_count("bookstore.login.attempt", tags_ok)
    _, js = user_login(USER_PHONE, USER_PWD)
    check(js.get("code") == 200, "用户正常登录成功（%s）" % js.get("message"))
    after = metric_count("bookstore.login.attempt", tags_ok)
    check(after == before + 1,
          "一次登录成功 → bookstore.login.attempt{scope=user,result=success} +1（%g→%g）"
          % (before, after))

    # 5.3 管理员登录失败（独立 scope 标签，便于分开告警）
    tags_admin = {"scope": "admin", "result": "failure"}
    before = metric_count("bookstore.login.attempt", tags_admin)
    admin_login(ADMIN_PWD + "-wrong-metric")
    after = metric_count("bookstore.login.attempt", tags_admin)
    check(after == before + 1,
          "管理员登录失败 → bookstore.login.attempt{scope=admin,result=failure} +1（%g→%g）"
          % (before, after))
    clear_login_keys()

    # 5.4 令牌吊销（退出登录）
    ut = user_token()
    tags_role = {"role": "user"}
    before = metric_count("bookstore.token.revoked", tags_role)
    call("POST", "/api/user/logout", token=ut)
    after = metric_count("bookstore.token.revoked", tags_role)
    check(after == before + 1,
          "退出登录 → bookstore.token.revoked{role=user} +1（%g→%g）" % (before, after))

    # 5.5 下单 / 支付
    verify_order_metrics()


def verify_order_metrics():
    """用一个演示账号真实走一遍「加购 → 下单 → 支付」，确认订单相关计数器递增"""
    ut = user_token()

    # 找一个可售图书
    _, js = call("GET", "/api/book/hot?limit=1")
    books = (js.get("data") or {})
    book_list = books.get("list") if isinstance(books, dict) else books
    if not book_list:
        # 退化为从图书分页接口取第一本
        _, js2 = call("GET", "/api/book/page?pageNum=1&pageSize=1")
        book_list = ((js2.get("data") or {}).get("list")) or []
    if not book_list:
        check(False, "取不到可售图书，无法验证订单埋点")
        return
    book_id = book_list[0].get("id")

    # 加购
    _, js_add = call("POST", "/api/cart/add", {"bookId": book_id, "quantity": 1}, token=ut)
    if js_add.get("code") != 200:
        check(False, "加入购物车失败，无法验证订单埋点：%s" % js_add.get("message"))
        return

    # 取该条购物车条目的 id（注意主键是 id，不是 cartId）
    _, js_cart = call("GET", "/api/cart/list", token=ut)
    items = ((js_cart.get("data") or {}).get("list")) or []
    target = None
    for it in items:
        if it.get("bookId") == book_id:
            target = it
            break
    if not target:
        check(False, "购物车中找不到刚加入的条目，无法下单")
        return
    cart_id = target.get("id")

    # 取一个收货地址，没有就新建一个
    _, js_addr = call("GET", "/api/address/list", token=ut)
    addresses = js_addr.get("data") or []
    if addresses:
        address_id = addresses[0].get("id")
    else:
        _, js_new = call("POST", "/api/address/add", {
            "receiver": "埋点验证", "phone": USER_PHONE,
            "province": "北京市", "city": "北京市", "district": "海淀区",
            "detail": "可观测性验证用临时地址", "isDefault": 1,
        }, token=ut)
        address_id = (js_new.get("data") or {}).get("id")
    if not address_id:
        check(False, "取不到收货地址，无法下单")
        return

    before_created = metric_count("bookstore.order.created")
    before_paid = metric_count("bookstore.order.paid")

    _, js_create = call("POST", "/api/order/create",
                        {"addressId": address_id, "cartIds": [cart_id]}, token=ut)
    order = js_create.get("data") or {}
    order_id = order.get("id")
    check(js_create.get("code") == 200 and order_id,
          "下单成功（orderNo=%s）" % order.get("orderNo"))
    if not order_id:
        return

    after_created = metric_count("bookstore.order.created")
    check(after_created == before_created + 1,
          "下单 → bookstore.order.created +1（%g→%g）" % (before_created, after_created))

    _, js_pay = call("POST", "/api/order/%s/pay" % order_id, token=ut)
    check(js_pay.get("code") == 200, "发起支付成功：%s" % js_pay.get("message"))
    after_paid = metric_count("bookstore.order.paid")
    check(after_paid == before_paid + 1,
          "支付入账 → bookstore.order.paid +1（%g→%g）" % (before_paid, after_paid))

    print("  [INFO] 本次验证在库中留下 1 笔已支付订单（orderNo=%s），"
          "如需还原可执行 db/mock_dataset.sql 或 db/seed.sql" % order.get("orderNo"))


# ---------------------------------------------------------------- 生产形态

def verify_prod_form(pbase):
    """验证 prod profile 的形态：只暴露 health、隐藏依赖明细、关闭文档入口。

    用 `--spring.profiles.active=local,prod` 启动的第二实例（DB 口令等仍取自 local，
    运维端点与日志形态由 prod 覆盖），使「生产配置」不再是纸面约定而是可运行验证的对象。
    """
    print("\n[6] 生产形态验证（prod profile）  BASE=%s" % pbase)

    status, js = call("GET", "/actuator/health", base=pbase)
    check(status == 200 and js.get("status") == "UP",
          "prod 下 /actuator/health 仍可访问且 UP（存活探测不能被自己挡住）")
    check("components" not in js,
          "health 不再返回 components 明细（show-details=never，避免暴露内部拓扑）")

    status, _ = call("GET", "/actuator/info", base=pbase)
    check(status == 404, "prod 下 /actuator/info 未暴露 → 404（实际 %s）" % status)

    _, js_anon = call("GET", "/actuator/metrics", base=pbase)
    check(js_anon.get("code") == 401,
          "prod 下匿名访问 /actuator/metrics → 401（安全层先拦，实际 code=%s）" % js_anon.get("code"))

    code, token = login(pbase, "/api/admin/login",
                        {"username": ADMIN_USER, "password": ADMIN_PWD})
    check(code == 200, "prod 下管理员可正常登录（%s）" % code)
    if token:
        status, _ = call("GET", "/actuator/metrics", token=token, base=pbase)
        check(status == 404,
              "prod 下即使带 ADMIN 令牌，/actuator/metrics 也是 404（不是没权限，是压根没开）")

    _, js_biz = call("GET", "/api/category/list", base=pbase)
    check(js_biz.get("code") == 200, "prod 下业务接口正常（说明收敛的是运维端点，不是业务）")

    # 文档入口：prod 下被安全层挡下（按项目约定 HTTP 仍为 200，靠 body.code 表达拒绝）
    for doc_path in ("/doc.html", "/v3/api-docs", "/swagger-ui.html"):
        _, js_doc = call("GET", doc_path, base=pbase)
        check(is_denied(js_doc) and js_doc.get("code") == 401,
              "prod 下接口文档入口 %s 被安全层挡住（body.code=%s）" % (doc_path, js_doc.get("code")))

    # 对照：本地实例的文档入口是开放的 —— 证明 prod 的「关闭」是配置真的生效，
    # 而不是这些路径本来就不存在（否则这条断言毫无意义）
    _, js_local_doc = call("GET", "/v3/api-docs")
    check(js_local_doc.get("openapi") == "3.0.1",
          "对照：本地实例 /v3/api-docs 正常返回 OpenAPI 文档，说明 prod 的关闭确由配置生效")


# ---------------------------------------------------------------- 日志格式

def verify_json_log(path):
    """校验 prod 形态的结构化日志：逐行合法 JSON、必需字段齐全、MDC 链路 ID 已落到日志里。"""
    print("\n[7] 结构化日志校验（JSON 行式）  FILE=%s" % path)
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            lines = [ln.rstrip("\n") for ln in f if ln.startswith("{")]
    except OSError as e:
        check(False, "无法读取日志文件 %s：%s" % (path, e))
        return

    check(len(lines) > 0, "日志文件中存在 JSON 行（共 %d 行）" % len(lines))

    required = ("timestamp", "level", "thread", "logger", "message")
    parsed = []
    broken = 0
    for ln in lines:
        try:
            obj = json.loads(ln)
            # 每行都应能独立解析 —— 这同时证明了「一条日志 = 一行」，
            # 采集端（Filebeat / Fluent Bit）才能按行切分，不会把多行堆栈粘成一坨。
            if all(k in obj for k in required):
                parsed.append(obj)
            else:
                broken += 1
        except Exception:
            broken += 1
    check(broken == 0, "全部 JSON 行可独立解析且含 %s 字段（异常行 %d）"
          % ("/".join(required), broken))

    with_rid = [o for o in parsed if o.get("requestId")]
    check(len(with_rid) > 0,
          "存在带 requestId 的日志行（%d/%d），证明 MDC 链路 ID 已贯穿到普通组件日志"
          % (len(with_rid), len(parsed)))
    if with_rid:
        sample = with_rid[0]
        check(len(str(sample["requestId"])) >= 8,
              "requestId 形如链路 ID（示例 %s）" % sample["requestId"])

    levels = {o.get("level") for o in parsed}
    check(levels and levels.issubset({"TRACE", "DEBUG", "INFO", "WARN", "ERROR"}),
          "level 取值规范（本文件出现 %s）" % ",".join(sorted(levels)))


# ---------------------------------------------------------------- 主流程

def main():
    global ADMIN_TOKEN, BASE
    prod_base = None
    json_log = None
    for arg in sys.argv[1:]:
        if arg.startswith("--prod-base="):
            prod_base = arg.split("=", 1)[1]
        elif arg.startswith("--json-log="):
            json_log = arg.split("=", 1)[1]
        elif arg.startswith("--base="):
            BASE = arg.split("=", 1)[1]

    print("=" * 72)
    print("可观测性验证  BASE=%s" % BASE)
    print("=" * 72)

    # 前置检查：用「查库」的接口判断后端与数据库是否就绪（不要用 /health，它不查库）
    _, probe = call("GET", "/api/category/list")
    if probe.get("code") != 200:
        print("[ERROR] 后端接口不可用，请先启动后端（默认 http://127.0.0.1:8080）")
        return 2
    try:
        if "PONG" not in redis("ping").upper():
            raise RuntimeError("no pong")
    except Exception as e:
        print("[ERROR] Redis 不可用（%s），本验证依赖 Redis 计数与令牌吊销" % e)
        return 2

    try:
        ADMIN_TOKEN = admin_token()
    except Exception as e:
        print("[ERROR] %s" % e)
        return 2

    atexit.register(clear_login_keys)
    try:
        verify_health()
        verify_info()
        verify_auth_boundary()
        verify_least_exposure()
        verify_metrics_counting()
        if prod_base:
            verify_prod_form(prod_base)
        if json_log:
            verify_json_log(json_log)
    finally:
        clear_login_keys()

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
