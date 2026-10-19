# -*- coding: utf-8 -*-
"""
线上书店 APP —— tabBar 图标生成脚本

用途：为 pages.json 的 tabBar 生成 4 个 tab 的普通态/选中态图标（共 8 张 PNG）。

规格依据（uni-app 官方 pages.json 文档）：
- iconPath 大小限制 40KB，建议尺寸 81px x 81px；
- 不支持网络图片、不支持字体图标，只能使用本地路径；
- 因此统一输出 81x81 PNG，白色背景透明。

输出：bookstore-app/static/tabbar/*.png

运行：D:/anaconda3/python.exe tools/gen_tabbar_icons.py
"""

import os
from PIL import Image, ImageDraw

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "bookstore-app", "static", "tabbar")

SIZE = 81          # 官方建议尺寸
SS = 8             # 超采样倍数，先放大绘制再缩小，边缘更平滑
N = SIZE * SS

NORMAL = (154, 160, 166)   # 未选中：#9AA0A6
ACTIVE = (192, 57, 43)     # 选中  ：#C0392B


def _new():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def _finish(img, name):
    img = img.resize((SIZE, SIZE), Image.LANCZOS)
    p = os.path.join(OUT_DIR, name)
    img.save(p, "PNG", optimize=True)
    return p


def px(v):
    """把 81 坐标系下的值换算到超采样画布。"""
    return v * SS


# ------------------------------------------------------------------ 首页
def icon_home(color, name):
    img, d = _new()
    c = color
    # 屋顶（三角形）+ 主体，形成"房子"轮廓
    d.polygon([(px(40.5), px(12)), (px(9), px(40)), (px(72), px(40))], fill=c)
    d.rounded_rectangle([px(16), px(37), px(65), px(70)], radius=px(4), fill=c)
    # 门（挖空，透出背景）
    d.rounded_rectangle([px(33), px(50), px(48), px(70)], radius=px(3), fill=(0, 0, 0, 0))
    return _finish(img, name)


# ------------------------------------------------------------------ 分类
def icon_category(color, name):
    img, d = _new()
    c = color
    r = px(6)
    boxes = [
        (12, 12, 37, 37),
        (44, 12, 69, 37),
        (12, 44, 37, 69),
        (44, 44, 69, 69),
    ]
    for x0, y0, x1, y1 in boxes:
        d.rounded_rectangle([px(x0), px(y0), px(x1), px(y1)], radius=r, fill=c)
    return _finish(img, name)


# ------------------------------------------------------------------ 购物车
def icon_cart(color, name):
    img, d = _new()
    c = color
    # 推杆
    d.line([(px(9), px(17)), (px(19), px(17)), (px(26), px(19))],
           fill=c, width=px(5), joint="curve")
    # 车斗（梯形）
    d.polygon([(px(24), px(20)), (px(72), px(20)), (px(64), px(50)), (px(32), px(50))], fill=c)
    # 把手
    d.line([(px(19), px(17)), (px(27), px(53))], fill=c, width=px(5))
    # 车轮
    d.ellipse([px(33), px(55), px(47), px(69)], fill=c)
    d.ellipse([px(55), px(55), px(69), px(69)], fill=c)
    return _finish(img, name)


# ------------------------------------------------------------------ 我的
def icon_mine(color, name):
    img, d = _new()
    c = color
    # 头
    d.ellipse([px(27), px(11), px(54), px(38)], fill=c)
    # 肩（饼形拼出上半身）
    d.pieslice([px(13), px(44), px(68), px(96)], start=180, end=360, fill=c)
    return _finish(img, name)


ICONS = [("home", icon_home), ("category", icon_category),
         ("cart", icon_cart), ("mine", icon_mine)]


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, fn in ICONS:
        fn(NORMAL, name + ".png")
        fn(ACTIVE, name + "_active.png")
        print("生成:", name + ".png", "/", name + "_active.png")
    print("\n完成，共", len(ICONS) * 2, "张 ->", OUT_DIR)


if __name__ == "__main__":
    main()
