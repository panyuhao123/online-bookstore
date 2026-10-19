# -*- coding: utf-8 -*-
"""
图书封面自动抓取工具（豆瓣读书为主源，当当为备源）

设计：
  1. 从 db/seed.sql 解析出 24 本书的 title / author / publisher / 封面文件名
  2. 到豆瓣读书搜索页抓取 -> 页面内嵌 window.__DATA__ JSON，直接拿到 title/cover_url/abstract
  3. 按「书名标准化匹配 + 作者匹配 + 出版社匹配」三重打分挑最佳结果
  4. 带 Referer 下载封面原图（豆瓣 CDN 对无 Referer 请求返回 418）
  5. 校验图片有效性（JPEG 头尾 + 体积），合格后写回 upload/cover/<原文件名>

关键点：seed.sql 中封面文件名（如 /files/cover/live.jpg）保持不变，
        因此只需覆盖图片文件，无需改动数据库记录与任何业务代码。

用法：
  python tools/fetch_covers.py            # 全量抓取
  python tools/fetch_covers.py --dry      # 只解析匹配，不下载
  python tools/fetch_covers.py 活着 Java  # 只处理书名包含指定关键字的条目
"""
import argparse
import json
import os
import random
import re
import ssl
import string
import sys
import time
import urllib.parse
import urllib.request

ssl._create_default_https_context = ssl._create_unverified_context

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED = os.path.join(ROOT, "db", "seed.sql")
OUT_DIR = os.path.join(ROOT, "bookstore-server", "upload", "cover")
REPORT = os.path.join(ROOT, "tools", "covers_report.json")

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
DOUBAN_REF = "https://book.douban.com/"


# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------
def http_get(url, referer=None, timeout=15, retries=3):
    """带重试的 GET，返回 bytes；对豆瓣请求附带随机 bid Cookie 绕过限流"""
    last = None
    for i in range(retries):
        try:
            headers = {
                "User-Agent": UA,
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,"
                          "image/avif,image/webp,*/*;q=0.8",
                "Accept-Language": "zh-CN,zh;q=0.9",
                "Connection": "close",
            }
            if "douban.com" in url:
                # 豆瓣按 bid Cookie 识别跟踪，随机 bid 即可规避限流
                bid = "".join(random.choices(string.ascii_letters + string.digits, k=11))
                headers["Cookie"] = f"bid={bid}"
            if referer:
                headers["Referer"] = referer
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read()
        except Exception as e:
            last = e
            time.sleep(1.2 * (i + 1))
    raise last


# --------------------------------------------------------------------------
# seed.sql 解析
# --------------------------------------------------------------------------
BOOK_RE = re.compile(
    r"\((\d+),\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*"
    r"([\d.]+),\s*(\d+),\s*'/files/cover/([^']+)'"
)


def parse_seed():
    text = open(SEED, encoding="utf-8").read()
    books = []
    for m in BOOK_RE.finditer(text):
        cat, title, author, publisher, price, stock, cover = m.groups()
        books.append({
            "categoryId": int(cat),
            "title": title.replace("''", "'").strip(),
            "author": author.replace("''", "'").strip(),
            "publisher": publisher.replace("''", "'").strip(),
            "cover": cover.strip(),
        })
    return books


# --------------------------------------------------------------------------
# 名称标准化与打分
# --------------------------------------------------------------------------
def normalize(s):
    """书名标准化：去括号内容、副标题、版本信息、标点、空格"""
    if not s:
        return ""
    s = re.sub(r"[（(\[【][^）)\]】]*[）)\]】]", "", s)
    s = re.split(r"[:：·・\-—]", s)[0]
    s = re.sub(r"[第\d版修订本精装平装上下册]+$", "", s)
    s = re.sub(r"[\s\.,，。!！?？'\"“”‘’《》<>_~·]+", "", s)
    return s.lower()


def author_key(a):
    """作者标准化：去掉空格/点/连字符，取括号前部分"""
    if not a:
        return ""
    a = re.sub(r"[\s\.\-]+", "", a).lower()
    return re.split(r"[（(]", a)[0]


def score(item, book):
    """对单条候选打分，返回 (分数, 说明)；<30 视为未命中"""
    t = item.get("title") or ""
    ab = item.get("abstract") or ""
    if not t or not item.get("cover_url"):
        return -1, "无标题或封面"

    # 排除非图书条目：DVD/音像/电子书等，其封面多为豆瓣默认占位图
    blob = (t + " " + ab).lower()
    if re.search(r"dvd|音像|cd|vcd|有声书|电子书", blob):
        return -1, "非纸质图书条目(DVD/音像等)"

    s, why = 0, []
    nt, nb = normalize(t), normalize(book["title"])
    if nt and nb:
        if nt == nb:
            s += 60
            why.append("书名精确一致")
        elif nb in nt or nt in nb:
            s += 30
            why.append("书名包含")

    ak = author_key(book["author"])
    if ak:
        aab = re.sub(r"[\s\.\-]+", "", ab).lower()
        if ak in aab:
            s += 30
            why.append("作者匹配")
        elif len(ak) >= 2 and ak[:2] in aab:
            s += 12
            why.append("作者部分匹配")

    pk = re.sub(r"\s+", "", book["publisher"])
    if pk and pk in re.sub(r"\s+", "", ab):
        s += 15
        why.append("出版社匹配")

    if not why:
        return -1, "无匹配依据"
    return s, "+".join(why)


