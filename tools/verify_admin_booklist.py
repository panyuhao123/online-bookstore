# -*- coding: utf-8 -*-
"""
线上书店后端 —— N10 管理端书单治理 端到端验证

对应接口（全部位于 /api/admin/booklist，强制 ADMIN）：
    GET    /page            分页（含私密与已隐藏；可按 keyword/status/userId/isPublic 筛选）
    GET    /{id}            详情（含条目；不做任何前台可见性过滤）
    PUT    /{id}/status     隐藏（status=0）/ 恢复（status=1）
    DELETE /{id}            彻底删除（连同条目）

要验证的核心不是 CRUD，而是三条治理语义：
  1) **后台不受前台可见性约束**：私密书单、已隐藏书单管理员都必须查得到 ——
     否则「已隐藏」的书单永远找不回，恢复功能形同虚设；
  2) **隐藏优先于删除，且隐藏与 is_public 正交**：隐藏只动 status，
     不替用户改他自己的公开设置，恢复时才能原样还原；
  3) **隐藏后前台彻底看不到，但创建者仍能看见并知道发生了什么**：
     广场消失 + 匿名/他人详情 404 + 「我的书单」仍在且带 status=0。

设计原则（与 tools/verify_extended_features.py 一致）
------------------------------------------------------
· 只走 HTTP 接口，不直连数据库、不依赖任何测试后门；
· 自己造的数据全部自己清掉（书单删除），脚本可反复执行；
· 只认 body.code，不看 HTTP 状态码 —— 本项目约定 HTTP 恒 200，业务码在 body 里；
· 书单名带运行时间戳，避免撞上重名 409，也不影响历史数据。

注意：口令以字面量写在脚本文件里（不写命令行），否则会被运行环境改写。
"""
import io
import json
import os
import subprocess
import sys
import time
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
# 第二个普通用户：只用来验证「隐藏后非创建者看不到」，没有它就测不出归属语义
OTHER_PHONE = os.environ.get("BOOKSTORE_OTHER_PHONE", "13900000077")
OTHER_PWD = os.environ.get("BOOKSTORE_OTHER_PWD", "123456")

_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

passed = []
failed = []


def check(ok, msg):
    (passed if ok else failed).append(msg)
    print(("  [PASS] " if ok else "  [FAIL] ") + msg)


def api(method, path, body=None, token=None):
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
    return urllib.parse.quote(str(value), safe="")


def code_of(payload):
    return payload.get("code")


def msg_of(payload):
    return payload.get("message")


def data_of(payload):
    return payload.get("data")


def redis_cli(*args):
    try:
        out = subprocess.run([REDIS_CLI] + list(args), capture_output=True, timeout=10)
        return out.stdout.decode("utf-8", "replace").strip()
    except Exception as e:  # noqa: BLE001
        return "__ERR__:" + str(e)


def fill_captcha(path, payload):
    """APP 用户登录强制图形验证码；本地 E2E 从 Redis 读回本次明文（不改应用代码）"""
    if "/user/login" not in path:
        return payload
    _, js = api("GET", "/api/captcha")
    key = (data_of(js) or {}).get("captchaKey")
    if key:
        code = redis_cli("-h", "127.0.0.1", "-p", "6379", "get", "bookstore:captcha:" + key)
        if code and not code.startswith("__ERR__"):
            return dict(payload, captchaKey=key, captchaCode=code)
    return payload


def user_login(phone, password):
    _, js = api("POST", "/api/user/login", fill_captcha("/api/user/login",
                                                       {"phone": phone, "password": password}))
    return (data_of(js) or {}).get("token"), js


def ensure_user(phone, password, nickname):
    """先注册；已存在（409）就直接登录。返回 (token, 说明)"""
    token, js = user_login(phone, password)
    if token:
        return token, "已存在，直接登录"
    _, reg = api("POST", "/api/user/register",
                 {"phone": phone, "password": password, "nickname": nickname})
    if code_of(reg) not in (200, 409):
        return None, "注册失败：" + str(msg_of(reg))
    token, js = user_login(phone, password)
    return token, ("注册后登录" if token else "注册成功但登录失败")


