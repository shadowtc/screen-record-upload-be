package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PDF图片分页查询响应DTO
 * 用于返回PDF转换后的图片列表，支持分页查询
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfImageResponse {
    
    /**
     * 业务ID
     */
    private String businessId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 总页数
     */
    private Integer totalPages;
    
    /**
     * 起始页码（从1开始）
     */
    private Integer startPage;
    
    /**
     * 每页大小
     */
    private Integer pageSize;
    
    /**
     * 实际返回的页数
     */
    private Integer returnedPages;
    
    /**
     * 页面图片信息列表
     */
    private List<PdfPageImageInfo> images;
    
    /**
     * 响应状态：SUCCESS(成功)、ERROR(错误)、NOT_FOUND(未找到)
     */
    private String status;
    
    /**
     * 响应消息
     */
    private String message;
}
