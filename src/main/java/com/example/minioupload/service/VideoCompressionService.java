package com.example.minioupload.service;

import com.example.minioupload.config.VideoCompressionProperties;
import com.example.minioupload.dto.VideoCompressionRequest;
import com.example.minioupload.dto.VideoCompressionResponse;
import com.example.minioupload.dto.CompressionProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoCompressionService {
    
    private final VideoCompressionProperties properties;
    private final ConcurrentHashMap<String, CompressionProgress> progressMap = new ConcurrentHashMap<>();
    
    @Async("videoCompressionExecutor")
    public CompletableFuture<VideoCompressionResponse> compressVideoAsync(VideoCompressionRequest request) {
        try {
            return CompletableFuture.completedFuture(compressVideo(request));
        } catch (Exception e) {
            log.error("Video compression failed", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
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
    
    private void compressVideoFile(String inputPath, String outputPath, 
                                 VideoCompressionSettings settings, String jobId) throws Exception {
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath);
             FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, 
                     grabber.getAudioChannels(), grabber.getVideoWidth(), grabber.getVideoHeight())) {
            
            grabber.start();
            
            // Configure recorder
            recorder.setVideoCodec(settings.getVideoCodec());
            recorder.setAudioCodec(settings.getAudioCodec());
            recorder.setVideoBitrate(settings.getVideoBitrate());
            recorder.setAudioBitrate(settings.getAudioBitrate());
            recorder.setVideoQuality(settings.getCrf());
            recorder.setPixelFormat(avcodec.AV_PIX_FMT_YUV420P);
            recorder.setFrameRate(grabber.getVideoFrameRate());
            
            // Set resolution if specified
            if (settings.getWidth() > 0 && settings.getHeight() > 0) {
                recorder.setImageWidth(settings.getWidth());
                recorder.setImageHeight(settings.getHeight());
            } else {
                recorder.setImageWidth(grabber.getVideoWidth());
                recorder.setImageHeight(grabber.getVideoHeight());
            }
            
            // Set encoder preset
            recorder.setVideoOption("preset", settings.getPreset());
            
            // Set threads
            recorder.setVideoOption("threads", String.valueOf(settings.getThreads()));
            
            recorder.start();
            
            // Progress tracking
            long totalFrames = grabber.getLengthInVideoFrames();
            AtomicLong processedFrames = new AtomicLong(0);
            
            Frame frame;
            while ((frame = grabber.grab()) != null) {
                recorder.record(frame);
                
                // Update progress
                if (totalFrames > 0) {
                    double progress = (double) processedFrames.incrementAndGet() / totalFrames * 100;
                    progressMap.put(jobId, new CompressionProgress(jobId, progress, "Compressing..."));
                }
            }
            
            progressMap.put(jobId, new CompressionProgress(jobId, 100.0, "Finalizing..."));
            
        } catch (Exception e) {
            throw new RuntimeException("Video compression failed", e);
        }
    }
    
    private VideoInfo getVideoInfo(String filePath) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(filePath)) {
            grabber.start();
            
            File file = new File(filePath);
            
            return VideoInfo.builder()
                .duration(grabber.getLengthInTime() / 1000000.0) // Convert to seconds
                .width(grabber.getVideoWidth())
                .height(grabber.getVideoHeight())
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
    
    private VideoCompressionSettings applyCompressionSettings(VideoCompressionRequest request, VideoInfo videoInfo) {
        VideoCompressionSettings settings = new VideoCompressionSettings();
        
        // Use preset if specified
        if (request.getPreset() != null && properties.getPresets().getProfiles() != null) {
            var preset = properties.getPresets().getProfiles().get(request.getPreset());
            if (preset != null) {
                settings.setVideoBitrate(preset.getVideoBitrate());
                settings.setAudioBitrate(preset.getAudioBitrate());
                settings.setCodec(preset.getCodec());
                settings.setPreset(preset.getPreset());
                settings.setCrf(preset.getCrf());
                settings.setTwoPass(preset.isTwoPass());
                
                // Apply resolution from preset
                if (preset.getResolution() != null && properties.getResolution().getPresets() != null) {
                    var resolution = properties.getResolution().getPresets().get(preset.getResolution());
                    if (resolution != null) {
                        settings.setWidth(resolution.getWidth());
                        settings.setHeight(resolution.getHeight());
                    }
                }
            }
        }
        
        // Override with request parameters
        if (request.getVideoBitrate() != null) {
            settings.setVideoBitrate(request.getVideoBitrate());
        }
        if (request.getAudioBitrate() != null) {
            settings.setAudioBitrate(request.getAudioBitrate());
        }
        if (request.getWidth() != null) {
            settings.setWidth(request.getWidth());
        }
        if (request.getHeight() != null) {
            settings.setHeight(request.getHeight());
        }
        if (request.getCrf() != null) {
            settings.setCrf(request.getCrf());
        }
        if (request.getPreset() != null && !properties.getPresets().getProfiles().containsKey(request.getPreset())) {
            settings.setPreset(request.getPreset());
        }
        
        // Set defaults if still null
        if (settings.getVideoCodec() == null) {
            settings.setVideoCodec(properties.getEncoding().getDefaultCodec());
        }
        if (settings.getAudioCodec() == null) {
            settings.setAudioCodec(properties.getEncoding().getDefaultAudioCodec());
        }
        if (settings.getPreset() == null) {
            settings.setPreset(properties.getEncoding().getDefaultPreset());
        }
        if (settings.getCrf() == null) {
            settings.setCrf(properties.getEncoding().getDefaultCRF());
        }
        if (settings.getAudioBitrate() == null) {
            settings.setAudioBitrate(properties.getEncoding().getDefaultAudioBitrate());
        }
        if (settings.getThreads() == null) {
            settings.setThreads(properties.getEncoding().getDefaultThreads());
        }
        
        return settings;
    }
    
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
    
    private double calculateCompressionRatio(long originalSize, long compressedSize) {
        if (originalSize == 0) return 0.0;
        return (double) (originalSize - compressedSize) / originalSize * 100;
    }
    
    public CompressionProgress getCompressionProgress(String jobId) {
        return progressMap.get(jobId);
    }
    
    @lombok.Data
    @lombok.Builder
    public static class VideoCompressionSettings {
        private String videoCodec;
        private String audioCodec;
        private Integer videoBitrate;
        private Integer audioBitrate;
        private Integer width;
        private Integer height;
        private Integer crf;
        private String preset;
        private Boolean twoPass;
        private Integer threads;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class VideoInfo {
        private double duration;
        private int width;
        private int height;
        private double frameRate;
        private Integer videoBitrate;
        private Integer audioBitrate;
        private String videoCodec;
        private String audioCodec;
        private long fileSize;
        private String format;
    }
}