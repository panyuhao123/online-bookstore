# -*- coding: utf-8 -*-
"""
生成 APP 端演示数据集（脱敏真实数据）。

输入：
  - tools/mock_catalog.py   书目底稿（一级 9 类 / 二级 48 类 / 96 本真实书目 / 20 个书单）
  - db/seed.sql             真实演示数据，只取其中的「销量」与「库存」两项业务指标，
                            让 seed 里的 24 本书在 mock 数据集中保持真实热度

输出（全部写入 bookstore-app/common/mock/）：
  - categories.js   9 个一级分类 + 48 个二级子分类
  - books.js        96 本图书，字段兼容后端 BookVO，并扩展演示所需字段
  - booklists.js    4 个 Tab + 20 个书单（含书目 id 列表与收藏人数）
  - index.js        模拟接口层，函数签名与 api/book.js、api/category.js 对齐
  同时输出 tools/mock_covers_manifest.json，供 tools/fetch_mock_covers.py 抓封面。

设计要点：
  1) 随机量全部用「书名哈希」做种子，同一份底稿永远生成同一份数据，
     避免每次重跑导致销量/评分跳动，答辩演示数据可复现。
  2) 图书主键从 10001 起编号，与真实库 id（1~24）完全隔离，
     这样 mock 数据永远不会误写入真实业务表。
  3) 脱敏：不导出 user/admin 的手机号、密码、盐值，不导出订单与地址。

用法：
  python tools/build_mock_data.py
"""
import hashlib
import json
import os
import random
import re
from datetime import date, timedelta

import mock_catalog as catalog

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "bookstore-app", "common", "mock")
MANIFEST = os.path.join(ROOT, "tools", "mock_covers_manifest.json")

SEED = os.path.join(ROOT, "db", "seed.sql")
# 封面抓取报告：存在时用于把确实没抓到封面的书目 coverUrl 置空
COVER_REPORT = os.path.join(ROOT, "tools", "mock_covers_report.json")
# 后端已抓取的真实封面目录（本脚本从这里复制 seed 图书的封面到 APP static）
UPLOAD_COVER = os.path.join(ROOT, "bookstore-server", "upload", "cover")

# 演示基准日：数据集按此日期推算「本周上新 / 本月新书 / 新书标签」
BASE_DATE = date(2026, 10, 17)

ID_BASE = 10001

# 分类 key -> 中文名（图书上存的是中文名，书单规则里写的是 key）
CAT_NAME = {key: name for key, name, _sort, _remark in catalog.CATEGORIES}


# --------------------------------------------------------------------------
# 从 db/seed.sql 提取真实销量与库存（脱敏：只取业务指标，不取任何账号信息）
# --------------------------------------------------------------------------
SEED_RE = re.compile(
    r"\(\d+,\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*"
    r"([\d.]+),\s*(\d+),\s*'/files/cover/([^']+)'"
)


def read_seed_metrics():
    """{书名: (销量, 库存)}"""
    if not os.path.exists(SEED):
        return {}
    text = open(SEED, encoding="utf-8").read()
    out = {}
    for m in SEED_RE.finditer(text):
        title, _author, _pub, _price, stock, cover = m.groups()
        # 销量在 description 之后，单独再抓一次
        seg = text[m.end(): m.end() + 400]
        sm = re.search(r"(\d+),\s*[01]\)", seg)
        out[title.replace("''", "'").strip()] = (int(sm.group(1)) if sm else 0, int(stock), cover)
    return out


# --------------------------------------------------------------------------
# 确定性随机
# --------------------------------------------------------------------------
def read_cover_report():
    """抓取脚本确认「无封面」的书名集合。命中后把 coverUrl 置空，
    让 APP 直接渲染渐变块，而不是先发一个必然 404 的图片请求。"""
    if not os.path.exists(COVER_REPORT):
        return set()
    try:
        rows = json.load(open(COVER_REPORT, encoding="utf-8"))
    except Exception:
        return set()
    return {r["title"] for r in rows if r.get("status") in ("MISS", "DL_FAIL", "ERROR")}


def rng_for(title):
    return random.Random(int(hashlib.md5(title.encode("utf-8")).hexdigest()[:8], 16))


def date_text(d):
    return d.isoformat()


