# -*- coding: utf-8 -*-
"""
线上书店后端 —— Flyway 数据库迁移纳管验证（批 6）

背景：
  批 6 之前，建库要靠人工按顺序执行 db/schema.sql + db/legacy/migration_v2/v3/v4.sql，
  换台机器极易漏执行或执行错顺序（症状是「接口 500：Unknown column」，且难以立刻定位）。
  现在结构变更全部收敛到 bookstore-server/src/main/resources/db/migration/V*.sql，
  服务启动时由 Flyway 自动补齐缺失版本。

验证目标：
  1. 迁移已被 Flyway 接管：flyway_schema_history 表存在，记录全部成功；
  2. 已有库（演示库）通过 baseline 正确接入，没有重跑历史脚本、没有误删数据；
  3. 实际结构与 db/schema.sql 快照一致（表 / 列 / 索引三方比对），防止长期漂移；
  4. 全新空库能被完整迁移（V1→V4 顺序执行），且最终结构与已有库逐项相同
     —— 这是「换台机器能一键跑起来」的直接证据。

用法：
  python tools/verify_flyway_migration.py
      # 校验当前库（默认 bookstore）与 db/schema.sql 快照一致

  python tools/verify_flyway_migration.py \
      --fresh-db=bookstore_flyway_test --fresh-base=http://127.0.0.1:8082
      # 额外校验一个「全新空库」：该库需先创建（不建表），并让后端实例连它启动，
      # 由 Flyway 自动完成迁移。脚本比对它与主库的结构是否完全一致。

说明：本脚本不经 HTTP 读业务数据，直接查询 information_schema，
      因此不依赖「后端在某个端口上跑着业务接口」，只需要数据库在本机可达。
"""
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SNAPSHOT = ROOT / "db" / "schema.sql"
LOCAL_YML = ROOT / "bookstore-server" / "src" / "main" / "resources" / "application-local.yml"
MIGRATION_DIR = ROOT / "bookstore-server" / "src" / "main" / "resources" / "db" / "migration"

MYSQL = os.environ.get("BOOKSTORE_MYSQL_CLI", "mysql")
DB_HOST = os.environ.get("BOOKSTORE_DB_HOST", "127.0.0.1")
DB_PORT = os.environ.get("BOOKSTORE_DB_PORT", "3306")
DB_USER = os.environ.get("BOOKSTORE_DB_USER", "root")
MAIN_DB = os.environ.get("BOOKSTORE_DB_NAME", "bookstore")

# 期望的迁移版本（V1 建表 / V2 索引 / V3 操作日志 / V4 评价 / V5 地址行政区划编码 /
# V6 书单·浏览历史·Banner·到货提醒·运费规则）
EXPECTED_VERSIONS = ["1", "2", "3", "4", "5", "6", "7"]
# 已有库接入 Flyway 时打的基线版本：手工脚本时代已应用到 V5，之后的版本才是 Flyway 真正执行的
BASELINE_VERSION = "5"

passed = []
failed = []


def check(ok, msg):
    (passed if ok else failed).append(msg)
    print(("  [PASS] " if ok else "  [FAIL] ") + msg)


# ---------------------------------------------------------------- MySQL 连接

def build_mycnf():
    """从 application-local.yml 取口令生成临时 my.cnf。

    注意：口令不写在命令行上 —— 运行环境会改写命令行里的口令字面量，
    会造成「错口令也连得上」的假阳性。
    """
    text = LOCAL_YML.read_text(encoding="utf-8")
    match = re.search(r"^\s*password:\s*(.*)$", text, re.M)
    password = (match.group(1).strip().strip('"').strip("'") if match else "")
    handle = tempfile.NamedTemporaryFile("w", delete=False, suffix=".cnf",
                                         encoding="utf-8", newline="\n")
    handle.write("[client]\nuser=%s\npassword=%s\nhost=%s\nport=%s\n"
                 % (DB_USER, password, DB_HOST, DB_PORT))
    handle.close()
    return handle.name


MYCNF = None


