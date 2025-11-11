-- ============================================================
-- MySQL 8.0 快速初始化脚本
-- 项目：minio-multipart-upload
-- 版本：1.0.0
-- 描述：基于VideoRecording实体的简化数据库结构初始化
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `minio_upload` 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

USE `minio_upload`;

-- 创建视频录制表
DROP TABLE IF EXISTS `video_recordings`;
CREATE TABLE `video_recordings` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` VARCHAR(100) NULL,
  `filename` VARCHAR(500) NOT NULL,
  `size` BIGINT NOT NULL,
  `duration` BIGINT NULL,
  `width` INT NULL,
  `height` INT NULL,
  `codec` VARCHAR(50) NULL,
  `object_key` VARCHAR(1000) NOT NULL,
  `status` VARCHAR(50) NOT NULL,
  `checksum` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_object_key` (`object_key`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建复合索引
CREATE INDEX `idx_user_status_created` ON `video_recordings` (`user_id`, `status`, `created_at`);
CREATE INDEX `idx_status_created` ON `video_recordings` (`status`, `created_at`);

SELECT 'MySQL 8.0 快速初始化完成！' AS message;