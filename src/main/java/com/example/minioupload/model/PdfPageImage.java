package com.example.minioupload.model;

import jakarta.persistence.*;
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
@Entity
@Table(name = "pdf_page_image", indexes = {
    @Index(name = "idx_task_id", columnList = "task_id"),
    @Index(name = "idx_business_id", columnList = "business_id"),
    @Index(name = "idx_is_base", columnList = "is_base")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfPageImage {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的转换任务ID
     * 对应PdfConversionTask表的taskId
     */
    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    /**
     * 业务ID
     * 用于业务级别的图片查询和合并
     */
    @Column(name = "business_id", nullable = false, length = 100)
    private String businessId;

    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /**
     * 页码（从1开始）
     * 表示该图片对应PDF的第几页
     */
    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    /**
     * 图片对象存储键
     * 可以是本地文件路径或MinIO/S3的对象键
     */
    @Column(name = "image_object_key", nullable = false, length = 1000)
    private String imageObjectKey;

    /**
     * 是否为基础转换的图片
     * true: 来自全量转换
     * false: 来自增量转换（后续更新）
     */
    @Column(name = "is_base", nullable = false)
    private Boolean isBase;

    /**
     * 图片宽度（像素）
     */
    @Column
    private Integer width;

    /**
     * 图片高度（像素）
     */
    @Column
    private Integer height;

    /**
     * 图片文件大小（字节）
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * 图片创建时间
     * 自动设置，不可更新
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 实体创建前的回调
     * 自动设置创建时间
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