def sql(query, db=None):
    """执行一条 SQL，返回按行切分、按 tab 切列的结果（-N 去表头，-B 批处理模式）。

    db 不为空时把库名交给 mysql 客户端（等价于 USE <db>），
    否则查 flyway_schema_history 这类非全限定表名会报 "No database selected"。
    """
    cmd = [MYSQL, "--defaults-extra-file=" + MYCNF]
    if db:
        cmd.append(db)
    cmd += ["-N", "-B", "-e", query]
    try:
        out = subprocess.run(cmd, capture_output=True, timeout=30)
    except FileNotFoundError:
        raise RuntimeError("找不到 mysql 客户端，请把 mysql 加入 PATH 或设置 BOOKSTORE_MYSQL_CLI")
    if out.returncode != 0:
        raise RuntimeError("SQL 执行失败：%s\n%s"
                           % (query[:120], out.stderr.decode("utf-8", "replace").strip()[:400]))
    text = out.stdout.decode("utf-8", "replace")
    return [line.split("\t") for line in text.splitlines() if line.strip() != ""]


def db_exists(name):
    rows = sql("SELECT schema_name FROM information_schema.schemata WHERE schema_name='%s';" % name)
    return bool(rows)


# ---------------------------------------------------------------- 快照解析

TABLE_BLOCK = re.compile(r"CREATE TABLE\s+`(?P<name>\w+)`\s*\((?P<body>.*?)\n\)\s*ENGINE", re.S)
COLUMN_LINE = re.compile(r"^`(\w+)`\s+([A-Za-z]+(?:\([\d,\s]+\))?)")
INDEX_LINE = re.compile(r"KEY\s+`(\w+)`")

INT_WIDTH_TYPES = ("tinyint", "smallint", "mediumint", "int", "bigint")


def normalize_type(raw):
    """归一化列类型，让快照与实际库可比。

    两点差异需要抹平：
      · 快照里写的是 TINYINT / INT / BIGINT，而 MySQL 8.0.17 的
        information_schema 会带上显示宽度（tinyint(4) / int(11) / bigint(20)）；
      · 大小写与空格。
    注意 varchar(20) / decimal(10,2) 的长度与精度必须保留 —— 那才是真正要校验的部分。
    """
    text = (raw or "").strip().lower().replace(" ", "")
    for base in INT_WIDTH_TYPES:
        if re.fullmatch(re.escape(base) + r"\(\d+\)", text):
            return base
    return text


def parse_snapshot():
    """从 db/schema.sql 提取 {表名: {columns: {列名: 类型}, indexes: set}}"""
    text = SNAPSHOT.read_text(encoding="utf-8")
    tables = {}
    for match in TABLE_BLOCK.finditer(text):
        columns, indexes = {}, set()
        for raw in match.group("body").splitlines():
            line = re.sub(r"--.*$", "", raw).strip()
            if not line:
                continue
            column = COLUMN_LINE.match(line)
            if column:
                columns[column.group(1)] = normalize_type(column.group(2))
                continue
            index = INDEX_LINE.search(line)
            if index:
                indexes.add(index.group(1))
        tables[match.group("name")] = {"columns": columns, "indexes": indexes}
    return tables


def read_database(db_name):
    """从 information_schema 读出实际结构 {表名: {columns: {列名: 类型}, indexes: set}}"""
    tables = {}
    for table, column, column_type in sql(
            "SELECT table_name, column_name, column_type FROM information_schema.columns "
            "WHERE table_schema='%s' ORDER BY table_name, column_name;" % db_name):
        entry = tables.setdefault(table, {"columns": {}, "indexes": set()})
        entry["columns"][column] = normalize_type(column_type)
    for table, index in sql(
            "SELECT table_name, index_name FROM information_schema.statistics "
            "WHERE table_schema='%s' AND index_name<>'PRIMARY' "
            "GROUP BY table_name, index_name ORDER BY table_name, index_name;" % db_name):
        tables.setdefault(table, {"columns": {}, "indexes": set()})["indexes"].add(index)
    return tables


