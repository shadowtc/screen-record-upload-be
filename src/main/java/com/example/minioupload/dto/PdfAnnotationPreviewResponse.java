package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PDF注解预览响应
 * 返回基类图片和渲染后的图片
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfAnnotationPreviewResponse {
    
    /**
     * 响应状态：SUCCESS, ERROR, NOT_FOUND
     */
    private String status;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 业务ID
     */
    private String businessId;
    
    /**
     * 租户ID
     */
    private String tenantId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 总页数
     */
    private Integer totalPages;
    
    /**
     * 渲染的页面数量
     */
    private Integer renderedPages;
    
    /**
     * 页面图片列表（包含原始和渲染后的图片）
     */
    private List<PageImageInfo> images;
    
    /**
     * 单个页面图片信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PageImageInfo {
        /**
         * 页码
         */
        private Integer pageNumber;
        
        /**
         * 图片访问URL（预签名URL）
         */
        private String imageUrl;
        
        /**
         * 图片对象键（MinIO）
         */
        private String imageObjectKey;
        
        /**
         * 是否为渲染后的图片
         */
        private Boolean isRendered;
        
        /**
         * 是否来自基类任务
         */
        private Boolean isBase;
        
        /**
         * 图片宽度
         */
        private Integer width;
        
        /**
         * 图片高度
         */
        private Integer height;
    }
}
