#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一键回归：环境预检 → 拉起后端 → 依次执行全部验证脚本 → 汇总报告 → 收尾停服。

设计目标
--------
此前跑一次完整回归要手工做五件事：起 Redis/MinIO、起后端、等就绪、逐个跑 9 个脚本、
自己把「通过 N 项」加起来。手工环节越多，越容易漏跑某个脚本或跑在错误实例上
（例如忘记重启服务，结果新代码没被验证到）。本脚本把这条链路固化成一个命令。

用法
----
    # 全套（推荐）：起服务 → 跑 9 个端到端脚本 → 停服务
    python tools/run_all_checks.py

    # 连同单元测试、管理端测试与前端静态检查一起跑（质量门禁口径）
    python tools/run_all_checks.py --with-unit --with-admin --with-frontend

    # 再加上 PC 商城（bookstore-web）端到端：未起 Web dev server 时会自动拉起 5176
    python tools/run_all_checks.py --with-unit --with-admin --with-frontend --with-web

    # 复用已经在跑的实例（不再自己起/停服务）
    python tools/run_all_checks.py --reuse-server

    # 只跑其中几项
    python tools/run_all_checks.py --only=verify_capabilities,verify_cache_commit

    # 指向别的地址（例如 prod 形态实例）
    python tools/run_all_checks.py --base=http://127.0.0.1:8081 --reuse-server

返回码
------
    0 = 全部通过；1 = 有检查未通过或自身执行失败。

「需留意」与退出码的关系
------------------------
汇总里可能出现「需留意」条目（例如管理端只跑了 2/3 个测试文件）。它**不影响退出码**：
断言数字按实际跑到的如实统计，只是额外提示「本次覆盖可能不完整」。区分开「测试失败」
（必须红）与「覆盖不完整」（需要人看一眼），避免把环境问题误判成代码问题。

不在默认范围内的检查（需要额外实例或浏览器，见 --help 与 docs/测试报告.md）
--------------------------------------------------------------------------
    verify_cache_commit.py --legacy-base=...        需再起一个 CACHE_EVICT_AFTER_COMMIT=false 的实例
    verify_flyway_migration.py --fresh-db=...       需一个全新空库实例
    verify_observability.py --prod-base=...         需一个 prod profile 实例
    verify_cache_degrade.py                         会 taskkill Redis，演示期勿跑
    check_app_sfc.cjs                              随 --with-frontend 跑（APP 的 SFC 静态检查）
    verify_web_pc.cjs                              随 --with-web 跑（PC 商城端到端，需 Chrome；
                                                   未起 Web dev server 时脚本会自动拉起 5176）
    check_grid_styles.cjs / verify_web_guard|interaction|search.cjs
                                                   面向 APP H5（5175），本脚本不自动拉起该实例
    bench_cache.py / probe_openapi_docs.py          汇报型工具，不产出断言
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / "tools"
SERVER_DIR = ROOT / "bookstore-server"
WEB_DIR = ROOT / "bookstore-web"
OUT_DIR = TOOLS / "out"
SERVER_LOG = SERVER_DIR / "run_all_checks_server.log"
WEB_LOG = WEB_DIR / "run_all_checks_web.log"

WEB_PORT = 5176
WEB_URL = "http://localhost:%d" % WEB_PORT

DEFAULT_BASE = os.environ.get("BOOKSTORE_BASE", "http://127.0.0.1:8080")
DEFAULT_PORT = 8080

# 本机有 HTTP_PROXY，访问 127.0.0.1 必须绕开代理，否则拿到的是代理的错误响应
_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