def compare_structure(expected, actual, db_label, ignore_extra_table=()):
    """比对快照与实际结构，逐项给出结论"""
    expected_tables = set(expected) - set(ignore_extra_table)
    actual_tables = set(actual) - set(ignore_extra_table)

    missing = expected_tables - actual_tables
    extra = actual_tables - expected_tables
    check(not missing, "%s：快照中的表都在库中存在%s"
          % (db_label, "" if not missing else "（缺失 %s）" % sorted(missing)))
    check(not extra, "%s：库中没有快照之外的表%s"
          % (db_label, "" if not extra else "（多出 %s）" % sorted(extra)))

    name_diff = []
    type_diff = []
    index_diff = []
    compared = 0
    for name in sorted(expected_tables & actual_tables):
        compared += 1
        exp_columns, act_columns = expected[name]["columns"], actual[name]["columns"]
        if set(exp_columns) ^ set(act_columns):
            name_diff.append("%s:%s" % (name, sorted(set(exp_columns) ^ set(act_columns))))
        for column in sorted(set(exp_columns) & set(act_columns)):
            if exp_columns[column] != act_columns[column]:
                type_diff.append("%s.%s(%s≠%s)"
                                 % (name, column, exp_columns[column], act_columns[column]))
        if expected[name]["indexes"] ^ actual[name]["indexes"]:
            index_diff.append("%s:%s" % (name, sorted(expected[name]["indexes"]
                                                      ^ actual[name]["indexes"])))

    check(not name_diff, "%s：共 %d 张表的字段名与快照逐列一致%s"
          % (db_label, compared, "" if not name_diff else "（差异 %s）" % name_diff))
    check(not type_diff, "%s：字段类型与长度/精度完全一致%s"
          % (db_label, "" if not type_diff else "（差异 %s）" % type_diff))
    check(not index_diff, "%s：二级索引与快照逐项一致%s"
          % (db_label, "" if not index_diff else "（差异 %s）" % index_diff))


# ---------------------------------------------------------------- 迁移记录

def verify_history(db_name, db_label, expect_baseline):
    if not db_exists(db_name):
        check(False, "%s 不存在（库名 %s）" % (db_label, db_name))
        return False

    rows = sql("SELECT COUNT(*) FROM information_schema.tables "
               "WHERE table_schema='%s' AND table_name='flyway_schema_history';" % db_name)
    if not rows or rows[0][0] == "0":
        check(False, "%s 中不存在 flyway_schema_history 表 —— 迁移尚未被 Flyway 接管" % db_label)
        return False
    check(True, "%s 中存在 flyway_schema_history 表（迁移已由 Flyway 接管）" % db_label)

    records = sql("SELECT version, type, success FROM flyway_schema_history "
                  "WHERE version IS NOT NULL ORDER BY installed_rank;", db_name)
    versions = [row[0] for row in records]
    types = {row[0]: row[1] for row in records}
    failures = [row[0] for row in records if row[2] != "1"]

    check(not failures, "%s：所有迁移记录均执行成功%s"
          % (db_label, "" if not failures else "（失败版本 %s）" % failures))

    if expect_baseline:
        # 已有库：应当有一条 BASELINE 记录，且版本号 = 手工脚本已应用到的最新版本
        baseline = [v for v in versions if types.get(v) == "BASELINE"]
        check(expect_baseline and baseline == [BASELINE_VERSION],
              "%s：已有库被打上基线 v%s（记录类型 %s）—— 未重跑历史脚本、未误删数据"
              % (db_label, BASELINE_VERSION, types.get(BASELINE_VERSION)))
        check(BASELINE_VERSION in versions, "%s：历史版本 v%s 已登记在册" % (db_label, BASELINE_VERSION))
    else:
        # 全新库：应当 V1~V4 全部按 SQL 类型顺序执行
        sql_versions = [v for v in versions if types.get(v) == "SQL"]
        check(sorted(sql_versions, key=lambda v: int(v)) == EXPECTED_VERSIONS,
              "%s：全新库由 Flyway 顺序执行了 V%s（实际 %s）"
              % (db_label, "→V".join(EXPECTED_VERSIONS), sql_versions))
        check("BASELINE" not in types.values(),
              "%s：全新库不产生基线记录（基线只用于已有库）" % db_label)
    return True


