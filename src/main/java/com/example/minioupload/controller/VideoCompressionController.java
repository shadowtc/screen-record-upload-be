package com.example.minioupload.controller;

import com.example.minioupload.dto.CompressionProgress;
import com.example.minioupload.dto.VideoCompressionRequest;
import com.example.minioupload.dto.VideoCompressionResponse;
import com.example.minioupload.service.VideoCompressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 视频压缩控制器
 * 
 * 提供视频压缩相关的REST API端点。支持同步和异步两种压缩模式，
 * 提供进度查询和预设列表等接口。
 * 
 * API端点：
 * - POST /api/video/compress - 同步压缩视频
 * - POST /api/video/compress/async - 异步压缩视频
 * - GET /api/video/progress/{jobId} - 查询压缩进度
 * - GET /api/video/presets - 获取可用预设列表
 * 
 * @author Video Compression Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
@Validated
public class VideoCompressionController {
    
    /** 视频压缩服务 */
    private final VideoCompressionService videoCompressionService;
    
    /**
     * 同步视频压缩端点
     * 
     * 执行同步压缩操作，请求会阻塞直到压缩完成。适用于较小文件或对响应时间要求不高的场景。
     * 
     * HTTP方法：POST
     * 路径：/api/video/compress
     * 
     * 请求体示例：
     * {
     *   "inputFilePath": "/path/to/video.mp4",
     *   "preset": "balanced",
     *   "videoBitrate": 2500000,
     *   "audioBitrate": 128000,
     *   "width": 1280,
     *   "height": 720
     * }
     * 
     * 响应示例（成功）：
     * {
     *   "jobId": "550e8400-e29b-41d4-a716-446655440000",
     *   "success": true,
     *   "outputFilePath": "/tmp/video-compression/video_compressed_balanced.mp4",
     *   "originalSize": 1073741824,
     *   "compressedSize": 268435456,
     *   "compressionRatio": 75.0,
     *   "compressionTimeMs": 120000
     * }
     * 
     * @param request 视频压缩请求对象，必须包含inputFilePath
     * @return ResponseEntity<VideoCompressionResponse> 压缩结果
     *         - HTTP 200：压缩成功，返回详细结果
     *         - HTTP 500：压缩失败，返回错误信息
     * 
     * @see VideoCompressionRequest
     * @see VideoCompressionResponse
     */
    @PostMapping("/compress")
    public ResponseEntity<VideoCompressionResponse> compressVideo(
            @Valid @RequestBody VideoCompressionRequest request) {
        
        log.info("Received video compression request for file: {}", request.getInputFilePath());
        
        try {
            // 执行同步压缩
            VideoCompressionResponse response = videoCompressionService.compressVideo(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Video compression failed", e);
            VideoCompressionResponse errorResponse = VideoCompressionResponse.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 异步视频压缩端点
     * 
     * 提交异步压缩任务。请求立即返回任务ID，客户端可通过轮询或回调方式获取压缩结果。
     * 适用于大文件或需要非阻塞处理的场景。
     * 
     * HTTP方法：POST
     * 路径：/api/video/compress/async
     * 
     * 请求体与同步接口相同，但响应立即返回。
     * 
     * 响应示例（已提交）：
     * {
     *   "jobId": "550e8400-e29b-41d4-a716-446655440000",
     *   "success": true,
     *   "status": "SUBMITTED"
     * }
     * 
     * 然后通过 GET /api/video/progress/{jobId} 查询进度
     * 
     * @param request 视频压缩请求对象
     * @return ResponseEntity<VideoCompressionResponse> 异步响应（立即返回）
     *         - HTTP 202：任务已提交
     *         - HTTP 400：请求参数无效
     *         - HTTP 500：提交失败
     */
    @PostMapping("/compress/async")
    public ResponseEntity<VideoCompressionResponse> compressVideoAsync(
            @Valid @RequestBody VideoCompressionRequest request) {
        
        log.info("Received async video compression request for file: {}", request.getInputFilePath());
        
        try {
            // 提交异步任务（不等待完成）
            String jobId = videoCompressionService.submitCompressionJob(request);
            
            // 立即返回，包含任务ID供客户端轮询
            VideoCompressionResponse response = VideoCompressionResponse.builder()
                .jobId(jobId)
                .success(true)
                .status("SUBMITTED")
                .build();
            
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Failed to submit async video compression", e);
            VideoCompressionResponse errorResponse = VideoCompressionResponse.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 获取压缩进度端点
     * 
     * 查询指定任务ID的当前压缩进度。通常用于轮询方式追踪压缩进度。
     * 
     * HTTP方法：GET
     * 路径：/api/video/progress/{jobId}
     * 
     * 响应示例：
     * {
     *   "jobId": "550e8400-e29b-41d4-a716-446655440000",
     *   "progress": 45.5,
     *   "status": "Compressing...",
     *   "timestamp": 1699000000000
     * }
     * 
     * 进度值说明：
     * - 0-100：压缩中
     * - 100：已完成
     * - -1：错误发生
     * 
     * @param jobId 压缩任务的唯一标识符
     * @return ResponseEntity<CompressionProgress> 进度信息
     *         - HTTP 200：任务存在，返回进度
     *         - HTTP 404：任务不存在或已过期
     */
    @GetMapping("/progress/{jobId}")
    public ResponseEntity<CompressionProgress> getCompressionProgress(@PathVariable String jobId) {
        // 查询任务进度
        CompressionProgress progress = videoCompressionService.getCompressionProgress(jobId);
        
        if (progress == null) {
            // 任务不存在或已过期
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(progress);
    }
    
    /**
     * 获取可用预设列表端点
     * 
     * 返回所有可用的压缩预设、分辨率和编码器预设列表。
     * 客户端可用此信息呈现可选项给用户选择。
     * 
     * HTTP方法：GET
     * 路径：/api/video/presets
     * 
     * 响应示例：
     * {
     *   "presets": ["high-quality", "balanced", "high-compression", "screen-recording"],
     *   "resolutions": ["480p", "720p", "1080p", "1440p", "4k"],
     *   "encoderPresets": ["ultrafast", "superfast", "veryfast", "faster", "fast", 
     *                      "medium", "slow", "slower", "veryslow"]
     * }
     * 
     * 预设说明：
     * - high-quality：最高质量，最大文件大小，最长编码时间
     * - balanced：质量与文件大小的平衡
     * - high-compression：最小文件大小，最短编码时间，质量下降
     * - screen-recording：针对屏幕录制内容优化
     * 
     * @return ResponseEntity<Object> 可用预设列表
     */
    @GetMapping("/presets")
    public ResponseEntity<Object> getAvailablePresets() {
        // 返回所有可用的压缩预设和相关选项
        return ResponseEntity.ok(new Object() {
            // 压缩预设：预定义的参数组合
            public final String[] presets = {"high-quality", "balanced", "high-compression", "screen-recording"};
            // 分辨率预设：常用的输出分辨率
            public final String[] resolutions = {"480p", "720p", "1080p", "1440p", "4k"};
            // 编码器预设：控制编码速度和压缩效果
            public final String[] encoderPresets = {
                "ultrafast",   // 编码最快，压缩效果最差
                "superfast", 
                "veryfast", 
                "faster", 
                "fast", 
                "medium",      // 平衡
                "slow", 
                "slower", 
                "veryslow"     // 编码最慢，压缩效果最好
            };
        });
    }
}