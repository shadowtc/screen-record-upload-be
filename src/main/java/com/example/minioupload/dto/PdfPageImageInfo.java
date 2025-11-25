package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PDF页面图片信息DTO
 * 用于表示单个PDF页面转换后的图片元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfPageImageInfo {
    
    /**
     * 页码（从1开始）
     */
    private Integer pageNumber;
    
    /**
     * 图片对象存储键（可能是文件路径或MinIO对象键）
     */
    private String imageObjectKey;
    
    /**
     * 图片预签名URL（用于直接访问）
     */
    private String imageUrl;
    
    /**
     * 是否为基础转换的图片
     * true表示来自全量转换，false表示来自增量转换
     */
    private Boolean isBase;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 图片宽度（像素）
     */
    private Integer width;
    
    /**
     * 图片高度（像素）
     */
    private Integer height;
    
    /**
     * 图片文件大小（字节）
     */
    private Long fileSize;
}
