package com.example.minioupload.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

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
     * 要上传的文件。
     * 必填字段 - 不能为空。
     * 文件名、大小和内容类型从MultipartFile中自动提取。
     */
    @NotNull(message = "file is required")
    private MultipartFile file;

    /**
     * 可选的自定义分片/块大小（字节）。
     * 如果未提供，将使用服务器的默认分片大小。
     * 最小值：5MB（S3要求）
     * 最大值：5GB（S3限制）
     */
    @Positive(message = "chunkSize must be positive")
    private Long chunkSize;
}
