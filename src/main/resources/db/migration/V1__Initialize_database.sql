-- ============================================================
-- MySQL 8.0 初始化脚本
-- 项目：minio-multipart-upload
-- 版本：1.0.0
-- 描述：基于VideoRecording实体的数据库结构初始化
-- ============================================================

-- 设置SQL模式以确保数据完整性
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO';

-- ============================================================
-- 数据库创建
-- ============================================================

-- 如果不存在则创建数据库
CREATE DATABASE IF NOT EXISTS `minio_upload` 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

-- 使用创建的数据库
USE `minio_upload`;

-- ============================================================
-- 表结构创建
-- ============================================================

-- 创建视频录制表
-- 基于VideoRecording实体类映射
DROP TABLE IF EXISTS `video_recordings`;
CREATE TABLE `video_recordings` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 - 视频录制的自动生成唯一标识符',
  `user_id` VARCHAR(100) NULL COMMENT '与此视频录制关联的用户ID，已索引以支持高效的基于用户的查询',
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_object_key` (`object_key`) COMMENT '对象键唯一索引，确保S3对象键的唯一性',
  KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引，支持高效的基于用户的查询',
  KEY `idx_status` (`status`) COMMENT '状态索引，支持高效的基于状态的查询',
  KEY `idx_created_at` (`created_at`) COMMENT '创建时间索引，支持高效的基于时间的查询和排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频录制表 - 存储上传到S3/MinIO存储的视频录制元数据';

-- ============================================================
-- 索引优化
-- ============================================================

-- 为复合查询创建复合索引
-- 常用查询组合：用户ID + 状态 + 创建时间
CREATE INDEX `idx_user_status_created` ON `video_recordings` (`user_id`, `status`, `created_at`) COMMENT '用户状态创建时间复合索引';

-- 为状态查询和时间范围查询创建复合索引
CREATE INDEX `idx_status_created` ON `video_recordings` (`status`, `created_at`) COMMENT '状态创建时间复合索引';

-- 为用户查询和时间范围查询创建复合索引
CREATE INDEX `idx_user_created` ON `video_recordings` (`user_id`, `created_at`) COMMENT '用户创建时间复合索引';

-- ============================================================
-- 触发器创建
-- ============================================================

-- 创建触发器自动设置created_at字段（如果应用层未设置）
DELIMITER $$
CREATE TRIGGER `video_recordings_before_insert` 
BEFORE INSERT ON `video_recordings`
FOR EACH ROW
BEGIN
    -- 如果created_at为NULL，则设置为当前时间
    IF NEW.created_at IS NULL THEN
        SET NEW.created_at = CURRENT_TIMESTAMP;
    END IF;
END$$
DELIMITER ;

-- ============================================================
-- 视图创建
-- ============================================================

-- 创建视频统计视图
CREATE OR REPLACE VIEW `video_statistics` AS
SELECT 
    COUNT(*) AS total_videos,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed_videos,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) AS failed_videos,
    COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) AS in_progress_videos,
    SUM(size) AS total_size_bytes,
    AVG(size) AS average_size_bytes,
    MAX(size) AS max_size_bytes,
    MIN(size) AS min_size_bytes,
    SUM(duration) AS total_duration_seconds,
    AVG(duration) AS average_duration_seconds,
    COUNT(DISTINCT user_id) AS unique_users,
    DATE(MIN(created_at)) AS first_upload_date,
    DATE(MAX(created_at)) AS last_upload_date
FROM `video_recordings`;

-- 创建用户视频统计视图
CREATE OR REPLACE VIEW `user_video_statistics` AS
SELECT 
    user_id,
    COUNT(*) AS video_count,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed_count,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) AS failed_count,
    COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) AS in_progress_count,
    SUM(size) AS total_size_bytes,
    AVG(size) AS average_size_bytes,
    SUM(duration) AS total_duration_seconds,
    AVG(duration) AS average_duration_seconds,
    MIN(created_at) AS first_upload_date,
    MAX(created_at) AS last_upload_date
FROM `video_recordings`
GROUP BY user_id;

-- ============================================================
-- 存储过程创建
-- ============================================================

-- 清理过期或失败记录的存储过程
DELIMITER $$
CREATE PROCEDURE `cleanup_old_records`(
    IN days_old INT,
    IN status_filter VARCHAR(50)
)
BEGIN
    DECLARE deleted_count INT DEFAULT 0;
    
    -- 删除指定天数前的记录
    IF status_filter IS NOT NULL AND status_filter != '' THEN
        DELETE FROM `video_recordings` 
        WHERE created_at < DATE_SUB(NOW(), INTERVAL days_old DAY)
        AND status = status_filter;
    ELSE
        DELETE FROM `video_recordings` 
        WHERE created_at < DATE_SUB(NOW(), INTERVAL days_old DAY);
    END IF;
    
    SET deleted_count = ROW_COUNT();
    
    SELECT CONCAT('已删除 ', deleted_count, ' 条记录') AS result;
END$$
DELIMITER ;

-- 获取用户视频列表的存储过程
DELIMITER $$
CREATE PROCEDURE `get_user_videos`(
    IN p_user_id VARCHAR(100),
    IN p_status VARCHAR(50),
    IN p_limit INT,
    IN p_offset INT
)
BEGIN
    SET @sql = 'SELECT id, filename, size, duration, width, height, codec, object_key, status, checksum, created_at 
                FROM video_recordings 
                WHERE user_id = ?';
    
    IF p_status IS NOT NULL AND p_status != '' THEN
        SET @sql = CONCAT(@sql, ' AND status = ?');
    END IF;
    
    SET @sql = CONCAT(@sql, ' ORDER BY created_at DESC');
    
    IF p_limit IS NOT NULL AND p_limit > 0 THEN
        SET @sql = CONCAT(@sql, ' LIMIT ?');
        IF p_offset IS NOT NULL AND p_offset > 0 THEN
            SET @sql = CONCAT(@sql, ' OFFSET ?');
        END IF;
    END IF;
    
    -- 准备和执行动态SQL
    IF p_status IS NOT NULL AND p_status != '' THEN
        IF p_offset IS NOT NULL AND p_offset > 0 THEN
            PREPARE stmt FROM @sql;
            EXECUTE stmt USING p_user_id, p_status, p_limit, p_offset;
        ELSE
            PREPARE stmt FROM @sql;
            EXECUTE stmt USING p_user_id, p_status, p_limit;
        END IF;
    ELSE
        IF p_offset IS NOT NULL AND p_offset > 0 THEN
            PREPARE stmt FROM @sql;
            EXECUTE stmt USING p_user_id, p_limit, p_offset;
        ELSE
            PREPARE stmt FROM @sql;
            EXECUTE stmt USING p_user_id, p_limit;
        END IF;
    END IF;
    
    DEALLOCATE PREPARE stmt;
END$$
DELIMITER ;

-- ============================================================
-- 数据插入（示例数据）
-- ============================================================

-- 插入示例数据（可选，用于测试）
INSERT INTO `video_recordings` (
    `user_id`, `filename`, `size`, `duration`, `width`, `height`, `codec`, 
    `object_key`, `status`, `checksum`
) VALUES 
('user001', 'demo-video-1.mp4', 1073741824, 600, 1920, 1080, 'H.264', 
 'videos/user001/2024/01/15/demo-video-1.mp4', 'COMPLETED', 'd41d8cd98f00b204e9800998ecf8427e'),
('user001', 'demo-video-2.mp4', 536870912, 300, 1280, 720, 'H.264', 
 'videos/user001/2024/01/16/demo-video-2.mp4', 'COMPLETED', 'e41d8cd98f00b204e9800998ecf8427f'),
('user002', 'screen-recording.mp4', 268435456, 180, 1920, 1080, 'H.264', 
 'videos/user002/2024/01/17/screen-recording.mp4', 'IN_PROGRESS', NULL),
('user003', 'failed-upload.mp4', 1048576, 10, 640, 480, 'VP9', 
 'videos/user003/2024/01/18/failed-upload.mp4', 'FAILED', 'f41d8cd98f00b204e9800998ecf8427g');

-- ============================================================
-- 权限设置
-- ============================================================

-- 创建应用用户（如果需要）
-- CREATE USER IF NOT EXISTS 'app_user'@'%' IDENTIFIED BY 'secure_password';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON `minio_upload`.* TO 'app_user'@'%';
-- FLUSH PRIVILEGES;

-- ============================================================
-- 优化设置
-- ============================================================

-- 设置表引擎参数优化
ALTER TABLE `video_recordings` 
ENGINE=InnoDB 
ROW_FORMAT=COMPRESSED 
KEY_BLOCK_SIZE=8;

-- 分析表以优化查询性能
ANALYZE TABLE `video_recordings`;

-- 重置外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 完成信息
-- ============================================================

SELECT 'MySQL 8.0 初始化脚本执行完成！' AS message,
       'minio_upload' AS database_name,
       'video_recordings' AS main_table,
       COUNT(*) AS sample_records_count
FROM `video_recordings`;