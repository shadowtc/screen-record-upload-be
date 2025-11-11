package com.example.minioupload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "video.compression")
public class VideoCompressionProperties {
    
    private boolean enabled = true;
    private String tempDirectory = "/tmp/video-compression";
    private int maxConcurrentJobs = 2;
    private long maxFileSize = 1073741824L; // 1GB
    private long timeoutSeconds = 300L; // 5 minutes
    
    private PresetConfig presets = new PresetConfig();
    private EncodingConfig encoding = new EncodingConfig();
    private ResolutionConfig resolution = new ResolutionConfig();
    
    @Data
    public static class PresetConfig {
        private Map<String, Preset> profiles;
        
        @Data
        public static class Preset {
            private String name;
            private String description;
            private int videoBitrate;
            private int audioBitrate = 128000;
            private String resolution;
            private String codec = "libx264";
            private String preset = "medium";
            private int crf = 23;
            private boolean twoPass = false;
        }
    }
    
    @Data
    public static class EncodingConfig {
        private String defaultCodec = "libx264";
        private String defaultPreset = "medium";
        private int defaultCRF = 23;
        private int defaultAudioBitrate = 128000;
        private String defaultAudioCodec = "aac";
        private int defaultThreads = 0; // 0 = auto
    }
    
    @Data
    public static class ResolutionConfig {
        private Map<String, Resolution> presets;
        
        @Data
        public static class Resolution {
            private String name;
            private int width;
            private int height;
            private int recommendedBitrate;
            private String description;
        }
    }
}