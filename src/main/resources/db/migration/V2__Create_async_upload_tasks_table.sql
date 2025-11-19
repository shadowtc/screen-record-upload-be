-- ============================================================
-- MySQL 8.0 异步上传任务表创建脚本
-- 项目：minio-multipart-upload
-- 版本：2.0.0
-- 描述：创建async_upload_tasks表以支持断点续传功能
-- ============================================================

-- 设置SQL模式以确保数据完整性
SET NAMES utf8mb4;
SET sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO';

-- ============================================================
-- 表结构创建
-- ============================================================

-- 创建异步上传任务表
DROP TABLE IF EXISTS `async_upload_tasks`;
CREATE TABLE `async_upload_tasks` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 - 自动生成的唯一标识符',
  `job_id` VARCHAR(36) NOT NULL COMMENT '上传任务的唯一标识符（UUID），客户端使用此ID查询进度和恢复上传',
  `status` VARCHAR(20) NOT NULL COMMENT '上传状态：SUBMITTED、UPLOADING、PAUSED、COMPLETED、FAILED',
  `progress` DOUBLE NOT NULL DEFAULT 0 COMMENT '上传进度百分比（0-100）',
  `message` VARCHAR(1000) NULL COMMENT '状态消息，提供更详细的进度信息或错误描述',
  `uploaded_parts` INT NOT NULL DEFAULT 0 COMMENT '当前已上传的分片数',
  `total_parts` INT NOT NULL COMMENT '总分片数',
  `file_name` VARCHAR(500) NOT NULL COMMENT '文件名',
  `file_size` BIGINT NOT NULL COMMENT '文件大小（字节）',
  `content_type` VARCHAR(100) NULL COMMENT '文件内容类型',
  `chunk_size` BIGINT NOT NULL COMMENT '分片大小（字节）',
  `upload_id` VARCHAR(500) NULL COMMENT 'MinIO上传ID',
  `object_key` VARCHAR(1000) NULL COMMENT 'S3/MinIO对象键',
  `temp_file_path` VARCHAR(1000) NULL COMMENT '临时文件路径（用于断点续传）',
  `video_recording_id` BIGINT NULL COMMENT '已完成的视频录制ID（上传完成后关联到VideoRecording）',
  `start_time` DATETIME NOT NULL COMMENT '任务开始时间',
  `end_time` DATETIME NULL COMMENT '任务结束时间（仅在COMPLETED或FAILED状态时可用）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时的时间戳',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时的时间戳',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_job_id` (`job_id`) COMMENT '任务ID唯一索引',
  KEY `idx_status` (`status`) COMMENT '状态索引，支持高效的基于状态的查询',
  KEY `idx_created_at` (`created_at`) COMMENT '创建时间索引，支持高效的基于时间的查询和排序',
  KEY `idx_video_recording_id` (`video_recording_id`) COMMENT '视频录制ID索引',
  CONSTRAINT `fk_video_recording` FOREIGN KEY (`video_recording_id`) REFERENCES `video_recordings` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步上传任务表 - 支持断点续传功能';

-- ============================================================
-- 索引优化
-- ============================================================

-- 为状态查询和时间范围查询创建复合索引
CREATE INDEX `idx_status_created` ON `async_upload_tasks` (`status`, `created_at`) COMMENT '状态创建时间复合索引';

-- 为清理任务创建复合索引
CREATE INDEX `idx_status_updated` ON `async_upload_tasks` (`status`, `updated_at`) COMMENT '状态更新时间复合索引';

-- ============================================================
-- 视图创建
-- ============================================================

