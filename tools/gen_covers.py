# -*- coding: utf-8 -*-
"""
线上书店 APP —— 图书占位封面生成脚本

用途：为演示数据中的 24 本图书批量生成封面图，避免 APP 真机演示时出现空白图。
设计：按图书分类配色 + 统一版式（分类标签 / 书名 / 作者 / 出版社），
      中文由字体直接渲染，不会出现 AI 生图的文字乱码问题。

输出：bookstore-server/upload/cover/*.jpg  （通过后端 /files/cover/xxx.jpg 访问）

运行：
  D:/anaconda3/python.exe tools/gen_covers.py
"""

import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ---------------------------------------------------------------- 基础配置
W, H = 600, 800                      # 3:4 标准书封比例
OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "bookstore-server", "upload", "cover")

FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"   # 微软雅黑 粗体
FONT_REG = "C:/Windows/Fonts/msyh.ttc"      # 微软雅黑 常规
FONT_HEI = "C:/Windows/Fonts/simhei.ttf"    # 黑体（备用）

# 分类配色：底色渐变（深 -> 浅）+ 强调色
THEME = {
    "计算机": ((18, 38, 82), (37, 99, 168), (96, 205, 255)),
    "文学":   ((92, 27, 34), (164, 52, 58),  (255, 176, 120)),
    "历史":   ((58, 42, 24), (118, 85, 42),  (226, 186, 110)),
    "经济":   ((12, 58, 52), (24, 110, 92),  (110, 224, 178)),
    "心理学": ((48, 26, 78), (104, 60, 152), (196, 156, 255)),
    "科普":   ((16, 46, 66), (30, 100, 130), (120, 226, 230)),
}

# 与 db/seed.sql 中 book 表严格对应的 24 条数据
BOOKS = [
    ("java-thinking.jpg", "Java 编程思想（第4版）", "Bruce Eckel", "机械工业出版社", "计算机"),
    ("springboot.jpg",   "Spring Boot 实战",       "Craig Walls", "人民邮电出版社", "计算机"),
    ("csapp.jpg",        "深入理解计算机系统",      "Randal Bryant", "机械工业出版社", "计算机"),
    ("algorithms.jpg",   "算法导论（第3版）",       "Thomas Cormen", "机械工业出版社", "计算机"),
    ("mysql.jpg",        "MySQL 是怎样运行的",      "小孩子4919", "人民邮电出版社", "计算机"),

    ("live.jpg",         "活着",                   "余华", "作家出版社", "文学"),
    ("solitude.jpg",     "百年孤独",               "加西亚·马尔克斯", "南海出版公司", "文学"),
    ("ordinary.jpg",     "平凡的世界",             "路遥", "人民文学出版社", "文学"),
    ("fortress.jpg",     "围城",                   "钱钟书", "人民文学出版社", "文学"),

    ("shiji.jpg",        "史记（全本）",           "司马迁", "中华书局", "历史"),
    ("wanli.jpg",        "万历十五年",             "黄仁宇", "中华书局", "历史"),
    ("sapiens.jpg",      "人类简史",               "尤瓦尔·赫拉利", "中信出版社", "历史"),
    ("worldhistory.jpg", "全球通史",               "斯塔夫里阿诺斯", "北京大学出版社", "历史"),

    ("economics.jpg",    "经济学原理",             "N.格里高利·曼昆", "北京大学出版社", "经济"),
    ("wealth.jpg",       "国富论",                 "亚当·斯密", "商务印书馆", "经济"),
    ("richdad.jpg",      "穷爸爸富爸爸",           "罗伯特·清崎", "萧氏出版社", "经济"),
    ("principles.jpg",   "原则",                   "瑞·达利欧", "中信出版社", "经济"),

    ("psychology.jpg",   "心理学与生活",           "理查德·格里格", "人民邮电出版社", "心理学"),
    ("thinking.jpg",     "思考，快与慢",           "丹尼尔·卡尼曼", "中信出版社", "心理学"),
    ("courage.jpg",      "被讨厌的勇气",           "岸见一郎", "机械工业出版社", "心理学"),
    ("toad.jpg",         "蛤蟆先生去看心理医生",   "罗伯特·戴博德", "天津人民出版社", "心理学"),

    ("time.jpg",         "时间简史",               "史蒂芬·霍金", "湖南科技出版社", "科普"),
    ("short.jpg",        "万物简史",               "比尔·布莱森", "接力出版社", "科普"),
    ("quantum.jpg",      "上帝掷骰子吗",           "曹天元", "北京联合出版公司", "科普"),
]


