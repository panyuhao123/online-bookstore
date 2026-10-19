# -*- coding: utf-8 -*-
"""
线上书店后端 —— 会话可吊销（令牌吊销 / 退出登录真正生效）验证（批 4）

验证目标：
  1. 令牌携带 jti，且同一用户多次登录的 jti 互不相同（多设备各持一枚，可独立吊销）；
  2. 退出登录在服务端生效：调用 /logout 后，同一枚令牌访问任何需登录接口都返回 401，
     而不是像以前那样「客户端删掉、服务端还认」；
  3. 黑名单键带 TTL，且 TTL 收敛到令牌剩余有效期（不会在 Redis 里永久堆积）；
  4. 退出登录是幂等的：令牌缺失、已登出、格式非法时都返回成功（登出目标已达成）；
  5. 吊销只作用于被登出的那一枚令牌：重新登录拿到的新令牌可用，
     另一台设备（同一用户第二次登录）的令牌不受影响；
  6. 管理端 /api/admin/logout 同样生效（管理端令牌权限更大，更需要服务端作废）；
  7. 令牌有效期已收敛：token 的 exp - iat ≈ 配置的 expire-hours（本地默认 24 小时）。

⚠️ 本脚本只做登录 / 登出 / 读取，不修改数据库数据；
   结束时删除本次产生的所有吊销键，避免影响后续回归脚本。

注意：口令以字面量写在脚本文件里（不写命令行），否则会被运行环境改写。
"""
import atexit
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BOOKSTORE_BASE", "http://127.0.0.1:8080")
REDIS_CLI = os.environ.get("BOOKSTORE_REDIS_CLI", "D:/redis5/redis-cli.exe")

ADMIN_USER = os.environ.get("BOOKSTORE_ADMIN_USER", "admin")
ADMIN_PWD = os.environ.get("BOOKSTORE_ADMIN_PWD", "123456")
USER_PHONE = os.environ.get("BOOKSTORE_USER_PHONE", "13800138000")
USER_PWD = os.environ.get("BOOKSTORE_USER_PWD", "123456")

# 与 application.yml 的 bookstore.jwt.expire-hours 默认值保持一致
EXPECT_EXPIRE_HOURS = int(os.environ.get("BOOKSTORE_EXPECT_EXPIRE_HOURS", "24"))

REVOKE_PREFIX = "bookstore:token:revoked:"

_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

passed = []
failed = []
# 本次脚本吊销过的 jti，用于收尾清理
revoked_jtis = []


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


def revoke_key(jti):
    return REVOKE_PREFIX + jti


def clear_keys(*keys):
    for key in keys:
        redis("del", key)


# ---------------------------------------------------------------- JWT 解析

def decode_jwt(token):
    """只做 base64 解码，不验签：脚本只关心载荷里的 jti / userId / exp"""
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)       # 补回 base64url 省略的填充
        return json.loads(base64.urlsafe_b64decode(payload).decode("utf-8"))
    except Exception:
        return {}


def fake_token(user_id=999999):
    """构造一枚「格式正确但签名非法」的令牌，用于验证登出接口的容错"""
    def b64(obj):
        raw = json.dumps(obj, separators=(",", ":")).encode("utf-8")
        return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")

    header = b64({"alg": "HS256", "typ": "JWT"})
    payload = b64({"userId": user_id, "role": "user", "jti": "forgedjti",
                   "exp": int(time.time()) + 3600})
    return "%s.%s.%s" % (header, payload, "not-a-valid-signature")


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


def user_login(password=None):
    payload = {"phone": USER_PHONE, "password": password or USER_PWD}
    cred = captcha_credentials()
    if cred:
        payload.update(cred)
    return req("POST", "/api/user/login", payload)


def user_token():
    """登录并返回 (token, data)；失败返回 (None, data)"""
    js = user_login()
    data = js.get("data") or {}
    return data.get("token"), data


