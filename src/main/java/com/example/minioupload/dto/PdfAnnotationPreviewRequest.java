package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * PDF注解预览请求
 * 用于在基类PDF图片上渲染注解
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfAnnotationPreviewRequest {
    
    /**
     * 业务ID，用于查找基类任务
     */
    private String businessId;
    
    /**
     * 租户ID
     */
    private String tenantId;
    
    /**
     * 用户ID（可选）
     */
    private String userId;
    
    /**
     * 导出时间
     */
    private LocalDateTime exportTime;
    
    /**
     * 总注解数量
     */
    private Integer totalAnnotations;
    
    /**
     * 按页码分组的注解信息
     * Key: 页码（字符串）
     * Value: 该页的注解列表
     */
    private Map<String, java.util.List<PageAnnotation>> pageAnnotations;

    /**
     * 与 pageAnnotations 内容一致的原始 markJson 字符串
     * 如果未提供则由后端根据 pageAnnotations 自动生成
     */
    private String markJson;
    
    /**
     * 单个页面注解
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PageAnnotation {
        /**
         * 注解ID
         */
        private String id;
        
        /**
         * 注解索引
         */
        private Integer index;
        
        /**
         * 注解内容（要渲染的文字）
         */
        private String contents;
        
        /**
         * 标记值
         */
        private String markValue;
        
        /**
         * 页码
         */
        private String pageNumber;
        
        /**
         * 渲染坐标（PDF坐标系）[x1, y1, x2, y2]
         */
        private double[] pdf;

        /**
         * 页面矩形坐标 [x1, y1, x2, y2]
         */
        private double[] rect;

        /**
         * 缩放比例
         */
        private Double scale;
        
        /**
         * 标准化坐标信息
         */
        private NormalizedRect normalized;
    }
    
    /**
     * 标准化矩形信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NormalizedRect {
        private String x;
        private String y;
        private String width;
        private String height;
        private String basePoint;
    }
}
