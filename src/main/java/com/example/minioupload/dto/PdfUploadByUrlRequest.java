package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通过URL上传PDF请求DTO
 * 用于从远程URL下载PDF并转换为图片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfUploadByUrlRequest {
    
    /**
     * PDF文件URL，必填
     */
    private String fileUrl;
    
    /**
     * 业务ID，必填，用于关联业务场景
     */
    private String businessId;
    
    /**
     * 用户ID，必填
     */
    private String userId;
    
    /**
     * 租户ID，必填
     */
    private String tenantId;
    
    /**
     * 需要转换的页码列表（可选）
     * 如果为空或null，则执行全量转换
     * 如果指定页码，则执行增量转换（需先完成全量转换）
     */
    private List<Integer> pages;
    
    /**
     * 图片DPI（分辨率），可选
     * 默认值由配置文件指定，推荐300 DPI
     */
    private Integer imageDpi;
    
    /**
     * 图片格式，可选
     * 支持：PNG、JPG等
     * 默认值由配置文件指定，推荐PNG格式
     */
    private String imageFormat;
}
