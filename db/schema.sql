-- =====================================================================
-- 线上书店 APP —— MySQL 8.0 数据库结构快照（完整版）
--
-- 【本文件的定位】
--   这是一份「最终结构」的可读快照，便于人工查阅、写论文和手工比对。
--   ⚠ 结构变更的**唯一权威来源**是后端工程内的迁移脚本：
--       bookstore-server/src/main/resources/db/migration/V*.sql
--   服务启动时由 Flyway 自动执行缺失的版本，**不需要**再手工跑本文件。
--   改了迁移脚本后请同步本文件，并用 tools/verify_flyway_migration.py 校验两者一致。
--
-- 【版本对应关系】
--   V1__init_schema.sql          本文件第 1~8 节（基础 8 张表）
--   V2__book_filter_indexes.sql  book 表的 idx_book_status_cate_price / idx_book_sales
--   V3__operation_log.sql        本文件第 9 节
--   V4__book_review.sql          本文件第 10 节
--   V5__address_adcode_columns.sql  address 表的 province_adcode / city_adcode / district_adcode
--   历史手工脚本已归档至 db/legacy/
--
-- 【手工建库（可选，正常不需要）】
--   推荐做法：只要执行
--     CREATE DATABASE IF NOT EXISTS bookstore DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
--   然后启动后端服务，Flyway 会自动建好全部表。
--   若确实要手工执行本文件，请把上文四段迁移的效果一并理解后再执行，避免漏表。
--
-- 设计约定：
--   1) 全部使用「业务外键」，不建数据库物理外键，避免级联锁与迁移困难；
--   2) 金额统一 DECIMAL(10,2)，不用 FLOAT/DOUBLE，避免精度丢失；
--   3) 删除一律「逻辑删除」，表内保留 create_time / update_time 审计字段；
--   4) 订单保存收货地址快照，避免用户改地址后历史订单信息被污染。
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. 用户表 user：APP 移动端注册用户
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `phone`       VARCHAR(20)  NOT NULL                COMMENT '手机号（登录账号，唯一）',
    `password`    VARCHAR(64)  NOT NULL                COMMENT '密码密文：BCrypt（新数据）或历史 MD5(明文 + salt)',
    `salt`        VARCHAR(32)  NOT NULL                COMMENT '历史遗留盐值，BCrypt 数据下为空串',
    `nickname`    VARCHAR(50)  DEFAULT NULL            COMMENT '用户昵称',
    `avatar_url`  VARCHAR(500) DEFAULT NULL            COMMENT '头像地址',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1-正常 0-禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_phone` (`phone`),
    KEY `idx_user_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'APP 用户表';

-- ---------------------------------------------------------------------
-- 2. 管理员表 admin：Web 管理后台账号（与用户表分开，后台仅单一管理员角色）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `admin`;
CREATE TABLE `admin` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`   VARCHAR(50) NOT NULL                COMMENT '管理员账号（唯一）',
    `password`   VARCHAR(64) NOT NULL                COMMENT '密码密文：BCrypt（新数据）或历史 MD5(明文 + salt)',
    `salt`       VARCHAR(32) NOT NULL                COMMENT '历史遗留盐值，BCrypt 数据下为空串',
    `real_name`  VARCHAR(50) DEFAULT NULL            COMMENT '真实姓名',
    `status`     TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：1-正常 0-禁用',
    `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '管理员表';

