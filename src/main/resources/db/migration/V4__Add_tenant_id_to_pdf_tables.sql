-- ============================================================
-- MySQL 8.0 PDF转换表添加tenant_id字段
-- 项目：minio-multipart-upload
-- 版本：4.0.0
-- 描述：为pdf_conversion_task和pdf_page_image表添加tenant_id字段
-- ============================================================

SET NAMES utf8mb4;
SET sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO';

-- ============================================================
-- 添加tenant_id字段
-- ============================================================

-- 在pdf_conversion_task表中添加tenant_id字段
ALTER TABLE `pdf_conversion_task` 
ADD COLUMN `tenant_id` VARCHAR(100) NOT NULL COMMENT '租户ID' AFTER `user_id`;

-- 在pdf_page_image表中添加tenant_id字段
ALTER TABLE `pdf_page_image` 
ADD COLUMN `tenant_id` VARCHAR(100) NOT NULL COMMENT '租户ID' AFTER `user_id`;

-- ============================================================
-- 添加索引优化查询
-- ============================================================

-- 为pdf_conversion_task表添加tenant_id相关索引
ALTER TABLE `pdf_conversion_task` 
ADD INDEX `idx_tenant_id` (`tenant_id`),
ADD INDEX `idx_tenant_business` (`tenant_id`, `business_id`),
ADD INDEX `idx_tenant_user` (`tenant_id`, `user_id`);

-- 为pdf_page_image表添加tenant_id相关索引
ALTER TABLE `pdf_page_image` 
ADD INDEX `idx_tenant_id` (`tenant_id`),
ADD INDEX `idx_tenant_business` (`tenant_id`, `business_id`),
ADD INDEX `idx_tenant_business_page` (`tenant_id`, `business_id`, `page_number`);

-- ============================================================
-- 更新现有复合索引（如果需要包含tenant_id）
-- ============================================================

-- 注意：保留原有索引以兼容旧代码，新增带tenant_id的索引

-- ============================================================
-- 分析表以优化查询性能
-- ============================================================

ANALYZE TABLE `pdf_conversion_task`;
ANALYZE TABLE `pdf_page_image`;

-- ============================================================
-- 完成信息
-- ============================================================

SELECT 'PDF转换表tenant_id字段添加完成！' AS message,
       'pdf_conversion_task, pdf_page_image' AS tables,
       '新增tenant_id字段和相关索引' AS feature;
