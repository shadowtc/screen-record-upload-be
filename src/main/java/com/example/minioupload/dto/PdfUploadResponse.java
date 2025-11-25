package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PDF上传响应DTO
 * 用于返回PDF文件上传后的任务信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfUploadResponse {
    
    /**
     * 任务ID，用于后续查询进度和结果
     */
    private String taskId;
    
    /**
     * 上传状态：PROCESSING(处理中)、ERROR(错误)
     */
    private String status;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * PDF文档总页数（如果已获取）
     */
    private Integer totalPages;
}
