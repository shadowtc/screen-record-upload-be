package com.example.minioupload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for MinIO Multipart Upload Service.
 * 
 * This application provides RESTful APIs for handling large file uploads to MinIO/S3
 * using multipart upload functionality with resumable capabilities.
 * 
 * Key features:
 * - Resumable multipart uploads to MinIO/S3
 * - Pre-signed URL generation for direct client uploads
 * - Upload status tracking and management
 * - Video recording metadata storage in MySQL 8.0
 * - Support for large file uploads with configurable chunk sizes
 * 
 * The application uses:
 * - Spring Boot 3.2.0 with Java 17
 * - MySQL 8.0 for persistent storage
 * - AWS SDK v2 for S3-compatible operations
 * - HikariCP for optimized database connection pooling
 */
@SpringBootApplication
public class MinioUploadApplication {

    /**
     * Application entry point.
     * Initializes the Spring Boot application context and starts the embedded web server.
     * 
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(MinioUploadApplication.class, args);
    }
}
