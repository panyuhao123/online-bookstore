# -*- coding: utf-8 -*-
"""
线上书店后端 —— 登录失败锁定验证（批 3）

验证目标：
  1. APP 用户：连续输错密码达阈值后账号被临时锁定；
  2. 锁定生效期「即使密码正确也拒绝登录」（这是防护有效的关键 —— 拒绝发生在口令校验之前）；
  3. 管理端登录同样受保护（管理端没有图形验证码，这层是它唯一的在线防爆破手段）；
  4. Redis 中确实写入了计数键与锁定键，且两者都带 TTL（不会永久残留）；
  5. 锁定只针对被攻击的那个账号，不连坐其他账号；
  6. 解锁后（reset）正常登录立刻恢复，且失败计数从 0 重新开始；
  7. 登录成功会清零失败计数，避免零星输错累积成「连续失败」。

⚠️ 本脚本会故意把演示账号（user 13800138000 / admin）连错 5 次以触发真实锁定，
   并在结束时删除相关 Redis 键恢复原状（即使中途异常也会清理）。
   不会修改数据库中的任何数据。

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
OTHER_PHONE = os.environ.get("BOOKSTORE_OTHER_PHONE", "18251354608")

# 与 application.yml 中 bookstore.login-guard 的默认值保持一致
MAX_FAILURES = int(os.environ.get("BOOKSTORE_LOGIN_MAX_FAILURES", "5"))
WINDOW_MINUTES = int(os.environ.get("BOOKSTORE_LOGIN_WINDOW_MINUTES", "30"))
LOCK_MINUTES = int(os.environ.get("BOOKSTORE_LOGIN_LOCK_MINUTES", "15"))

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

def req(method, path, body=None, token=None):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
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
                             capture_output=True, timeout=5)
        if out.returncode != 0:
            raise RuntimeError(out.stderr.decode("utf-8", "replace").strip())
        return out.stdout.decode("utf-8", "replace").strip()
    except Exception as e:
        print("  [ERROR] redis-cli 执行失败：%s" % e)
        raise


def redis_ttl(key):
    """键的剩余秒数；-2 表示键不存在，-1 表示存在但无 TTL"""
    return int(redis("ttl", key))


def clear_keys(*keys):
    for key in keys:
        redis("del", key)


# ---------------------------------------------------------------- 辅助

def captcha_credentials():
    """取一次图形验证码（APP 登录必需），返回 {captchaKey, captchaCode}"""
    js = req("GET", "/api/captcha")
    key = (js.get("data") or {}).get("captchaKey")
    if not key:
        return None
    code = redis("get", "bookstore:captcha:" + key)
    if not code or code.startswith("__ERR__"):
        return None
    return {"captchaKey": key, "captchaCode": code}


def user_login(phone, password):
    """带验证码的 APP 登录；验证码取不到时退化为不带验证码（会得到 400，便于暴露问题）"""
    payload = {"phone": phone, "password": password}
    cred = captcha_credentials()
    if cred:
        payload.update(cred)
    return req("POST", "/api/user/login", payload)


def admin_login(password):
    return req("POST", "/api/admin/login", {"username": ADMIN_USER, "password": password})


def cleanup():
    """把本次验证制造的锁定与计数痕迹全部清掉，避免影响后续回归脚本"""
    try:
        clear_keys(FAIL_KEY_USER, LOCK_KEY_USER, FAIL_KEY_ADMIN, LOCK_KEY_ADMIN,
                   "bookstore:login:fail:user:" + OTHER_PHONE,
                   "bookstore:login:lock:user:" + OTHER_PHONE)
    except Exception:
        pass


# ---------------------------------------------------------------- 主流程

def verify_user_lockout():
    print("\n[1] APP 用户：连续输错密码触发锁定")
    clear_keys(FAIL_KEY_USER, LOCK_KEY_USER)
    check(redis_ttl(LOCK_KEY_USER) == -2, "起始状态：无锁定键，账号可用")

    wrong = USER_PWD + "-wrong"
    for i in range(1, MAX_FAILURES + 1):
        js = user_login(USER_PHONE, wrong)
        check(js.get("code") == 401,
              "第 %d 次错误密码返回 401（账号或密码错误）：%s" % (i, js.get("message")))
        fail_ttl = redis_ttl(FAIL_KEY_USER)
        if i < MAX_FAILURES:
            check(fail_ttl > 0, "第 %d 次失败后计数键存在且带 TTL（%d 秒）" % (i, fail_ttl))
            check(redis("get", FAIL_KEY_USER) == str(i),
                  "计数键值等于失败次数 %d，实际 %s" % (i, redis("get", FAIL_KEY_USER)))
        else:
            check(fail_ttl == -2, "达到阈值的那次失败会把计数键清空（解锁后从 0 重新计数）")

    lock_ttl = redis_ttl(LOCK_KEY_USER)
    check(lock_ttl > 0, "第 %d 次失败后锁定键已写入（TTL %d 秒）" % (MAX_FAILURES, lock_ttl))
    check(lock_ttl <= LOCK_MINUTES * 60 + 5,
          "锁定 TTL 不超过配置的 %d 分钟（实际 %d 秒）" % (LOCK_MINUTES, lock_ttl))

    print("\n[2] 锁定生效：即使密码正确也拒绝登录")
    js = user_login(USER_PHONE, USER_PWD)
    check(js.get("code") == 422, "正确密码在锁定期内被拒绝，返回 422：%s" % js.get("message"))
    check(not (js.get("data") or {}).get("token"), "锁定期间不签发任何令牌")
    check("锁定" in (js.get("message") or ""), "错误文案明确告知账号已被锁定")
    check("分钟" in (js.get("message") or ""), "错误文案告知还需等待多久")

    print("\n[3] 锁定不连坐：其他账号仍可正常登录")
    js_other = user_login(OTHER_PHONE, "definitely-wrong")
    check(js_other.get("code") == 401,
          "另一个账号首次输错只返回 401（说明未被锁定牵连）：%s" % js_other.get("message"))

    print("\n[4] 成功登录会清零历史失败计数")
    clear_keys(FAIL_KEY_USER, LOCK_KEY_USER)
    user_login(USER_PHONE, wrong)
    user_login(USER_PHONE, wrong)
    check(redis("get", FAIL_KEY_USER) == "2", "累计 2 次失败后计数为 2")
    js_ok = user_login(USER_PHONE, USER_PWD)
    check(js_ok.get("code") == 200, "解锁状态下用正确密码登录成功：%s" % js_ok.get("message"))
    check(redis_ttl(FAIL_KEY_USER) == -2, "登录成功后失败计数被清零（否则零星输错会累积成锁定）")


def verify_manual_unlock():
    print("\n[5] 手动解锁后立即恢复可用（管理员应急出口）")
    wrong = USER_PWD + "-wrong"
    for _ in range(MAX_FAILURES):
        user_login(USER_PHONE, wrong)
    check(redis_ttl(LOCK_KEY_USER) > 0, "账号已被锁定")

    clear_keys(FAIL_KEY_USER, LOCK_KEY_USER)
    js = user_login(USER_PHONE, USER_PWD)
    check(js.get("code") == 200, "删除锁定键后同一账号立刻可登录：%s" % js.get("message"))
    check(bool((js.get("data") or {}).get("token")), "恢复登录后正常返回令牌")


def verify_admin_lockout():
    print("\n[6] 管理端：无验证码，因此失败锁定是它唯一的在线防护")
    clear_keys(FAIL_KEY_ADMIN, LOCK_KEY_ADMIN)

    wrong = ADMIN_PWD + "-wrong"
    for i in range(1, MAX_FAILURES + 1):
        js = admin_login(wrong)
        check(js.get("code") == 401,
              "管理员第 %d 次错误口令返回 401：%s" % (i, js.get("message")))

    lock_ttl = redis_ttl(LOCK_KEY_ADMIN)
    check(lock_ttl > 0, "管理员账号已被锁定（TTL %d 秒）" % lock_ttl)

    js = admin_login(ADMIN_PWD)
    check(js.get("code") == 422, "锁定期内管理员用正确口令也被拒绝：%s" % js.get("message"))

    clear_keys(FAIL_KEY_ADMIN, LOCK_KEY_ADMIN)
    js_ok = admin_login(ADMIN_PWD)
    check(js_ok.get("code") == 200, "解锁后管理员正常登录：%s" % js_ok.get("message"))
    check(redis_ttl(FAIL_KEY_ADMIN) == -2, "登录成功后管理员失败计数被清零")

    print("\n[7] 账号域隔离：APP 用户的锁定不影响管理员同名账号")
    clear_keys(FAIL_KEY_USER, LOCK_KEY_USER)
    for _ in range(MAX_FAILURES):
        user_login(USER_PHONE, ADMIN_PWD + "-wrong")
    check(redis_ttl(LOCK_KEY_USER) > 0, "APP 用户账号已被锁定")
    check(redis_ttl(LOCK_KEY_ADMIN) == -2, "管理端的锁定键不存在（两个域各算各的）")


def main():
    print("=" * 72)
    print("登录失败锁定验证  BASE=%s" % BASE)
    print("策略：连续 %d 次失败 → 锁定 %d 分钟；计数窗口 %d 分钟"
          % (MAX_FAILURES, LOCK_MINUTES, WINDOW_MINUTES))
    print("=" * 72)

    # 提前确认后端与 Redis 可用，避免中途以难懂的方式失败
    probe = req("GET", "/api/category/list")
    if probe.get("code") != 200:
        print("[ERROR] 后端接口不可用，请先启动后端（默认 http://127.0.0.1:8080）")
        return 2
    ping = redis("ping")
    if "PONG" not in ping.upper():
        print("[ERROR] Redis 不可用，请先启动 Redis（该功能依赖 Redis 计数）")
        return 2

    atexit.register(cleanup)
    try:
        verify_user_lockout()
        verify_manual_unlock()
        verify_admin_lockout()
    finally:
        cleanup()

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
