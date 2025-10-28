package com.example.minioupload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "upload")
public class UploadConfigProperties {
    
    private long maxFileSize;
    private long defaultChunkSize;
    private int presignedUrlExpirationMinutes;

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public long getDefaultChunkSize() {
        return defaultChunkSize;
    }

    public void setDefaultChunkSize(long defaultChunkSize) {
        this.defaultChunkSize = defaultChunkSize;
    }

    public int getPresignedUrlExpirationMinutes() {
        return presignedUrlExpirationMinutes;
    }

    public void setPresignedUrlExpirationMinutes(int presignedUrlExpirationMinutes) {
        this.presignedUrlExpirationMinutes = presignedUrlExpirationMinutes;
    }
}
