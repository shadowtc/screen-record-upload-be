package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 预签名上传URL响应的数据传输对象。
 * 
 * 包含客户端可用于通过HTTP PUT直接上传特定分片到S3/MinIO的具有时间限制的URL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {

    /**
     * 此URL对应的分片编号（从1开始）。
     * 上传时必须与分片编号匹配。
     */
    private int partNumber;

    /**
     * 用于上传此分片的预签名URL。
     * 客户端应向此URL发送带有分片数据的HTTP PUT请求。
     * 响应将包含必须保存以供完成的ETag标头。
     */
    private String url;

    /**
     * 此URL过期时的时间戳。
     * 过期后，URL无法使用，必须请求新的URL。
     */
    private Instant expiresAt;
}