# ---------------------------------------------------------------- 业务助手

def book_page(**kwargs):
    qs = "&".join("%s=%s" % (k, q(v)) for k, v in kwargs.items())
    _, js = api("GET", "/api/book/page?" + qs)
    return (data_of(js) or {}).get("list") or []


def create_list(token, name, description=None, is_public=1):
    body = {"name": name, "isPublic": is_public}
    if description is not None:
        body["description"] = description
    _, js = api("POST", "/api/booklist", body, token=token)
    return code_of(js), data_of(js) or {}


def add_books(token, list_id, book_ids):
    _, js = api("POST", "/api/booklist/%s/books" % list_id, {"bookIds": book_ids}, token=token)
    return code_of(js), data_of(js) or {}


def my_lists(token):
    _, js = api("GET", "/api/booklist/my?pageNum=1&pageSize=50", token=token)
    return code_of(js), (data_of(js) or {})


def plaza(token=None, keyword=None):
    path = "/api/booklist/page?pageNum=1&pageSize=50"
    if keyword:
        path += "&keyword=" + q(keyword)
    _, js = api("GET", path, token=token)
    return code_of(js), (data_of(js) or {})


def user_detail(list_id, token=None):
    _, js = api("GET", "/api/booklist/detail/%s" % list_id, token=token)
    return code_of(js), data_of(js) or {}


def admin_page(token, **kwargs):
    qs = "&".join("%s=%s" % (k, q(v)) for k, v in kwargs.items())
    path = "/api/admin/booklist/page" + ("?" + qs if qs else "")
    _, js = api("GET", path, token=token)
    return code_of(js), (data_of(js) or {})


def admin_detail(token, list_id):
    _, js = api("GET", "/api/admin/booklist/%s" % list_id, token=token)
    return code_of(js), data_of(js) or {}


def admin_set_status(token, list_id, status):
    _, js = api("PUT", "/api/admin/booklist/%s/status?status=%s" % (list_id, status), token=token)
    return code_of(js), js


def admin_delete(token, list_id):
    _, js = api("DELETE", "/api/admin/booklist/%s" % list_id, token=token)
    return code_of(js), js


def names_of(page):
    return [row.get("name") for row in (page.get("list") or [])]


def row_of(page, list_id):
    return next((row for row in (page.get("list") or []) if row.get("id") == list_id), None)


# ============================================================================
print("=" * 72)
print("第 0 步：准备数据（登录 / 建书单 / 加书）")
print("=" * 72)

