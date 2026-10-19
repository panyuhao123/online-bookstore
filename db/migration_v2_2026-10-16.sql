-- ---------------------------------------------------------------------
-- 增量迁移脚本 v2（2026-10-16）：图书筛选性能优化
--
-- 背景：图书列表新增「价格区间」条件，典型查询变为
--   SELECT ... FROM book WHERE status = 1 AND category_id = ? AND price BETWEEN ? AND ?
--   ORDER BY sales DESC, id DESC LIMIT ?
-- 原有单列索引（idx_book_status、idx_book_category）在这个组合条件下只能命中一个，
-- 需要补组合索引。
--
-- 特点：脚本可重复执行（幂等）。MySQL 8 不支持 CREATE INDEX IF NOT EXISTS，
-- 因此用 information_schema 判断后再建，避免迁移脚本被重跑时报错中断。
--
-- 执行方式：
--   mysql --defaults-extra-file=<含口令的配置文件> < db/migration_v2_2026-10-16.sql
-- ---------------------------------------------------------------------

USE `bookstore`;

DROP PROCEDURE IF EXISTS `sp_add_index_if_absent`;

DELIMITER $$
CREATE PROCEDURE `sp_add_index_if_absent`(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_columns VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = p_table
                     AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('已创建索引 ', p_index) AS result;
    ELSE
        SELECT CONCAT('索引已存在，跳过 ', p_index) AS result;
    END IF;
END$$
DELIMITER ;

-- 组合索引：上架 + 分类 + 价格区间（服务前台筛选的主路径）
CALL sp_add_index_if_absent('book', 'idx_book_status_cate_price', '`status`, `category_id`, `price`');

-- 销量索引：列表默认按销量倒序
CALL sp_add_index_if_absent('book', 'idx_book_sales', '`sales`');

DROP PROCEDURE IF EXISTS `sp_add_index_if_absent`;
