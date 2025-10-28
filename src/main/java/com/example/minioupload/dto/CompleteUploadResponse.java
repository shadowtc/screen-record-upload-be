package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 已完成上传响应的数据传输对象。
 * 
 * 包含成功上传文件的元数据，包括数据库记录信息和下载URL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadResponse {

    /**
     * 视频录制记录的数据库ID。
     * 可用于未来的查询和更新。
     */
    private Long id;

    /**
     * 已上传文件的原始文件名。
     * 从对象键中提取。
     */
    private String filename;

    /**
     * 已上传文件的总大小（字节）。
     * 完成后从S3/MinIO获取。
     */
    private Long size;

    /**
     * 文件存储的S3对象键。
     * 可用于以编程方式访问文件。
     */
    private String objectKey;

    /**
     * 上传状态（例如"COMPLETED"）。
     * 指示上传的最终状态。
     */
    private String status;

    /**
     * 用于访问文件的预签名下载URL。
     * 具有时间限制的URL，在配置的持续时间后过期。
     * 允许从S3/MinIO直接下载而无需身份验证。
     */
    private String downloadUrl;

    /**
     * 记录在数据库中创建时的时间戳。
     * 在持久化期间自动设置。
     */
    private LocalDateTime createdAt;
}
