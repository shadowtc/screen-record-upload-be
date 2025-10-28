package com.example.minioupload.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for S3/MinIO connection settings.
 * 
 * This class binds configuration values from application.yml (prefix: s3)
 * to strongly-typed Java properties. All values can be overridden by
 * environment variables for deployment flexibility.
 * 
 * These properties are used to configure the AWS SDK S3 client and pre-signer
 * for communicating with S3-compatible storage (MinIO, AWS S3, etc.).
 */
@Component
@ConfigurationProperties(prefix = "s3")
@Getter
@Setter
public class S3ConfigProperties {
    
    /**
     * S3/MinIO endpoint URL.
     * For MinIO: http://hostname:9000
     * For AWS S3: leave default or use regional endpoint
     */
    private String endpoint;
    
    /**
     * Access key for S3/MinIO authentication.
     * Equivalent to AWS Access Key ID.
     */
    private String accessKey;
    
    /**
     * Secret key for S3/MinIO authentication.
     * Equivalent to AWS Secret Access Key.
     */
    private String secretKey;
    
    /**
     * Name of the S3 bucket where files will be stored.
     * Must exist before application starts.
     */
    private String bucket;
    
    /**
     * AWS region for S3 operations.
     * For MinIO, can be any valid region string (e.g., us-east-1).
     * For AWS S3, must match the bucket's region.
     */
    private String region;
    
    /**
     * Whether to use path-style access for S3 URLs.
     * 
     * Path-style: http://endpoint/bucket/key
     * Virtual-hosted-style: http://bucket.endpoint/key
     * 
     * MinIO requires path-style access (true).
     * AWS S3 supports both but recommends virtual-hosted-style (false).
     */
    private boolean pathStyleAccess;
}
