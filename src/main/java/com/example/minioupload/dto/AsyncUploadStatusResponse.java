package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步服务端分片上传进度/状态响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsyncUploadStatusResponse {
    private String jobId;
    private String status; // QUEUED, UPLOADING, COMPLETED, FAILED
    private double progress; // 0-100
    private Integer uploadedParts;
    private Integer totalParts;
    private Long uploadedBytes;
    private Long totalBytes;
    private String objectKey;
    private String errorMessage;
    private String downloadUrl; // 完成后返回
}