# --------------------------------------------------------------------------- #
# 汇总行解析
#
# 各脚本历史上各写各的汇总格式（「通过 N 项，失败 M 项」「结果汇总：…」「通过 N / 失败 M」），
# 与其去改九个已经稳定的脚本，不如在这里各写一个解析器 —— 解析失败会被明确标记为
# 「异常」而不是悄悄算成 0 通过，所以不会掩盖问题。
# --------------------------------------------------------------------------- #
# 「通过 N 项，失败 M 项」/「结果：通过 N 项，失败 M 项」/「结果汇总：…」
RE_PY_SUMMARY = re.compile(r"通过\s*(\d+)\s*项[，,]\s*失败\s*(\d+)\s*项")
# check_app_sfc.cjs：「===== SFC 通过 32 / 失败 1 =====」
RE_CJS_SUMMARY = re.compile(r"通过\s*(\d+)\s*/\s*失败\s*(\d+)")
# Maven Surefire 总计行：「Tests run: 123, Failures: 0, Errors: 0, Skipped: 0」
RE_MVN_SUMMARY = re.compile(r"Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+)")

# 各检查的输出可能带 ANSI 颜色码（vitest 在管道下也照样上色），
# 形如 \x1b[2m      Tests \x1b[22m \x1b[1m\x1b[32m22 passed\x1b[39m，
# 会让「Tests\s+(\d+)」这类正则整行失配。统一剥离后再解析。
RE_ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")


def strip_ansi(text: str) -> str:
    return RE_ANSI.sub("", text)


def parse_py(text: str):
    """解析 python 端到端脚本的汇总行"""
    hits = RE_PY_SUMMARY.findall(text)
    return (int(hits[-1][0]), int(hits[-1][1])) if hits else None


def parse_cjs(text: str):
    """解析 check_app_sfc.cjs 的汇总行"""
    hits = RE_CJS_SUMMARY.findall(text)
    return (int(hits[-1][0]), int(hits[-1][1])) if hits else None


def parse_mvn(text: str):
    """解析 Surefire 汇总：失败数 = Failures + Errors（两者都该让门禁变红）"""
    hits = RE_MVN_SUMMARY.findall(text)
    if not hits:
        return None
    total, failures, errors = hits[-1]
    return (int(total), int(failures) + int(errors))


def parse_vitest(text: str):
    """
    解析 vitest 汇总。

    两种形态都要认：
      全通过  → 「Tests  22 passed (22)」
      有失败  → 「Tests  20 passed | 2 failed (22)」或纯「Tests  2 failed (2)」
    因此 passed / failed 分别独立匹配，缺哪边就按 0 计。
    """
    passed = re.findall(r"Tests\s+(\d+)\s+passed", text)
    failed = re.findall(r"Tests\s+[^\n]*?(\d+)\s+failed", text)
    if not passed and not failed:
        return None
    return (int(passed[-1]) if passed else 0, int(failed[-1]) if failed else 0)


RE_VITEST_FILES = re.compile(r"Test Files\s+(\d+)\s+passed\s*\((\d+)\)")


def admin_completeness_note(text: str) -> str:
    """
    校验管理端这一次是否「真的把该跑的用例都跑了」。

    必要性来自一次实测：在受限环境（往临时目录写文件被拒）下 vitest 会抛 unhandled
    error 并中断收集阶段 —— 3 个测试文件只跑了 2 个，但汇总行依然是
    「Tests 12 passed, 0 failed」，看起来完全正常。只统计断言数会把这种「漏跑」
    当成通过，所以这里额外比对「实际跑到的文件数 vs 仓库里的 spec 文件数」。
    """
    notes: list[str] = []
    spec_dir = ROOT / "bookstore-admin" / "src"
    expect_files = len(list(spec_dir.rglob("*.spec.js"))) if spec_dir.exists() else 0
    m = RE_VITEST_FILES.search(text)
    if expect_files and m:
        ran = int(m.group(1))
        if ran < expect_files:
            notes.append("只跑了 %d/%d 个测试文件（用例数不完整）" % (ran, expect_files))
    elif expect_files and not m:
        notes.append("未输出 Test Files 汇总行（可能整体未跑起来）")
    if "Unhandled Error" in text or re.search(r"Errors\s+\d+\s+error", text):
        notes.append("存在 unhandled error（受限环境下写临时目录被拒会触发，会连带跳过部分文件）")
    return "；".join(notes)