-- ---------------------------------------------------------------------
-- 3. 图书分类表 category
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`        VARCHAR(50) NOT NULL                COMMENT '分类名称，如：文学 / 计算机 / 历史',
    `sort`        INT         NOT NULL DEFAULT 0      COMMENT '排序值，越大越靠前',
    `remark`      VARCHAR(200) DEFAULT NULL           COMMENT '备注',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_name` (`name`),
    KEY `idx_category_sort` (`sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '图书分类表';

-- ---------------------------------------------------------------------
-- 4. 图书表 book
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `book`;
CREATE TABLE `book` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category_id` BIGINT        DEFAULT NULL            COMMENT '所属分类ID（业务外键 -> category.id）',
    `title`       VARCHAR(200)  NOT NULL                COMMENT '书名',
    `author`      VARCHAR(100)  DEFAULT NULL            COMMENT '作者',
    `publisher`   VARCHAR(100)  DEFAULT NULL            COMMENT '出版社',
    `price`       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '单价',
    `stock`       INT           NOT NULL DEFAULT 0      COMMENT '库存数量',
    `cover_url`   VARCHAR(500)  DEFAULT NULL            COMMENT '封面图地址',
    `description` TEXT          DEFAULT NULL            COMMENT '图书简介',
    `sales`       INT           NOT NULL DEFAULT 0      COMMENT '销量',
    `status`      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1-上架 0-下架',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_book_category` (`category_id`),
    KEY `idx_book_status` (`status`),
    -- 组合索引：覆盖「上架 + 指定分类 + 价格区间」这一最典型的前台筛选组合，
    -- 顺序按选择性从高到低（status 只有 2 个值放最左是因为它与查询强绑定且能立即收敛范围）
    KEY `idx_book_status_cate_price` (`status`, `category_id`, `price`),
    KEY `idx_book_sales` (`sales`),
    KEY `idx_book_title` (`title`),
    FULLTEXT KEY `ft_book_search` (`title`, `author`)  -- 书名/作者全文模糊检索（ngram 分词）
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '图书表';

-- ---------------------------------------------------------------------
-- 5. 购物车表 cart：同一用户对同一本书只保留一条记录，重复加入则累加数量
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `cart`;
CREATE TABLE `cart` (
    `id`          BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT  NOT NULL                COMMENT '用户ID（业务外键 -> user.id）',
    `book_id`     BIGINT  NOT NULL                COMMENT '图书ID（业务外键 -> book.id）',
    `quantity`    INT     NOT NULL DEFAULT 1      COMMENT '购买数量',
    `checked`     TINYINT NOT NULL DEFAULT 1      COMMENT '是否勾选：1-已选 0-未选',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cart_user_book` (`user_id`, `book_id`),
    KEY `idx_cart_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '购物车表';

-- ---------------------------------------------------------------------
-- 6. 收货地址表 address
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `address`;
CREATE TABLE `address` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT      NOT NULL                COMMENT '用户ID（业务外键 -> user.id）',
    `receiver`    VARCHAR(50) NOT NULL                COMMENT '收件人姓名',
    `phone`       VARCHAR(20) NOT NULL                COMMENT '联系电话',
    `province`    VARCHAR(50) DEFAULT NULL            COMMENT '省',
    `city`        VARCHAR(50) DEFAULT NULL            COMMENT '市',
    `district`    VARCHAR(50) DEFAULT NULL            COMMENT '区/县',
    `detail`      VARCHAR(255) NOT NULL               COMMENT '详细地址',
    `is_default`  TINYINT     NOT NULL DEFAULT 0      COMMENT '是否默认地址：1-默认 0-普通',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    -- 行政区划编码（对应迁移 V5）：保存省/市/区的 adcode，供高德地理编码使用
    `province_adcode` VARCHAR(12) DEFAULT NULL        COMMENT '省份 adcode 编码',
    `city_adcode`     VARCHAR(12) DEFAULT NULL        COMMENT '城市 adcode 编码',
    `district_adcode` VARCHAR(12) DEFAULT NULL        COMMENT '区县 adcode 编码',
    PRIMARY KEY (`id`),
    KEY `idx_address_user` (`user_id`),
    KEY `idx_address_default` (`user_id`, `is_default`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '收货地址表';

-- ---------------------------------------------------------------------
-- 7. 订单主表 orders：订单状态 1待支付 2已支付 3已发货 4已完成 5已取消
--    说明：address_snapshot 保存下单时的地址快照（JSON），用户改地址不影响历史订单
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no`         VARCHAR(32)   NOT NULL                COMMENT '订单编号（唯一，前端展示用）',
    `user_id`          BIGINT        NOT NULL                COMMENT '下单用户ID（业务外键 -> user.id）',
    `total_amount`     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '订单总金额',
    `status`           TINYINT       NOT NULL DEFAULT 1      COMMENT '订单状态：1-待支付 2-已支付 3-已发货 4-已完成 5-已取消',
    `address_snapshot` VARCHAR(500)  DEFAULT NULL            COMMENT '收货地址快照：收件人 / 电话 / 完整地址',
    `pay_time`         DATETIME      DEFAULT NULL            COMMENT '支付时间（模拟支付写入）',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_no` (`order_no`),
    KEY `idx_orders_user` (`user_id`),
    KEY `idx_orders_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单主表';

-- ---------------------------------------------------------------------
-- 8. 订单明细表 order_item：冗余书名/封面/单价，保证历史订单可还原
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id`    BIGINT        NOT NULL                COMMENT '订单ID（业务外键 -> orders.id）',
    `book_id`     BIGINT        NOT NULL                COMMENT '图书ID（业务外键 -> book.id）',
    `book_title`  VARCHAR(200)  NOT NULL                COMMENT '书名快照',
    `book_cover`  VARCHAR(500)  DEFAULT NULL            COMMENT '封面快照',
    `price`       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '下单时单价快照',
    `quantity`    INT           NOT NULL DEFAULT 1      COMMENT '购买数量',
    `subtotal`    DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '小计金额 = price * quantity',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_item_order` (`order_id`),
    KEY `idx_order_item_book` (`book_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单明细表';

-- ---------------------------------------------------------------------
-- 9. 管理端操作日志表 operation_log（对应迁移 V3）
--    配合 AOP 切面把管理端写操作结构化落库：谁、什么时候、对哪个模块做了什么、成功与否
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `admin_id`    BIGINT       DEFAULT NULL COMMENT '操作管理员ID（业务外键 -> admin.id，不可变标识）',
    `admin_name`  VARCHAR(64)  DEFAULT NULL COMMENT '操作人名称快照（管理员真实姓名；名称可能被修改，故同时保留 admin_id）',
    `module`      VARCHAR(64)  DEFAULT NULL COMMENT '业务模块，如「图书管理」',
    `action`      VARCHAR(64)  DEFAULT NULL COMMENT '操作动作，如「新增图书」',
    `http_method` VARCHAR(10)  DEFAULT NULL COMMENT 'HTTP 方法',
    `uri`         VARCHAR(255) DEFAULT NULL COMMENT '请求路径',
    `params`      VARCHAR(2000) DEFAULT NULL COMMENT '请求参数（敏感字段已脱敏，超长截断）',
    `ip`          VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    `duration_ms` BIGINT       DEFAULT NULL COMMENT '耗时（毫秒）',
    `success`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：1-成功 0-失败',
    `error_msg`   VARCHAR(500) DEFAULT NULL COMMENT '失败原因（仅失败时记录）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_oplog_create_time` (`create_time`),
    KEY `idx_oplog_module` (`module`),
    KEY `idx_oplog_admin` (`admin_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '管理端操作日志';

-- ---------------------------------------------------------------------
-- 10. 图书评价表 review（对应迁移 V4）
--     唯一键 (user_id, book_id, order_id) 保证「同一订单同一本书只评一次」，
--     并用 order_id 把评价与真实购买绑定，从数据层面排除刷评
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `book_id`     BIGINT       NOT NULL COMMENT '图书ID（业务外键 -> book.id）',
    `user_id`     BIGINT       NOT NULL COMMENT '评价用户ID（业务外键 -> user.id）',
    `order_id`    BIGINT       NOT NULL COMMENT '来源订单ID，保证「只有买过才能评」',
    `rating`      TINYINT      NOT NULL COMMENT '星级评分 1~5',
    `content`     VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
    `anonymous`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否匿名：1-匿名 0-显示昵称',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已隐藏（后台下架违规评价）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_review_user_book_order` (`user_id`, `book_id`, `order_id`),
    KEY `idx_review_book_status_time` (`book_id`, `status`, `create_time`),
    KEY `idx_review_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '图书评价';

SET FOREIGN_KEY_CHECKS = 1;
