package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步服务端分片上传初始化响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsyncUploadStartResponse {
    /** 服务器端上传任务ID */
    private String jobId;
    /** 预期在MinIO中的对象键（uploads/{uuid}/{filename}） */
    private String objectKey;
    /** 可读的信息 */
    private String message;
}
