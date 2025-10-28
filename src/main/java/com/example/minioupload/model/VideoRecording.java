package com.example.minioupload.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 表示上传到S3/MinIO存储的视频录制的实体类。
 * 此实体跟踪已上传视频的元数据，包括文件信息、存储位置、上传状态和视频属性。
 * 
 * 使用MySQL 8.0作为持久层，并为常见查询优化了索引。
 */
@Entity
@Table(name = "video_recordings", indexes = {
    @Index(name = "idx_object_key", columnList = "object_key", unique = true),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoRecording {

    /**
     * 主键 - 视频录制的自动生成唯一标识符
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 与此视频录制关联的用户ID
     * 已索引以支持高效的基于用户的查询
     */
    @Column(name = "user_id", length = 100)
    private String userId;

    /**
     * 已上传视频的原始文件名
     * 必填字段，存储视频文件的名称
     */
    @Column(nullable = false, length = 500)
    private String filename;

    /**
     * 视频文件的大小（字节）
     * 必填字段，用于存储跟踪和验证
     */
    @Column(nullable = false)
    private Long size;

    /**
     * 视频时长（秒）
     * 可选字段，用于视频播放信息
     */
    private Long duration;

    /**
     * 视频宽度（像素）
     * 可选字段，用于视频分辨率信息
     */
    private Integer width;

    /**
     * 视频高度（像素）
     * 可选字段，用于视频分辨率信息
     */
    private Integer height;

    /**
     * 视频编解码器信息（例如H.264、VP9）
     * 可选字段，用于视频编码详情
     */
    @Column(length = 50)
    private String codec;

    /**
     * S3/MinIO对象键 - 唯一存储标识符
     * 必填且唯一的字段，表示对象存储中的完整路径
     * 已索引以在访问存储的视频时进行高效查找
     */
    @Column(name = "object_key", nullable = false, unique = true, length = 1000)
    private String objectKey;

    /**
     * 视频的上传状态（例如COMPLETED、FAILED、IN_PROGRESS）
     * 必填字段，在整个上传生命周期中跟踪
     * 已索引以支持高效的基于状态的查询
     */
    @Column(nullable = false, length = 50)
    private String status;

    /**
     * 已上传文件的校验和/ETag，用于完整性验证
     * 存储成功上传后S3返回的ETag
     */
    @Column(length = 255)
    private String checksum;

    /**
     * 记录创建时的时间戳
     * 在实体创建时自动设置
     * 已索引以支持高效的基于时间的查询和排序
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA生命周期回调 - 在实体持久化到数据库之前执行
     * 自动设置创建时间戳
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