# --------------------------------------------------------------------------
# 数据源
# --------------------------------------------------------------------------
def _douban_items(html):
    """从豆瓣搜索页 HTML 中解析 window.__DATA__ 的 items"""
    m = re.search(r"window\.__DATA__\s*=\s*(\{.*?\});\s*\n", html, re.S) or \
        re.search(r"window\.__DATA__\s*=\s*(\{.*?\})</script>", html, re.S)
    if not m:
        return None
    try:
        data = json.loads(m.group(1))
    except json.JSONDecodeError:
        return None
    items = data.get("items") or []
    for it in items:
        if it.get("cover_url"):
            # m(medium) -> l(large)，取更清晰的大图
            it["cover_url"] = it["cover_url"].replace("/subject/m/", "/subject/l/")
    return items


def search_douban(keyword, tries=5, verbose=True):
    """
    豆瓣读书搜索。

    豆瓣对高频请求会临时限流（表现为页面无 __DATA__ 或 items 为空），
    因此这里做指数退避重试：5s -> 10s -> 20s -> 40s，并叠加随机抖动。
    """
    url = "https://book.douban.com/subject_search?search_text=" + urllib.parse.quote(keyword)
    delay = 5.0
    for k in range(tries):
        try:
            items = _douban_items(http_get(url, referer=DOUBAN_REF, retries=1)
                                  .decode("utf-8", "ignore"))
            if items:
                return items
            reason = "空结果"
        except Exception as e:
            reason = f"{type(e).__name__}"
        if k < tries - 1:
            if verbose:
                print(f"     (豆瓣 {reason}，{delay:.0f}s 后重试 {k + 2}/{tries})")
            time.sleep(delay + random.uniform(0, 1.5))
            delay *= 2
    return []


def search_dangdang(keyword, tries=2, verbose=False):
    """
    当当搜索（备源）。按 <li ddt-pit> 结果块解析：
    书名取 a.pic 的 title，封面取 img src，作者/出版社取 search_book_author 内的 title，
    合成与豆瓣一致的 abstract，从而复用同一套打分逻辑校验版本。
    """
    url = "https://search.dangdang.com/?key=" + urllib.parse.quote(keyword) + "&act=input"
    html = None
    for k in range(tries):
        try:
            html = http_get(url, referer="https://www.dangdang.com/",
                            retries=1).decode("gb18030", "ignore")
            break
        except Exception:
            if k < tries - 1:
                time.sleep(2.0)
    if not html:
        return []

    out = []
    for li in re.split(r"<li\s+ddt-pit=", html)[1:]:
        m_img = re.search(r"<a[^>]+class=\"pic\"[^>]*>\s*<img[^>]+src=['\"](//[^'\"]+\.(?:jpg|png))",
                          li, re.I)
        m_title = re.search(r"<a[^>]+class=\"pic\"[^>]+title=\"([^\"]{1,80})\"", li, re.I) or \
            re.search(r"<a[^>]+title=\"([^\"]{1,80})\"[^>]+class=\"pic\"", li, re.I)
        if not (m_img and m_title):
            continue
        m_author = re.search(r"name='itemlist-author'[^>]*title='([^']*)'", li) or \
            re.search(r"title='([^']*)'[^>]*name='itemlist-author'", li)
        m_pub = re.search(r"dd_name='单品出版社'\s+title='([^']*)'", li) or \
            re.search(r"title='([^']*)'[^>]*dd_name='单品出版社'", li)
        abstract = " / ".join(x for x in [
            (m_author.group(1) if m_author else ""),
            (m_pub.group(1) if m_pub else ""),
        ] if x)
        src = m_img.group(1)
        out.append({
            "title": m_title.group(1).strip(),
            "cover_url": ("https:" + src) if src.startswith("//") else src,
            "abstract": abstract,
            "url": "",
        })
    return out


# --------------------------------------------------------------------------
# 下载与校验
# --------------------------------------------------------------------------
def valid_jpeg(b):
    return len(b) > 8000 and b[:2] == b"\xff\xd8" and b[-2:] == b"\xff\xd9"