# ---------------------------------------------------------------- 工具函数
def lerp(c1, c2, t):
    return tuple(int(round(a + (b - a) * t)) for a, b in zip(c1, c2))


def make_background(c_deep, c_mid):
    """生成对角渐变底图 + 柔和光斑，比纯色更有质感。"""
    img = Image.new("RGB", (W, H), c_deep)
    d = ImageDraw.Draw(img)
    # 对角渐变：按 (x+y) 归一化取色
    denom = W + H
    for y in range(H):
        for x in range(0, W, 4):          # 每 4px 画一段，性能与观感平衡
            t = (x + y) / denom
            d.line([(x, y), (x + 4, y)], fill=lerp(c_deep, c_mid, t))
        d.line([(W - 4, y), (W, y)], fill=lerp(c_deep, c_mid, W / denom))

    # 右上角柔光
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([W * 0.45, -H * 0.18, W * 1.35, H * 0.62], fill=(255, 255, 255, 26))
    gd.ellipse([-W * 0.35, H * 0.55, W * 0.55, H * 1.3], fill=(255, 255, 255, 16))
    glow = glow.filter(ImageFilter.GaussianBlur(60))
    img = Image.alpha_composite(img.convert("RGBA"), glow).convert("RGB")
    return img


OPEN_P = "（《【"
CLOSE_P = "）》】，。！？：；、"


def tokenize(text):
    """英文单词/数字整体为一个 token，中文逐字为一个 token，便于中英混排换行。"""
    cjk_punct = OPEN_P + CLOSE_P
    tokens, buf = [], ""
    for ch in text:
        if "\u4e00" <= ch <= "\u9fff" or ch in cjk_punct:
            if buf:
                tokens.append(buf)
                buf = ""
            tokens.append(ch)
        else:
            buf += ch
    if buf:
        tokens.append(buf)
    return tokens


def wrap(draw, text, font, max_w):
    """中英混排换行，并处理三类排版细节：
    1) 收尾标点悬挂——"）》"等放不下也贴在行尾，不允许孤行；
    2) 开括号不落行尾——"（"等若将留在行尾则挪到下一行行首；
    3) 孤字回填——末行只剩 1 个字符时，从上一行挪一个 token 下来。
    """
    lines, cur = [], []          # lines: List[List[token]]
    for tk in tokenize(text):
        trial = "".join(cur) + tk
        if cur and draw.textlength(trial.strip(), font=font) > max_w and tk not in CLOSE_P:
            move = []
            while cur and cur[-1] in OPEN_P:
                move.insert(0, cur.pop())
            if cur:
                lines.append(cur)
            cur = move + [tk]
        else:
            cur.append(tk)
    if cur:
        lines.append(cur)

    # 孤字回填
    if len(lines) >= 2 and len("".join(lines[-1]).strip()) <= 1 and len(lines[-2]) > 1:
        lines[-1].insert(0, lines[-2].pop())
        while lines[-2] and lines[-2][-1] in OPEN_P:
            lines[-1].insert(0, lines[-2].pop())
        if not lines[-2]:
            lines.pop(-2)
    return ["".join(l).strip() for l in lines]


def fit_title(draw, title, max_w, max_lines=4):
    """自动收敛字号，保证书名在 4 行内铺满且不溢出。"""
    for size in (58, 54, 50, 46, 42, 38, 34, 30):
        font = ImageFont.truetype(FONT_BOLD, size)
        lines = wrap(draw, title, font, max_w)
        if len(lines) <= max_lines:
            return font, lines, size
    return font, lines, size


def draw_center(draw, lines, font, y, fill, line_gap=12):
    for ln in lines:
        w = draw.textlength(ln, font=font)
        draw.text(((W - w) / 2, y), ln, font=font, fill=fill)
        y += font.size + line_gap
    return y


def draw_tracked(draw, text, font, y, fill, tracking=3):
    """带字距的居中文本，用于出版社与英文标签。"""
    widths = [draw.textlength(ch, font=font) for ch in text]
    total = sum(widths) + tracking * (len(text) - 1)
    x = (W - total) / 2
    for ch, w in zip(text, widths):
        draw.text((x, y), ch, font=font, fill=fill)
        x += w + tracking


