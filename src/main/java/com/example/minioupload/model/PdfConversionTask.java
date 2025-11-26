package com.example.minioupload.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * PDF转换任务实体类
 * 用于持久化PDF转换任务的状态和元数据
 * 
 * 数据库表：pdf_conversion_task
 * 索引：
 * - idx_task_id: 任务ID唯一索引
 * - idx_business_id: 业务ID索引，用于按业务查询
 * - idx_user_id: 用户ID索引，用于按用户查询
 * - idx_status: 状态索引，用于查询特定状态的任务
 * - idx_created_at: 创建时间索引，用于时间范围查询
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("pdf_conversion_task")
public class PdfConversionTask {

    /**
     * 主键ID，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务唯一标识符（UUID）
     * 用于对外暴露的任务ID
     */
    @TableField("task_id")
    private String taskId;

    /**
     * 业务ID
     * 用于关联业务场景，支持相同业务的增量转换
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
     * 原始PDF文件名
     */
    @TableField("filename")
    private String filename;

    /**
     * PDF文档总页数
     */
    @TableField("total_pages")
    private Integer totalPages;

    /**
     * 已转换的页码列表（JSON格式）
     * 用于增量转换时记录转换的页码
     */
    @TableField("converted_pages")
    private String convertedPages;

    /**
     * PDF文件在对象存储中的键
     * 可用于存储MinIO或S3的对象键
     */
    @TableField("pdf_object_key")
    private String pdfObjectKey;

    /**
     * 任务状态
     * 可能的值：SUBMITTED(已提交)、PROCESSING(处理中)、COMPLETED(已完成)、FAILED(失败)
     */
    @TableField("status")
    private String status;

    /**
     * 是否为基础转换（全量转换）
     * true: 全量转换，转换所有页面
     * false: 增量转换，只转换指定页面
     */
    @TableField("is_base")
    private Boolean isBase;

    /**
     * 错误信息
     * 任务失败时记录错误详情
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 任务创建时间
     * 自动设置，不可更新
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 任务最后更新时间
     * 每次更新时自动刷新
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
