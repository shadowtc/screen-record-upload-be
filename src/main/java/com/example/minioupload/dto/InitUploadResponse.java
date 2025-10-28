package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片上传初始化响应的数据传输对象。
 * 
 * 包含客户端继续上传文件分片到S3/MinIO所需的所有信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitUploadResponse {

    /**
     * 来自S3/MinIO的唯一上传会话ID。
     * 必须包含在所有后续请求中（获取URL、完成、中止）。
     */
    private String uploadId;

    /**
     * 文件将存储的S3对象键。
     * 格式："uploads/{uuid}/{filename}"
     * 必须包含在所有后续请求中。
     */
    private String objectKey;

    /**
     * 每个分片/块的大小（字节）。
     * 客户端应将文件拆分为此大小的块进行上传。
     * 最后一个块可能小于此大小。
     */
    private long partSize;

    /**
     * 最小分片编号（始终为1）。
     * S3分片编号从1开始。
     */
    private int minPartNumber;

    /**
     * 完成上传所需的最大分片编号。
     * 计算方式：ceil(fileSize / partSize)
     * 客户端应上传从1到maxPartNumber的分片。
     */
    private int maxPartNumber;
}