def download(url, referer):
    b = http_get(url, referer=referer, timeout=20)
    return b if valid_jpeg(b) else None


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------
def core_title(t):
    """去掉括号与副标题，得到用于二次检索的核心书名"""
    t = re.sub(r"[（(\[【][^）)\]】]*[）)\]】]", "", t)
    t = re.split(r"[:：·]", t)[0]
    return t.strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("keys", nargs="*", help="只处理书名包含这些关键字的条目")
    ap.add_argument("--dry", action="store_true", help="只匹配不下载")
    ap.add_argument("--resume", action="store_true", help="跳过上一轮已成功的条目")
    ap.add_argument("--retry", action="store_true", help="只重试上一轮失败的条目")
    ap.add_argument("--sleep", type=float, default=1.0, help="每本书之间的间隔秒数")
    args = ap.parse_args()

    books = parse_seed()
    if not books:
        print("[FATAL] 未能从 seed.sql 解析出图书，请检查 BOOK_RE 正则")
        return 1
    print(f"[INFO] seed.sql 解析到 {len(books)} 本书\n")

    targets = books
    if args.keys:
        targets = [b for b in books if any(k in b["title"] for k in args.keys)]
        print(f"[INFO] 按关键字过滤，待处理 {len(targets)} 本\n")

    os.makedirs(OUT_DIR, exist_ok=True)

    # 断点续跑：载入上一轮报告
    prev = {}
    if os.path.exists(REPORT) and (args.resume or args.retry):
        try:
            prev = {r["title"]: r for r in json.load(open(REPORT, encoding="utf-8"))}
            print(f"[INFO] 载入上一轮报告 {len(prev)} 条")
        except Exception:
            prev = {}
    if args.resume:
        before = len(targets)
        targets = [b for b in targets if prev.get(b["title"], {}).get("status") != "OK"]
        print(f"[INFO] --resume：跳过已成功 {before - len(targets)} 本，待处理 {len(targets)} 本\n")
    elif args.retry:
        before = len(targets)
        targets = [b for b in targets
                   if prev.get(b["title"], {}).get("status") not in ("OK",)]
        targets = [b for b in targets if prev.get(b["title"], {}).get("status") != "OK"]
        print(f"[INFO] --retry：待重试 {len(targets)} 本（原 {before} 本）\n")

    report = [r for r in prev.values()] if (args.resume or args.retry) else []

    def emit(r):
        """resume 模式下 rec 可能已存在于 report，避免重复追加"""
        if not any(x is r for x in report):
            report.append(r)

    for i, b in enumerate(targets, 1):
        tag = f"[{i}/{len(targets)}] {b['title']}"
        rec = prev.get(b["title"])
        if rec is None:
            rec = {"title": b["title"], "author": b["author"],
                   "publisher": b["publisher"], "cover": b["cover"]}
        rec.update({"status": "MISS", "source": "", "why": "", "url": ""})
        emit(rec)
        try:
            best, bs, why, src = None, -1, "", ""

            for kw in dict.fromkeys([b["title"], core_title(b["title"])]):
                if not kw:
                    continue
                for it in search_douban(kw):
                    s, w = score(it, b)
                    if s > bs:
                        best, bs, why, src = it, s, w, "douban"
                if bs >= 60:      # 书名+作者已确认，无需再换关键词
                    break
            if bs < 60:           # 豆瓣没拿到可靠结果时才走当当备源
                dd_best, dd_s, dd_why = None, -1, ""
                for it in search_dangdang(b["title"]):
                    s, w = score(it, b)
                    if s > dd_s:
                        dd_best, dd_s, dd_why = it, s, w
                # 当当搜索结果鱼龙混杂（套装/纪念版/青少版），
                # 必须书名+作者+出版社三重确认（>=75）才接受，宁缺毋滥
                if dd_s >= 75:
                    best, bs, why, src = dd_best, dd_s, dd_why, "dangdang"

            if bs < 30 or best is None:
                print(f"{tag}\n  -> 未命中（最高分 {bs}）")
                emit(rec)
                time.sleep(args.sleep)
                continue

            rec.update(source=src, why=f"{why}({bs})", url=best["cover_url"],
                       title_db=best.get("title"))
            print(f"{tag}\n  -> {src} 命中：{best.get('title')} | {best.get('abstract')}")
            print(f"     分数 {bs} ({why})")

            if args.dry:
                rec["status"] = "DRY"
                emit(rec)
                time.sleep(args.sleep)
                continue

            ref = DOUBAN_REF if src == "douban" else "https://www.dangdang.com/"
            data = download(rec["url"], ref)
            if not data:
                print(f"     下载失败或图片无效: {rec['url']}")
                rec["status"] = "DL_FAIL"
                emit(rec)
                time.sleep(args.sleep)
                continue

            path = os.path.join(OUT_DIR, b["cover"])
            with open(path, "wb") as f:
                f.write(data)
            rec["status"] = "OK"
            rec["bytes"] = len(data)
            print(f"     已保存 {b['cover']} ({len(data) // 1024} KB)")
        except Exception as e:
            print(f"{tag}\n  -> 异常: {type(e).__name__}: {str(e)[:120]}")
            rec["status"] = "ERROR"
            rec["why"] = str(e)[:200]
        emit(rec)
        time.sleep(args.sleep)

    with open(REPORT, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    ok = sum(1 for r in report if r["status"] == "OK")
    print("\n" + "=" * 60)
    print(f"完成：{ok} 成功 / {len(report)} 总计")
    for r in report:
        if r["status"] != "OK":
            print(f"  [{r['status']}] {r['title']}  {r.get('why', '')}")
    print(f"报告：{REPORT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
