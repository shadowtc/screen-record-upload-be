package com.example.minioupload.service;

import com.example.minioupload.config.S3ConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;

/**
 * MinIO存储服务
 * 
 * 提供文件上传、下载、删除等基础操作
 * 
 * 主要功能：
 * 1. 上传文件到MinIO
 * 2. 生成预签名URL用于下载
 * 3. 删除MinIO中的文件
 * 4. 检查文件是否存在
 * 
 * 技术实现：
 * - 使用AWS SDK v2 S3Client
 * - 支持多种内容类型
 * - 自动检测文件MIME类型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {
    
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3ConfigProperties s3ConfigProperties;
    
    /**
     * 上传文件到MinIO
     * 
     * @param file 要上传的文件
     * @param objectKey MinIO中的对象键（路径）
     * @return 上传成功返回objectKey，失败抛出异常
     * @throws IOException 上传失败时抛出
     */
    public String uploadFile(File file, String objectKey) throws IOException {
        log.info("Uploading file to MinIO: {} -> {}", file.getName(), objectKey);
        
        String contentType = detectContentType(file);
        
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3ConfigProperties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(file.length())
                .build();
            
            s3Client.putObject(request, RequestBody.fromFile(file));
            
            log.info("File uploaded successfully: {} ({} bytes)", objectKey, file.length());
            return objectKey;
            
        } catch (S3Exception e) {
            log.error("Failed to upload file to MinIO: {}", objectKey, e);
            throw new IOException("MinIO upload failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 通过输入流上传文件到MinIO
     * 
     * @param inputStream 文件输入流
     * @param objectKey MinIO中的对象键
     * @param size 文件大小（字节）
     * @param contentType 内容类型
     * @return 上传成功返回objectKey
     * @throws IOException 上传失败时抛出
     */
    public String uploadInputStream(InputStream inputStream, String objectKey, long size, String contentType) throws IOException {
        log.info("Uploading stream to MinIO: {}, size: {}, type: {}", objectKey, size, contentType);
        
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3ConfigProperties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(size)
                .build();
            
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
            
            log.info("Stream uploaded successfully: {} ({} bytes)", objectKey, size);
            return objectKey;
            
        } catch (S3Exception e) {
            log.error("Failed to upload stream to MinIO: {}", objectKey, e);
            throw new IOException("MinIO upload failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除MinIO中的文件
     * 
     * @param objectKey 要删除的对象键
     * @throws IOException 删除失败时抛出
     */
    public void deleteFile(String objectKey) throws IOException {
        log.info("Deleting file from MinIO: {}", objectKey);
        
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(s3ConfigProperties.getBucket())
                .key(objectKey)
                .build();
            
            s3Client.deleteObject(request);
            
            log.info("File deleted successfully: {}", objectKey);
            
        } catch (S3Exception e) {
            log.error("Failed to delete file from MinIO: {}", objectKey, e);
            throw new IOException("MinIO delete failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量删除MinIO中的文件
     * 
     * @param objectKeys 要删除的对象键列表
     * @throws IOException 删除失败时抛出
     */
    public void deleteFiles(java.util.List<String> objectKeys) throws IOException {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        
        log.info("Batch deleting {} files from MinIO", objectKeys.size());
        
        try {
            java.util.List<ObjectIdentifier> objects = objectKeys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .collect(java.util.stream.Collectors.toList());
            
            Delete delete = Delete.builder()
                .objects(objects)
                .build();
            
            DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(s3ConfigProperties.getBucket())
                .delete(delete)
                .build();
            
            DeleteObjectsResponse response = s3Client.deleteObjects(request);
            
            log.info("Batch delete completed: {} deleted, {} errors", 
                response.deleted().size(), response.errors().size());
            
        } catch (S3Exception e) {
            log.error("Failed to batch delete files from MinIO", e);
            throw new IOException("MinIO batch delete failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查文件是否存在
     * 
     * @param objectKey 对象键
     * @return 存在返回true，否则返回false
     */
    public boolean fileExists(String objectKey) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(s3ConfigProperties.getBucket())
                .key(objectKey)
                .build();
            
            s3Client.headObject(request);
            return true;
            
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.error("Failed to check file existence: {}", objectKey, e);
            return false;
        }
    }
    
    /**
     * 获取文件的预签名下载URL
     * 
     * @param objectKey 对象键
     * @param expirationMinutes 过期时间（分钟）
     * @return 预签名URL
     */
    public String getPresignedUrl(String objectKey, int expirationMinutes) {
        log.debug("Generating presigned URL for: {}, expiration: {} minutes", objectKey, expirationMinutes);
        
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3ConfigProperties.getBucket())
                .key(objectKey)
                .build();
            
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();
            
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            
            String url = presignedRequest.url().toString();
            log.debug("Presigned URL generated: {}", url);
            return url;
            
        } catch (S3Exception e) {
            log.error("Failed to generate presigned URL: {}", objectKey, e);
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检测文件的内容类型
     * 
     * @param file 文件
     * @return MIME类型
     * @throws IOException 检测失败时抛出
     */
    private String detectContentType(File file) throws IOException {
        String contentType = Files.probeContentType(file.toPath());
        
        if (contentType == null) {
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".pdf")) {
                contentType = "application/pdf";
            } else if (fileName.endsWith(".png")) {
                contentType = "image/png";
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (fileName.endsWith(".gif")) {
                contentType = "image/gif";
            } else {
                contentType = "application/octet-stream";
            }
        }
        
        return contentType;
    }
}