def money(v):
    """统一两位小数，避免 JS 端出现 37.33000000001"""
    return round(float(v) + 1e-9, 2)


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------
def build_categories():
    """一级 1~9；二级 = 一级*100 + 序号，保证两级 id 不冲突"""
    rows = []
    for i, (key, name, sort, remark) in enumerate(catalog.CATEGORIES, start=1):
        children = []
        for j, sub in enumerate(catalog.SUB_CATEGORIES[key], start=1):
            children.append({"id": i * 100 + j, "name": sub, "sort": j})
        rows.append({
            "id": i,
            "key": key,
            "name": name,
            "sort": sort,
            "remark": remark,
            "children": children,
        })
    return rows


def build_books(categories):
    seed_metrics = read_seed_metrics()
    cover_missing = read_cover_report()

    cat_by_key = {c["key"]: c for c in categories}
    sub_id = {}
    for c in categories:
        for ch in c["children"]:
            sub_id[(c["key"], ch["name"])] = ch["id"]

    # 入库时间分档：24 本周 / 30 本月 / 其余 31~60 天，打散后按书目顺序发放
    buckets = [(0, 6)] * 24 + [(7, 29)] * 30 + [(30, 59)] * 60
    rng_all = random.Random(20261017)
    rng_all.shuffle(buckets)

    books = []
    for i, row in enumerate(catalog.BOOKS):
        cat_key, sub_name, title, author, publisher, price, discount, desc, cover = row
        rng = rng_for(title)
        cat = cat_by_key[cat_key]

        # 定价：由「真实售价 / 折扣」反推到整数元，保证售价真实、定价合乎市场
        original_price = round(price / discount)
        if original_price <= price:
            original_price = int(price) + 1
        real_discount = round(price / original_price, 3)

        # 销量 / 库存：seed 中的真实书目沿用真实值，其余按哈希生成
        m = seed_metrics.get(title)
        sales = m[0] if m and m[0] else rng.randint(30, 2400)
        # 库存分布：约 8% 售罄（演示售罄遮罩）、12% 仅剩几件（演示「仅剩 N 件」标签），其余充足
        r = rng.random()
        if m and m[1]:
            stock = m[1]
        elif r < 0.08:
            stock = 0
        elif r < 0.20:
            stock = rng.randint(1, 5)
        else:
            stock = rng.randint(20, 320)
        if sales == 0:
            sales = rng.randint(30, 2400)

        # 评分：冷门书更可能无人评价（与后端 avgRating=null 语义一致），
        # 热门书一律有分，避免出现「销量 TOP 却暂无评分」这种自相矛盾的演示数据
        has_rating = sales >= 200 or rng.random() > 0.25
        avg_rating = round(rng.uniform(3.6, 4.9), 1) if has_rating else None
        review_count = int(sales * rng.uniform(0.08, 0.22)) if has_rating else 0

        publish_date = BASE_DATE - timedelta(days=rng.randint(120, 3900))
        lo, hi = buckets[i % len(buckets)]
        stock_date = BASE_DATE - timedelta(days=rng.randint(lo, hi))

        if real_discount <= 0.32:
            promo = ["清仓", "特价"]
        elif real_discount <= 0.45:
            promo = ["特价"]
        elif real_discount <= 0.55:
            promo = ["满减"]
        else:
            promo = []

        books.append({
            "id": ID_BASE + i,
            "categoryId": cat["id"],
            "categoryName": cat["name"],
            "subCategoryId": sub_id[(cat_key, sub_name)],
            "subCategoryName": sub_name,
            "title": title,
            "author": author,
            "publisher": publisher,
            "price": money(price),
            "originalPrice": money(original_price),
            "discount": real_discount,
            "stock": stock,
            "coverUrl": ("/static/cover/" + (cover or slug(title) + ".jpg"))
                        if (cover or title not in cover_missing) else "",
            "description": desc,
            "sales": sales,
            "status": 1,
            "avgRating": avg_rating,
            "reviewCount": review_count,
            "publishDate": date_text(publish_date),
            "stockDate": date_text(stock_date),
            "isNew": (BASE_DATE - stock_date).days <= 30,
            "promoTags": promo,
            # 是否有可用封面文件；false 时 APP 端渲染渐变块兜底
            "hasCover": bool(cover) or title not in cover_missing,
        })
    return books


def slug(title):
    """生成封面文件名：中文转拼音不便，统一用稳定哈希，避免文件名乱码"""
    return "bk" + hashlib.md5(title.encode("utf-8")).hexdigest()[:10]


