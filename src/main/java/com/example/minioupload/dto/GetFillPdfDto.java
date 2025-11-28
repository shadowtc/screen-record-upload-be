package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Description 获取随机填充的pdfDto
 * @Author tongwl
 * @Date 2025/9/22 14:03
 * @Version 1.0
 **/
@Data
public class GetFillPdfDto {
    /**
     * 文件路径
     */
    @NotBlank(message = "文件路径")
    private String objectName;
    /**
     * 标记的json串
     */
    @NotBlank(message = "标记的json串")
    private String markJson;
}
