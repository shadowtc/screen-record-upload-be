package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于中止分片上传的数据传输对象。
 * 
 * 用于取消上传并从S3/MinIO清理所有已上传的分片。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbortUploadRequest {

    /**
     * 来自初始化的上传会话ID。
     * 必填字段 - 不能为空。
     */
    @NotBlank(message = "uploadId is required")
    private String uploadId;

    /**
     * 来自初始化的S3对象键。
     * 必填字段 - 不能为空。
     */
    @NotBlank(message = "objectKey is required")
    private String objectKey;
}