def build_booklists(books):
    by_id = {b["id"]: b for b in books}
    tabs = [{"id": t[0], "name": t[1]} for t in catalog.BOOKLIST_TABS]
    lists = []

    for i, (tab_id, title, desc, tags, rule) in enumerate(catalog.BOOKLISTS, start=1):
        picked = pick_books(books, rule)
        ids = [b["id"] for b in picked]
        rng = rng_for(title)
        lists.append({
            "id": 20001 + i,
            "tabId": tab_id,
            "title": title,
            "desc": desc,
            "tags": tags,
            "bookIds": ids,
            # 卡片封面取书单前 4 本，不足 4 本时用已有的补齐
            "coverIds": (ids * 2)[:4],
            "collectCount": rng.randint(600, 48000),
            "bookCount": len(ids),
        })
    return tabs, lists


def pick_books(books, rule):
    """按底稿规则挑书：先按条件筛选，再按指标排序取前 size 本。"""
    size = rule.get("size", 8)

    if rule["rule"] == "titles":
        order = {k: i for i, k in enumerate(rule["keys"])}
        hit = [b for b in books if any(k in b["title"] for k in rule["keys"])]
        hit.sort(key=lambda b: min(order[k] for k in rule["keys"] if k in b["title"]))
        return hit[:size]

    pool = list(books)
    if "cats" in rule:
        # 底稿里写的是分类 key（fiction / business ...），图书上存的是中文名，这里做一次映射
        names = {CAT_NAME.get(c, c) for c in rule["cats"]}
        pool = [b for b in pool if b["categoryName"] in names]
    if "subs" in rule:
        pool = [b for b in pool if b["subCategoryName"] in set(rule["subs"])]
    if rule["rule"] == "recent":
        pool = [b for b in pool if (BASE_DATE - date.fromisoformat(b["stockDate"])).days <= rule["days"]]
    if rule["rule"] == "discount":
        pool = [b for b in pool if b["discount"] <= rule["maxDiscount"]]
    if rule["rule"] == "priceMax":
        pool = [b for b in pool if b["price"] <= rule["maxPrice"]]
    if "minRating" in rule:
        pool = [b for b in pool if (b["avgRating"] or 0) >= rule["minRating"]]

    if rule.get("metric") == "discount":
        # 折扣越低越便宜，升序取前 size 本
        pool.sort(key=lambda b: (b["discount"], b["price"]))
        return pool[:size]

    sorters = {
        "sales": lambda b: b["sales"],
        "rating": lambda b: ((b["avgRating"] or 0), b["reviewCount"]),
        "stockDate": lambda b: b["stockDate"]
    }
    pool.sort(key=sorters.get(rule.get("metric"), sorters["sales"]), reverse=True)
    return pool[:size]


# --------------------------------------------------------------------------
# 可选：导出可导入真实后端的 SQL
# --------------------------------------------------------------------------
def sql_str(v):
    return str(v).replace("'", "''")


