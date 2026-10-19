# -*- coding: utf-8 -*-
"""
线上书店后端 —— 密码哈希透明升级验证（批 2）

验证目标：
  1. 存量 MD5(明文 + salt) 账号仍能正常登录（升级不能把老用户锁在门外）；
  2. 登录成功后，库中密文被就地改写为 BCrypt（$2a$10$ 开头，60 字符）；
  3. 改写后再次登录走 BCrypt 校验路径，依然通过；
  4. 密码错误时既不发令牌、也不改写密文；
  5. 新注册用户的密文从一开始就是 BCrypt；
  6. 管理员账号与 APP 用户走同一套策略。

⚠️ 本脚本会把「演示账号」user 13800138000 与 admin 的 password/salt 两列
   重置为 seed.sql 中的历史 MD5 值，以便每次运行都能完整演示迁移过程。
   只影响这两个演示账号的这两列，不触碰其他数据。加 --no-reset 可跳过重置。

注意：口令以字面量写在脚本文件里（不写命令行），否则会被运行环境改写。
"""
import atexit
import json
import os
import random
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

BASE = os.environ.get("BOOKSTORE_BASE", "http://127.0.0.1:8080")
REDIS_CLI = os.environ.get("BOOKSTORE_REDIS_CLI", "D:/redis5/redis-cli.exe")
MYSQL_BIN = os.environ.get(
    "BOOKSTORE_MYSQL", "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe")

ADMIN_USER = os.environ.get("BOOKSTORE_ADMIN_USER", "admin")
ADMIN_PWD = os.environ.get("BOOKSTORE_ADMIN_PWD", "123456")
USER_PHONE = os.environ.get("BOOKSTORE_USER_PHONE", "13800138000")
USER_PWD = os.environ.get("BOOKSTORE_USER_PWD", "123456")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCAL_YML = os.path.join(ROOT, "bookstore-server", "src", "main", "resources", "application-local.yml")

# seed.sql 中的历史密文（MD5(明文 + salt)），供重置与断言使用
LEGACY_USER_HASH, LEGACY_USER_SALT = "58ab4bd28431ad3abf469ff87e3658d6", "user888"
LEGACY_ADMIN_HASH, LEGACY_ADMIN_SALT = "95719c0d6509e437a5182cd4c3e8cb73", "admin888"

_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

passed = []
failed = []


def check(ok, msg):
    (passed if ok else failed).append(msg)
    print(("  [PASS] " if ok else "  [FAIL] ") + msg)


def skip(msg):
    print("  [SKIP] " + msg)


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


# ---------------------------------------------------------------- 数据库

def mysql_config():
    """从 application-local.yml 取账号口令生成临时配置文件，避免口令出现在命令行上"""
    user, pwd = "root", ""
    with open(LOCAL_YML, encoding="utf-8") as f:
        for line in f:
            m = re.match(r"\s*username:\s*(.+?)\s*$", line)
            if m and user == "root":
                user = m.group(1).strip().strip('"')
            m = re.match(r'\s*password:\s*"?(.*?)"?\s*$', line)
            if m:
                pwd = m.group(1).strip().strip('"')
                break
    path = os.path.join(tempfile.gettempdir(), "wb_bookstore_verify.cnf")
    with open(path, "w", encoding="ascii") as f:
        f.write("[client]\nuser=%s\npassword=%s\nhost=127.0.0.1\nport=3306\n" % (user, pwd))
    # 含口令的临时文件用完即删，不在磁盘上留凭据
    atexit.register(lambda: os.path.exists(path) and os.remove(path))
    return path


def sql(query):
    """执行一条 SQL，返回制表符分隔的行列表"""
    out = subprocess.run(
        [MYSQL_BIN, "--defaults-extra-file=" + mysql_config(), "-N", "-B", "-e", query],
        capture_output=True, timeout=20)
    if out.returncode != 0:
        raise RuntimeError("MySQL 执行失败：" + out.stderr.decode("utf-8", "replace").strip())
    # 注意：不能对整体结果做 strip()，否则行尾的空字段（salt 为空串）会被吃掉，
    # 导致 split("\t") 少一列。这里只丢掉空行。
    text = out.stdout.decode("utf-8", "replace").replace("\r", "")
    return [line.split("\t") for line in text.split("\n") if line != ""]


def user_row(phone):
    rows = sql("SELECT password, salt FROM bookstore.user WHERE phone='%s'" % phone)
    return rows[0] if rows else None


def admin_row(username):
    rows = sql("SELECT password, salt FROM bookstore.admin WHERE username='%s'" % username)
    return rows[0] if rows else None


# ---------------------------------------------------------------- 辅助

def redis_get(key):
    try:
        out = subprocess.run([REDIS_CLI, "-h", "127.0.0.1", "-p", "6379", "get", key],
                             capture_output=True, timeout=5)
        return out.stdout.decode("utf-8", "replace").strip()
    except Exception:
        return ""


def login_with_captcha(path, payload):
    js = req("GET", "/api/captcha")
    key = (js.get("data") or {}).get("captchaKey")
    if key:
        code = redis_get("bookstore:captcha:" + key)
        if code:
            payload = dict(payload, captchaKey=key, captchaCode=code)
    return req("POST", path, payload)


def is_bcrypt(value):
    return bool(re.match(r"^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$", value or ""))


def is_legacy_md5(value):
    return bool(re.match(r"^[0-9a-f]{32}$", value or ""))


# ---------------------------------------------------------------- 主流程

