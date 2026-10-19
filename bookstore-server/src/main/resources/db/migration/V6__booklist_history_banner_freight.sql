-- =====================================================================
-- V6：书单 / 浏览历史 / 首页 Banner / 到货提醒 / 运费规则
--
-- 背景：PC 商城此前有六项功能没有后端支撑 ——
--   「书单、浏览历史、图书对比」数据只存在浏览器 localStorage 里，
--   「首页 Banner」是三条写死在页面里的渐变，
--   「免运费」「商品目录」则完全没有实现。
--   本次把这些能力落到数据库，使「换台电脑数据还在」「后台可维护」成立。
--
-- 设计要点：
--   1) book_list / book_list_item：书单与书单条目分两张表。条目表用
--      (list_id, book_id) 唯一键在数据库层兜住「同一本书在一个书单里只能出现一次」，
--      业务层再查一次只是为了给友好提示，不依赖应用层判断正确性；
--      另存 book_count 冗余计数，让书单列表页不必逐条 count。
--   2) browse_history：唯一键 (user_id, book_id) 让「重复浏览」退化为一次 upsert，
--      天然去重；按 (user_id, view_time) 建索引服务「我的浏览历史倒序」这一主路径。
--      不做物理上限约束 —— 100 条上限由应用层在写入后裁剪，便于将来调整而不用改表。
--   3) banner：image_url 为空时前端回退渲染 bg_color 渐变，这样演示库不必先上传图片
--      就能有可看的首页；运营补图后自动优先用图片，不需要改前端代码。
--   4) stock_notice：到货提醒登记表。它真正的价值在管理端 ——
--      「这本书有多少人在等」直接指导补货优先级，属于可讲清楚业务意义的真实功能。
--   5) sys_config：极简 key-value 配置表。运费规则（开关/包邮门槛/基础运费）
--      放这里而不是只放 yml，是为了让管理端真的能改，而不是换个地方写死。
--      editable=0 的行表示「只读展示」，避免把 JWT 密钥之类的启动期配置也搬进来。
--   6) orders 补三列：
--      - freight_amount：运费金额。刻意<b>不</b>把 total_amount 改成「含运费的总价」——
--        存量订单的 total_amount 语义是商品小计，改写语义会让历史数据对不上账。
--        因此应付金额 = total_amount + freight_amount，由应用层相加得出。
--      - ship_time / finish_time：订单四节点时间线需要真实的发货与完成时刻。
--        此前只能用 update_time 近似，而 update_time 会被任何一次无关更新刷新，
--        时间线上会出现「发货时间晚于收货时间」这类看起来像 bug 的展示。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 书单主表
-- ---------------------------------------------------------------------
CREATE TABLE `book_list`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT       NOT NULL COMMENT '创建者用户ID（业务外键 -> user.id）',
    `name`        VARCHAR(50)  NOT NULL COMMENT '书单名称',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '书单简介',
    `cover_url`   VARCHAR(500) DEFAULT NULL COMMENT '封面图地址（为空时前端按首本书封面兜底）',
    `is_public`   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否公开：1-公开 0-仅自己可见',
    `book_count`  INT          NOT NULL DEFAULT 0 COMMENT '书单内图书数量（冗余计数，列表页免 count）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 书单列表页主路径：公开书单按创建时间倒序
    KEY `idx_book_list_public_time` (`is_public`, `create_time`),
    -- 「我的书单」
    KEY `idx_book_list_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户书单';

-- ---------------------------------------------------------------------
-- 2. 书单条目表
-- ---------------------------------------------------------------------
CREATE TABLE `book_list_item`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `list_id`     BIGINT   NOT NULL COMMENT '书单ID（业务外键 -> book_list.id）',
    `book_id`     BIGINT   NOT NULL COMMENT '图书ID（业务外键 -> book.id）',
    `sort`        INT      NOT NULL DEFAULT 0 COMMENT '书单内排序值，越小越靠前',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    -- 同一书单内同一本书只能出现一次（重复加书时应用层据此给出「已在书单中」提示）
    UNIQUE KEY `uk_book_list_item` (`list_id`, `book_id`),
    -- 反向查询：某本书被哪些书单收录
    KEY `idx_book_list_item_book` (`book_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '书单条目';

