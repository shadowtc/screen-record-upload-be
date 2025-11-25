package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PDF转换进度DTO
 * 用于实时查询PDF转换任务的处理进度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfConversionProgress {
    
    /**
     * 任务ID
     */
    private String jobId;
    
    /**
     * 任务状态：SUBMITTED(已提交)、PROCESSING(处理中)、COMPLETED(已完成)、FAILED(失败)、NOT_FOUND(未找到)
     */
    private String status;
    
    /**
     * 当前处理阶段描述
     */
    private String currentPhase;
    
    /**
     * PDF文档总页数
     */
    private Integer totalPages;
    
    /**
     * 已处理的页数
     */
    private Integer processedPages;
    
    /**
     * 进度百分比（0-100）
     */
    private Integer progressPercentage;
    
    /**
     * 进度消息
     */
    private String message;
    
    /**
     * 错误信息（失败时）
     */
    private String errorMessage;
    
    /**
     * 任务开始时间（时间戳）
     */
    private Long startTime;
    
    /**
     * 已耗时（毫秒）
     */
    private Long elapsedTimeMs;
}