# (key, 说明, 是否需后端服务, 已知预期失败数)
PY_CHECKS = [
    ("verify_capabilities",       "核心能力：浏览 / 搜索 / 购物车 / 订单 / 评价闭环", True, 0),
    ("verify_review_pay_cart",    "评价 · 支付 · 购物车专项",                        True, 0),
    ("verify_schedule_stats_log", "定时任务 · 统计 · 操作日志",                      True, 0),
    ("verify_password_upgrade",   "密码哈希升级与登录透明重哈希",                    True, 0),
    ("verify_login_lockout",      "登录失败锁定与自动解锁",                          True, 0),
    ("verify_token_revoke",       "会话吊销（登出黑名单）",                          True, 0),
    ("verify_observability",      "可观测性：健康 / 信息 / 指标 / 埋点",             True, 0),
    ("verify_cache_commit",       "缓存清理时机（事务提交后清理）",                  True, 0),
    ("verify_flyway_migration",   "Flyway 迁移与结构快照一致性",                     True, 0),
]

# 静态检查：不需要后端服务，但需要 node
CJS_CHECKS = [
    ("check_app_sfc.cjs", "APP 端 SFC/JS 语法与结构", 1),
]

# PC 商城端到端：需要后端服务 + Web dev server(5176) + 本机 Chrome（用 --with-web 开启）
WEB_CHECKS = [
    ("verify_web_pc.cjs",
     "PC 商城端到端：官方书单 / 排序透传 / 检索翻页 / 对比栏同步 / 购物车批量 / 立即购买", 0),
]

INFRA = [
    ("MySQL", 3306, True,  "数据库（必需，未就绪则后端无法启动）"),
    ("Redis", 6379, True,  "缓存与验证码（必需，未就绪时依赖它的检查会失败）"),
    ("MinIO", 9000, False, "对象存储（可选，STORAGE_TYPE=local 时不参与）"),
]


def log(msg: str = "") -> None:
    print(msg, flush=True)


def rule(title: str) -> None:
    log()
    log("=" * 72)
    log(title)
    log("=" * 72)


def port_open(host: str, port: int, timeout: float = 1.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def http_ok(url: str, timeout: float = 4.0) -> bool:
    try:
        with _OPENER.open(url, timeout=timeout) as resp:
            return resp.status == 200
    except Exception:
        return False


def port_of(base: str) -> int:
    m = re.search(r":(\d+)", base)
    return int(m.group(1)) if m else DEFAULT_PORT


def pid_listening(port: int) -> int | None:
    """用 netstat 找监听指定端口的 PID（环境拦截 sc.exe，只能用 netstat）。"""
    try:
        out = subprocess.run(["netstat", "-ano"], capture_output=True, text=True,
                             encoding="utf-8", errors="replace", timeout=20).stdout
    except Exception:
        return None
    for line in out.splitlines():
        if "LISTENING" not in line:
            continue
        if re.search(r":%d\s" % port, line):
            parts = line.split()
            if parts and parts[-1].isdigit():
                return int(parts[-1])
    return None


def kill_tree(pid: int) -> bool:
    """taskkill /T 连同子进程（cmd → mvn → java）一起结束。"""
    try:
        r = subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)],
                           capture_output=True, text=True, encoding="utf-8", errors="replace")
        return r.returncode == 0
    except Exception:
        return False


