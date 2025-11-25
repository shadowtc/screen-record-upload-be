-- ============================================================
-- MySQL 8.0 PDF转换任务表创建脚本
-- 项目：minio-multipart-upload
-- 版本：3.0.0
-- 描述：创建PDF转换任务表，支持多人签署场景的增量转换
-- ============================================================

SET NAMES utf8mb4;
SET sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO';

-- ============================================================
-- 表结构创建
-- ============================================================

-- 创建PDF转换任务表
DROP TABLE IF EXISTS `pdf_conversion_task`;
CREATE TABLE `pdf_conversion_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` VARCHAR(36) NOT NULL COMMENT '任务唯一标识符（UUID）',
  `business_id` VARCHAR(100) NOT NULL COMMENT '业务ID',
  `user_id` VARCHAR(100) NOT NULL COMMENT '用户ID',
  `filename` VARCHAR(500) NOT NULL COMMENT '文件名',
  `total_pages` INT NOT NULL COMMENT 'PDF总页数',
  `converted_pages` TEXT NULL COMMENT '转换的页面（JSON数组，如[1,3,5]），NULL表示全量转换',
  `pdf_object_key` VARCHAR(1000) NULL COMMENT 'PDF在MinIO的对象键',
  `status` VARCHAR(20) NOT NULL COMMENT '状态：SUBMITTED、PROCESSING、COMPLETED、FAILED',
  `is_base` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为基类全量转换',
  `error_message` TEXT NULL COMMENT '错误信息',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_task_id` (`task_id`),
  KEY `idx_business_id` (`business_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_business_user` (`business_id`, `user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PDF转换任务表';

-- 创建PDF页面图片表
DROP TABLE IF EXISTS `pdf_page_image`;
CREATE TABLE `pdf_page_image` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` VARCHAR(36) NOT NULL COMMENT '关联的任务ID',
  `business_id` VARCHAR(100) NOT NULL COMMENT '业务ID',
  `user_id` VARCHAR(100) NOT NULL COMMENT '用户ID',
  `page_number` INT NOT NULL COMMENT '页码（从1开始）',
  `image_object_key` VARCHAR(1000) NOT NULL COMMENT '图片在MinIO的对象键',
  `is_base` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为基类图片',
  `width` INT NULL COMMENT '图片宽度',
  `height` INT NULL COMMENT '图片高度',
  `file_size` BIGINT NULL COMMENT '图片文件大小（字节）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_business_id` (`business_id`),
  KEY `idx_business_page` (`business_id`, `page_number`),
  KEY `idx_business_user_page` (`business_id`, `user_id`, `page_number`),
  KEY `idx_is_base` (`is_base`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PDF页面图片表';

-- ============================================================
-- 视图创建
-- ============================================================

-- 创建PDF转换任务统计视图
CREATE OR REPLACE VIEW `pdf_conversion_statistics` AS
SELECT 
    COUNT(*) AS total_tasks,
    COUNT(CASE WHEN status = 'SUBMITTED' THEN 1 END) AS submitted_tasks,
    COUNT(CASE WHEN status = 'PROCESSING' THEN 1 END) AS processing_tasks,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed_tasks,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) AS failed_tasks,
    COUNT(CASE WHEN is_base = TRUE THEN 1 END) AS base_conversion_tasks,
    COUNT(CASE WHEN is_base = FALSE THEN 1 END) AS incremental_conversion_tasks,
    COUNT(DISTINCT business_id) AS unique_businesses,
    COUNT(DISTINCT user_id) AS unique_users
FROM `pdf_conversion_task`;

-- ============================================================
-- 分析表以优化查询性能
-- ============================================================

ANALYZE TABLE `pdf_conversion_task`;
ANALYZE TABLE `pdf_page_image`;

-- ============================================================
-- 完成信息
-- ============================================================

SELECT 'PDF转换表创建完成！' AS message,
       'pdf_conversion_task, pdf_page_image' AS tables,
       '支持多人签署增量转换' AS feature;
