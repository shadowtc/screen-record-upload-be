package com.example.minioupload.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 关键字实体类
 * 用于存储和管理内容过滤的关键字
 * 
 * 数据库表：keyword
 * 索引：
 * - uk_keyword: 关键字唯一索引
 * - idx_enabled: 启用状态索引
 * - idx_category: 分类索引
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("keyword")
public class Keyword {

    /**
     * 主键ID，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关键字内容
     * 最大长度200字符，必须唯一
     */
    @TableField("keyword")
    private String keyword;

    /**
     * 关键字分类
     * 用于分类管理，如：politics(政治)、illegal(违法)、prohibited(违禁)等
     */
    @TableField("category")
    private String category;

    /**
     * 启用状态
     * true(1): 启用，参与过滤
     * false(0): 禁用，不参与过滤
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 创建时间
     * 自动设置，不可更新
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     * 每次更新时自动刷新
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
