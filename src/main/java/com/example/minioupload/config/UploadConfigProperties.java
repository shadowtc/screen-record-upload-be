package com.example.minioupload.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for file upload constraints and defaults.
 * 
 * This class binds configuration values from application.yml (prefix: upload)
 * to strongly-typed Java properties. These settings control upload behavior
 * and limits across the application.
 * 
 * All values can be overridden by environment variables for deployment flexibility.
 */
@Component
@ConfigurationProperties(prefix = "upload")
@Getter
@Setter
public class UploadConfigProperties {
    
    /**
     * Maximum allowed file size in bytes.
     * 
     * Files larger than this will be rejected during upload initialization.
     * Default: 2GB (2147483648 bytes)
     * 
     * Consider available storage and network bandwidth when setting this value.
     */
    private long maxFileSize;
    
    /**
     * Default chunk/part size for multipart uploads in bytes.
     * 
     * This size is used when the client doesn't specify a chunk size.
     * Smaller chunks allow for more granular progress tracking but increase
     * the number of HTTP requests. Larger chunks reduce overhead but may
     * impact resumability on slow networks.
     * 
     * Default: 8MB (8388608 bytes)
     * Minimum for S3: 5MB (except last part)
     * Maximum for S3: 5GB
     */
    private long defaultChunkSize;
    
    /**
     * Expiration time for pre-signed URLs in minutes.
     * 
     * Applies to both upload (part URLs) and download URLs.
     * After expiration, URLs cannot be used and must be regenerated.
     * 
     * Default: 60 minutes
     * 
     * Balance security (shorter expiration) with user experience
     * (longer expiration for slow uploads).
     */
    private int presignedUrlExpirationMinutes;
}