def rounded_label(draw, text, font, cx, y, bg, fg, pad_x=22, pad_y=9):
    tw = draw.textlength(text, font=font)
    th = font.size
    x0, y0 = cx - tw / 2 - pad_x, y
    x1, y1 = cx + tw / 2 + pad_x, y + th + pad_y * 2
    draw.rounded_rectangle([x0, y0, x1, y1], radius=(y1 - y0) / 2, fill=bg)
    draw.text((cx - tw / 2, y + pad_y - 2), text, font=font, fill=fg)
    return y1


# ---------------------------------------------------------------- 单张封面
def build_cover(path, title, author, publisher, category):
    c_deep, c_mid, accent = THEME[category]
    img = make_background(c_deep, c_mid)
    d = ImageDraw.Draw(img, "RGBA")

    # 左侧强调色竖条
    d.rectangle([0, 0, 10, H], fill=accent)
    # 顶部细描边
    d.rectangle([10, 0, W, 3], fill=accent)

    # 分类标签（胶囊）
    f_cat = ImageFont.truetype(FONT_REG, 26)
    rounded_label(d, category, f_cat, W / 2, 62,
                  bg=(255, 255, 255, 235), fg=lerp(c_deep, (0, 0, 0), 0.15))

    # 书名（垂直居中偏上）
    f_title, t_lines, t_size = fit_title(d, title, W - 150)
    block_h = len(t_lines) * (t_size + 12) - 12
    ty = (H - block_h) / 2 + 20
    # 书名后加淡色衬底，提升长书名的可读性
    if len(t_lines) >= 3:
        d.rounded_rectangle([62, ty - 34, W - 62, ty + block_h + 26],
                            radius=18, fill=(0, 0, 0, 40))
    tidx = len(t_lines) // 2
    for i, ln in enumerate(t_lines):
        f = f_title if i == tidx else ImageFont.truetype(FONT_BOLD, max(t_size - 8, 26))
        w = d.textlength(ln, font=f)
        d.text(((W - w) / 2 + 1, ty + 2), ln, font=f, fill=(0, 0, 0, 90))   # 投影
        d.text(((W - w) / 2, ty), ln, font=f, fill=(255, 255, 255))
        ty += t_size + 12
    bottom_of_title = ty

    # 分隔线
    ly = bottom_of_title + 26
    d.rectangle([W / 2 - 46, ly, W / 2 + 46, ly + 3], fill=accent)

    # 作者
    f_author = ImageFont.truetype(FONT_REG, 30)
    aw = d.textlength(author, font=f_author)
    d.text(((W - aw) / 2, ly + 30), author, font=f_author, fill=(255, 255, 255, 235))

    # 出版社（底部，带字距）
    f_pub = ImageFont.truetype(FONT_REG, 22)
    draw_tracked(d, publisher, f_pub, H - 96, (255, 255, 255, 165), tracking=4)

    # 右下角小装饰
    d.ellipse([W - 92, H - 66, W - 74, H - 48], fill=(255, 255, 255, 90))

    img.save(path, "JPEG", quality=90, optimize=True)
    return path


def build_default():
    """兜底封面：图书无封面时使用。"""
    c_deep, c_mid, accent = THEME["计算机"]
    img = make_background(c_deep, c_mid)
    d = ImageDraw.Draw(img, "RGBA")
    d.rectangle([0, 0, 10, H], fill=accent)
    f = ImageFont.truetype(FONT_BOLD, 46)
    t = "暂无封面"
    w = d.textlength(t, font=f)
    d.text(((W - w) / 2, H / 2 - 30), t, font=f, fill=(255, 255, 255))
    f2 = ImageFont.truetype(FONT_REG, 22)
    draw_tracked(d, "NO COVER", f2, H / 2 + 44, (255, 255, 255, 150), tracking=6)
    p = os.path.join(OUT_DIR, "default.jpg")
    img.save(p, "JPEG", quality=90, optimize=True)
    return p


# ---------------------------------------------------------------- 主流程
def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, title, author, publisher, category in BOOKS:
        p = build_cover(os.path.join(OUT_DIR, name), title, author, publisher, category)
        print("生成:", os.path.basename(p), "|", title)
    build_default()
    print("生成: default.jpg | 兜底封面")
    print("\n完成，共", len(BOOKS) + 1, "张 ->", OUT_DIR)


if __name__ == "__main__":
    main()
