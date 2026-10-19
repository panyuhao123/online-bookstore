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
--   V6__booklist_history_banner_freight.sql  本文件第 11~16 节 + orders 表补 freight_amount / ship_time / finish_time
--   V7__book_list_status.sql     book_list 表补 status（平台治理）与 idx_book_list_status_time
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
--    金额口径：total_amount 是**商品小计**（不含运费），应付 = total_amount + freight_amount
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no`         VARCHAR(32)   NOT NULL                COMMENT '订单编号（唯一，前端展示用）',
    `user_id`          BIGINT        NOT NULL                COMMENT '下单用户ID（业务外键 -> user.id）',
    `total_amount`     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '商品小计（各明细之和，不含运费）',
    -- 对应迁移 V6：存量订单一律为 0，语义上视为「当时包邮」，无需数据迁移
    `freight_amount`   DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '运费金额；应付合计 = total_amount + freight_amount',
    `status`           TINYINT       NOT NULL DEFAULT 1      COMMENT '订单状态：1-待支付 2-已支付 3-已发货 4-已完成 5-已取消',
    `address_snapshot` VARCHAR(500)  DEFAULT NULL            COMMENT '收货地址快照：收件人 / 电话 / 完整地址',
    `pay_time`         DATETIME      DEFAULT NULL            COMMENT '支付时间（模拟支付写入）',
    -- 对应迁移 V6：订单四节点时间线需要真实的发货 / 完成时刻，
    -- 用 update_time 近似会被任何一次无关更新刷新，时间线上会出现「发货晚于收货」这类假 bug
    `ship_time`        DATETIME      DEFAULT NULL            COMMENT '发货时间（管理端发货写入）',
    `finish_time`      DATETIME      DEFAULT NULL            COMMENT '完成时间（用户确认收货写入）',
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

-- ---------------------------------------------------------------------
-- 11. 用户书单表 book_list（对应迁移 V6；status 列由 V7 追加）
--     此前书单只存在浏览器 localStorage 里，换设备就丢；落库后可跨端、可分享、可管理
--     is_public 是「用户自己的公开/私密」，status 是「平台是否隐藏」，两者语义正交
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `book_list`;
CREATE TABLE `book_list` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT       NOT NULL COMMENT '创建者用户ID（业务外键 -> user.id）',
    `name`        VARCHAR(50)  NOT NULL COMMENT '书单名称（同一用户内不重名）',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '书单简介',
    `cover_url`   VARCHAR(500) DEFAULT NULL COMMENT '封面图地址（为空时前端按首本书封面兜底）',
    `is_public`   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否公开：1-公开 0-仅自己可见',
    `book_count`  INT          NOT NULL DEFAULT 0 COMMENT '书单内图书数量（冗余计数，列表页免 count）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已隐藏（平台治理用，与 is_public 语义正交）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 书单广场主路径：公开书单按创建时间倒序
    KEY `idx_book_list_public_time` (`is_public`, `create_time`),
    -- 「我的书单」
    KEY `idx_book_list_user` (`user_id`),
    -- 管理端治理主路径：按状态筛选 + 创建时间倒序
    KEY `idx_book_list_status_time` (`status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户书单';