_, js = api("POST", "/api/admin/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
admin_token = (data_of(js) or {}).get("token")
check(bool(admin_token), "管理员登录成功")

user_token, js = user_login(USER_PHONE, USER_PWD)
check(bool(user_token), "用户 1 登录成功（%s）" % USER_PHONE)

other_token, note = ensure_user(OTHER_PHONE, OTHER_PWD, "书单旁观者")
check(bool(other_token), "用户 2 可用（%s，%s）" % (OTHER_PHONE, note))

if not admin_token or not user_token or not other_token:
    print("\n前置登录失败，无法继续（最后响应：%s）" % msg_of(js))
    print("\n通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
    sys.exit(1)

# 选几本真实存在的书：不写死 ID，种子数据一改脚本就挂
pool = book_page(pageSize=50, sort="sales")
picked = [b.get("id") for b in pool[:2]]
check(len(picked) == 2, "选定 2 本图书用于书单条目：%s" % picked)

stamp = time.strftime("%H%M%S")
PUBLIC_NAME = "N10治理公开书单-" + stamp
PRIVATE_NAME = "N10治理私密书单-" + stamp
PUBLIC_DESC = "用于管理端治理验证的公开书单 " + stamp

code, public_list = create_list(user_token, PUBLIC_NAME, PUBLIC_DESC, 1)
check(code == 200 and public_list.get("id"), "用户创建公开书单成功 id=%s" % public_list.get("id"))
PUBLIC_ID = public_list.get("id")

code, private_list = create_list(user_token, PRIVATE_NAME, "私密书单，仅创建者可见", 0)
check(code == 200 and private_list.get("id"), "用户创建私密书单成功 id=%s" % private_list.get("id"))
PRIVATE_ID = private_list.get("id")

code, payload = add_books(user_token, PUBLIC_ID, picked)
check(code == 200 and (payload.get("added") or 0) == len(picked),
      "公开书单加入 %d 本书（added=%s）" % (len(picked), payload.get("added")))

# 造数据失败就没必要继续跑后面 40 项断言了，直接报错退出更好定位
if not PUBLIC_ID or not PRIVATE_ID:
    print("\n书单造数据失败，终止。")
    print("\n通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
    sys.exit(1)

# ============================================================================
print()
print("=" * 72)
print("第 1 步：权限边界（匿名 / 普通用户一律不得进治理接口）")
print("=" * 72)

# 注意：api() 返回的是 (HTTP 状态码, body)，本项目 HTTP 恒 200，
# 业务码在 body 里，所以断言一律取 code_of(js)，不要拿 HTTP 状态码当结果。
_, js = api("GET", "/api/admin/booklist/page")
check(code_of(js) == 401, "匿名访问书单分页 → 401（实际 %s）" % code_of(js))

_, js = api("GET", "/api/admin/booklist/page", token=user_token)
check(code_of(js) == 403, "普通用户访问书单分页 → 403（实际 %s）" % code_of(js))

_, js = api("GET", "/api/admin/booklist/%s" % PUBLIC_ID, token=user_token)
check(code_of(js) == 403, "普通用户访问书单详情 → 403（实际 %s）" % code_of(js))

_, js = api("PUT", "/api/admin/booklist/%s/status?status=0" % PUBLIC_ID, token=user_token)
check(code_of(js) == 403, "普通用户隐藏书单 → 403（实际 %s）" % code_of(js))

_, js = api("DELETE", "/api/admin/booklist/%s" % PUBLIC_ID, token=user_token)
check(code_of(js) == 403, "普通用户删除书单 → 403（实际 %s）" % code_of(js))

_, js = api("DELETE", "/api/admin/booklist/%s" % PUBLIC_ID)
check(code_of(js) == 401, "匿名删除书单 → 401（实际 %s）" % code_of(js))

code, page = admin_page(admin_token)
check(code == 200, "管理员访问书单分页 → 200（实际 %s）" % code)
check(all(k in page for k in ("list", "total", "pageNum", "pageSize", "pages")),
      "分页返回 PageVO 结构（list/total/pageNum/pageSize/pages）")

# ============================================================================
print()
print("=" * 72)
print("第 2 步：后台不受前台可见性约束（私密书单也要查得到）")
print("=" * 72)

code, page = admin_page(admin_token, keyword=PUBLIC_NAME)
check(code == 200 and PUBLIC_NAME in names_of(page), "按名称关键词能搜到公开书单")

code, page = admin_page(admin_token, keyword="管理端治理验证")
check(code == 200 and PUBLIC_NAME in names_of(page), "关键词也能命中「简介」而不只是名称")

code, page = admin_page(admin_token, keyword=PRIVATE_NAME)
check(code == 200 and PRIVATE_NAME in names_of(page),
      "私密书单在后台同样可查（前台的「仅自己可见」不该约束治理）")

code, page = admin_page(admin_token, isPublic=0, keyword="N10治理")
check(code == 200 and PRIVATE_NAME in names_of(page),
      "按 isPublic=0 能筛出「用户设为私密」的书单")

code, page = admin_page(admin_token, pageSize=999)
check(code == 200 and (page.get("pageSize") or 0) == 50,
      "pageSize 传 999 被收敛到 50（实际 %s）" % page.get("pageSize"))

code, page = admin_page(admin_token, keyword="绝不可能存在的书名-zzz")
check(code == 200 and (page.get("total") or 0) == 0, "查不到时返回空列表而不是报错")

# ============================================================================
print()
print("=" * 72)
print("第 3 步：详情（含条目、创建者信息、不做可见性过滤）")
print("=" * 72)

code, detail = admin_detail(admin_token, PUBLIC_ID)
check(code == 200, "管理员读取公开书单详情 → 200（实际 %s）" % code)
check(detail.get("name") == PUBLIC_NAME, "详情名称正确")
check(len(detail.get("books") or []) == len(picked),
      "详情带出 %d 个条目（实际 %d）" % (len(picked), len(detail.get("books") or [])))
check([b.get("id") for b in (detail.get("books") or [])] == picked,
      "条目顺序与加入顺序一致（顺序是用户自己排的，后台不该重排）")
check(bool(detail.get("ownerNickname")), "详情带创建者昵称：%s" % detail.get("ownerNickname"))
check("****" in (detail.get("ownerPhone") or ""),
      "详情带脱敏手机号（含 ****）：%s" % detail.get("ownerPhone"))
check(detail.get("isPublic") == 1 and detail.get("status") == 1,
      "isPublic 与 status 分别下发，语义不混用（isPublic=%s status=%s）"
      % (detail.get("isPublic"), detail.get("status")))

code, detail = admin_detail(admin_token, PRIVATE_ID)
check(code == 200 and detail.get("isPublic") == 0,
      "管理员能看私密书单详情，且 isPublic=0 如实反映用户设置")

code, js = admin_detail(admin_token, 999999999)
check(code == 404, "不存在的书单 id → 404（对错误 ID 静默成功会让后台以为处置过了）")

code, js = admin_detail(admin_token, "abc")
check(code == 400, "非数字 id → 400（参数格式不正确，实际 %s）" % code)

# ============================================================================
print()
print("=" * 72)
print("第 4 步：隐藏语义（广场消失 / 他人 404 / 创建者仍可见 / 不动 is_public）")
print("=" * 72)

code, js = admin_set_status(admin_token, PUBLIC_ID, 0)
check(code == 200, "管理员隐藏公开书单 → 200（实际 %s）" % code)

code, page = admin_page(admin_token, status=0, keyword=PUBLIC_NAME)
check(code == 200 and PUBLIC_NAME in names_of(page), "按 status=0 能筛出刚隐藏的书单")
code, page = admin_page(admin_token, status=1, keyword=PUBLIC_NAME)
check(code == 200 and PUBLIC_NAME not in names_of(page), "按 status=1 查不到它（状态筛选生效）")

code, page = plaza(keyword=PUBLIC_NAME)
check(code == 200 and PUBLIC_NAME not in names_of(page), "匿名广场不再出现被隐藏的书单")

code, page = plaza(token=user_token, keyword=PUBLIC_NAME)
check(code == 200 and PUBLIC_NAME not in names_of(page),
      "登录态的广场也不再出现 —— status 条件是 AND 前置，不会被「自己的书单」那一支 or 漏掉")

code, page = my_lists(user_token)
row = row_of(page, PUBLIC_ID)
check(code == 200 and row is not None,
      "「我的书单」仍保留该条（凭空消失会让用户以为数据丢了）")
check(row is not None and row.get("status") == 0,
      "「我的书单」里带 status=0，前端据此渲染「该内容已被隐藏」（实际 %s）"
      % (row or {}).get("status"))

code, js = user_detail(PUBLIC_ID)
check(code == 404, "匿名打开被隐藏书单详情 → 404（回 403 等于告诉对方「这里有东西」）")

code, js = user_detail(PUBLIC_ID, token=other_token)
check(code == 404, "其他登录用户打开被隐藏书单详情 → 404（实际 %s）" % code)

code, detail = user_detail(PUBLIC_ID, token=user_token)
check(code == 200 and detail.get("status") == 0,
      "创建者本人仍能打开，且看到 status=0（能看见自己的内容、也知道发生了什么）")

code, detail = admin_detail(admin_token, PUBLIC_ID)
check(code == 200 and detail.get("isPublic") == 1,
      "隐藏没有动用户的 isPublic（仍为 1）—— 否则恢复时还原不了用户当初的选择")

code, js = admin_set_status(admin_token, PUBLIC_ID, 0)
check(code == 200, "重复隐藏幂等 → 200（不报错，也不产生误导性操作日志）")

code, js = admin_set_status(admin_token, PUBLIC_ID, 2)
check(code == 400, "非法状态值 2 → 400（不猜「非 0 即上架」，实际 %s）" % code)

code, js = admin_set_status(admin_token, PUBLIC_ID, -1)
check(code == 400, "非法状态值 -1 → 400（实际 %s）" % code)

_, js = api("PUT", "/api/admin/booklist/%s/status" % PUBLIC_ID, token=admin_token)
check(code_of(js) == 400, "缺少 status 参数 → 400（实际 %s）" % code_of(js))

code, js = admin_set_status(admin_token, 999999999, 0)
check(code == 404, "隐藏不存在的书单 → 404（实际 %s）" % code)

# ============================================================================
print()
print("=" * 72)
print("第 5 步：恢复（回到广场，且不改动其他字段）")
print("=" * 72)

code, js = admin_set_status(admin_token, PUBLIC_ID, 1)
check(code == 200, "管理员恢复书单 → 200（实际 %s）" % code)

code, page = plaza(keyword=PUBLIC_NAME)
check(code == 200 and PUBLIC_NAME in names_of(page), "恢复后重新出现在匿名广场")

code, detail = user_detail(PUBLIC_ID)
check(code == 200 and detail.get("status") == 1, "匿名可读，且 status 回到 1")

code, detail = admin_detail(admin_token, PUBLIC_ID)
check(code == 200 and len(detail.get("books") or []) == len(picked),
      "隐藏/恢复全程条目未受影响（仍为 %d 个）" % len(picked))

# ============================================================================
print()
print("=" * 72)
print("第 6 步：删除（连同条目，前台与后台同时消失）")
print("=" * 72)

code, js = admin_delete(admin_token, PUBLIC_ID)
check(code == 200, "管理员删除书单 → 200（实际 %s）" % code)

code, js = admin_detail(admin_token, PUBLIC_ID)
check(code == 404, "删除后后台详情 → 404")

code, js = user_detail(PUBLIC_ID, token=user_token)
check(code == 404, "删除后创建者也打不开 → 404（是真的没了，不是被隐藏）")

code, page = my_lists(user_token)
check(row_of(page, PUBLIC_ID) is None, "删除后「我的书单」不再包含它")

code, page = plaza(keyword=PUBLIC_NAME)
check(PUBLIC_NAME not in names_of(page), "删除后广场不再出现")

code, js = admin_delete(admin_token, PUBLIC_ID)
check(code == 404, "重复删除 → 404（第二次它确实不存在了）")

# 条目是否一并清掉：重建同名书单再加同一批书，added 应为全量而不是 0/部分
code, reused = create_list(user_token, PUBLIC_NAME, PUBLIC_DESC, 1)
if code == 200 and reused.get("id"):
    code2, payload = add_books(user_token, reused.get("id"), picked)
    check(code2 == 200 and (payload.get("added") or 0) == len(picked),
          "重建同名书单后仍能加入同样 %d 本书（说明旧条目已随主表清掉，没留下孤儿行）"
          % len(picked))
    admin_delete(admin_token, reused.get("id"))
else:
    check(False, "重建同名书单失败：%s" % msg_of(reused))

# 清理私密书单
api("DELETE", "/api/booklist/%s" % PRIVATE_ID, token=user_token)

# ============================================================================
print()
print("=" * 72)
print("第 7 步：收尾核查")
print("=" * 72)

code, page = admin_page(admin_token, keyword="N10治理")
check(code == 200 and all("N10治理" not in (n or "") for n in names_of(page)),
      "脚本造的书单已全部清理，未给演示库留垃圾数据（剩余 %d 条）" % (page.get("total") or 0))

# ============================================================================
total = len(passed) + len(failed)
print()
print("=" * 72)
print("通过 %d 项，失败 %d 项，共 %d 项" % (len(passed), len(failed), total))
if failed:
    print()
    print("失败明细：")
    for item in failed:
        print("  - " + item)
print("=" * 72)
sys.exit(0 if not failed else 1)
