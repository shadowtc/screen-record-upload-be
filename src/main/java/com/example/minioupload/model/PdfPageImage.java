package com.example.minioupload.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * PDF页面图片实体类
 * 用于持久化PDF转换后的页面图片信息
 * 
 * 数据库表：pdf_page_image
 * 索引：
 * - idx_task_id: 任务ID索引，用于按任务查询所有页面
 * - idx_business_id: 业务ID索引，用于按业务查询所有页面
 * - idx_is_base: 基础标识索引，用于区分全量和增量转换的图片
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("pdf_page_image")
public class PdfPageImage {

    /**
     * 主键ID，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的转换任务ID
     * 对应PdfConversionTask表的taskId
     */
    @TableField("task_id")
    private String taskId;

    /**
     * 业务ID
     * 用于业务级别的图片查询和合并
     */
    @TableField("business_id")
    private String businessId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 租户ID
     */
    @TableField("tenant_id")
    private String tenantId;

    /**
     * 页码（从1开始）
     * 表示该图片对应PDF的第几页
     */
    @TableField("page_number")
    private Integer pageNumber;

    /**
     * 图片对象存储键
     * 可以是本地文件路径或MinIO/S3的对象键
     */
    @TableField("image_object_key")
    private String imageObjectKey;

    /**
     * 是否为基础转换的图片
     * true: 来自全量转换
     * false: 来自增量转换（后续更新）
     */
    @TableField("is_base")
    private Boolean isBase;

    /**
     * 图片宽度（像素）
     */
    @TableField("width")
    private Integer width;

    /**
     * 图片高度（像素）
     */
    @TableField("height")
    private Integer height;

    /**
     * PDF页面宽度（PDF点，1点=1/72英寸）
     */
    @TableField("pdf_width")
    private Double pdfWidth;

    /**
     * PDF页面高度（PDF点，1点=1/72英寸）
     */
    @TableField("pdf_height")
    private Double pdfHeight;

    /**
     * 渲染DPI（用于坐标转换）
     */
    @TableField("rendering_dpi")
    private Integer renderingDpi;

    /**
     * 图片文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 图片创建时间
     * 自动设置，不可更新
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
