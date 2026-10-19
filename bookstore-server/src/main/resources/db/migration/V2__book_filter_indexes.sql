-- =====================================================================
-- V2：图书筛选性能优化（补两个组合索引）
--
-- 背景：图书列表新增「价格区间」条件，典型查询变为
--   SELECT ... FROM book WHERE status = 1 AND category_id = ? AND price BETWEEN ? AND ?
--   ORDER BY sales DESC, id DESC LIMIT ?
-- 原有单列索引（idx_book_status、idx_book_category）在这个组合条件下只能命中一个，
-- 需要补组合索引；列表默认按销量倒序，sales 也需要独立索引。
--
-- 与旧手工脚本（原 db/migration_v2_2026-10-16.sql）的差异：
--   旧脚本用 information_schema + 存储过程做「幂等」判断，属于手工执行时代的补丁；
--   纳入 Flyway 后不需要 —— Flyway 保证每个版本在每个库上只执行一次，
--   而 DELIMITER / CREATE PROCEDURE 也不是 Flyway SQL 解析器支持的语法。
-- =====================================================================

-- 组合索引：上架 + 分类 + 价格区间（服务前台筛选的主路径）
-- 列顺序按选择性从高到低：status 只有 2 个值，与查询强绑定，放最左能立即收敛范围
ALTER TABLE `book` ADD INDEX `idx_book_status_cate_price` (`status`, `category_id`, `price`);

-- 销量索引：列表默认按销量倒序
ALTER TABLE `book` ADD INDEX `idx_book_sales` (`sales`);
