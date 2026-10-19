# -*- coding: utf-8 -*-
"""
封面核验预览：把 upload/cover 下的封面拼成一张大图，每格标注书名与命中信息。
人工核验无张冠李戴后即完成替换。

用法：python tools/covers_preview.py
输出：tools/covers_preview.png
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COVER_DIR = os.path.join(ROOT, "bookstore-server", "upload", "cover")
REPORT = os.path.join(ROOT, "tools", "covers_report.json")
OUT = os.path.join(ROOT, "tools", "covers_preview.png")

CELL_W, CELL_H, LABEL_H = 220, 300, 64
COLS = 6
FONT_SM = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 15)
FONT_XS = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 12)


def fit_cover(img, w, h):
    """封面等比缩放并居中到格子"""
    ratio = min(w / img.width, h / img.height)
    nw, nh = int(img.width * ratio), int(img.height * ratio)
    img = img.resize((nw, nh), Image.LANCZOS)
    return img


def main():
    info = {}
    if os.path.exists(REPORT):
        for r in json.load(open(REPORT, encoding="utf-8")):
            info[r.get("cover", "")] = r

    # 按 seed.sql 的顺序取文件
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from fetch_covers import parse_seed
    books = parse_seed()

    rows = (len(books) + COLS - 1) // COLS
    sheet = Image.new("RGB", (COLS * CELL_W, rows * (CELL_H + LABEL_H)), "#FFFFFF")
    draw = ImageDraw.Draw(sheet)

    for i, b in enumerate(books):
        x = (i % COLS) * CELL_W
        y = (i // COLS) * (CELL_H + LABEL_H)
        path = os.path.join(COVER_DIR, b["cover"])
        try:
            img = Image.open(path).convert("RGB")
            img = fit_cover(img, CELL_W - 12, CELL_H - 8)
            sheet.paste(img, (x + (CELL_W - img.width) // 2, y + 4))
        except Exception as e:
            draw.text((x + 8, y + 20), f"[读图失败]\n{type(e).__name__}", font=FONT_SM, fill="#CC0000")

        r = info.get(b["cover"], {})
        status = r.get("status", "?")
        src = r.get("source", "")
        color = "#1A7F37" if status == "OK" else "#CC0000"
        draw.rectangle([x, y + CELL_H, x + CELL_W, y + CELL_H + LABEL_H], fill="#F6F7F9")
        draw.text((x + 6, y + CELL_H + 4), b["title"][:14], font=FONT_SM, fill="#1F2126")
        tag = f"{status} {src} {r.get('why', '')[:18]}"
        draw.text((x + 6, y + CELL_H + 26), tag, font=FONT_XS, fill=color)
        draw.text((x + 6, y + CELL_H + 44), f"{b['author'][:12]} / {b['publisher'][:10]}",
                  font=FONT_XS, fill="#8C9099")
        draw.rectangle([x, y, x + CELL_W - 1, y + CELL_H + LABEL_H - 1], outline="#EDEEF0")

    sheet.save(OUT, quality=90)
    print(f"预览已生成：{OUT}  共 {len(books)} 格")


if __name__ == "__main__":
    main()
