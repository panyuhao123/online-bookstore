# -*- coding: utf-8 -*-
"""
演示数据集封面准备工具。

分工：
  - tools/fetch_covers.py      给「后端 upload 目录」抓真实封面（服务于真实接口）
  - 本脚本                     给「APP static 目录」准备封面（服务于 mock 数据集）

流程：
  1) 读 tools/mock_covers_manifest.json（由 tools/build_mock_data.py 产出）；
  2) ready=true 的书目：直接从 bookstore-server/upload/cover/ 复制已抓好的真实封面；
  3) ready=false 的书目：豆瓣读书为主源、当当为备源，三重打分校验后下载；
     目标文件已存在则跳过（断点续跑），抓不到的不产出文件；
  4) 结果写入 tools/mock_covers_report.json。

抓不到封面不是失败：APP 端会按书名哈希渲染柔和渐变块 + 书名首字，
因此演示效果始终完整，不会出现黑洞。重跑 tools/build_mock_data.py 时，
会读取本脚本的报告，把确实没有封面的书目 coverUrl 置空，避免无谓的 404 请求。

用法：
  python tools/fetch_mock_covers.py              # 全量（跳过已存在的目标文件）
  python tools/fetch_mock_covers.py --dry        # 只匹配不下载
  python tools/fetch_mock_covers.py 三体 小王子   # 只处理书名含关键字的书目
"""
import argparse
import json
import os
import random
import shutil
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# 复用已验证的抓取器：HTTP 重试、随机 bid Cookie、豆瓣 __DATA__ 解析、三重打分
import fetch_covers as fc  # noqa: E402

ROOT = fc.ROOT
MANIFEST = os.path.join(ROOT, "tools", "mock_covers_manifest.json")
REPORT = os.path.join(ROOT, "tools", "mock_covers_report.json")
SEED_COVER_DIR = fc.OUT_DIR
OUT_DIR = os.path.join(ROOT, "bookstore-app", "static", "cover")


def load_manifest():
    return json.load(open(MANIFEST, encoding="utf-8"))


def copy_ready(item):
    """复制后端 upload 目录里已抓好的真实封面"""
    src = os.path.join(SEED_COVER_DIR, item["file"])
    dst = os.path.join(OUT_DIR, item["file"])
    if not os.path.exists(src):
        return False, "upload 目录缺少源文件"
    shutil.copyfile(src, dst)
    return True, "复用已有真实封面"


def fetch_one(item):
    """豆瓣主源 + 当当备源，返回 (状态, 说明)"""
    best, bs, why, src = None, -1, "", ""

    for kw in dict.fromkeys([item["title"], fc.core_title(item["title"])]):
        if not kw:
            continue
        for it in fc.search_douban(kw, tries=3, verbose=False):
            s, w = fc.score(it, item)
            if s > bs:
                best, bs, why, src = it, s, w, "douban"
        if bs >= 60:
            break

    if bs < 60:
        for it in fc.search_dangdang(item["title"]):
            s, w = fc.score(it, item)
            if s > bs:
                best, bs, why, src = it, s, w, "dangdang"
        # 当当结果鱼龙混杂，必须三重确认（>=75）才接受
        if bs < 75:
            best = None

    if bs < 30 or best is None:
        return "MISS", f"未命中（最高分 {bs}）"

    ref = fc.DOUBAN_REF if src == "douban" else "https://www.dangdang.com/"
    data = fc.download(best["cover_url"], ref)
    if not data:
        return "DL_FAIL", "下载失败或图片无效"

    save_both(item["file"], data)
    return "OK", f"{src} 命中 {best.get('title')} | {why}({bs}) | {len(data) // 1024}KB"


def save_both(filename, data):
    """
    同时写入 APP 静态目录与后端 upload 目录。

    两个目录的用途不同：APP 的 mock 数据用 /static/cover/，
    而 db/mock_dataset.sql 导入真实库后，后端的 cover_url 指向 /files/cover/。
    一份图写两处，前端演示与后端下单就不会出现「列表有图、下单页无图」。
    """
    for d in (OUT_DIR, SEED_COVER_DIR):
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, filename), "wb") as f:
            f.write(data)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("keys", nargs="*", help="只处理书名包含这些关键字的条目")
    ap.add_argument("--dry", action="store_true", help="只匹配不下载")
    ap.add_argument("--sleep", type=float, default=1.2, help="每本书之间的间隔秒数")
    ap.add_argument("--force", action="store_true", help="覆盖已存在的目标文件")
    args = ap.parse_args()

    items = load_manifest()
    os.makedirs(OUT_DIR, exist_ok=True)

    # 上一轮的失败记录只用于决定重试策略：已经抓到的文件一律跳过，
    # 上一轮 MISS 的除非显式 --force 也不再重复请求（豆瓣会限流）
    prev = {}
    if os.path.exists(REPORT):
        try:
            prev = {r["title"]: r for r in json.load(open(REPORT, encoding="utf-8"))}
        except Exception:
            prev = {}

    for i, item in enumerate(items, 1):
        dst = os.path.join(OUT_DIR, item["file"])

        # 断点续跑：目标文件已存在就不重复请求
        if os.path.exists(dst) and not args.force:
            continue

        if args.keys and not any(k in item["title"] for k in args.keys):
            continue

        if args.dry:
            print(f"[{i}/{len(items)}] {item['title']}  -> dry")
            continue

        # 上一轮已确认抓不到的，默认不再重试，避免把时间耗在必定失败的条目上
        if not args.force and prev.get(item["title"], {}).get("status") in ("MISS", "DL_FAIL"):
            continue

        if item["ready"]:
            done, note = copy_ready(item)
            print(f"[{i}/{len(items)}] {item['title']}  -> {'READY' if done else 'MISS'}  {note}")
            continue

        status, note = fetch_one(item)
        print(f"[{i}/{len(items)}] {item['title']}  -> {status}  {note}")
        time.sleep(args.sleep + random.uniform(0, 0.6))

    # 报告统一在结束后按「文件系统实况 + 清单」生成，
    # 而不是在循环里边跑边累积 —— 一旦某个分支漏了 append，
    # 报告就会与实际文件脱节，下游 build_mock_data.py 会据此误判。
    report = []
    for item in items:
        if os.path.exists(os.path.join(OUT_DIR, item["file"])):
            status, why = "OK", "已有封面文件"
        else:
            status, why = "MISS", (prev.get(item["title"], {}).get("why") or "未抓取到封面")
        report.append({
            "title": item["title"],
            "file": item["file"],
            "id": item["id"],
            "status": status,
            "why": why
        })

    with open(REPORT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    ok = sum(1 for r in report if r["status"] == "OK")
    miss = sum(1 for r in report if r["status"] == "MISS")

    print("\n" + "=" * 58)
    print(f"有封面 {ok} 本 / 无封面 {miss} 本（无封面的由 APP 端渐变块兜底）")
    print(f"封面目录：{os.path.relpath(OUT_DIR, ROOT)}")
    print(f"报告：{os.path.relpath(REPORT, ROOT)}")
    print("\n下一步：重新运行 python tools/build_mock_data.py，让缺失封面的书目 coverUrl 置空")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
