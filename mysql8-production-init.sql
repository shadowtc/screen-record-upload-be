-- ============================================================
-- MySQL 8.0 生产环境初始化脚本
-- 项目：minio-multipart-upload
-- 版本：1.0.0
-- 描述：生产环境优化的数据库初始化脚本
-- ============================================================

-- 设置SQL模式以确保数据完整性和安全性
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,ONLY_FULL_GROUP_BY';

-- ============================================================
-- 数据库创建
-- ============================================================

-- 创建生产数据库
CREATE DATABASE IF NOT EXISTS `minio_upload` 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE `minio_upload`;

-- ============================================================
-- 表结构创建（生产优化）
-- ============================================================

-- 创建视频录制表
DROP TABLE IF EXISTS `video_recordings`;
CREATE TABLE `video_recordings` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 - 视频录制的自动生成唯一标识符',
  `user_id` VARCHAR(100) NOT NULL COMMENT '与此视频录制关联的用户ID，已索引以支持高效的基于用户的查询',
  `filename` VARCHAR(500) NOT NULL COMMENT '已上传视频的原始文件名，必填字段，存储视频文件的名称',
  `size` BIGINT NOT NULL COMMENT '视频文件的大小（字节），必填字段，用于存储跟踪和验证',
  `duration` BIGINT NULL COMMENT '视频时长（秒），可选字段，用于视频播放信息',
  `width` INT NULL COMMENT '视频宽度（像素），可选字段，用于视频分辨率信息',
  `height` INT NULL COMMENT '视频高度（像素），可选字段，用于视频分辨率信息',
  `codec` VARCHAR(50) NULL COMMENT '视频编解码器信息（例如H.264、VP9），可选字段，用于视频编码详情',
  `object_key` VARCHAR(1000) NOT NULL COMMENT 'S3/MinIO对象键 - 唯一存储标识符，必填且唯一的字段，表示对象存储中的完整路径',
  `status` VARCHAR(50) NOT NULL COMMENT '视频的上传状态（例如COMPLETED、FAILED、IN_PROGRESS），必填字段，在整个上传生命周期中跟踪',
  `checksum` VARCHAR(255) NULL COMMENT '已上传文件的校验和/ETag，用于完整性验证，存储成功上传后S3返回的ETag',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时的时间戳，在实体创建时自动设置，已索引以支持高效的基于时间的查询和排序',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间戳，用于跟踪数据变更',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_object_key` (`object_key`) COMMENT '对象键唯一约束，确保S3对象键的唯一性',
  KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引，支持高效的基于用户的查询',
  KEY `idx_status` (`status`) COMMENT '状态索引，支持高效的基于状态的查询',
  KEY `idx_created_at` (`created_at`) COMMENT '创建时间索引，支持高效的基于时间的查询和排序',
  KEY `idx_user_status_created` (`user_id`, `status`, `created_at`) COMMENT '用户状态创建时间复合索引，优化常用查询',
  KEY `idx_status_created` (`status`, `created_at`) COMMENT '状态创建时间复合索引，优化状态查询',
  CONSTRAINT `chk_status` CHECK (`status` IN ('COMPLETED', 'FAILED', 'IN_PROGRESS', 'PENDING', 'ABORTED')) COMMENT '状态值约束',
  CONSTRAINT `chk_size_positive` CHECK (`size` > 0) COMMENT '文件大小必须为正数',
  CONSTRAINT `chk_duration_positive` CHECK (`duration` IS NULL OR `duration` >= 0) COMMENT '时长必须为非负数',
  CONSTRAINT `chk_width_positive` CHECK (`width` IS NULL OR `width` > 0) COMMENT '宽度必须为正数',
  CONSTRAINT `chk_height_positive` CHECK (`height` IS NULL OR `height` > 0) COMMENT '高度必须为正数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频录制表 - 存储上传到S3/MinIO存储的视频录制元数据' ROW_FORMAT=DYNAMIC;

-- ============================================================
-- 触发器创建
-- ============================================================

-- 创建更新时间触发器
DELIMITER $$
CREATE TRIGGER `video_recordings_before_update` 
BEFORE UPDATE ON `video_recordings`
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END$$
DELIMITER ;

-- ============================================================
-- 视图创建（生产优化）
-- ============================================================

-- 创建视频统计视图（只读）
CREATE OR REPLACE VIEW `v_video_statistics` AS
SELECT 
    COUNT(*) AS total_videos,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed_videos,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) AS failed_videos,
    COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) AS in_progress_videos,
    COUNT(CASE WHEN status = 'PENDING' THEN 1 END) AS pending_videos,
    COUNT(CASE WHEN status = 'ABORTED' THEN 1 END) AS aborted_videos,
    COALESCE(SUM(size), 0) AS total_size_bytes,
    COALESCE(AVG(size), 0) AS average_size_bytes,
    COALESCE(MAX(size), 0) AS max_size_bytes,
    COALESCE(MIN(size), 0) AS min_size_bytes,
    COALESCE(SUM(duration), 0) AS total_duration_seconds,
    COALESCE(AVG(duration), 0) AS average_duration_seconds,
    COUNT(DISTINCT user_id) AS unique_users,
    DATE(MIN(created_at)) AS first_upload_date,
    DATE(MAX(created_at)) AS last_upload_date,
    CURRENT_TIMESTAMP() AS statistics_updated_at
FROM `video_recordings`;

-- 创建用户视频统计视图（只读）
CREATE OR REPLACE VIEW `v_user_video_statistics` AS
SELECT 
    user_id,
    COUNT(*) AS video_count,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed_count,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) AS failed_count,
    COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) AS in_progress_count,
    COUNT(CASE WHEN status = 'PENDING' THEN 1 END) AS pending_count,
    COUNT(CASE WHEN status = 'ABORTED' THEN 1 END) AS aborted_count,
    COALESCE(SUM(size), 0) AS total_size_bytes,
    COALESCE(AVG(size), 0) AS average_size_bytes,
    COALESCE(SUM(duration), 0) AS total_duration_seconds,
    COALESCE(AVG(duration), 0) AS average_duration_seconds,
    MIN(created_at) AS first_upload_date,
    MAX(created_at) AS last_upload_date,
    CURRENT_TIMESTAMP() AS statistics_updated_at
FROM `video_recordings`
GROUP BY user_id;

-- ============================================================
-- 存储过程创建（生产优化）
-- ============================================================

-- 清理过期记录的存储过程
DELIMITER $$
CREATE PROCEDURE `sp_cleanup_old_records`(
    IN p_days_old INT,
    IN p_status_filter VARCHAR(50),
    OUT p_deleted_count INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- 删除指定天数前的记录
    IF p_status_filter IS NOT NULL AND p_status_filter != '' THEN
        DELETE FROM `video_recordings` 
        WHERE created_at < DATE_SUB(NOW(), INTERVAL p_days_old DAY)
        AND status = p_status_filter;
    ELSE
        DELETE FROM `video_recordings` 
        WHERE created_at < DATE_SUB(NOW(), INTERVAL p_days_old DAY);
    END IF;
    
    SET p_deleted_count = ROW_COUNT();
    
    COMMIT;
END$$
DELIMITER ;

-- 获取用户视频列表的存储过程（优化版）
DELIMITER $$
CREATE PROCEDURE `sp_get_user_videos_paginated`(
    IN p_user_id VARCHAR(100),
    IN p_status VARCHAR(50),
    IN p_page_size INT,
    IN p_page_number INT,
    OUT p_total_count INT
)
BEGIN
    DECLARE v_offset INT;
    
    -- 计算偏移量
    SET v_offset = (p_page_number - 1) * p_page_size;
    
    -- 获取总记录数
    SELECT COUNT(*) INTO p_total_count
    FROM `video_recordings`
    WHERE user_id = p_user_id
    AND (p_status IS NULL OR p_status = '' OR status = p_status);
    
    -- 获取分页数据
    SELECT 
        id, filename, size, duration, width, height, codec, 
        object_key, status, checksum, created_at, updated_at
    FROM `video_recordings`
    WHERE user_id = p_user_id
    AND (p_status IS NULL OR p_status = '' OR status = p_status)
    ORDER BY created_at DESC
    LIMIT p_page_size OFFSET v_offset;
END$$
DELIMITER ;

-- 获取系统健康状态的存储过程
DELIMITER $$
CREATE PROCEDURE `sp_get_system_health`(
    OUT p_total_videos INT,
    OUT p_pending_uploads INT,
    OUT p_failed_uploads INT,
    OUT p_avg_upload_time_seconds DECIMAL(10,2),
    OUT p_disk_usage_gb DECIMAL(10,2)
)
BEGIN
    -- 总视频数
    SELECT COUNT(*) INTO p_total_videos FROM `video_recordings`;
    
    -- 待处理上传数
    SELECT COUNT(*) INTO p_pending_uploads 
    FROM `video_recordings` 
    WHERE status IN ('PENDING', 'IN_PROGRESS');
    
    -- 失败上传数
    SELECT COUNT(*) INTO p_failed_uploads 
    FROM `video_recordings` 
    WHERE status = 'FAILED';
    
    -- 平均上传时间（模拟计算）
    SELECT 
        CASE 
            WHEN COUNT(*) > 0 THEN AVG(TIMESTAMPDIFF(SECOND, created_at, updated_at))
            ELSE 0 
        END INTO p_avg_upload_time_seconds
    FROM `video_recordings`
    WHERE status = 'COMPLETED'
    AND updated_at > created_at;
    
    -- 磁盘使用量（GB）
    SELECT 
        COALESCE(SUM(size), 0) / 1024.0 / 1024.0 / 1024.0 INTO p_disk_usage_gb
    FROM `video_recordings`;
END$$
DELIMITER ;

-- ============================================================
-- 事件调度器（定时任务）
-- ============================================================

-- 创建清理过期记录的事件
CREATE EVENT IF NOT EXISTS `evt_cleanup_old_records`
ON SCHEDULE EVERY 1 DAY
STARTS CURRENT_TIMESTAMP
DO
BEGIN
    DECLARE v_deleted_count INT;
    
    -- 清理30天前的失败记录
    CALL sp_cleanup_old_records(30, 'FAILED', v_deleted_count);
    
    -- 记录清理日志（可以创建专门的日志表）
    INSERT INTO `video_recordings` (user_id, filename, size, object_key, status, created_at)
    VALUES ('SYSTEM', CONCAT('cleanup-', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s')), 0, 'system/cleanup', 'COMPLETED', NOW());
END;

-- 启用事件调度器
SET GLOBAL event_scheduler = ON;

-- ============================================================
-- 权限设置（生产环境）
-- ============================================================

-- 创建只读用户（用于报表和监控）
CREATE USER IF NOT EXISTS 'readonly_user'@'%' IDENTIFIED BY 'readonly_password_2024!';
GRANT SELECT ON `minio_upload`.* TO 'readonly_user'@'%';

-- 创建应用用户（读写权限）
CREATE USER IF NOT EXISTS 'app_user'@'%' IDENTIFIED BY 'app_secure_password_2024!';
GRANT SELECT, INSERT, UPDATE, DELETE ON `minio_upload`.* TO 'app_user'@'%';

-- 创建备份用户（用于数据备份）
CREATE USER IF NOT EXISTS 'backup_user'@'%' IDENTIFIED BY 'backup_password_2024!';
GRANT SELECT, LOCK TABLES, SHOW VIEW ON `minio_upload`.* TO 'backup_user'@'%';

-- 刷新权限
FLUSH PRIVILEGES;

-- ============================================================
-- 性能优化
-- ============================================================

-- 分析表以优化查询性能
ANALYZE TABLE `video_recordings`;

-- 检查表
CHECK TABLE `video_recordings`;

-- 优化表
OPTIMIZE TABLE `video_recordings`;

-- 重置外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 完成信息
-- ============================================================

SELECT 'MySQL 8.0 生产环境初始化完成！' AS message,
       'minio_upload' AS database_name,
       'video_recordings' AS main_table,
       VERSION() AS mysql_version,
       NOW() AS initialization_time;