def export_sql(categories, books):
    """
    生成 db/mock_dataset.sql。

    前端四个页面默认走 APP 内的 mock 数据，不需要后端；
    但「加入购物车 / 立即购买 / 下单」必须落在真实后端上才有意义。
    执行本脚本后，演示书目就进入真实库，加购与下单全链路可用，前端无需任何改动。

    注意：会清空 book / category 及其依赖的订单与购物车数据（保留账号），
    原有 db/seed.sql 仍可随时恢复。
    """
    lines = [
        "-- =====================================================================",
        "-- 线上书店 APP —— 演示数据集（脱敏真实数据）导入脚本",
        "--",
        "-- 由 tools/build_mock_data.py 生成，请勿手工编辑。",
        "--",
        "-- 作用：把 9 个一级分类 + 102 本演示书目写入真实库，",
        "--       让「加入购物车 / 立即购买 / 下单」走完整后端链路。",
        "--       前端列表页默认使用 APP 内的 mock 数据，不导入也能正常演示。",
        "--",
        "-- 影响：清空 book / category 及依赖它们的订单与购物车（用户账号保留）。",
        "--       需要恢复初始演示数据请执行：SOURCE db/seed.sql;",
        "-- 执行：USE bookstore; SOURCE db/mock_dataset.sql;",
        "-- =====================================================================",
        "",
        "USE `bookstore`;",
        "",
        "TRUNCATE TABLE `order_item`;",
        "TRUNCATE TABLE `orders`;",
        "TRUNCATE TABLE `cart`;",
        # 评价挂在图书上：不一起清会留下引用已删除图书 id 的孤儿数据
        "TRUNCATE TABLE `review`;",
        "TRUNCATE TABLE `book`;",
        "TRUNCATE TABLE `category`;",
        "",
        "-- ---------------------------------------------------------------------",
        "-- 一级分类（9 个；二级子分类只存在于 APP mock 数据中，后端分类表是平表）",
        "-- ---------------------------------------------------------------------",
        "INSERT INTO `category` (`id`, `name`, `sort`, `remark`) VALUES",
    ]

    cat_rows = [
        f"({c['id']}, '{sql_str(c['name'])}', {c['sort']}, '{sql_str(c['remark'])}')"
        for c in categories
    ]
    lines.append(",\n".join(cat_rows) + ";")
    lines += [
        "",
        "-- ---------------------------------------------------------------------",
        "-- 图书（102 本，主键 10001 起，与 seed.sql 的 1~24 不冲突）",
        "-- ---------------------------------------------------------------------",
        "INSERT INTO `book` (`id`, `category_id`, `title`, `author`, `publisher`,",
        "                    `price`, `stock`, `cover_url`, `description`, `sales`, `status`) VALUES",
    ]

    book_rows = []
    for b in books:
        if b["coverUrl"]:
            cover = "/files/cover/" + os.path.basename(b["coverUrl"])
        else:
            # 后端必须有值，否则 /files 接口会 404；用后端兜底封面顶上
            cover = "/files/cover/default.jpg"
        book_rows.append(
            f"({b['id']}, {b['categoryId']}, '{sql_str(b['title'])}', '{sql_str(b['author'])}', "
            f"'{sql_str(b['publisher'])}', {b['price']:.2f}, {b['stock']}, "
            f"'{cover}', '{sql_str(b['description'])}', {b['sales']}, 1)"
        )
    lines.append(",\n".join(book_rows) + ";")
    lines.append("")

    path = os.path.join(ROOT, "db", "mock_dataset.sql")
    write(path, "\n".join(lines))


