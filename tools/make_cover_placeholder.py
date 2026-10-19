#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
为「抓不到真实封面」的书生成排版式占位封面。

为什么不直接复用一张 default.jpg：
    - 30 本书共用同一张图，列表页会看到大量重复封面，观感上就是「图挂了」；
    - 渐变色按书名哈希选取，与 APP 端 cover-box 的兜底渐变同一套配色，
      就算将来图片资源丢了，前端降级出来的色彩也和现在一致。

产出：
    bookstore-app/static/cover/<file>       APP 本地静态资源
    bookstore-server/upload/cover/<file>    后端 /files/cover/ 可访问
两边同名同图，保证「列表有图、下单页也有图」。
"""
import argparse
import hashlib
import json
import os
import re

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")
APP_DIR = os.path.join(ROOT, "bookstore-app", "static", "cover")
SRV_DIR = os.path.join(ROOT, "bookstore-server", "upload", "cover")
MANIFEST = os.path.join(TOOLS, "mock_covers_manifest.json")
REPORT = os.path.join(TOOLS, "mock_covers_report.json")

# 与 common/cover.js 的 GRADIENTS 完全一致，保证前后端兜底色一致
GRADIENTS = [
    ("#f7e8e4", "#eed3cd"),
    ("#e9eef7", "#d5e0f0"),
    ("#ecf2ea", "#dae7d7"),
    ("#f6f0e3", "#ebdfc6"),
    ("#f1ecf6", "#e1d8ee"),
    ("#f4ede7", "#e4d7cb"),
    ("#e7f1f2", "#d1e4e8"),
    ("#f5e9ee", "#e8d4df"),
]
TEXT_DARK = (74, 78, 87)
TEXT_MUTED = (140, 144, 153)

W, H = 600, 800


def font(paths, size):
    for p in paths:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()


def load_fonts():
    win = os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts")
    return {
        "title": font([os.path.join(win, "msyhbd.ttc"), os.path.join(win, "msyh.ttc")], 56),
        "title_small": font([os.path.join(win, "msyhbd.ttc"), os.path.join(win, "msyh.ttc")], 44),
        "meta": font([os.path.join(win, "msyh.ttc"), os.path.join(win, "simhei.ttf")], 30),
    }


def hex_rgb(s):
    s = s.lstrip("#")
    return tuple(int(s[i : i + 2], 16) for i in (0, 2, 4))


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def vertical_gradient(top, bottom):
    """145° 渐变：PIL 没有原生渐变，按行插值即可"""
    img = Image.new("RGB", (1, H))
    px = img.load()
    for y in range(H):
        px[0, y] = lerp(top, bottom, y / (H - 1))
    return img.resize((W, H))


def wrap(draw, text, f, max_w):
    """按像素宽度折行：中文逐字，英文整体不拆"""
    lines, cur = [], ""
    for ch in text:
        trial = cur + ch
        if draw.textlength(trial, font=f) > max_w and cur:
            lines.append(cur)
            cur = ch
        else:
            cur = trial
    if cur:
        lines.append(cur)
    return lines[:3]


def build(item, fonts):
    title = item["title"]
    h = int(hashlib.md5(title.encode("utf-8")).hexdigest()[:8], 16)
    top, bottom = hex_rgb(GRADIENTS[h % len(GRADIENTS)][0]), hex_rgb(GRADIENTS[h % len(GRADIENTS)][1])

    img = vertical_gradient(top, bottom)
    d = ImageDraw.Draw(img)

    # 内框：留白边，让成品像一本实体书的封面
    d.rounded_rectangle([36, 36, W - 36, H - 36], radius=18, outline=(255, 255, 255, 255), width=3)

    # 顶部装饰线
    d.line([(96, 150), (W - 96, 150)], fill=(255, 255, 255, 230), width=3)

    max_w = W - 200
    f_title = fonts["title"] if len(title) <= 9 else fonts["title_small"]
    lines = wrap(d, title, f_title, max_w)
    line_h = 76 if f_title is fonts["title"] else 62
    total = line_h * len(lines)
    y = (H - total) / 2 - 40
    for ln in lines:
        w = d.textlength(ln, font=f_title)
        d.text(((W - w) / 2, y), ln, font=f_title, fill=TEXT_DARK)
        y += line_h

    # 底部：作者 · 出版社
    meta = item.get("author") or ""
    if item.get("publisher"):
        meta = f"{meta} · {item['publisher']}" if meta else item["publisher"]
    if meta:
        if len(meta) > 18:
            meta = meta[:17] + "…"
        w = d.textlength(meta, font=fonts["meta"])
        d.text(((W - w) / 2, H - 150), meta, font=fonts["meta"], fill=TEXT_MUTED)

    d.line([(140, H - 108), (W - 140, H - 108)], fill=(255, 255, 255, 200), width=2)
    return img


def load_books_meta():
    """从 books.js 取作者/出版社"""
    p = os.path.join(ROOT, "bookstore-app", "common", "mock", "books.js")
    txt = open(p, encoding="utf-8").read()
    body = re.search(r"export const BOOKS = \[(.*)\]", txt, re.S).group(1)
    rows = json.loads("[" + body + "]")
    return {r["title"]: r for r in rows}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="已存在也重新生成")
    args = ap.parse_args()

    manifest = json.load(open(MANIFEST, encoding="utf-8"))
    books = load_books_meta()
    report = {}
    if os.path.exists(REPORT):
        for r in json.load(open(REPORT, encoding="utf-8")):
            report[r["title"]] = r

    targets = [m for m in manifest if (args.force or not os.path.exists(os.path.join(APP_DIR, m["file"])))]
    print(f"待生成占位封面：{len(targets)} 本")

    os.makedirs(APP_DIR, exist_ok=True)
    os.makedirs(SRV_DIR, exist_ok=True)
    fonts = load_fonts()

    made = 0
    for m in targets:
        info = dict(m)
        b = books.get(m["title"], {})
        info["author"] = b.get("author", "")
        info["publisher"] = b.get("publisher", "")
        img = build(info, fonts)
        for d in (APP_DIR, SRV_DIR):
            img.save(os.path.join(d, m["file"]), "JPEG", quality=88, optimize=True)
        made += 1
        print(f"  [{made}/{len(targets)}] {m['title']}  -> {m['file']}")

    print(f"\n生成 {made} 张占位封面（已同步到后端 upload 目录）")
    print(f"APP 目录现有封面：{len([f for f in os.listdir(APP_DIR) if f.endswith('.jpg')])} 张")


if __name__ == "__main__":
    main()
