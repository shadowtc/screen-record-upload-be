package com.example.minioupload.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Spring configuration class for S3/MinIO client beans.
 * 
 * This configuration creates and configures AWS SDK v2 beans for:
 * 1. S3Client - for standard S3 operations (create, complete, abort uploads)
 * 2. S3Presigner - for generating pre-signed URLs
 * 
 * Both beans are configured with the same credentials and endpoint from
 * S3ConfigProperties, ensuring consistent behavior across the application.
 * 
 * The configuration supports both AWS S3 and S3-compatible storage like MinIO.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class S3Config {

    /**
     * Injected S3/MinIO configuration properties
     */
    private final S3ConfigProperties s3ConfigProperties;

    /**
     * Creates and configures the S3 client bean.
     * 
     * This client is used for standard S3 operations such as:
     * - Creating multipart uploads
     * - Completing multipart uploads
     * - Aborting multipart uploads
     * - Listing upload parts
     * - Retrieving object metadata
     * 
     * Configuration includes:
     * - Custom endpoint for MinIO compatibility
     * - AWS region setting
     * - Static credentials (access key + secret key)
     * - Path-style access for MinIO
     * 
     * @return configured S3Client instance
     */
    @Bean
    public S3Client s3Client() {
        log.info("Initializing S3Client with endpoint: {}, region: {}, bucket: {}", 
                s3ConfigProperties.getEndpoint(), 
                s3ConfigProperties.getRegion(),
                s3ConfigProperties.getBucket());
        
        // Create credentials from configuration
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3ConfigProperties.getAccessKey(),
                s3ConfigProperties.getSecretKey()
        );

        // Build and configure S3 client
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(s3ConfigProperties.getEndpoint()))
                .region(Region.of(s3ConfigProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(s3ConfigProperties.isPathStyleAccess())
                .build();
        
        log.info("S3Client initialized successfully");
        return client;
    }

    /**
     * Creates and configures the S3 pre-signer bean.
     * 
     * This pre-signer is used to generate time-limited, signed URLs that allow:
     * - Clients to upload parts directly to S3/MinIO
     * - Clients to download files directly from S3/MinIO
     * 
     * Pre-signed URLs eliminate the need for clients to have AWS credentials
     * and reduce server bandwidth by enabling direct client-to-S3 transfers.
     * 
     * Configuration includes:
     * - Custom endpoint for MinIO compatibility
     * - AWS region setting
     * - Static credentials (access key + secret key)
     * 
     * Note: Unlike S3Client, S3Presigner doesn't support forcePathStyle() method,
     * but it works correctly with MinIO when the endpoint is properly configured.
     * 
     * @return configured S3Presigner instance
     */
    @Bean
    public S3Presigner s3Presigner() {
        log.info("Initializing S3Presigner");
        
        // Create credentials from configuration
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3ConfigProperties.getAccessKey(),
                s3ConfigProperties.getSecretKey()
        );

        // Build and configure S3 pre-signer
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(s3ConfigProperties.getEndpoint()))
                .region(Region.of(s3ConfigProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
        
        log.info("S3Presigner initialized successfully");
        return presigner;
    }
}
