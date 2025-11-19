package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 异步上传进度DTO
 * 
 * 用于表示异步上传任务的实时状态和进度信息。
 * 客户端可以通过轮询此对象来获取上传任务的当前状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncUploadProgress {
    
    /**
     * 上传任务的唯一标识符
     */
    private String jobId;
    
    /**
     * 上传状态
     * SUBMITTED：已提交，等待开始
     * UPLOADING：正在上传中
     * COMPLETED：上传完成
     * FAILED：上传失败
     */
    private String status;
    
    /**
     * 上传进度百分比（0-100）
     * -1表示发生错误
     */
    private Double progress;
    
    /**
     * 状态消息，提供更详细的进度信息或错误描述
     */
    private String message;
    
    /**
     * 当前已上传的分片数
     */
    private Integer uploadedParts;
    
    /**
     * 总分片数
     */
    private Integer totalParts;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 上传完成后的响应（仅在COMPLETED状态时可用）
     */
    private CompleteUploadResponse uploadResponse;
    
    /**
     * 任务开始时间
     */
    private Instant startTime;
    
    /**
     * 任务结束时间（仅在COMPLETED或FAILED状态时可用）
     */
    private Instant endTime;
}