-- ---------------------------------------------------------------------
-- 3. 浏览历史
-- ---------------------------------------------------------------------
CREATE TABLE `browse_history`
(
    `id`        BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`   BIGINT   NOT NULL COMMENT '用户ID（业务外键 -> user.id）',
    `book_id`   BIGINT   NOT NULL COMMENT '图书ID（业务外键 -> book.id）',
    `view_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次浏览时间',
    PRIMARY KEY (`id`),
    -- 唯一键把「重复浏览同一本书」变成一次 upsert：不用先查再插，也不会产生重复行
    UNIQUE KEY `uk_browse_history_user_book` (`user_id`, `book_id`),
    -- 主查询路径：我的浏览历史按时间倒序
    KEY `idx_browse_history_user_time` (`user_id`, `view_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户浏览历史';

-- ---------------------------------------------------------------------
-- 4. 首页 Banner
-- ---------------------------------------------------------------------
CREATE TABLE `banner`
(
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
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '首页轮播图';

-- 初始数据：把原先写死在 home/index.vue 里的三条渐变搬进库，
-- 迁移后首页观感保持不变，但从此可以在管理端增删改。
INSERT INTO `banner` (`title`, `sub_title`, `image_url`, `bg_color`, `link_url`, `sort`, `status`)
VALUES ('新书上架', '海量新书 抢先阅读', NULL, 'linear-gradient(135deg,#667eea,#764ba2)', '/books?sort=default', 1, 1),
       ('低价优选', '精选好书 低至5折', NULL, 'linear-gradient(135deg,#f093fb,#f5576c)', '/books?sort=price_asc', 2, 1),
       ('畅销榜单', '热门好书 不容错过', NULL, 'linear-gradient(135deg,#4facfe,#00f2fe)', '/books?sort=sales', 3, 1);

-- ---------------------------------------------------------------------
-- 5. 到货提醒（缺货登记）
-- ---------------------------------------------------------------------
CREATE TABLE `stock_notice`
(
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
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '缺货到货提醒订阅';

-- ---------------------------------------------------------------------
-- 6. 系统配置（key-value）
-- ---------------------------------------------------------------------
CREATE TABLE `sys_config`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key`   VARCHAR(64)  NOT NULL COMMENT '配置键（如 freight.free-threshold）',
    `config_value` VARCHAR(200) NOT NULL COMMENT '配置值（统一按字符串存，读取方自行转换）',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '配置说明（管理端展示，便于运营理解）',
    `editable`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否允许在管理端修改：1-可改 0-只读展示',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_config_key` (`config_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '系统配置';

-- 初始数据：运费规则。
-- 值为数据库配置，yml 中的 bookstore.freight.* 仅作为「库里读不到时的兜底默认值」，
-- 保证配置表被清空也不会让下单流程报错。
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `editable`)
VALUES ('freight.enabled', '1', '是否收取运费：1-收取 0-全场包邮', 1),
       ('freight.free-threshold', '99', '满多少元包邮（元）', 1),
       ('freight.base-fee', '8.00', '未达包邮门槛时的运费（元）', 1);

-- ---------------------------------------------------------------------
-- 7. orders 补列
-- ---------------------------------------------------------------------
ALTER TABLE `orders`
    ADD COLUMN `freight_amount` DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '运费金额（存量订单一律为 0，语义上视为「当时包邮」）' AFTER `total_amount`,
    ADD COLUMN `ship_time`      DATETIME DEFAULT NULL COMMENT '发货时间' AFTER `pay_time`,
    ADD COLUMN `finish_time`    DATETIME DEFAULT NULL COMMENT '完成时间（用户确认收货）' AFTER `ship_time`;
