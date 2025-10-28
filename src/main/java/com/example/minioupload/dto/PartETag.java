package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片ETag信息的数据传输对象。
 * 
 * 在完成上传请求中使用，以指定已上传的分片及其对应的ETag以供验证。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartETag {

    /**
     * 分片编号（从1开始）。
     * 必须为正数且对应于已上传的分片。
     */
    @Positive(message = "partNumber must be positive")
    private int partNumber;

    /**
     * S3/MinIO在上传分片后返回的ETag。
     * 这是从上传响应的ETag标头中提取的。
     * S3需要此信息来正确验证和组装分片。
     */
    @NotBlank(message = "eTag is required")
    private String eTag;
}