def admin_token():
    js = req("POST", "/api/admin/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
    data = js.get("data") or {}
    return data.get("token"), data


def logout(token=None, path="/api/user/logout"):
    return req("POST", path, {}, token)


def track(token):
    """记录令牌的 jti，收尾时统一清理"""
    jti = decode_jwt(token).get("jti")
    if jti:
        revoked_jtis.append(jti)
    return jti


def cleanup():
    """清掉本次验证产生的吊销键，避免影响后续回归脚本"""
    try:
        clear_keys(*[revoke_key(jti) for jti in revoked_jtis])
    except Exception:
        pass


# ---------------------------------------------------------------- 主流程

def verify_token_shape():
    print("\n[1] 令牌形态：jti 存在、每次登录不同、有效期已收敛")
    token, data = user_token()
    check(bool(token), "APP 登录成功并返回令牌")
    claims = decode_jwt(token)
    jti = claims.get("jti")
    check(bool(jti), "令牌载荷携带 jti（否则无法被单独吊销）：%s" % jti)
    check(isinstance(jti, str) and len(jti) == 32 and all(c in "0123456789abcdef" for c in jti),
          "jti 为 32 位十六进制随机串，实际 %r" % jti)

    second_token, _ = user_token()
    check(decode_jwt(second_token).get("jti") != jti,
          "同一用户第二次登录拿到不同 jti（多设备各自独立，可分别登出）")

    check(data.get("expireHours") == EXPECT_EXPIRE_HOURS,
          "登录返回的 expireHours = %s（已从初版 72 收敛为 %d）"
          % (data.get("expireHours"), EXPECT_EXPIRE_HOURS))
    exp = claims.get("exp", 0)
    iat = claims.get("iat", 0)
    span_hours = (exp - iat) / 3600.0
    check(abs(span_hours - EXPECT_EXPIRE_HOURS) < 0.01,
          "令牌 exp - iat = %.2f 小时，与配置的 %d 小时一致" % (span_hours, EXPECT_EXPIRE_HOURS))
    return token


def verify_logout_revokes(token):
    print("\n[2] 登出前：令牌可用，且未出现在黑名单中")
    jti = track(token)
    js = req("GET", "/api/user/info", token=token)
    check(js.get("code") == 200, "登出前访问 /api/user/info 成功：%s" % js.get("message"))
    check((js.get("data") or {}).get("phone") == USER_PHONE,
          "返回的正是当前登录用户的资料")
    check(redis_ttl(revoke_key(jti)) == -2, "登出前该 jti 不在黑名单中")

    print("\n[3] 执行登出：服务端写入黑名单")
    js = logout(token)
    check(js.get("code") == 200, "POST /api/user/logout 返回成功：%s" % js.get("message"))
    ttl = redis_ttl(revoke_key(jti))
    check(ttl > 0, "黑名单键已写入且带 TTL（%d 秒），不会永久堆积" % ttl)
    check(ttl <= EXPECT_EXPIRE_HOURS * 3600 + 5,
          "黑名单 TTL 不超过令牌有效期（%d 小时）" % EXPECT_EXPIRE_HOURS)
    body = redis("get", revoke_key(jti))
    check(body.startswith("user:"), "黑名单值记录了吊销主体，便于排查：%s" % body)

    print("\n[4] 登出后：同一枚令牌立即失效")
    js = req("GET", "/api/user/info", token=token)
    check(js.get("code") == 401, "原令牌访问 /api/user/info 返回 401：%s" % js.get("message"))
    check("失效" in (js.get("message") or "") or "登录" in (js.get("message") or ""),
          "提示语告知登录状态已失效：%s" % js.get("message"))
    js = req("GET", "/api/cart/list", token=token)
    check(js.get("code") == 401, "其他需登录接口（/api/cart/list）同样被拒绝：%s" % js.get("message"))
    js = req("GET", "/api/book/hot")
    check(js.get("code") == 200,
          "不带令牌的公共浏览（图书热榜）不受影响：登出只作废那一枚凭证，不影响系统本身")
    js = req("GET", "/api/book/hot", token=token)
    check(js.get("code") == 401,
          "携带已失效令牌访问任何接口都会被拒 —— 该凭证在被吊销的瞬间就彻底作废")


def verify_idempotent_logout():
    print("\n[5] 登出幂等：重复登出 / 无令牌 / 非法令牌都返回成功")
    js = logout(None)
    check(js.get("code") == 200, "不携带令牌调用登出仍返回成功（幂等）：%s" % js.get("message"))

    js = logout(fake_token())
    check(js.get("code") == 200,
          "携带签名非法令牌调用登出返回成功，不把用户卡在登不出去的状态：%s" % js.get("message"))

    token, _ = user_token()
    track(token)
    first = logout(token).get("code")
    second = logout(token).get("code")
    check(first == 200 and second == 200, "同一枚令牌连续登出两次都返回成功（%s / %s）" % (first, second))


def verify_scope_isolation():
    print("\n[6] 吊销只作用于被登出的那一枚令牌")
    token_a, _ = user_token()
    token_b, _ = user_token()
    jti_a, jti_b = track(token_a), track(token_b)
    check(jti_a != jti_b, "两次登录得到不同的 jti")

    check(logout(token_a).get("code") == 200, "设备 A 登出成功")
    check(req("GET", "/api/user/info", token=token_a).get("code") == 401,
          "设备 A 的令牌已失效")
    check(req("GET", "/api/user/info", token=token_b).get("code") == 200,
          "设备 B 的令牌不受影响（一个设备登出不该把其他设备一起踢下线）")
    check(redis_ttl(revoke_key(jti_b)) == -2, "设备 B 的 jti 未被写入黑名单")

    print("\n[7] 重新登录可正常使用，且新令牌不受旧吊销记录影响")
    token_c, _ = user_token()
    jti_c = track(token_c)
    check(jti_c not in (jti_a, jti_b), "重新登录拿到新的 jti")
    check(req("GET", "/api/user/info", token=token_c).get("code") == 200,
          "新令牌可正常访问（黑名单不会误伤后来签发的令牌）")
    check(req("GET", "/api/user/info", token=token_a).get("code") == 401,
          "旧令牌仍然失效，不会因为重新登录而「复活」")


def verify_admin_logout():
    print("\n[8] 管理端登出：令牌同样在服务端作废")
    token, data = admin_token()
    check(bool(token), "管理员登录成功")
    jti = track(token)
    js = req("GET", "/api/admin/info", token=token)
    check(js.get("code") == 200, "登出前访问 /api/admin/info 成功：%s" % js.get("message"))

    js = logout(token, path="/api/admin/logout")
    check(js.get("code") == 200, "POST /api/admin/logout 返回成功：%s" % js.get("message"))
    ttl = redis_ttl(revoke_key(jti))
    check(ttl > 0, "管理端令牌已写入黑名单（TTL %d 秒）" % ttl)
    check(redis("get", revoke_key(jti)).startswith("admin:"),
          "黑名单值记录为 admin 主体：%s" % redis("get", revoke_key(jti)))

    js = req("GET", "/api/admin/info", token=token)
    check(js.get("code") == 401, "登出后管理端接口返回 401：%s" % js.get("message"))
    js = req("GET", "/api/admin/dashboard", token=token)
    check(js.get("code") == 401, "其他管理端接口同样被拒绝")

    js = logout(None, path="/api/admin/logout")
    check(js.get("code") == 200, "管理端登出同样幂等（无令牌也返回成功）")

    token2, _ = admin_token()
    track(token2)
    check(req("GET", "/api/admin/info", token=token2).get("code") == 200,
          "管理员重新登录后可正常使用")


def main():
    print("=" * 72)
    print("会话可吊销（令牌吊销）验证  BASE=%s" % BASE)
    print("预期令牌有效期：%d 小时；黑名单键前缀：%s" % (EXPECT_EXPIRE_HOURS, REVOKE_PREFIX))
    print("=" * 72)

    probe = req("GET", "/api/category/list")
    if probe.get("code") != 200:
        print("[ERROR] 后端接口不可用，请先启动后端（默认 http://127.0.0.1:8080）")
        return 2
    ping = redis("ping")
    if "PONG" not in ping.upper():
        print("[ERROR] Redis 不可用，请先启动 Redis（令牌吊销依赖 Redis 黑名单）")
        return 2

    atexit.register(cleanup)
    try:
        token = verify_token_shape()
        verify_logout_revokes(token)
        verify_idempotent_logout()
        verify_scope_isolation()
        verify_admin_logout()
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