# --------------------------------------------------------------------------- #
# 环境预检
# --------------------------------------------------------------------------- #
def precheck() -> list[str]:
    rule("一、环境预检")
    warnings: list[str] = []
    for name, port, required, note in INFRA:
        up = port_open("127.0.0.1", port)
        tag = "就绪" if up else ("未就绪" if required else "未启动")
        log("  [%s] %-6s :%-5d  %s" % ("OK" if up else ("!!" if required else "--"), name, port, tag))
        log("         %s" % note)
        if not up and required:
            warnings.append("%s(:%d) 未就绪 —— %s" % (name, port, note))
    return warnings


# --------------------------------------------------------------------------- #
# 服务起停
# --------------------------------------------------------------------------- #
def exe_cmd(exe: str) -> list[str]:
    """Windows 上 mvn / npm 是 .cmd 批处理，必须经 cmd /c 才能被执行"""
    if exe.lower().endswith((".cmd", ".bat")):
        return ["cmd", "/c", exe]
    return [exe]


def mvn_cmd() -> list[str] | None:
    exe = shutil.which("mvn")
    return exe_cmd(exe) if exe else None


def start_server(port: int) -> subprocess.Popen | None:
    cmd = mvn_cmd()
    if cmd is None:
        log("  !! 找不到 mvn，无法自动起服务")
        return None
    args = cmd + ["-o", "-Dfile.encoding=UTF-8", "spring-boot:run",
                  "-Dspring-boot.run.arguments=--server.port=%d" % port]
    log("  启动：%s" % " ".join(args))
    log("  日志：%s" % SERVER_LOG.relative_to(ROOT))
    fh = open(SERVER_LOG, "w", encoding="utf-8", errors="replace")
    env = dict(os.environ, PYTHONIOENCODING="utf-8")
    return subprocess.Popen(args, cwd=str(SERVER_DIR), stdout=fh, stderr=subprocess.STDOUT, env=env)


def wait_ready(base: str, timeout: int = 180) -> bool:
    """
    就绪判据用 /api/category/list 而不是 /actuator/health —— 后者不查库、恒 200，
    服务「端口已监听但数据库没连上」时会被误判成就绪（本项目排障铁律）。
    """
    probe = base.rstrip("/") + "/api/category/list"
    log("  等待就绪（探测 %s，最多 %ds）…" % (probe, timeout))
    deadline = time.time() + timeout
    tick = 0
    while time.time() < deadline:
        if http_ok(probe):
            log("  就绪，用时 %ds" % int(time.time() - (deadline - timeout)))
            return True
        tick += 1
        if tick % 10 == 0:
            log("    …已等待 %ds" % (timeout - int(deadline - time.time())))
        time.sleep(2)
    return False


def tail_server_log(lines: int = 20) -> None:
    if not SERVER_LOG.exists():
        return
    content = SERVER_LOG.read_text(encoding="utf-8", errors="replace").splitlines()
    log("  —— 服务日志末尾 %d 行 ——" % lines)
    for line in content[-lines:]:
        log("    " + line.rstrip())


def npm_cmd() -> list[str] | None:
    exe = shutil.which("npm")
    return exe_cmd(exe) if exe else None


def start_web() -> subprocess.Popen | None:
    """拉起 PC 商城 dev server（vite，端口固定 5176，/api 与 /files 反代到 8080）。"""
    if not (WEB_DIR / "package.json").exists():
        return None
    cmd = npm_cmd()
    if cmd is None:
        return None
    args = cmd + ["run", "dev"]
    log("  启动：%s（cwd=%s）" % (" ".join(args), WEB_DIR.relative_to(ROOT)))
    log("  日志：%s" % WEB_LOG.relative_to(ROOT))
    fh = open(WEB_LOG, "w", encoding="utf-8", errors="replace")
    env = dict(os.environ, PYTHONIOENCODING="utf-8")
    return subprocess.Popen(args, cwd=str(WEB_DIR), stdout=fh, stderr=subprocess.STDOUT, env=env)


