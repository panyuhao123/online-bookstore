#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
补齐 / 替换演示数据集的封面图。

背景：
  tools/fetch_mock_covers.py 只做「首次抓取」——目标文件存在就跳过。
  于是当初抓不到的书被 tools/make_cover_placeholder.py 生成了排版式占位图
  （600×800 浅色卡 + 书名），这些文件存在，后续再跑抓取脚本永远不会被替换。

本脚本解决三件事：
  1) 认得出占位图：600×800 且四角为浅色渐变（与 make_cover_placeholder 同源）；
  2) 认得出「不像封面」的真图：促销横幅、方图、分辨率过低；
  3) 对目标书目重新抓取，且对候选做形态校验（竖版比例 / 最小宽度），
     校验不过的候选直接弃用 —— 宁可保留原图，也不要把方图裁成畸形。

产出：bookstore-app/static/cover/<file> 与 bookstore-server/upload/cover/<file>
      同名同图（两份用途不同，缺一份就会出现「列表有图、下单页无图」）。

用法：
  python tools/refetch_mock_covers.py --scan          # 只体检，不下载
  python tools/refetch_mock_covers.py                 # 替换全部不合格封面
  python tools/refetch_mock_covers.py 三体 高等数学    # 只处理书名含关键字的
"""
import argparse
import io
import json
import os
import re
import sys
import time
import urllib.parse

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fetch_covers as fc  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(ROOT, "tools", "mock_covers_manifest.json")
APP_DIR = os.path.join(ROOT, "bookstore-app", "static", "cover")
SRV_DIR = os.path.join(ROOT, "bookstore-server", "upload", "cover")
REPORT = os.path.join(ROOT, "tools", "cover_refetch_report.json")

PLACEHOLDER_SIZE = (600, 800)
MIN_WIDTH = 200          # 扫描阈值：行卡 80px×2 倍屏=160px，低于 200 才算「明显不够」
MIN_ACCEPT = 140         # 下载可接受下限：宁可略糊也要真封面，别留排版占位图
MIN_RATIO, MAX_RATIO = 0.55, 0.95   # 竖版书封区间（3:4=0.75；绘本接近方形，别一刀切）


def is_placeholder(im):
    """
    占位图特征：600×800 + 四角是「同色的极浅色」。
    两个条件必须同时成立 —— 只看尺寸会把恰好 600×800 的真封面认错，
    只看同色会把「纯色边框」的真封面认错（如《亲密关系》深棕底）。
    """
    if im.size != PLACEHOLDER_SIZE:
        return False
    rgb = im.convert("RGB")
    w, h = rgb.size
    corners = [rgb.getpixel((8, 8)), rgb.getpixel((w - 8, 8)),
               rgb.getpixel((8, h - 8)), rgb.getpixel((w - 8, h - 8))]
    pale = all(min(c) > 190 and max(c) - min(c) <= 45 for c in corners)

    def near(a, b):
        return max(abs(x - y) for x, y in zip(a, b)) <= 6

    return pale and near(corners[0], corners[1]) and near(corners[2], corners[3])


def quality_note(path):
    """返回 (是否需要替换, 原因)"""
    try:
        im = Image.open(path)
    except Exception as e:
        return True, f"无法读取({e.__class__.__name__})"
    w, h = im.size
    if is_placeholder(im):
        return True, "排版式占位图"
    if abs(w - h) / max(w, h) < 0.12:
        return True, f"方图 {w}×{h}（裁成竖版会切掉标题）"
    if w < MIN_WIDTH:
        return True, f"分辨率过低 {w}×{h}"
    ratio = w / h
    if not (MIN_RATIO <= ratio <= MAX_RATIO):
        return True, f"比例异常 {w}×{h}（{ratio:.2f}，非书封形态）"
    return False, ""


def portrait_ok(data_path):
    """下载后校验：真 JPEG、竖版比例、宽度够用"""
    try:
        im = Image.open(data_path)
        w, h = im.size
    except Exception:
        return False, "图片无法解析"
    ratio = w / h
    if not (MIN_RATIO <= ratio <= MAX_RATIO):
        return False, f"候选比例 {ratio:.2f} 不合格"
    if w < MIN_ACCEPT:
        return False, f"候选宽度 {w} 不足"
    return True, f"{w}×{h}"


def suggest_items(keyword):
    """
    豆瓣「联想」接口：/subject_suggest 是给搜索框用的轻量 JSON，
    比 /subject_search 稳定得多（后者对未登录/机房 IP 会返回空结果集）。
    封面图取 /l/ 大图（约 350×500），/s/ 只有 270×385。
    """
    url = "https://book.douban.com/j/subject_suggest?q=" + urllib.parse.quote(keyword)
    try:
        raw = fc.http_get(url, referer=fc.DOUBAN_REF, retries=1, timeout=10)
        data = json.loads(raw.decode("utf-8", "ignore"))
    except Exception:
        return []
    out = []
    for it in data:
        pic = (it.get("pic") or "").replace("/s/public/", "/l/public/")
        if not pic:
            continue
        out.append({
            "title": it.get("title") or "",
            "abstract": it.get("author_name") or "",
            "cover_url": pic,
            "url": it.get("url") or "",
        })
    return out


# 联想接口对这些书名不出结果，补一条「去标点、空格分词」的查询
EXTRA_QUERIES = {
    "Head First 设计模式": ["Head First 设计模式 中文版"],
    "深入理解计算机系统": ["CSAPP", "深入理解计算机系统 原书第3版"],
    "建筑空间组合论": ["建筑空间组合论 第三版"],
    "孤独星球：中国": ["孤独星球", "孤独星球 中国"],
    "人工智能：现代方法": ["人工智能 现代方法", "Artificial Intelligence A Modern Approach"],
    "考研英语黄皮书": ["张剑 考研英语", "考研英语 黄皮书"],
    "大学英语四级真题": ["大学英语四级 真题", "四级真题"],
}


def candidates(item):
    """两个入口都问一遍：搜索结果页 + 联想接口，按打分取前几名"""
    seen, out = set(), []
    queries = [item["title"], f"{item['title']} {item['author']}"]
    queries += EXTRA_QUERIES.get(item["title"], [])
    for kw in dict.fromkeys(q for q in queries if q):
        pool = []
        try:
            pool += fc.search_douban(kw, tries=1, verbose=False)
        except Exception:
            pass
        pool += suggest_items(kw)
        for it in pool:
            url = it.get("cover_url") or ""
            if not url or url in seen:
                continue
            sc, why = fc.score(it, item)
            if sc >= 30:                     # 30=书名包含，60=精确一致，90+=作者也吻合
                seen.add(url)
                out.append((sc, why, it))
        if len(out) >= 6:
            break
    return sorted(out, key=lambda x: -x[0])


def grab(url):
    """下载封面：单域名超时只等 8 秒，失败就换 img1/2/3/9 再试一次"""
    hosts = ["img3", "img1", "img2", "img9"]
    urls = [url]
    for h in hosts:
        alt = re.sub(r"//img\d+\.doubanio\.com", f"//{h}.doubanio.com", url)
        if alt not in urls:
            urls.append(alt)
    for u in urls[:3]:
        try:
            b = fc.http_get(u, referer=fc.DOUBAN_REF, timeout=8, retries=1)
            # 用 PIL 验证而不是校验首尾字节：豆瓣部分老图的结尾带换行，
            # 只比对 ff d8 / ff d9 会把正常图片判成坏图
            try:
                im = Image.open(io.BytesIO(b))
                im.verify()
                return b
            except Exception:
                continue
        except Exception:
            continue
    return None


def fetch(item, tmp, deadline):
    for s, why, it in candidates(item):
        if time.time() > deadline:
            return None, "本轮超时，下轮再试"
        data = grab(it["cover_url"])
        if not data:
            continue
        with open(tmp, "wb") as f:
            f.write(data)
        ok, note = portrait_ok(tmp)
        if not ok:
            print(f"      弃用 {it['title'][:24]}：{note}", flush=True)
            continue
        return data, f"douban 命中《{it['title'][:28]}》{note} | {why}({s}) | {len(data)//1024}KB"
    return None, "未找到合格的竖版封面"


def save_both(filename, data):
    for d in (APP_DIR, SRV_DIR):
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, filename), "wb") as f:
            f.write(data)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("keys", nargs="*", help="只处理书名包含这些关键字的条目")
    ap.add_argument("--scan", action="store_true", help="只体检不下载")
    ap.add_argument("--sleep", type=float, default=1.0)
    args = ap.parse_args()

    items = json.load(open(MANIFEST, encoding="utf-8"))
    if args.keys:
        items = [i for i in items if any(k in i["title"] for k in args.keys)]

    todo = []
    print("=" * 66)
    print("封面体检")
    print("=" * 66)
    for item in items:
        path = os.path.join(APP_DIR, item["file"])
        if not os.path.exists(path):
            todo.append((item, "文件缺失"))
            print(f"  [缺] {item['title']}  {item['file']}")
            continue
        bad, why = quality_note(path)
        if bad:
            todo.append((item, why))
            print(f"  [换] {item['title'][:20]:<22} {why}")

    print(f"\n合计 {len(items)} 本，需处理 {len(todo)} 本")
    if args.scan or not todo:
        return 0

    tmp = os.path.join(ROOT, "tools", ".tmp", "cover_dl.jpg")
    os.makedirs(os.path.dirname(tmp), exist_ok=True)
    report = []
    for i, (item, why) in enumerate(todo, 1):
        print(f"[{i}/{len(todo)}] {item['title']}  ({why})", flush=True)
        data, note = fetch(item, tmp, time.time() + 90)
        if data:
            save_both(item["file"], data)
            print(f"      -> 已替换：{note}", flush=True)
            report.append({"id": item["id"], "title": item["title"],
                           "file": item["file"], "status": "REPLACED",
                           "before": why, "note": note})
        else:
            print(f"      -> 保留原图：{note}", flush=True)
            report.append({"id": item["id"], "title": item["title"],
                           "file": item["file"], "status": "KEPT",
                           "before": why, "note": note})
        time.sleep(args.sleep)

    with open(REPORT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    replaced = sum(1 for r in report if r["status"] == "REPLACED")
    print("\n" + "=" * 66)
    print(f"替换 {replaced} 本 / 保留 {len(report) - replaced} 本")
    print(f"报告：{os.path.relpath(REPORT, ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
