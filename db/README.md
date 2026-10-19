# db 目录说明

## 一、权威来源：Flyway 迁移脚本

数据库结构变更的**唯一权威来源**是后端工程内的迁移脚本：

```
bookstore-server/src/main/resources/db/migration/
├── V1__init_schema.sql             基础 8 张表（user / admin / category / book / cart / address / orders / order_item）
├── V2__book_filter_indexes.sql     book 表的 idx_book_status_cate_price、idx_book_sales
├── V3__operation_log.sql           管理端操作日志表
├── V4__book_review.sql             图书评价表
└── V5__address_adcode_columns.sql  address 表补 province/city/district_adcode 三列
```

服务启动时 Flyway 会自动比对并执行数据库中缺失的版本，因此**部署只需两步**：

```bash
# 1. 建一个空库（不建表）
mysql --defaults-extra-file=<配置文件> -e \
  "CREATE DATABASE IF NOT EXISTS bookstore DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
# 2. 启动后端，Flyway 自动建好全部表
cd bookstore-server && mvn spring-boot:run
```

启动日志里能看到 `Successfully applied N migrations` 或 `Schema "bookstore" is up to date`。

### 已有数据库怎么办（baseline 机制）

历史库（演示库、生产库）的表是早期用 `db/schema.sql` + `db/legacy/migration_v*.sql`
手工建的，没有 `flyway_schema_history` 表。若不做处理，Flyway 会误判为「空库」并从 V1 执行，
撞上「表已存在」直接启动失败。

`application.yml` 中因此配置了：

| 配置 | 值 | 作用 |
| --- | --- | --- |
| `spring.flyway.baseline-on-migrate` | `true` | 非空库自动打基线，不重跑历史脚本 |
| `spring.flyway.baseline-version` | `5` | 基线版本 = 手工脚本已应用到的最新版本（含 adcode 三列） |
| `spring.flyway.validate-on-migrate` | `true` | 历史脚本被人改动时立刻失败，不静默放过 |
| `spring.flyway.clean-disabled` | `true` | 禁止 `flyway clean`（会清空整库） |

**全新空库**不受 baseline 影响，仍从 V1 顺序执行到最新版本。

## 二、本目录文件

| 文件 | 用途 |
| --- | --- |
| `../db/schema.sql` | **完整结构快照**，便于人工查阅与论文引用；不是执行入口，变更请改 Flyway 脚本后同步 |
| `seed.sql` | 最小演示数据（账号 + 少量图书分类），用于干净起步 |
| `mock_dataset.sql` | 完整演示数据集：9 个一级分类 / 48 个二级分类 / 102 本图书（会清空 book、category 及其依赖的订单/购物车/评价数据，账号保留） |
| `backup_before_mock_20261017.sql` | 导入演示数据集前的备份 |
| `legacy/` | 历史手工迁移脚本，**已全部迁入 Flyway，仅供查阅**，不要再执行 |

## 三、演示数据导入

结构由 Flyway 负责，**数据**仍需手工导入：

```bash
# 完整演示数据集（推荐用于答辩）
mysql --defaults-extra-file=<配置文件> bookstore < db/mock_dataset.sql

# 或最小数据集
mysql --defaults-extra-file=<配置文件> bookstore < db/seed.sql
```

## 四、一致性校验

```bash
# 已有库：校验快照 vs 实际结构、迁移历史与 baseline 记录
python tools/verify_flyway_migration.py

# 全新空库：额外校验「建空库 + 启动服务」后的结构与已有一致
#   （需先建空库，并用 --spring.datasource.url= 指向它启动第二个实例）
python tools/verify_flyway_migration.py --fresh-db=bookstore_flyway_test --fresh-base=http://127.0.0.1:8082
```

比对「`db/schema.sql` 快照」与「Flyway 迁移实际建出的结构」，逐表/逐列/逐索引/逐字段类型长度精度比对，
两者不一致时列出差异，防止快照与迁移脚本长期漂移。