def wait_web_ready(timeout: int = 120) -> bool:
    log("  等待 Web dev server 就绪（探测 %s，最多 %ds）…" % (WEB_URL, timeout))
    deadline = time.time() + timeout
    while time.time() < deadline:
        if http_ok(WEB_URL, timeout=3.0):
            log("  就绪，用时 %ds" % int(timeout - (deadline - time.time())))
            return True
        time.sleep(2)
    return False


def tail_web_log(lines: int = 20) -> None:
    if not WEB_LOG.exists():
        return
    content = WEB_LOG.read_text(encoding="utf-8", errors="replace").splitlines()
    log("  —— Web dev server 日志末尾 %d 行 ——" % lines)
    for line in content[-lines:]:
        log("    " + line.rstrip())


# --------------------------------------------------------------------------- #
# 执行单个检查
# --------------------------------------------------------------------------- #
class Outcome:
    def __init__(self, name: str, desc: str):
        self.name = name
        self.desc = desc
        self.passed = 0
        self.failed = 0
        self.expected_fail = 0
        self.seconds = 0.0
        self.error: str | None = None   # 执行层面的错误（解析不到汇总 / 超时 / 非零退出）
        self.tail: str = ""             # 解析失败时保留的输出尾部，便于当场定位
        self.note: str = ""             # 需要提醒但不影响判定的事项（如「用例数不完整」）

    @property
    def ok(self) -> bool:
        if self.error:
            return False
        # 允许「已知的预期失败」：失败数不超过声明值即视为通过
        return self.failed <= self.expected_fail

    @property
    def status(self) -> str:
        if self.error:
            return "异常"
        if self.failed > self.expected_fail:
            return "失败"
        if self.expected_fail:
            return "通过*"
        return "通过"


def run_cmd(name: str, desc: str, args: list[str], cwd: Path, env: dict,
            parser, expect_fail: int,
            timeout: int, echo: bool, note_fn=None) -> Outcome:
    out = Outcome(name, desc)
    t0 = time.time()
    try:
        proc = subprocess.run(args, cwd=str(cwd), env=env, capture_output=True,
                              text=True, encoding="utf-8", errors="replace", timeout=timeout)
        # 先剥色再解析，否则带颜色码的汇总行会整行匹配不上（详见 RE_ANSI 注释）
        text = strip_ansi((proc.stdout or "") + "\n" + (proc.stderr or ""))
        if echo:
            log("  —— 输出 ——")
            for line in text.splitlines():
                log("    " + line.rstrip())
    except subprocess.TimeoutExpired:
        out.seconds = time.time() - t0
        out.error = "超时（%ds）" % timeout
        return out
    except Exception as e:  # noqa: BLE001
        out.seconds = time.time() - t0
        out.error = "%s: %s" % (type(e).__name__, e)
        return out

    out.seconds = time.time() - t0
    if note_fn:
        try:
            out.note = note_fn(text) or ""
        except Exception:  # noqa: BLE001 — 备注只是锦上添花，绝不能因其失败影响判定
            out.note = ""
    parsed = parser(text)
    if parsed:
        out.passed, out.failed = parsed
        out.expected_fail = expect_fail
    else:
        # 解析不到汇总行时把输出尾部留证，否则只看到一个「异常」无从复现
        out.error = "未能解析出汇总行（退出码 %s）" % proc.returncode
        out.tail = "\n".join(text.strip().splitlines()[-15:])
    return out


