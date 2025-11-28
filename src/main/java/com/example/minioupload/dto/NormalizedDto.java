package com.example.minioupload.dto;

import lombok.Data;

/**
 * @Description 标准化坐标数据传输对象
 * @Author AI Assistant
 * @Date 2025/09/22
 * @Version 1.0
 **/
@Data
public class NormalizedDto {
    /**
     * x坐标
     */
    private String x;

    /**
     * y坐标
     */
    private String y;

    /**
     * 宽度
     */
    private String width;

    /**
     * 高度
     */
    private String height;
    /**
     * 定位方式
     */
    private String basePoint;
}
