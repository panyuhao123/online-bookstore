-- =====================================================================
-- V5：收货地址补充行政区划编码（adcode）
--
-- 背景：地址新增「高德地理编码」能力后，需要保存省/市/区的行政区划编码，
-- 用于精确定位与后续的地图相关功能（仅存编码，不存经纬度）。
--
-- ⚠ 这个变更原本是手工直接改库的，既没进 db/schema.sql，也没有迁移脚本 ——
--   属于「结构漂移」：新环境照快照重建出来的表会缺这三列，运行时才报 Unknown column。
--   批 6 在做「快照 vs 实际结构」比对时把它揪了出来，一并补成正式迁移。
--
-- 已有库说明：这些库在手工时代已经加过这三列，因此会被 baseline 到 v5（跳过本脚本），
--           不会重复执行；全新空库则会在 V1 建表后由本脚本补上，两边结构最终一致。
-- =====================================================================

ALTER TABLE `address`
    ADD COLUMN `province_adcode` VARCHAR(12) DEFAULT NULL COMMENT '省份 adcode 编码' AFTER `update_time`;

ALTER TABLE `address`
    ADD COLUMN `city_adcode` VARCHAR(12) DEFAULT NULL COMMENT '城市 adcode 编码' AFTER `province_adcode`;

ALTER TABLE `address`
    ADD COLUMN `district_adcode` VARCHAR(12) DEFAULT NULL COMMENT '区县 adcode 编码' AFTER `city_adcode`;
