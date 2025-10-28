package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已上传分片信息的数据传输对象。
 * 
 * 包含来自S3/MinIO的成功上传分片的元数据。
 * 用于通知客户端哪些分片已上传以实现可恢复性。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadPartInfo {

    /**
     * 分片编号（从1开始）。
     */
    private int partNumber;

    /**
     * 来自S3/MinIO的已上传分片的ETag。
     * 这是用于验证分片完整性的哈希/校验和。
     * 完成分片上传所必需的。
     */
    private String etag;

    /**
     * 已上传分片的大小（字节）。
     * 用于计算上传进度。
     */
    private long size;
}
