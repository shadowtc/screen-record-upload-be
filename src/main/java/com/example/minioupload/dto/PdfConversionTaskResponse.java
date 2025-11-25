package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PDF转换任务响应DTO
 * 用于返回PDF转换任务的详细信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfConversionTaskResponse {
    
    /**
     * 任务唯一标识符
     */
    private String taskId;
    
    /**
     * 业务ID，用于关联业务场景
     */
    private String businessId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 原始文件名
     */
    private String filename;
    
    /**
     * PDF文档总页数
     */
    private Integer totalPages;
    
    /**
     * 已转换的页码列表（增量转换时使用）
     */
    private List<Integer> convertedPages;
    
    /**
     * PDF文件在MinIO中的对象键
     */
    private String pdfObjectKey;
    
    /**
     * PDF文件预签名URL（用于下载）
     */
    private String pdfUrl;
    
    /**
     * 任务状态：SUBMITTED(已提交)、PROCESSING(处理中)、COMPLETED(已完成)、FAILED(失败)
     */
    private String status;
    
    /**
     * 是否为基础转换（全量转换）
     * true表示全量转换，false表示增量转换
     */
    private Boolean isBase;
    
    /**
     * 错误信息（任务失败时）
     */
    private String errorMessage;
    
    /**
     * 任务创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 任务最后更新时间
     */
    private LocalDateTime updatedAt;
}
