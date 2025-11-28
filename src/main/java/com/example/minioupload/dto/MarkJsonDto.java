package com.example.minioupload.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @Description MarkJson数据传输对象
 * @Author AI Assistant
 * @Date 2025/09/22
 * @Version 1.0
 **/
@Data
public class MarkJsonDto {
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 总页数
     */
    private Integer totalPages;
    
    /**
     * 总注释数
     */
    private Integer totalAnnotations;
    
    /**
     * 导出时间
     */
    private String exportTime;
    
    /**
     * 页面注释映射，key为页码，value为该页的注释列表
     */
    private Map<String, List<AnnotationDto>> pageAnnotations;
}