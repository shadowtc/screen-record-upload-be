package com.example.minioupload.dto;

import com.example.minioupload.service.VideoCompressionService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * 视频压缩响应DTO
 * 
 * 返回给客户端的视频压缩结果。包含压缩成功/失败状态和详细的处理结果。
 * 在异步模式下，任务提交时也会返回此对象，其中仅包含jobId和success字段。
 */
@Data
@Builder
@AllArgsConstructor
public class VideoCompressionResponse {
    
    /**
     * 压缩任务的唯一标识符
     * 用于追踪任务和查询进度
     */
    private String jobId;
    
    /**
     * 压缩是否成功完成
     * true：压缩成功，其他字段有效
     * false：压缩失败，查看errorMessage获取错误信息
     */
    private boolean success;
    
    /**
     * 压缩后输出文件的绝对路径
     * 仅在success=true时有效
     */
    private String outputFilePath;
    
    /**
     * 原始输入文件的大小（字节）
     * 用于计算压缩率
     */
    private long originalSize;
    
    /**
     * 压缩后输出文件的大小（字节）
     * 用于计算压缩率和文件缩小幅度
     */
    private long compressedSize;
    
    /**
     * 压缩率百分比
     * 计算公式：(原始大小 - 压缩后大小) / 原始大小 * 100
     * 
     * 示例：
     * - 100.0：文件完全消失（错误情况）
     * - 75.0：文件缩小到原来的25%，压缩率75%
     * - 50.0：文件缩小到原来的50%，压缩率50%
     * - 0.0：文件大小不变或增加（不推荐压缩）
     */
    private double compressionRatio;
    
    /**
     * 原始输入视频的时长（秒）
     */
    private double originalDuration;
    
    /**
     * 压缩处理耗时（毫秒）
     * 包括FFmpeg初始化、逐帧编码和最终化的总时间
     * 不包括网络传输和磁盘I/O延迟
     */
    private long compressionTimeMs;
    
    /**
     * 本次压缩使用的所有编码参数
     * 包括编码器、码率、分辨率、CRF等详细配置
     * 客户端可用此信息验证使用的参数是否符合预期
     */
    private VideoCompressionService.VideoCompressionSettings settings;
    
    /**
     * 压缩后视频的详细信息
     * 包括分辨率、帧率、码率、编码器等元数据
     * 用于验证压缩结果是否符合预期
     */
    private VideoCompressionService.VideoInfo videoInfo;
    
    /**
     * 错误消息
     * 仅在success=false时包含具体的错误描述
     */
    private String errorMessage;
    
    /**
     * 任务状态
     * 可能值：
     * - pending：等待处理
     * - processing：正在处理
     * - completed：处理完成
     * - failed：处理失败
     */
    private String status;
    
    /**
     * 响应时间戳（毫秒）
     * 服务器生成响应的系统时间
     */
    private long timestamp;

    /**
     * 构造函数：初始化时间戳
     * 在响应创建时自动设置当前时间
     */
    public VideoCompressionResponse() {
        this.timestamp = System.currentTimeMillis();
    }
}