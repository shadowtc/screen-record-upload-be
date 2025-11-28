package com.example.minioupload.dto;

import lombok.Data;

import java.util.List;

/**
 * @Description 注释数据传输对象
 * @Author AI Assistant
 * @Date 2025/09/22
 * @Version 1.0
 **/
@Data
public class AnnotationDto {
    /**
     * 注释ID
     */
    private String id;

    /**
     * 注释类型
     */
    private String type;

    /**
     * 注释内容（待填充的值）
     */
    private String contents;
    /**
     * 标记的值
     */
    private String markValue;

    /**
     * 页面矩形坐标 [x1, y1, x2, y2]
     */
    private List<Double> rect;

    /**
     * PDF坐标 [x1, y1, x2, y2]
     */
    private List<Double> pdf;

    /**
     * 标准化坐标
     */
    private NormalizedDto normalized;

    /**
     * 作者
     */
    private String author;

    /**
     * 创建时间
     */
    private String creationDate;

    /**
     * 颜色
     */
    private List<Double> color;
}