def reset_demo_accounts():
    print("\n[0] 把演示账号重置为历史 MD5 密文（仅这两个账号的 password/salt 两列）")
    sql("UPDATE bookstore.user SET password='%s', salt='%s' WHERE phone='%s';"
        % (LEGACY_USER_HASH, LEGACY_USER_SALT, USER_PHONE))
    sql("UPDATE bookstore.admin SET password='%s', salt='%s' WHERE username='%s';"
        % (LEGACY_ADMIN_HASH, LEGACY_ADMIN_SALT, ADMIN_USER))
    u, a = user_row(USER_PHONE), admin_row(ADMIN_USER)
    check(u is not None and u[0] == LEGACY_USER_HASH, "用户 %s 已重置为历史 MD5 密文" % USER_PHONE)
    check(a is not None and a[0] == LEGACY_ADMIN_HASH, "管理员 %s 已重置为历史 MD5 密文" % ADMIN_USER)


def verify_legacy_login_and_upgrade(reset):
    """reset=True：账号刚被重置为 MD5，可完整验证「旧密文 → 升级」。
    reset=False：账号多半已是 BCrypt，跑 --no-reset 时应跳过迁移段断言而非报失败。"""
    print("\n[1] 历史 MD5 账号登录 → 密文应被就地升级为 BCrypt")

    before_u = user_row(USER_PHONE)
    if reset:
        check(is_legacy_md5(before_u[0]), "登录前用户密文为 32 位十六进制（旧算法）")
        check(not is_bcrypt(before_u[0]), "登录前用户密文不是 BCrypt")
    elif is_bcrypt(before_u[0]):
        skip("--no-reset 且账号已是 BCrypt，跳过「旧密文迁移」相关断言"
             "（去掉 --no-reset 即可完整演示迁移）")

    js = login_with_captcha("/api/user/login", {"phone": USER_PHONE, "password": USER_PWD})
    check(js.get("code") == 200, "历史账号用原密码登录成功（透明升级不阻断登录）：%s" % js.get("message"))
    token = (js.get("data") or {}).get("token")
    check(bool(token), "登录接口返回了令牌")

    after_u = user_row(USER_PHONE)
    print("      登录前：%s" % before_u[0])
    print("      登录后：%s" % after_u[0])
    check(is_bcrypt(after_u[0]), "登录后用户密文为 BCrypt（$2a$10$ 前缀）")
    check(len(after_u[0]) == 60, "BCrypt 密文长度 60，可容纳于 password VARCHAR(64)")
    check(after_u[1] == "", "BCrypt 数据下 salt 列为空串占位")
    if reset:
        check(after_u[0] != before_u[0], "密文确实被改写（非原值返回）")

    print("\n[2] 升级后再次登录（走 BCrypt 校验路径）")
    js2 = login_with_captcha("/api/user/login", {"phone": USER_PHONE, "password": USER_PWD})
    check(js2.get("code") == 200, "BCrypt 账号二次登录成功：%s" % js2.get("message"))
    after_u2 = user_row(USER_PHONE)
    check(after_u2[0] == after_u[0], "已是 BCrypt 的账号不会被重复改写")

    print("\n[3] 密码错误：不发令牌、不改写密文")
    js3 = login_with_captcha("/api/user/login", {"phone": USER_PHONE, "password": USER_PWD + "x"})
    check(js3.get("code") == 401, "错误密码返回 401：%s" % js3.get("message"))
    check(not (js3.get("data") or {}).get("token"), "错误密码不返回令牌")
    check(user_row(USER_PHONE)[0] == after_u[0], "错误密码不会改写库中密文")

    print("\n[4] 管理员账号走同一套策略")
    before_a = admin_row(ADMIN_USER)
    if reset:
        check(is_legacy_md5(before_a[0]), "登录前管理员密文为历史 MD5")
    js4 = req("POST", "/api/admin/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
    check(js4.get("code") == 200, "管理员用原口令登录成功：%s" % js4.get("message"))
    after_a = admin_row(ADMIN_USER)
    print("      登录前：%s" % before_a[0])
    print("      登录后：%s" % after_a[0])
    check(is_bcrypt(after_a[0]), "登录后管理员密文为 BCrypt")
    js5 = req("POST", "/api/admin/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
    check(js5.get("code") == 200, "管理员二次登录（BCrypt 路径）成功")


def verify_new_registration_is_bcrypt():
    print("\n[5] 新注册用户的密文从第一天起就是 BCrypt")
    phone = "18" + "".join(random.choice("0123456789") for _ in range(9))
    js = req("POST", "/api/user/register",
             {"phone": phone, "password": "NewPass123", "nickname": "哈希验证"})
    check(js.get("code") == 200, "新用户注册成功 phone=%s：%s" % (phone, js.get("message")))
    row = user_row(phone)
    if row:
        print("      新用户密文：%s" % row[0])
        check(is_bcrypt(row[0]), "新用户密文为 BCrypt")
        check(row[1] == "", "新用户 salt 列为空串")
        check(not is_legacy_md5(row[0]), "新用户密文不是裸 MD5")
        js2 = login_with_captcha("/api/user/login", {"phone": phone, "password": "NewPass123"})
        check(js2.get("code") == 200, "新用户可正常登录")
        # 清理：删除本次验证创建的账号，避免污染演示数据
        sql("DELETE FROM bookstore.user WHERE phone='%s';" % phone)
        check(user_row(phone) is None, "验证账号已清理")
    else:
        check(False, "未能读到新注册用户的数据库行")


def main():
    print("=" * 72)
    print("密码哈希透明升级验证  BASE=%s" % BASE)
    print("=" * 72)
    if "--no-reset" not in sys.argv:
        reset_demo_accounts()
    verify_legacy_login_and_upgrade("--no-reset" not in sys.argv)
    verify_new_registration_is_bcrypt()

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
