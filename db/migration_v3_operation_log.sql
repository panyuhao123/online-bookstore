-- ---------------------------------------------------------------------
-- 增量迁移脚本 v3（2026-10-16）：操作日志表
--
-- 背景：管理后台的所有写操作（新增/修改/删除图书、上下架、发货、启停用户…）
-- 目前只留在 logback 的文本日志里，无法查询、无法追责，也无法在后台页面展示。
-- 本脚本新增 operation_log 表，配合 AOP 切面把管理端写操作结构化落库。
--
-- 与 migration_v2 的区别：本脚本用 CREATE TABLE IF NOT EXISTS 天然幂等，
-- 不需要像建索引那样绕 information_schema（MySQL 8 的 CREATE INDEX 没有 IF NOT EXISTS）。
--
-- 执行方式：
--   mysql --defaults-extra-file=<含口令的配置文件> < db/migration_v3_operation_log.sql
-- ---------------------------------------------------------------------

USE `bookstore`;

CREATE TABLE IF NOT EXISTS `operation_log`
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
    -- 后台按时间倒序分页查看，这个索引是主路径
    KEY `idx_oplog_create_time` (`create_time`),
    -- 按模块/操作人筛选
    KEY `idx_oplog_module` (`module`),
    KEY `idx_oplog_admin` (`admin_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '管理端操作日志';

SELECT '操作日志表已就绪' AS result;