-- 创建上传任务统计视图
CREATE OR REPLACE VIEW `upload_task_statistics` AS
SELECT 
    COUNT(*) AS total_tasks,
    COUNT(CASE WHEN status = 'SUBMITTED' THEN 1 END) AS submitted_tasks,
    COUNT(CASE WHEN status = 'UPLOADING' THEN 1 END) AS uploading_tasks,
    COUNT(CASE WHEN status = 'PAUSED' THEN 1 END) AS paused_tasks,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed_tasks,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) AS failed_tasks,
    SUM(file_size) AS total_file_size_bytes,
    AVG(file_size) AS average_file_size_bytes,
    AVG(CASE WHEN status = 'COMPLETED' THEN progress END) AS average_completion_progress,
    DATE(MIN(created_at)) AS first_task_date,
    DATE(MAX(created_at)) AS last_task_date
FROM `async_upload_tasks`;

-- 创建可恢复任务视图（暂停或上传中的任务）
CREATE OR REPLACE VIEW `resumable_upload_tasks` AS
SELECT 
    job_id,
    file_name,
    file_size,
    progress,
    uploaded_parts,
    total_parts,
    temp_file_path,
    upload_id,
    object_key,
    status,
    message,
    created_at,
    updated_at,
    TIMESTAMPDIFF(MINUTE, updated_at, NOW()) AS minutes_since_last_update
FROM `async_upload_tasks`
WHERE status IN ('UPLOADING', 'PAUSED')
AND temp_file_path IS NOT NULL
ORDER BY updated_at DESC;

-- ============================================================
-- 存储过程创建
-- ============================================================

-- 清理旧任务的存储过程
DELIMITER $$
CREATE PROCEDURE `cleanup_old_upload_tasks`(
    IN days_old INT
)
BEGIN
    DECLARE deleted_count INT DEFAULT 0;
    
    -- 删除指定天数前的已完成或失败任务
    DELETE FROM `async_upload_tasks` 
    WHERE created_at < DATE_SUB(NOW(), INTERVAL days_old DAY)
    AND status IN ('COMPLETED', 'FAILED');
    
    SET deleted_count = ROW_COUNT();
    
    SELECT CONCAT('已删除 ', deleted_count, ' 条旧任务记录') AS result;
END$$
DELIMITER ;

-- 清理孤立任务的存储过程（上传中但长时间未更新的任务）
DELIMITER $$
CREATE PROCEDURE `cleanup_stale_upload_tasks`(
    IN hours_stale INT
)
BEGIN
    DECLARE updated_count INT DEFAULT 0;
    
    -- 将长时间未更新的上传中任务标记为失败
    UPDATE `async_upload_tasks` 
    SET status = 'FAILED',
        message = CONCAT('Task marked as failed due to inactivity (', hours_stale, ' hours)'),
        end_time = NOW()
    WHERE status = 'UPLOADING'
    AND updated_at < DATE_SUB(NOW(), INTERVAL hours_stale HOUR);
    
    SET updated_count = ROW_COUNT();
    
    SELECT CONCAT('已标记 ', updated_count, ' 条停滞任务为失败') AS result;
END$$
DELIMITER ;

-- 获取可恢复任务列表的存储过程
DELIMITER $$
CREATE PROCEDURE `get_resumable_tasks`(
    IN p_limit INT,
    IN p_offset INT
)
BEGIN
    IF p_limit IS NULL OR p_limit <= 0 THEN
        SET p_limit = 10;
    END IF;
    
    IF p_offset IS NULL OR p_offset < 0 THEN
        SET p_offset = 0;
    END IF;
    
    SELECT 
        job_id,
        file_name,
        file_size,
        progress,
        uploaded_parts,
        total_parts,
        status,
        message,
        created_at,
        updated_at
    FROM `async_upload_tasks`
    WHERE status IN ('UPLOADING', 'PAUSED')
    AND temp_file_path IS NOT NULL
    ORDER BY updated_at DESC
    LIMIT p_limit OFFSET p_offset;
END$$
DELIMITER ;

-- ============================================================
-- 分析表以优化查询性能
-- ============================================================

ANALYZE TABLE `async_upload_tasks`;

-- ============================================================
-- 完成信息
-- ============================================================

SELECT 'async_upload_tasks 表创建完成！' AS message,
       'async_upload_tasks' AS table_name,
       '支持断点续传功能' AS feature;
