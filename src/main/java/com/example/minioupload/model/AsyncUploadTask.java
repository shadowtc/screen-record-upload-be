package com.example.minioupload.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 异步上传任务实体
 * 
 * 用于持久化异步上传任务的状态，支持断点续传功能。
 * 记录上传的完整信息，包括临时文件位置、MinIO上传ID、已上传分片等。
 */
@Entity
@Table(name = "async_upload_tasks", indexes = {
    @Index(name = "idx_job_id", columnList = "job_id", unique = true),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsyncUploadTask {

    /**
     * 主键 - 自动生成的唯一标识符
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 上传任务的唯一标识符（UUID）
     * 客户端使用此ID查询进度和恢复上传
     */
    @Column(name = "job_id", nullable = false, unique = true, length = 36)
    private String jobId;

    /**
     * 上传状态
     * SUBMITTED：已提交，等待开始
     * UPLOADING：正在上传中
     * PAUSED：已暂停（用于断点续传）
     * COMPLETED：上传完成
     * FAILED：上传失败
     */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * 上传进度百分比（0-100）
     */
    @Column(nullable = false)
    private Double progress;

    /**
     * 状态消息，提供更详细的进度信息或错误描述
     */
    @Column(length = 1000)
    private String message;

    /**
     * 当前已上传的分片数
     */
    @Column(name = "uploaded_parts", nullable = false)
    private Integer uploadedParts;

    /**
     * 总分片数
     */
    @Column(name = "total_parts", nullable = false)
    private Integer totalParts;

    /**
     * 文件名
     */
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    /**
     * 文件大小（字节）
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * 文件内容类型
     */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /**
     * 分片大小（字节）
     */
    @Column(name = "chunk_size", nullable = false)
    private Long chunkSize;

    /**
     * MinIO上传ID
     */
    @Column(name = "upload_id", length = 500)
    private String uploadId;

    /**
     * S3/MinIO对象键
     */
    @Column(name = "object_key", length = 1000)
    private String objectKey;

    /**
     * 临时文件路径（用于断点续传）
     */
    @Column(name = "temp_file_path", length = 1000)
    private String tempFilePath;

    /**
     * 已完成的视频录制ID（上传完成后关联到VideoRecording）
     */
    @Column(name = "video_recording_id")
    private Long videoRecordingId;

    /**
     * 任务开始时间
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * 任务结束时间（仅在COMPLETED或FAILED状态时可用）
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 记录创建时的时间戳
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时的时间戳
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA生命周期回调 - 在实体持久化到数据库之前执行
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * JPA生命周期回调 - 在实体更新到数据库之前执行
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