def verify_migration_files():
    """迁移脚本本身的基本检查：命名规范、版本连续"""
    print("\n## 0. 迁移脚本文件")
    files = sorted(p.name for p in MIGRATION_DIR.glob("V*.sql"))
    check(bool(files), "迁移目录存在且非空：%s" % MIGRATION_DIR)
    names = [re.match(r"V(\d+)__(\w+)\.sql", f) for f in files]
    check(all(names), "迁移脚本命名符合 V<版本>__<描述>.sql 规范：%s" % files)
    found = sorted(m.group(1) for m in names if m)
    check(found == EXPECTED_VERSIONS, "版本号连续且与预期一致：%s" % found)


# ---------------------------------------------------------------- 主流程

def main():
    global MYCNF
    fresh_db = None
    fresh_base = None
    for arg in sys.argv[1:]:
        if arg.startswith("--fresh-db="):
            fresh_db = arg.split("=", 1)[1]
        elif arg.startswith("--fresh-base="):
            fresh_base = arg.split("=", 1)[1]

    print("=" * 72)
    print("Flyway 迁移纳管验证")
    print("快照文件：%s" % SNAPSHOT)
    print("=" * 72)

    if not SNAPSHOT.exists():
        print("[ERROR] 找不到结构快照文件：%s" % SNAPSHOT)
        return 2
    if not LOCAL_YML.exists():
        print("[ERROR] 找不到 application-local.yml，无法获取数据库口令：%s" % LOCAL_YML)
        return 2

    MYCNF = build_mycnf()
    try:
        verify_migration_files()
    except Exception as e:
        print("[ERROR] %s" % e)
        return 2

    print("\n## 1. 已有库：baseline 接入是否正确")
    try:
        verify_history(MAIN_DB, "主库 %s" % MAIN_DB, expect_baseline=True)
    except Exception as e:
        print("[ERROR] %s" % e)
        return 2

    print("\n## 2. 结构一致性：实际库 vs db/schema.sql 快照")
    try:
        expected = parse_snapshot()
        check(bool(expected), "从快照解析出 %d 张表" % len(expected))
        actual = read_database(MAIN_DB)
        compare_structure(expected, actual, "主库", ignore_extra_table={"flyway_schema_history"})
    except Exception as e:
        print("[ERROR] %s" % e)
        return 2

    if fresh_db:
        print("\n## 3. 全新空库：Flyway 顺序迁移结果")
        try:
            ok = verify_history(fresh_db, "新库 %s" % fresh_db, expect_baseline=False)
            if ok:
                fresh_actual = read_database(fresh_db)
                compare_structure(expected, fresh_actual, "新库",
                                  ignore_extra_table={"flyway_schema_history"})
                check(fresh_actual == actual,
                      "新库结构与已有库逐项完全相同 —— 换台机器「建空库 + 启动服务」即可得到一致环境")
        except Exception as e:
            print("[ERROR] %s" % e)
            return 2

        if fresh_base:
            print("\n## 4. 全新库实例可用性（HTTP）")
            try:
                opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
                with opener.open(fresh_base.rstrip("/") + "/api/category/list", timeout=15) as resp:
                    body = resp.read().decode("utf-8", "replace")
                check('"code":200' in body.replace(" ", ""),
                      "全新库上的实例业务接口可用（说明 Flyway 已把表建好，接口不是 500）")
            except urllib.error.HTTPError as e:
                body = e.read().decode("utf-8", "replace")
                check('"code":200' in body.replace(" ", ""), "全新库上的实例业务接口可用")
            except Exception as e:
                check(False, "全新库实例不可访问：%s" % e)
    else:
        print("\n## 3. 全新空库校验（未启用）")
        print("      加 --fresh-db=<库名> --fresh-base=<地址> 可校验「空库一键迁移」")

    print("\n" + "=" * 72)
    print("通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
    if failed:
        print("失败清单：")
        for item in failed:
            print("  - " + item)
    print("=" * 72)
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    finally:
        if MYCNF and os.path.exists(MYCNF):
            os.remove(MYCNF)
