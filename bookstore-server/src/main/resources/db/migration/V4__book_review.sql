-- =====================================================================
-- V4：图书评价模块
--
-- 背景：原始需求阶段决定不做评论以控制工作量；后按要求补充「图书评论 + 星级评分」。
--
-- 设计要点：
--   1) 只有「已完成」订单里的图书才能评价 —— 用 order_id 把评价与真实购买绑定，
--      从数据层面排除刷评（没有订单就写不出评价）；
--   2) 唯一键 (user_id, book_id, order_id)：同一笔订单里的同一本书只能评价一次，
--      但允许用户在不同订单里分别评价同一本书（都构成真实购买）；
--   3) status 字段用于后台隐藏违规评价，不做物理删除，保留审计痕迹；
--   4) 索引按「查某本书的评价并按时间倒序」这一主路径设计。
-- =====================================================================

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
    -- 同一订单内同一本书只能评一次（数据库层兜底，业务层也会先查一次给出友好提示）
    UNIQUE KEY `uk_review_user_book_order` (`user_id`, `book_id`, `order_id`),
    -- 主查询路径：某本书的正常评价，按时间倒序
    KEY `idx_review_book_status_time` (`book_id`, `status`, `create_time`),
    -- 「我的评价」列表
    KEY `idx_review_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '图书评价';
