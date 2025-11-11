package com.example.minioupload.dto;

import com.example.minioupload.service.VideoCompressionService;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class VideoCompressionResponse {
    
    private String jobId;
    private boolean success;
    private String outputFilePath;
    private long originalSize;
    private long compressedSize;
    private double compressionRatio; // Percentage reduction
    private double originalDuration;
    private long compressionTimeMs;
    private VideoCompressionService.VideoCompressionSettings settings;
    private VideoCompressionService.VideoInfo videoInfo;
    private String errorMessage;
    
    // Additional metadata
    private String status; // completed, failed
    private long timestamp;
    
    public VideoCompressionResponse() {
        this.timestamp = System.currentTimeMillis();
    }
}