-- ---------------------------------------------------------------------
-- 12. 书单条目表 book_list_item（对应迁移 V6）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `book_list_item`;
CREATE TABLE `book_list_item` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `list_id`     BIGINT   NOT NULL COMMENT '书单ID（业务外键 -> book_list.id）',
    `book_id`     BIGINT   NOT NULL COMMENT '图书ID（业务外键 -> book.id）',
    `sort`        INT      NOT NULL DEFAULT 0 COMMENT '书单内排序值，越小越靠前',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    -- 数据库层兜住「同一本书在一个书单里只能出现一次」，应用层判断只用于给友好提示
    UNIQUE KEY `uk_book_list_item` (`list_id`, `book_id`),
    -- 反向查询：某本书被哪些书单收录
    KEY `idx_book_list_item_book` (`book_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '书单条目';

-- ---------------------------------------------------------------------
-- 13. 浏览历史表 browse_history（对应迁移 V6）
--     唯一键让「重复浏览」退化为一次 upsert；100 条上限由应用层写入后裁剪，便于调整
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `browse_history`;
CREATE TABLE `browse_history` (
    `id`        BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`   BIGINT   NOT NULL COMMENT '用户ID（业务外键 -> user.id）',
    `book_id`   BIGINT   NOT NULL COMMENT '图书ID（业务外键 -> book.id）',
    `view_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次浏览时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_browse_history_user_book` (`user_id`, `book_id`),
    -- 主查询路径：我的浏览历史按时间倒序
    KEY `idx_browse_history_user_time` (`user_id`, `view_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户浏览历史';

-- ---------------------------------------------------------------------
-- 14. 首页轮播图表 banner（对应迁移 V6）
--     image_url 为空时前端回退渲染 bg_color 渐变，演示库不必先上传图片就有可看的首页
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `banner`;
CREATE TABLE `banner` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title`       VARCHAR(50)  NOT NULL COMMENT '主标题',
    `sub_title`   VARCHAR(100) DEFAULT NULL COMMENT '副标题/描述',
    `image_url`   VARCHAR(500) DEFAULT NULL COMMENT '背景图地址；为空时前端回退用 bg_color',
    `bg_color`    VARCHAR(120) DEFAULT NULL COMMENT '无图时的背景色/渐变（如 linear-gradient(...)）',
    `link_url`    VARCHAR(200) DEFAULT NULL COMMENT '点击跳转路径（站内 hash 路径或完整外链）',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-上架 0-下架',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 前台唯一查询路径：只取上架轮播，且按排序值升序
    KEY `idx_banner_status_sort` (`status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '首页轮播图';

-- 初始数据：把原先写死在 home/index.vue 里的三条渐变搬进库，迁移后首页观感不变但从此可维护
INSERT INTO `banner` (`title`, `sub_title`, `image_url`, `bg_color`, `link_url`, `sort`, `status`)
VALUES ('新书上架', '海量新书 抢先阅读', NULL, 'linear-gradient(135deg,#667eea,#764ba2)', '/books?sort=default', 1, 1),
       ('低价优选', '精选好书 低至5折', NULL, 'linear-gradient(135deg,#f093fb,#f5576c)', '/books?sort=price_asc', 2, 1),
       ('畅销榜单', '热门好书 不容错过', NULL, 'linear-gradient(135deg,#4facfe,#00f2fe)', '/books?sort=sales', 3, 1);

-- ---------------------------------------------------------------------
-- 15. 到货提醒（缺货登记）表 stock_notice（对应迁移 V6）
--     价值在管理端：「这本书有多少人在等」直接指导补货优先级
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `stock_notice`;
CREATE TABLE `stock_notice` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT   NOT NULL COMMENT '订阅用户ID（业务外键 -> user.id）',
    `book_id`     BIGINT   NOT NULL COMMENT '图书ID（业务外键 -> book.id）',
    `status`      TINYINT  NOT NULL DEFAULT 0 COMMENT '状态：0-待通知 1-已通知 2-已取消',
    `notify_time` DATETIME DEFAULT NULL COMMENT '通知时间（管理端标记已通知时写入）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
    PRIMARY KEY (`id`),
    -- 同一用户对同一本书只留一条订阅记录；取消后重新订阅复用该行（status 改回 0）
    UNIQUE KEY `uk_stock_notice_user_book` (`user_id`, `book_id`),
    -- 管理端看板主路径：按图书聚合待通知人数
    KEY `idx_stock_notice_book_status` (`book_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '缺货到货提醒订阅';

-- ---------------------------------------------------------------------
-- 16. 系统配置表 sys_config（对应迁移 V6）
--     极简 key-value 表。运费规则放这里而不是只放 yml，是为了让管理端真的能改；
--     editable=0 的行表示「只读展示」，避免把 JWT 密钥之类的启动期配置也搬进来。
--     刻意不做缓存：个位数量级的小表，用一次查询换掉「后台改了前台还读旧值」的一整类一致性问题。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key`   VARCHAR(64)  NOT NULL COMMENT '配置键（如 freight.free-threshold）',
    `config_value` VARCHAR(200) NOT NULL COMMENT '配置值（统一按字符串存，读取方自行转换）',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '配置说明（管理端展示，便于运营理解）',
    `editable`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否允许在管理端修改：1-可改 0-只读展示',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_config_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统配置';

-- 初始数据：运费规则。yml 中的 bookstore.freight.* 仅作为「库里读不到时的兜底默认值」，
-- 保证配置表被清空也不会让下单流程报错。
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `editable`)
VALUES ('freight.enabled', '1', '是否收取运费：1-收取 0-全场包邮', 1),
       ('freight.free-threshold', '99', '满多少元包邮（元）', 1),
       ('freight.base-fee', '8.00', '未达包邮门槛时的运费（元）', 1);

SET FOREIGN_KEY_CHECKS = 1;