def log_result(out: "Outcome") -> None:
    """统一的结果行 + 提醒事项 + 解析失败时的输出尾部（否则只看到一个「异常」无从复现）"""
    log("      结果：%s（通过 %d / 失败 %d）耗时 %.1fs%s"
        % (out.status, out.passed, out.failed, out.seconds,
           "" if not out.error else "  [" + out.error + "]"))
    if out.note:
        log("      ⚠ %s" % out.note)
    if out.tail:
        log("      —— 输出尾部 ——")
        for line in out.tail.splitlines():
            log("        " + line)


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #
def main() -> int:
    ap = argparse.ArgumentParser(description="线上书店一键回归")
    ap.add_argument("--base", default=DEFAULT_BASE, help="后端地址（默认 %s）" % DEFAULT_BASE)
    ap.add_argument("--port", type=int, default=None, help="自动起服务时使用的端口")
    ap.add_argument("--reuse-server", action="store_true", help="复用已在运行的实例，不自己起停")
    ap.add_argument("--only", default="", help="只跑指定检查，逗号分隔（如 verify_capabilities,verify_cache_commit）")
    ap.add_argument("--with-unit", action="store_true", help="附带执行 mvn test")
    ap.add_argument("--with-admin", action="store_true", help="附带执行管理端单元测试（npm run test / vitest）")
    ap.add_argument("--with-frontend", action="store_true", help="附带执行前端静态检查（check_app_sfc.cjs）")
    ap.add_argument("--with-web", action="store_true",
                    help="附带执行 PC 商城端到端（verify_web_pc.cjs，需 Chrome；未起 dev server 时自动拉起 5176）")
    ap.add_argument("--no-cleanup", action="store_true", help="跑完不停止自己拉起的服务")
    ap.add_argument("--verbose", action="store_true", help="完整回显每个脚本的输出")
    ap.add_argument("--timeout", type=int, default=600, help="单个检查的超时秒数（默认 600）")
    opt = ap.parse_args()

    base = opt.base.rstrip("/")
    port = opt.port or port_of(base)
    only = {s.strip() for s in opt.only.split(",") if s.strip()}

    started = datetime.now()
    rule("线上书店 · 一键回归")
    log("  地址      : %s" % base)
    log("  开始时间  : %s" % started.strftime("%Y-%m-%d %H:%M:%S"))
    log("  仓库根目录: %s" % ROOT)

    warnings = precheck()
    if warnings:
        log()
        for w in warnings:
            log("  ⚠ %s" % w)

    # ---- 服务：复用还是自起 ---- #
    proc: subprocess.Popen | None = None
    own_server = False
    running = pid_listening(port) is not None

    if opt.reuse_server:
        rule("二、服务（复用已有实例）")
        if not running:
            log("  !! 指定 --reuse-server，但 :%d 没有监听中的实例" % port)
            return 1
        if not http_ok(base + "/api/category/list"):
            log("  !! :%d 有监听但业务接口不可用，请确认启动完成或数据库连通" % port)
            return 1
        log("  已复用 :%d（PID %s）" % (port, pid_listening(port)))
    else:
        rule("二、服务（自动拉起）")
        if running:
            log("  !! :%d 已被占用（PID %s）。" % (port, pid_listening(port)))
            log("     要么加 --reuse-server 复用它，要么加 --port=<空闲端口> 另起。")
            return 1
        proc = start_server(port)
        if proc is None:
            return 1
        own_server = True
        if not wait_ready(base):
            log("  !! 服务在超时时间内未就绪，已放弃后续检查")
            tail_server_log()
            if not opt.no_cleanup:
                kill_tree(proc.pid)
            return 1

    env = dict(os.environ, BOOKSTORE_BASE=base, PYTHONIOENCODING="utf-8")
    py = sys.executable
    results: list[Outcome] = []

    # ---- 端到端（需要服务） ---- #
    rule("三、端到端接口检查（需后端服务）")
    for key, desc, need_srv, expect_fail in PY_CHECKS:
        if only and key not in only:
            continue
        log()
        log("  >>> %s —— %s" % (key, desc))
        out = run_cmd(key, desc, [py, str(TOOLS / (key + ".py"))], ROOT, env,
                      parse_py, expect_fail, opt.timeout, opt.verbose)
        results.append(out)
        log_result(out)

    # ---- 静态检查（不需要服务，可与服务并存） ---- #
    if opt.with_frontend:
        rule("四、前端静态检查（不需后端服务）")
        node = shutil.which("node")
        if not node:
            log("  !! 找不到 node，跳过")
        else:
            for script, desc, expect_fail in CJS_CHECKS:
                if only and Path(script).stem not in only:
                    continue
                log()
                log("  >>> %s —— %s" % (script, desc))
                out = run_cmd(script, desc, [node, str(TOOLS / script)], ROOT, env,
                              parse_cjs, expect_fail, opt.timeout, opt.verbose)
                results.append(out)
                log_result(out)
            log()
            log("  注：check_app_sfc.cjs 的 1 项失败是 common/config.js 的 #ifdef 重复导出，")
            log("      uni-app 编译会剥离该分支，属已知预期（详见 docs/新APP开发避坑清单.md）。")

    # ---- PC 商城端到端（需 Web dev server；必须放在停服之前） ---- #
    web_proc: subprocess.Popen | None = None
    if opt.with_web:
        rule("五、PC 商城端到端检查（需 Chrome + Web dev server）")
        node = shutil.which("node")
        if not node:
            log("  !! 找不到 node，跳过")
        else:
            reuse_web = port_open("127.0.0.1", WEB_PORT)
            if reuse_web:
                log("  复用已在运行的 Web dev server :%d（PID %s）" % (WEB_PORT, pid_listening(WEB_PORT)))
            else:
                web_proc = start_web()
                if web_proc is None:
                    log("  !! 无法启动 Web dev server（找不到 npm 或 bookstore-web/package.json），跳过")
            if reuse_web or web_proc is not None:
                if wait_web_ready():
                    for script, desc, expect_fail in WEB_CHECKS:
                        if only and Path(script).stem not in only:
                            continue
                        log()
                        log("  >>> %s —— %s" % (script, desc))
                        out = run_cmd(script, desc, [node, str(TOOLS / script)], ROOT, env,
                                      parse_cjs, expect_fail, max(opt.timeout, 420), opt.verbose)
                        results.append(out)
                        log_result(out)
                else:
                    log("  !! Web dev server 未在超时内就绪，跳过 PC 商城检查")
                    tail_web_log()

    # ---- 收尾服务（单测要与 spring-boot:run 争 target/，必须先停） ---- #
    if web_proc is not None:
        if kill_tree(web_proc.pid):
            log()
            log("  已停止自拉起的 Web dev server（PID %s 及其子进程）" % web_proc.pid)
        else:
            log("  !! Web dev server 停止失败，请手工检查 :%d" % WEB_PORT)
    if own_server and not opt.no_cleanup:
        rule("六、收尾：停止自拉起的服务")
        if kill_tree(proc.pid):
            log("  已停止（PID %s 及其子进程）" % proc.pid)
        else:
            log("  !! 停止失败，请手工检查 :%d" % port)
        time.sleep(2)

    # ---- 单元测试（需要 target/ 空闲） ---- #
    if opt.with_unit:
        rule("七、后端单元测试（mvn test）")
        if opt.reuse_server:
            log("  ⚠ 你用了 --reuse-server：若该实例仍在运行，mvn test 会与它争用 target/。")
            log("    建议先停掉外部实例，或去掉 --reuse-server 让本脚本自己管理服务。")
        cmd = mvn_cmd()
        if cmd is None:
            log("  !! 找不到 mvn，跳过")
        else:
            log("  执行：mvn -o -Dfile.encoding=UTF-8 test")
            out = run_cmd("mvn test", "后端单元测试", cmd + ["-o", "-Dfile.encoding=UTF-8", "test"],
                          SERVER_DIR, env, parse_mvn, 0, max(opt.timeout, 900), opt.verbose)
            results.append(out)
            log_result(out)

    # ---- 管理端单元测试（vitest） ---- #
    if opt.with_admin:
        rule("八、管理端单元测试（vitest）")
        admin_dir = ROOT / "bookstore-admin"
        npm = shutil.which("npm")
        if only and "admin" not in only:
            log("  已按 --only 过滤，跳过")
        elif not npm or not (admin_dir / "package.json").exists():
            log("  !! 找不到 npm 或 bookstore-admin/package.json，跳过")
        else:
            log("  执行：npm run test（vitest run）")
            # vitest 会把模块中转文件写进系统临时目录。把 TEMP 收到项目内的 .tmp
            # （已进 .gitignore）主要是为了不污染系统临时区；真正让「测试文件被跳过」
            # 消失的是 vitest.config.js 里的 `pool: 'threads'`（详见该文件注释）。
            tmp_dir = admin_dir / ".tmp"
            tmp_dir.mkdir(exist_ok=True)
            admin_env = dict(env, TEMP=str(tmp_dir), TMP=str(tmp_dir))
            out = run_cmd("npm run test", "管理端单元测试", exe_cmd(npm) + ["run", "test"], admin_dir,
                          admin_env, parse_vitest, 0, max(opt.timeout, 600), opt.verbose,
                          note_fn=admin_completeness_note)
            results.append(out)
            log_result(out)

    # ---- 汇总 ---- #
    rule("汇总")
    log("  %-28s %-8s %8s %8s %10s" % ("检查项", "结果", "通过", "失败", "耗时(s)"))
    log("  " + "-" * 68)
    total_p = total_f = 0
    bad: list[str] = []
    noted: list[str] = []
    for r in results:
        log("  %-28s %-8s %8d %8d %10.1f" % (r.name, r.status, r.passed, r.failed, r.seconds))
        total_p += r.passed
        total_f += r.failed
        if not r.ok:
            bad.append(r.name)
        if r.note:
            noted.append("%s：%s" % (r.name, r.note))

    log("  " + "-" * 68)
    log("  %-28s %-8s %8d %8d" % ("合计 %d 项检查" % len(results),
                                  "全部通过" if not bad else "存在失败", total_p, total_f))
    log()
    log("  断言总数：%d 通过 / %d 失败" % (total_p, total_f))
    if bad:
        log("  未通过  ：%s" % "、".join(bad))
    if noted:
        log("  需留意  ：")
        for n in noted:
            log("    ⚠ %s" % n)
        log("    （以上不影响判定：数字已如实统计，只是提示「本次覆盖可能不完整」）")
    log("  总耗时  ：%.1fs" % (time.time() - started.timestamp()))
    log("  结束时间：%s" % datetime.now().strftime("%Y-%m-%d %H:%M:%S"))

    # 报告落盘
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    report = OUT_DIR / ("regression_%s.md" % started.strftime("%Y%m%d_%H%M%S"))
    lines = ["# 一键回归报告", "",
             "- 时间：%s" % started.strftime("%Y-%m-%d %H:%M:%S"),
             "- 地址：%s" % base,
             "- 结论：**%s**（%d 项检查，%d 通过 / %d 失败）"
             % ("全部通过" if not bad else "存在失败", len(results), total_p, total_f), "",
             "| 检查项 | 结果 | 通过 | 失败 | 耗时(s) | 备注 |", "| --- | --- | ---: | ---: | ---: | --- |"]
    for r in results:
        remark = r.error or r.desc
        if r.note:
            remark = "%s；⚠ %s" % (remark, r.note)
        lines.append("| %s | %s | %d | %d | %.1f | %s |"
                     % (r.name, r.status, r.passed, r.failed, r.seconds, remark))
    lines += ["", "断言总数：%d 通过 / %d 失败；未通过：%s" % (total_p, total_f, "、".join(bad) or "无")]
    if noted:
        lines += ["", "需留意（不影响判定，仅提示覆盖可能不完整）："] + ["- %s" % n for n in noted]
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    log("  报告已写入：%s" % report.relative_to(ROOT))

    return 1 if (bad or not results) else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        log("\n已中断")
        sys.exit(130)
