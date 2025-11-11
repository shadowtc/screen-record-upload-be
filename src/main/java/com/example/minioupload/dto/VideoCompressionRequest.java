package com.example.minioupload.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Data
public class VideoCompressionRequest {
    
    @NotNull(message = "Input file path is required")
    private String inputFilePath;
    
    private String preset; // e.g., "high-quality", "balanced", "high-compression"
    
    @Min(value = 500000, message = "Video bitrate must be at least 500k")
    @Max(value = 5000000, message = "Video bitrate must not exceed 5000k")
    private Integer videoBitrate; // in bits per second
    
    @Min(value = 64000, message = "Audio bitrate must be at least 64k")
    @Max(value = 320000, message = "Audio bitrate must not exceed 320k")
    private Integer audioBitrate; // in bits per second
    
    private Integer width;
    private Integer height;
    
    @Min(value = 0, message = "CRF must be between 0 and 51")
    @Max(value = 51, message = "CRF must be between 0 and 51")
    private Integer crf; // Constant Rate Factor (0-51, lower = better quality)
    
    private String encoderPreset; // ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow
    
    private String outputFormat; // Default: mp4
    
    private Boolean twoPass; // Two-pass encoding
    
    private Boolean deleteOriginal; // Whether to delete original file after compression
}