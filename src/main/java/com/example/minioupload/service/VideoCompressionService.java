package com.example.minioupload.service;

import com.example.minioupload.config.VideoCompressionProperties;
import com.example.minioupload.dto.VideoCompressionRequest;
import com.example.minioupload.dto.VideoCompressionResponse;
import com.example.minioupload.dto.CompressionProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 视频压缩服务类
 * 
 * 基于JavaCV和FFmpeg的视频压缩处理服务。支持同步和异步两种压缩模式，
 * 提供实时进度跟踪，支持多种预设配置和自定义压缩参数。
 * 
 * 主要功能：
 * - 视频文件格式转换和编码
 * - 支持多种压缩预设（高质量、均衡、高压缩、屏幕录制优化）
 * - 实时进度追踪和任务管理
 * - 分辨率、码率、CRF等参数的灵活配置
 * - 视频信息提取和分析
 * 
 * @author Video Compression Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoCompressionService {
    
    /** 视频压缩配置属性 */
    private final VideoCompressionProperties properties;
    
    /** 压缩任务进度追踪映射表，使用ConcurrentHashMap确保线程安全性 */
    private final ConcurrentHashMap<String, CompressionProgress> progressMap = new ConcurrentHashMap<>();
    
    /**
     * 异步视频压缩方法
     * 
     * 将视频压缩操作提交到独立的线程池中异步执行，避免阻塞主请求线程。
     * 适用于大文件或长时间运行的压缩任务。
     * 
     * @param request 视频压缩请求对象，包含输入文件路径和压缩参数
     * @return CompletableFuture<VideoCompressionResponse> 异步压缩响应对象
     *         成功时返回压缩结果和详细信息，失败时返回错误信息
     * 
     * @see VideoCompressionRequest
     * @see VideoCompressionResponse
     */
    @Async("videoCompressionExecutor")
    public CompletableFuture<VideoCompressionResponse> compressVideoAsync(VideoCompressionRequest request) {
        try {
            return CompletableFuture.completedFuture(compressVideo(request));
        } catch (Exception e) {
            log.error("Video compression failed", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 同步视频压缩方法（核心压缩逻辑）
     * 
     * 执行完整的视频压缩流程：
     * 1. 验证输入文件存在性
     * 2. 生成输出文件路径
     * 3. 提取原始视频信息（分辨率、码率、时长等）
     * 4. 应用压缩设置（合并预设和请求参数）
     * 5. 执行FFmpeg压缩处理，实时追踪进度
     * 6. 提取压缩后的视频信息
     * 7. 计算压缩效果指标
     * 
     * @param request 视频压缩请求对象
     * @return VideoCompressionResponse 压缩结果响应对象，包含：
     *         - jobId：任务唯一标识符
     *         - success：压缩是否成功
     *         - outputFilePath：压缩后文件路径
     *         - 原始和压缩后的文件大小、时长
     *         - 压缩率、压缩耗时
     *         - 视频详细信息
     * 
     * @throws IllegalArgumentException 当输入文件不存在时
     * @throws RuntimeException 当FFmpeg处理失败时
     */
    public VideoCompressionResponse compressVideo(VideoCompressionRequest request) {
        String jobId = UUID.randomUUID().toString();
        log.info("Starting video compression job: {} for file: {}", jobId, request.getInputFilePath());
        
        try {
            // Validate input file
            Path inputPath = Paths.get(request.getInputFilePath());
            if (!Files.exists(inputPath)) {
                throw new IllegalArgumentException("Input file does not exist: " + request.getInputFilePath());
            }
            
            // Create output path
            String outputFileName = generateOutputFileName(request.getInputFilePath(), request.getPreset());
            Path outputPath = Paths.get(properties.getTempDirectory(), outputFileName);
            Files.createDirectories(outputPath.getParent());
            
            // Initialize progress tracking
            CompressionProgress progress = new CompressionProgress(jobId, 0.0, "Starting compression...");
            progressMap.put(jobId, progress);
            
            // Get video info
            VideoInfo videoInfo = getVideoInfo(request.getInputFilePath());
            
            // Apply compression settings
            VideoCompressionSettings settings = applyCompressionSettings(request, videoInfo);
            
            // Perform compression
            long startTime = System.currentTimeMillis();
            compressVideoFile(inputPath.toString(), outputPath.toString(), settings, jobId);
            long compressionTime = System.currentTimeMillis() - startTime;
            
            // Get compressed video info
            VideoInfo compressedInfo = getVideoInfo(outputPath.toString());
            
            // Create response
            VideoCompressionResponse response = VideoCompressionResponse.builder()
                .jobId(jobId)
                .success(true)
                .outputFilePath(outputPath.toString())
                .originalSize(videoInfo.fileSize)
                .compressedSize(compressedInfo.fileSize)
                .compressionRatio(calculateCompressionRatio(videoInfo.fileSize, compressedInfo.fileSize))
                .originalDuration(videoInfo.duration)
                .compressionTimeMs(compressionTime)
                .settings(settings)
                .videoInfo(compressedInfo)
                .build();
            
            log.info("Video compression completed successfully. Job: {}, Output: {}", jobId, outputPath);
            return response;
            
        } catch (Exception e) {
            log.error("Video compression failed for job: {}", jobId, e);
            progressMap.put(jobId, new CompressionProgress(jobId, -1.0, "Error: " + e.getMessage()));
            
            return VideoCompressionResponse.builder()
                .jobId(jobId)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        } finally {
            // Clean up progress after some time
            CompletableFuture.delayedExecutor(60, java.util.concurrent.TimeUnit.MINUTES)
                .execute(() -> progressMap.remove(jobId));
        }
    }
    
    /**
     * 执行视频文件压缩处理（FFmpeg核心处理方法）
     * 
     * 使用JavaCV/FFmpeg逐帧处理视频，应用指定的压缩设置。此方法是整个压缩流程的核心，
     * 负责：
     * 1. 初始化视频帧读取器（FFmpegFrameGrabber）用于读取原始视频
     * 2. 初始化视频帧记录器（FFmpegFrameRecorder）用于输出压缩视频
     * 3. 配置编码参数（编码器、码率、分辨率、CRF值等）
     * 4. 逐帧读取原始视频并进行编码
     * 5. 实时更新压缩进度
     * 
     * 参数说明：
     * - videoCodec：视频编码器（如libx264表示H.264编码）
     * - videoBitrate：目标视频比特率，单位bps，影响最终文件大小
     * - audioCodec：音频编码器（如aac）
     * - audioBitrate：音频比特率，范围64k-320k
     * - CRF值：恒定质量因子(0-51)，越低质量越好，范围18-28通常可接受
     * - Preset：编码速度预设(ultrafast-veryslow)，越快编码越快但压缩效果越差
     * 
     * @param inputPath 输入视频文件的绝对路径
     * @param outputPath 输出视频文件的绝对路径
     * @param settings 压缩设置对象，包含所有编码参数
     * @param jobId 任务ID，用于进度跟踪和日志记录
     * 
     * @throws Exception FFmpeg处理异常
     */
    private void compressVideoFile(String inputPath, String outputPath,
                                  VideoCompressionSettings settings, String jobId) throws Exception {

        FFmpegFrameGrabber grabber = null;
        FFmpegFrameRecorder recorder = null;

        try {
            grabber = new FFmpegFrameGrabber(inputPath);
            grabber.start();

            // 获取输入视频的音频通道数
            int audioChannels = grabber.getAudioChannels();

            // 创建录制器，仅在输入有音频时才设置音频通道
            recorder = new FFmpegFrameRecorder(outputPath, audioChannels > 0 ? audioChannels : 0);

            // 设置录制器的视频尺寸
            recorder.setImageWidth(grabber.getImageWidth());
            recorder.setImageHeight(grabber.getImageHeight());

            // 配置视频编码器参数
            // 使用标准编码器常量
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setVideoBitrate(settings.getVideoBitrate());
            // CRF：0-51，建议值18-28，越低质量越好
            recorder.setVideoQuality(settings.getCrf());
            // 设置像素格式为YUV420P，这是最常见的输出格式
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setFrameRate(grabber.getVideoFrameRate());

            // 只在输入有音频时才配置音频编码参数
            if (audioChannels > 0) {
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                recorder.setAudioBitrate(settings.getAudioBitrate());
                recorder.setAudioChannels(audioChannels);
                recorder.setSampleRate(grabber.getSampleRate());
            }

            // 设置输出分辨率，如果指定了则进行缩放，否则保持原始分辨率
            if (settings.getWidth() > 0 && settings.getHeight() > 0) {
                recorder.setImageWidth(settings.getWidth());
                recorder.setImageHeight(settings.getHeight());
            } else {
                recorder.setImageWidth(grabber.getImageWidth());
                recorder.setImageHeight(grabber.getImageHeight());
            }

            // 设置编码器预设(ultrafast-veryslow)，控制编码速度和压缩效果的平衡
            recorder.setVideoOption("preset", settings.getPreset());

            // 设置编码线程数，0表示自动检测
            recorder.setVideoOption("threads", String.valueOf(settings.getThreads()));

            // 启动帧记录器，准备开始输出
            recorder.start();

            // 计算总帧数用于进度计算
            long totalFrames = grabber.getLengthInVideoFrames();
            AtomicLong processedFrames = new AtomicLong(0);

            // 逐帧读取和编码处理
            Frame frame;
            while ((frame = grabber.grab()) != null) {
                // 只记录视频帧，音频帧仅在配置了音频时才记录
                if (frame.image != null || (audioChannels > 0 && frame.samples != null)) {
                    recorder.record(frame);

                    // 仅在处理视频帧时更新进度
                    if (frame.image != null && totalFrames > 0) {
                        double progress = (double) processedFrames.incrementAndGet() / totalFrames * 100;
                        progressMap.put(jobId, new CompressionProgress(jobId, progress, "Compressing..."));
                    }
                }
            }

            // 标记压缩完成，进入最终化阶段
            progressMap.put(jobId, new CompressionProgress(jobId, 100.0, "Finalizing..."));

        } catch (Exception e) {
            throw new RuntimeException("Video compression failed", e);
        } finally {
            // 确保资源被正确释放
            if (recorder != null) {
                try {
                    recorder.close();
                } catch (Exception e) {
                    log.warn("Failed to close recorder", e);
                }
            }
            if (grabber != null) {
                try {
                    grabber.close();
                } catch (Exception e) {
                    log.warn("Failed to close grabber", e);
                }
            }
        }
    }
    
    /**
     * 提取视频文件信息
     * 
     * 使用FFmpeg解析视频文件并提取各种媒体信息，包括分辨率、帧率、码率、编码器等。
     * 此信息用于：
     * 1. 显示原始视频的详细信息
     * 2. 辅助确定合适的压缩参数
     * 3. 计算压缩效果指标
     * 
     * @param filePath 视频文件的绝对路径
     * @return VideoInfo 包含视频详细信息的对象，包括：
     *         - duration：视频时长（秒）
     *         - width/height：分辨率
     *         - frameRate：帧率（fps）
     *         - videoBitrate：视频码率（bps）
     *         - audioBitrate：音频码率（bps）
     *         - videoCodec/audioCodec：编码器名称
     *         - fileSize：文件大小（字节）
     *         - format：媒体格式
     * 
     * @throws Exception 当视频文件无法解析时
     */
    private VideoInfo getVideoInfo(String filePath) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(filePath)) {
            grabber.start();
            
            File file = new File(filePath);
            
            return VideoInfo.builder()
                // 将微秒转换为秒
                .duration(grabber.getLengthInTime() / 1000000.0)
                .width(grabber.getImageWidth())
                .height(grabber.getImageHeight())
                .frameRate(grabber.getVideoFrameRate())
                .videoBitrate(grabber.getVideoBitrate())
                .audioBitrate(grabber.getAudioBitrate())
                .videoCodec(grabber.getVideoCodecName())
                .audioCodec(grabber.getAudioCodecName())
                .fileSize(file.length())
                .format(grabber.getFormat())
                .build();
        }
    }
    
    /**
     * 应用压缩设置（参数合并策略）
     * 
     * 合并配置文件中的预设参数和请求中的自定义参数，生成最终的压缩设置。
     * 优先级顺序（从低到高）：
     * 1. 配置文件的默认编码参数
     * 2. 选定预设中的参数
     * 3. 请求中明确指定的参数（最高优先级）
     * 
     * 处理流程：
     * 1. 如果请求指定了预设，加载该预设的所有参数
     * 2. 应用预设对应的分辨率配置
     * 3. 使用请求中指定的参数覆盖预设参数
     * 4. 为空值字段填充默认配置
     * 
     * @param request 视频压缩请求对象
     * @param videoInfo 原始视频信息（可用于后续参数优化）
     * @return VideoCompressionSettings 最终的压缩设置对象
     */
    private VideoCompressionSettings applyCompressionSettings(VideoCompressionRequest request, VideoInfo videoInfo) {
        VideoCompressionSettings.VideoCompressionSettingsBuilder settingsBuilder = VideoCompressionSettings.builder();
        
        // 如果指定了预设，从配置中加载预设参数
        if (request.getPreset() != null && properties.getPresets().getProfiles() != null) {
            VideoCompressionProperties.PresetConfig.Preset preset = properties.getPresets().getProfiles().get(request.getPreset());
            if (preset != null) {
                settingsBuilder.videoBitrate(preset.getVideoBitrate());
                settingsBuilder.audioBitrate(preset.getAudioBitrate());
                settingsBuilder.videoCodec(preset.getCodec());
                settingsBuilder.preset(preset.getPreset());
                settingsBuilder.crf(preset.getCrf());
                settingsBuilder.twoPass(preset.isTwoPass());
                
                // 应用预设中指定的分辨率配置
                if (preset.getResolution() != null && properties.getResolution().getPresets() != null) {
                    VideoCompressionProperties.ResolutionConfig.Resolution resolution = properties.getResolution().getPresets().get(preset.getResolution());
                    if (resolution != null) {
                        settingsBuilder.width(resolution.getWidth());
                        settingsBuilder.height(resolution.getHeight());
                    }
                }
            }
        }
        
        // 使用请求中的参数覆盖预设参数（最高优先级）
        if (request.getVideoBitrate() != null) {
            settingsBuilder.videoBitrate(request.getVideoBitrate());
        }
        if (request.getAudioBitrate() != null) {
            settingsBuilder.audioBitrate(request.getAudioBitrate());
        }
        if (request.getWidth() != null) {
            settingsBuilder.width(request.getWidth());
        }
        if (request.getHeight() != null) {
            settingsBuilder.height(request.getHeight());
        }
        if (request.getCrf() != null) {
            settingsBuilder.crf(request.getCrf());
        }
        // 如果请求中的预设不是配置的预设，则作为编码器预设使用
        if (request.getPreset() != null && !properties.getPresets().getProfiles().containsKey(request.getPreset())) {
            settingsBuilder.preset(request.getPreset());
        }
        
        // 为仍为空的参数填充默认值
        VideoCompressionSettings settings = settingsBuilder.build();
        
        if (settings.getVideoCodec() == null) {
            settingsBuilder.videoCodec(properties.getEncoding().getDefaultCodec());
        }
        if (settings.getAudioCodec() == null) {
            settingsBuilder.audioCodec(properties.getEncoding().getDefaultAudioCodec());
        }
        if (settings.getPreset() == null) {
            settingsBuilder.preset(properties.getEncoding().getDefaultPreset());
        }
        if (settings.getCrf() == null) {
            settingsBuilder.crf(properties.getEncoding().getDefaultCRF());
        }
        if (settings.getAudioBitrate() == null) {
            settingsBuilder.audioBitrate(properties.getEncoding().getDefaultAudioBitrate());
        }
        if (settings.getThreads() == null) {
            settingsBuilder.threads(properties.getEncoding().getDefaultThreads());
        }
        
        return settingsBuilder.build();
    }
    
    /**
     * 生成输出文件名
     * 
     * 基于输入文件名和压缩预设生成输出文件名。保持原始扩展名不变，
     * 在文件名中插入 "_compressed" 和预设名称以区分多个压缩版本。
     * 
     * 示例：
     * - 输入：video.mp4，预设：balanced
     * - 输出：video_compressed_balanced.mp4
     * 
     * @param inputPath 输入文件的绝对路径
     * @param preset 使用的压缩预设名称（可为null）
     * @return 生成的输出文件名（不包含目录路径）
     */
    private String generateOutputFileName(String inputPath, String preset) {
        Path path = Paths.get(inputPath);
        String fileName = path.getFileName().toString();
        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
        String extension = fileName.substring(fileName.lastIndexOf('.'));
        
        if (preset != null) {
            return nameWithoutExt + "_compressed_" + preset + extension;
        } else {
            return nameWithoutExt + "_compressed" + extension;
        }
    }
    
    /**
     * 计算压缩率
     * 
     * 计算压缩后文件相对于原始文件的大小减少比例，用于评估压缩效果。
     * 公式：(原始大小 - 压缩后大小) / 原始大小 * 100%
     * 
     * @param originalSize 原始文件大小（字节）
     * @param compressedSize 压缩后文件大小（字节）
     * @return 压缩率百分比（0-100）。例如：如果返回50.0，表示文件缩小了50%
     */
    private double calculateCompressionRatio(long originalSize, long compressedSize) {
        if (originalSize == 0) return 0.0;
        return (double) (originalSize - compressedSize) / originalSize * 100;
    }
    
    /**
     * 获取压缩任务进度
     * 
     * 查询指定任务ID的当前压缩进度。可用于实时监控压缩进度，
     * 通常通过轮询或WebSocket推送方式由客户端调用。
     * 
     * @param jobId 压缩任务的唯一标识符
     * @return CompressionProgress 任务进度对象，包含进度百分比和状态信息
     *         如果任务不存在或已过期，返回null
     * 
     * @see CompressionProgress
     */
    public CompressionProgress getCompressionProgress(String jobId) {
        return progressMap.get(jobId);
    }
    
    /**
     * 视频压缩设置内部类
     * 
     * 包含所有FFmpeg编码参数的配置对象。这些参数直接传递给FFmpeg进行视频编码处理。
     * 
     * 参数说明：
     * - videoCodec：视频编码器（libx264、libx265等），决定最终视频格式和兼容性
     * - audioCodec：音频编码器（aac、mp3等）
     * - videoBitrate：目标视频比特率（bps），越高质量越好但文件越大
     *                范围建议：1M-5M（bps），根据分辨率和内容类型调整
     * - audioBitrate：目标音频比特率（bps），范围64k-320k，128k通常为合理值
     * - width/height：输出分辨率，0表示保持原始尺寸
     * - crf：恒定质量因子（0-51），越低质量越好，建议18-28
     *        0-17：无损/近似无损，不推荐（文件过大）
     *        18-24：高质量，适合存档
     *        25-28：平衡质量和文件大小
     *        29-51：低质量，不推荐（质量下降明显）
     * - preset：编码速度预设(ultrafast-veryslow)，影响编码时间和压缩效果
     *           ultrafast < superfast < veryfast < faster < fast < medium < slow < slower < veryslow
     * - twoPass：是否启用两遍编码，提高压缩效果但耗时更长
     * - threads：编码线程数，0表示自动检测
     */
    @lombok.Data
    @lombok.Builder
    public static class VideoCompressionSettings {
        /** 视频编码器名称（如libx264、libx265） */
        private String videoCodec;
        /** 音频编码器名称（如aac、mp3） */
        private String audioCodec;
        /** 目标视频比特率（单位：bits per second） */
        private Integer videoBitrate;
        /** 目标音频比特率（单位：bits per second） */
        private Integer audioBitrate;
        /** 输出视频宽度（0表示保持原始宽度） */
        private Integer width;
        /** 输出视频高度（0表示保持原始高度） */
        private Integer height;
        /** 恒定质量因子CRF（0-51，越低质量越好） */
        private Integer crf;
        /** 编码器预设名称（控制编码速度） */
        private String preset;
        /** 是否启用两遍编码 */
        private Boolean twoPass;
        /** 编码线程数（0表示自动检测） */
        private Integer threads;
    }
    
    /**
     * 视频信息内部类
     * 
     * 存储从视频文件中提取的各种媒体元数据，包括时长、分辨率、码率、编码器等信息。
     * 这些信息用于显示视频详情和制定压缩策略。
     */
    @lombok.Data
    @lombok.Builder
    public static class VideoInfo {
        /** 视频时长（单位：秒） */
        private double duration;
        /** 视频宽度（像素） */
        private int width;
        /** 视频高度（像素） */
        private int height;
        /** 视频帧率（fps） */
        private double frameRate;
        /** 视频比特率（bps） */
        private Integer videoBitrate;
        /** 音频比特率（bps） */
        private Integer audioBitrate;
        /** 视频编码器名称 */
        private String videoCodec;
        /** 音频编码器名称 */
        private String audioCodec;
        /** 文件大小（字节） */
        private long fileSize;
        /** 媒体容器格式（如mp4、mkv） */
        private String format;
    }
}