package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于初始化分片上传的数据传输对象。
 * 
 * 此请求包含启动新上传会话所需的所有信息，包括文件元数据和可选的上传配置。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitUploadRequest {

    /**
     * 要上传的文件的原始文件名。
     * 必填字段 - 不能为空。
     */
    @NotBlank(message = "fileName is required")
    private String fileName;

    /**
     * 文件的总大小（字节）。
     * 必填字段 - 必须为正数。
     * 用于计算所需的分片数并验证最大文件大小。
     */
    @NotNull(message = "size is required")
    @Positive(message = "size must be positive")
    private Long size;

    /**
     * 文件的MIME内容类型（例如"video/mp4"）。
     * 必填字段 - 不能为空。
     * 必须以"video/"开头才能通过验证。
     */
    @NotBlank(message = "contentType is required")
    private String contentType;

    /**
     * 可选的自定义分片/块大小（字节）。
     * 如果未提供，将使用服务器的默认分片大小。
     * 最小值：5MB（S3要求）
     * 最大值：5GB（S3限制）
     */
    private Long chunkSize;
}
