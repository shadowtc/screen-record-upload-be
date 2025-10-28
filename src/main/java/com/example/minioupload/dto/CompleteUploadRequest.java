package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用于完成分片上传的数据传输对象。
 * 
 * 包含通过将已上传的分片组装成S3/MinIO中的单个对象来完成上传所需的所有信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRequest {

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

    /**
     * 包含ETag的所有已上传分片的列表。
     * 必须包含至少一个分片。
     * 分片应按顺序排列（1、2、3...）。
     * 每个ETag必须与S3/MinIO在分片上传期间返回的内容匹配。
     */
    @NotEmpty(message = "parts list cannot be empty")
    private List<PartETag> parts;
}
