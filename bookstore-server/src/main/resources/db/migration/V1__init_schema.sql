-- =====================================================================
-- V1：初始建表（线上书店 APP，MySQL 8.0）
--
-- 迁移历史说明：本脚本即项目初期手工执行的 `db/schema.sql`。
-- 纳入 Flyway 后，它只在「全新空库」上执行一次；已经建好表的库会在
-- 启动时被 baseline 到版本 4（见 application.yml 的 spring.flyway.baseline-version），
-- 因此不会重复执行、也不会误删已有数据。
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
--    （「上架 + 分类 + 价格区间」与「销量」两个索引由 V2 补建，本版本不含）
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

SET FOREIGN_KEY_CHECKS = 1;
