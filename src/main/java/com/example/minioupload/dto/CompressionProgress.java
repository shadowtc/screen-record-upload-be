package com.example.minioupload.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 视频压缩进度DTO
 * 
 * 用于返回正在进行的压缩任务的实时进度信息。
 * 客户端可通过轮询或WebSocket获取最新的进度。
 * 
 * 进度值范围及含义：
 * - 0-100：表示压缩进度百分比
 * - -1：表示发生错误，查看status字段获取错误信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompressionProgress {
    
    /**
     * 压缩任务的唯一标识符
     * 用于关联对应的压缩任务
     */
    private String jobId;
    
    /**
     * 当前压缩进度
     * 
     * 可能的值：
     * - 0.0-100.0：进度百分比
     *              0.0：刚开始，还未处理任何帧
     *              50.0：已处理完一半的视频
     *              100.0：处理完所有帧，进入最终化
     * - -1.0：发生错误，查看status获取错误信息
     */
    private double progress;
    
    /**
     * 进度状态描述
     * 
     * 示例值：
     * - "Starting compression..."：初始化
     * - "Compressing..."：正在压缩
     * - "Finalizing..."：最终化处理
     * - "Completed"：完成
     * - "Error: ..."：错误信息
     */
    private String status;
    
    /**
     * 进度信息的时间戳（毫秒）
     * 客户端可用此信息检测任务是否卡顿
     */
    private long timestamp;
    
    /**
     * 构造函数：创建进度对象并自动设置时间戳
     * 
     * @param jobId 任务ID
     * @param progress 进度百分比（0-100）或-1表示错误
     * @param status 状态描述文本
     */
    public CompressionProgress(String jobId, double progress, String status) {
        this.jobId = jobId;
        this.progress = progress;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 检查是否发生错误
     * 
     * @return 如果进度值为负数则表示发生错误，返回true
     */
    public boolean isError() {
        return progress < 0;
    }
    
    /**
     * 检查压缩是否已完成
     * 
     * @return 如果进度值≥100则表示已完成，返回true
     */
    public boolean isComplete() {
        return progress >= 100;
    }
}