# --------------------------------------------------------------------------
# 输出
# --------------------------------------------------------------------------
def js_array(name, rows, comment):
    body = ",\n".join("  " + json.dumps(r, ensure_ascii=False) for r in rows)
    return (
        "/**\n"
        f" * {comment}\n"
        " *\n"
        " * 本文件由 tools/build_mock_data.py 生成，请勿手工编辑；\n"
        " * 修改数据请改 tools/mock_catalog.py 后重新运行生成脚本。\n"
        " */\n"
        f"export const {name} = [\n{body}\n]\n"
    )


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print(f"[OK] {os.path.relpath(path, ROOT)}  ({len(text) // 1024} KB)")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    categories = build_categories()
    books = build_books(categories)
    tabs, lists = build_booklists(books)

    write(os.path.join(OUT_DIR, "categories.js"),
          js_array("CATEGORIES", categories,
                   "图书分类（脱敏真实数据）\n * 一级 9 类，二级 48 类；id 规则：一级 1~9，二级 = 一级*100 + 序号"))

    write(os.path.join(OUT_DIR, "books.js"),
          js_array("BOOKS", books,
                   "图书数据集（脱敏真实数据）\n * 字段兼容后端 BookVO，扩展字段供演示页使用：\n"
                   " *   originalPrice 定价 / discount 折扣 / subCategoryId 二级分类\n"
                   " *   publishDate 出版时间 / stockDate 入库时间 / isNew / promoTags\n"
                   " * 主键从 10001 起，与真实库 id 隔离；avgRating 无评价时为 null"))

    body = (
        js_array("BOOKLIST_TABS", tabs, "书单频道 Tab")
        + "\n"
        + js_array("BOOKLISTS", lists,
                   "书单数据集\n * coverIds 为卡片上拼接展示的 4 本图书；bookIds 为书单全部书目")
    )
    write(os.path.join(OUT_DIR, "booklists.js"), body)

    write(os.path.join(OUT_DIR, "index.js"), API_JS)
    export_sql(categories, books)

    # 封面抓取清单
    manifest = [
        {
            "id": b["id"],
            "title": b["title"],
            "author": b["author"],
            "publisher": b["publisher"],
            "file": os.path.basename(b["coverUrl"] or (slug(b["title"]) + ".jpg")),
            # ready=true 表示后端 upload 目录里已有同名真实封面，可直接复制；
            # 否则交给抓取脚本（目标文件已存在时它会自动跳过）
            "ready": bool(b["coverUrl"]) and os.path.exists(
                os.path.join(UPLOAD_COVER, os.path.basename(b["coverUrl"]))
            ),
        }
        for b in books
    ]
    with open(MANIFEST, "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    ok = sum(1 for b in books if b["hasCover"])
    cheap = sum(1 for b in books if b["discount"] <= 0.5)
    three = sum(1 for b in books if b["discount"] <= 0.32)
    new7 = sum(1 for b in books if (BASE_DATE - date.fromisoformat(b["stockDate"])).days <= 7)
    new30 = sum(1 for b in books if (BASE_DATE - date.fromisoformat(b["stockDate"])).days <= 30)

    print("\n" + "=" * 58)
    print(f"图书 {len(books)} 本   一级分类 {len(categories)} 个   "
          f"二级分类 {sum(len(c['children']) for c in categories)} 个")
    print(f"书单 {len(lists)} 个（{len(tabs)} 个 Tab）")
    print(f"已有真实封面 {ok} 本 / 待抓取 {len(books) - ok} 本")
    print(f"5折及以下 {cheap} 本   3折专区 {three} 本")
    print(f"本周上新 {new7} 本   本月新书 {new30} 本")
    print(f"封面清单：{os.path.relpath(MANIFEST, ROOT)}")
    return 0


# --------------------------------------------------------------------------
# 模拟接口层（与 api/book.js、api/category.js 签名一致，页面可整体切换）
# --------------------------------------------------------------------------
API_JS = r'''/**
 * 模拟接口层（开发环境数据源）。
 *
 * 数据来自 tools/mock_catalog.py 定义、由 tools/build_mock_data.py 生成的脱敏真实数据集，
 * 与后端接口同构：返回值沿用 PageVO {list,total,pageNum,pageSize,pages}，
 * 因此页面从 mock 切回真实后端时，只需把 import 从 './mock' 改成 '@/api/book'。
 *
 * 与真实接口的差异（仅此两条，已在下方注释标明）：
 *   1) 带 200ms 网络延迟，用于把骨架屏与下拉刷新的交互完整演示出来；
 *   2) sort 额外支持 new / publish / discount / rating 四档演示排序，
 *      真实后端的白名单是 default / sales / price_asc / price_desc。
 */
// 显式带 .js：uni-app 的打包器不强求，但 tools/check_mock_api.mjs 用 Node 原生 ESM
// 直接 import 这一层做校验，Node 不认省略扩展名的相对路径
import { CATEGORIES } from './categories.js'
import { BOOKS } from './books.js'
import { BOOKLIST_TABS, BOOKLISTS } from './booklists.js'

/** mock 图书主键起始值：id >= 此值即为演示数据，不会与真实库 id 冲突 */
export const MOCK_ID_BASE = 10001

/** 是否为演示数据主键。加购/下单据此判断能否提交到真实后端。 */
export function isMockId(id) {
  return Number(id) >= MOCK_ID_BASE
}

/** 模拟网络往返：让骨架屏、下拉刷新等状态可被真实观察到 */
function delay(ms = 220) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 深拷贝，避免页面直接改到数据集本身（筛选/排序会污染源数据） */
function clone(v) {
  return JSON.parse(JSON.stringify(v))
}

/** 分类列表 */
export async function getCategories() {
  await delay(120)
  return clone(CATEGORIES)
}

const SORTERS = {
  // 与后端白名单一致的三档
  default: null,
  sales: (a, b) => b.sales - a.sales,
  price_asc: (a, b) => a.price - b.price || b.id - a.id,
  price_desc: (a, b) => b.price - a.price || b.id - a.id,
  // 演示页专用：新书按入库/出版时间，低价页按折扣，书单按评分
  new: (a, b) => (a.stockDate < b.stockDate ? 1 : a.stockDate > b.stockDate ? -1 : b.id - a.id),
  publish: (a, b) => (a.publishDate < b.publishDate ? 1 : a.publishDate > b.publishDate ? -1 : b.id - a.id),
  rating: (a, b) => (Number(b.avgRating) || 0) - (Number(a.avgRating) || 0) || b.reviewCount - a.reviewCount,
  discount: (a, b) => a.discount - b.discount || a.price - b.price
}

/**
 * 图书分页。
 *
 * @param {Object} params keyword / categoryId / subCategoryId / minPrice / maxPrice
 *                        / maxDiscount / isNew / pageNum / pageSize / sort
 * @returns {Promise<{list:Array,total:number,pageNum:number,pageSize:number,pages:number}>}
 */
export async function pageBooks(params) {
  await delay()
  const p = params || {}
  const pageNum = Number(p.pageNum) || 1
  const pageSize = Number(p.pageSize) || 10

  let list = BOOKS.filter((b) => b.status === 1)

  if (p.categoryId) list = list.filter((b) => String(b.categoryId) === String(p.categoryId))
  if (p.subCategoryId) list = list.filter((b) => String(b.subCategoryId) === String(p.subCategoryId))
  if (p.keyword) {
    const kw = String(p.keyword).trim().toLowerCase()
    list = list.filter(
      (b) =>
        b.title.toLowerCase().indexOf(kw) >= 0 ||
        (b.author || '').toLowerCase().indexOf(kw) >= 0 ||
        (b.publisher || '').toLowerCase().indexOf(kw) >= 0
    )
  }
  if (p.minPrice != null) list = list.filter((b) => b.price >= Number(p.minPrice))
  if (p.maxPrice != null) list = list.filter((b) => b.price <= Number(p.maxPrice))
  if (p.maxDiscount != null) list = list.filter((b) => b.discount <= Number(p.maxDiscount))
  // 新书页筛选：latest 全量按入库倒序；week 近 7 天；month 近 30 天
  if (p.range === 'week') list = list.filter((b) => withinDays(b.stockDate, 7))
  else if (p.range === 'month') list = list.filter((b) => withinDays(b.stockDate, 30))

  const sorter = SORTERS[p.sort]
  if (sorter) list = list.slice().sort(sorter)

  const total = list.length
  const start = (pageNum - 1) * pageSize
  return {
    list: clone(list.slice(start, start + pageSize)),
    total,
    pageNum,
    pageSize,
    pages: Math.max(1, Math.ceil(total / pageSize))
  }
}

/** 距基准日是否已超过 n 天（基准日与生成脚本保持一致） */
const BASE_DATE = '2026-10-17'

function daysAgo(dateText) {
  const t = new Date(dateText + 'T00:00:00').getTime()
  if (isNaN(t)) return 9999
  return Math.floor((new Date(BASE_DATE + 'T00:00:00').getTime() - t) / 86400000)
}

function withinDays(dateText, n) {
  const d = daysAgo(dateText)
  return d >= 0 && d <= n
}

/** 图书详情 */
export async function getBook(id) {
  await delay(160)
  const hit = BOOKS.find((b) => String(b.id) === String(id))
  if (!hit) throw new Error('not found')
  return clone(hit)
}

/** 按 id 批量取书（书单详情用，保持传入顺序） */
export async function getBooksByIds(ids) {
  await delay(180)
  const index = {}
  BOOKS.forEach((b) => { index[String(b.id)] = b })
  return (ids || []).map((i) => index[String(i)]).filter(Boolean).map(clone)
}

/** 书单频道 Tab */
export async function getBooklistTabs() {
  await delay(80)
  return clone(BOOKLIST_TABS)
}

/**
 * 书单分页。
 * @param {Object} params tabId / pageNum / pageSize
 */
export async function pageBooklists(params) {
  await delay()
  const p = params || {}
  const pageNum = Number(p.pageNum) || 1
  const pageSize = Number(p.pageSize) || 6
  let list = BOOKLISTS
  if (p.tabId && p.tabId !== 'all') list = list.filter((l) => l.tabId === p.tabId)

  // 卡片上的四宫格拼接封面需要图书对象（书名 + 封面），
  // 这里一次带出，页面就不必为每张卡片再发一次批量查询
  const index = {}
  BOOKS.forEach((b) => { index[String(b.id)] = b })
  const withPreview = list.map((l) => ({
    ...l,
    preview: l.coverIds.map((i) => index[String(i)]).filter(Boolean).map(clone)
  }))

  const total = withPreview.length
  const start = (pageNum - 1) * pageSize
  return {
    list: withPreview.slice(start, start + pageSize),
    total,
    pageNum,
    pageSize,
    pages: Math.max(1, Math.ceil(total / pageSize))
  }
}

/** 书单详情（含书单内全部图书） */
export async function getBooklistDetail(id) {
  await delay(200)
  const item = BOOKLISTS.find((l) => String(l.id) === String(id))
  if (!item) throw new Error('not found')
  const index = {}
  BOOKS.forEach((b) => { index[String(b.id)] = b })
  const books = item.bookIds.map((i) => index[String(i)]).filter(Boolean).map(clone)
  return { ...clone(item), books }
}

export { CATEGORIES, BOOKS, BOOKLIST_TABS, BOOKLISTS }
'''


if __name__ == "__main__":
    raise SystemExit(main())
