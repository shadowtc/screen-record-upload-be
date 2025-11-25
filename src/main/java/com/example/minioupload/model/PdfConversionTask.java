package com.example.minioupload.model;

import jakarta.persistence.*;
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
@Entity
@Table(name = "pdf_conversion_task", indexes = {
    @Index(name = "idx_task_id", columnList = "task_id", unique = true),
    @Index(name = "idx_business_id", columnList = "business_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfConversionTask {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务唯一标识符（UUID）
     * 用于对外暴露的任务ID
     */
    @Column(name = "task_id", nullable = false, unique = true, length = 36)
    private String taskId;

    /**
     * 业务ID
     * 用于关联业务场景，支持相同业务的增量转换
     */
    @Column(name = "business_id", nullable = false, length = 100)
    private String businessId;

    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /**
     * 原始PDF文件名
     */
    @Column(nullable = false, length = 500)
    private String filename;

    /**
     * PDF文档总页数
     */
    @Column(name = "total_pages", nullable = false)
    private Integer totalPages;

    /**
     * 已转换的页码列表（JSON格式）
     * 用于增量转换时记录转换的页码
     */
    @Column(name = "converted_pages", columnDefinition = "TEXT")
    private String convertedPages;

    /**
     * PDF文件在对象存储中的键
     * 可用于存储MinIO或S3的对象键
     */
    @Column(name = "pdf_object_key", length = 1000)
    private String pdfObjectKey;

    /**
     * 任务状态
     * 可能的值：SUBMITTED(已提交)、PROCESSING(处理中)、COMPLETED(已完成)、FAILED(失败)
     */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * 是否为基础转换（全量转换）
     * true: 全量转换，转换所有页面
     * false: 增量转换，只转换指定页面
     */
    @Column(name = "is_base", nullable = false)
    private Boolean isBase;

    /**
     * 错误信息
     * 任务失败时记录错误详情
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 任务创建时间
     * 自动设置，不可更新
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 任务最后更新时间
     * 每次更新时自动刷新
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 实体创建前的回调
     * 自动设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * 实体更新前的回调
     * 自动更新最后更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
