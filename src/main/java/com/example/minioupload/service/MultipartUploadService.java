package com.example.minioupload.service;

import com.example.minioupload.config.S3ConfigProperties;
import com.example.minioupload.config.UploadConfigProperties;
import com.example.minioupload.dto.*;
import com.example.minioupload.model.VideoRecording;
import com.example.minioupload.repository.VideoRecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 处理S3/MinIO分片上传操作的服务类。
 * 
 * 此服务为可恢复的分片上传提供全面支持，包括：
 * - 带验证的分片上传初始化
 * - 用于客户端直接上传的预签名URL生成
 * - 上传状态跟踪和分片验证
 * - 带MySQL元数据持久化的上传完成
 * - 用于清理的上传中止
 * 
 * 该服务针对大文件上传进行了优化：
 * - 使用可配置的分片大小
 * - 生成预签名URL以避免服务器端上传瓶颈
 * - 在S3中跟踪上传进度以实现可恢复性
 * - 仅在MySQL中存储元数据以实现高效查询
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MultipartUploadService {

    /**
     * 用于执行S3操作的S3客户端（创建、完成、中止分片上传）
     */
    private final S3Client s3Client;
    
    /**
     * 用于生成具有时间限制的预签名URL的S3预签名器
     */
    private final S3Presigner s3Presigner;
    
    /**
     * S3/MinIO连接的配置属性
     */
    private final S3ConfigProperties s3Config;
    
    /**
     * 上传约束和默认值的配置属性
     */
    private final UploadConfigProperties uploadConfig;
    
    /**
     * 用于将视频录制元数据持久化到MySQL的仓库
     */
    private final VideoRecordingRepository videoRecordingRepository;

    /**
     * S3中上传目录前缀的常量
     */
    private static final String UPLOADS_PREFIX = "uploads/";
    
    /**
     * 已完成上传状态的常量
     */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /**
     * 验证分片列表的有效性。
     * 
     * 此方法验证：
     * 1. 分片编号必须从1开始
     * 2. 分片编号必须是连续的（1, 2, 3...）
     * 3. 分片编号不能重复
     * 4. 每个分片必须有有效的ETag
     * 
     * @param parts 要验证的分片列表
     * @throws IllegalArgumentException 如果验证失败
     */
    private void validateParts(List<PartETag> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("Parts list cannot be null or empty");
        }
        
        Set<Integer> partNumbers = new HashSet<>();
        int expectedPartNumber = 1;
        
        for (PartETag part : parts) {
            // 验证分片编号为正数
            if (part.getPartNumber() <= 0) {
                throw new IllegalArgumentException("Part numbers must be positive, got: " + part.getPartNumber());
            }
            
            // 验证ETag不为空
            if (part.getETag() == null || part.getETag().trim().isEmpty()) {
                throw new IllegalArgumentException("ETag cannot be null or empty for part: " + part.getPartNumber());
            }
            
            // 验证分片编号不重复
            if (!partNumbers.add(part.getPartNumber())) {
                throw new IllegalArgumentException("Duplicate part number found: " + part.getPartNumber());
            }
            
            expectedPartNumber++;
        }
        
        // 按分片编号排序后再验证连续性
        List<Integer> sortedPartNumbers = new ArrayList<>(partNumbers);
        sortedPartNumbers.sort(Integer::compareTo);
        
        for (int i = 0; i < sortedPartNumbers.size(); i++) {
            int expectedNumber = i + 1;
            int actualNumber = sortedPartNumbers.get(i);
            
            if (actualNumber != expectedNumber) {
                throw new IllegalArgumentException(
                        String.format("Parts must be consecutive. Expected part %d, but found %d", 
                                expectedNumber, actualNumber));
            }
        }
        
        log.debug("Parts validation passed for {} parts", parts.size());
    }

    /**
     * 在S3/MinIO中初始化新的分片上传会话。
     * 
     * 此方法执行以下操作：
     * 1. 从MultipartFile中提取文件元数据（名称、大小、内容类型）
     * 2. 验证文件类型（仅允许视频文件）
     * 3. 根据配置的最大值验证文件大小
     * 4. 生成基于UUID路径的唯一S3对象键
     * 5. 在S3中创建分片上传会话
     * 6. 根据分片大小计算最佳分片数
     * 
     * @param request 包含MultipartFile和可选分片大小的上传初始化请求
     * @return 包含uploadId、objectKey和分片信息的InitUploadResponse
     * @throws IllegalArgumentException 如果文件类型不是视频或大小超出限制
     */
    public InitUploadResponse initializeUpload(InitUploadRequest request) {
        // 从MultipartFile中提取文件元数据
        String fileName = request.getFile().getOriginalFilename();
        long fileSize = request.getFile().getSize();
        String contentType = request.getFile().getContentType();
        
        log.info("Initializing upload for file: {}, size: {} bytes", fileName, fileSize);
        
        // 验证文件名不为空
        if (fileName == null || fileName.trim().isEmpty()) {
            log.warn("Upload rejected: file name is empty");
            throw new IllegalArgumentException("File name cannot be empty");
        }
        
        // 验证文件类型 - 仅接受视频文件
        if (contentType == null || !contentType.startsWith("video/")) {
            log.warn("Upload rejected: invalid content type {}", contentType);
            throw new IllegalArgumentException("Only video files are allowed");
        }

        // 根据配置的最大值验证文件大小
        if (fileSize > uploadConfig.getMaxFileSize()) {
            log.warn("Upload rejected: file size {} exceeds maximum {}", fileSize, uploadConfig.getMaxFileSize());
            throw new IllegalArgumentException("File size exceeds maximum allowed size");
        }

        // 确定分片大小：使用客户端提供的值或回退到配置的默认值
        long chunkSize = request.getChunkSize() != null && request.getChunkSize() > 0
                ? request.getChunkSize()
                : uploadConfig.getDefaultChunkSize();

        // 验证分片大小是否符合S3要求
        // S3要求：除了最后一个分片，其他分片必须至少5MB
        final long MIN_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB
        final long MAX_CHUNK_SIZE = 5L * 1024 * 1024 * 1024; // 5GB (S3限制)
        
        if (chunkSize < MIN_CHUNK_SIZE) {
            log.warn("Upload rejected: chunk size {} is below minimum requirement {}", chunkSize, MIN_CHUNK_SIZE);
            throw new IllegalArgumentException("Chunk size must be at least 5MB (5242880 bytes)");
        }
        
        if (chunkSize > MAX_CHUNK_SIZE) {
            log.warn("Upload rejected: chunk size {} exceeds maximum {}", chunkSize, MAX_CHUNK_SIZE);
            throw new IllegalArgumentException("Chunk size cannot exceed 5GB (5368709120 bytes)");
        }

        // 生成带有UUID的唯一对象键以防止冲突
        String objectKey = UPLOADS_PREFIX + UUID.randomUUID() + "/" + fileName;

        // 在S3/MinIO中创建分片上传会话
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(s3Config.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
        log.info("Multipart upload initialized with uploadId: {}, objectKey: {}", createResponse.uploadId(), objectKey);

        // 计算上传所需的总分片数
        int maxPartNumber = (int) Math.ceil((double) fileSize / chunkSize);

        return new InitUploadResponse(
                createResponse.uploadId(),
                objectKey,
                chunkSize,
                1,
                maxPartNumber
        );
    }

    /**
     * 为上传分片上传的特定分片生成预签名URL。
     * 
     * 预签名URL允许客户端直接上传分片到S3/MinIO而无需通过应用程序服务器，这样可以：
     * - 减少服务器带宽和处理
     * - 提高上传性能
     * - 为失败的分片启用客户端重试逻辑
     * 
     * 每个URL都具有时间限制并特定于一个分片编号。
     * 
     * @param uploadId 来自S3的分片上传ID
     * @param objectKey 上传的S3对象键
     * @param startPartNumber 要生成URL的第一个分片编号（包含）
     * @param endPartNumber 要生成URL的最后一个分片编号（包含）
     * @return 包含URL和元数据的PresignedUrlResponse对象列表
     */
    public List<PresignedUrlResponse> generatePresignedUrls(
            String uploadId,
            String objectKey,
            int startPartNumber,
            int endPartNumber) {
        
        log.info("Generating pre-signed URLs for uploadId: {}, parts: {} to {}", uploadId, startPartNumber, endPartNumber);

        // 验证输入参数
        if (uploadId == null || uploadId.trim().isEmpty()) {
            throw new IllegalArgumentException("UploadId cannot be null or empty");
        }
        
        if (objectKey == null || objectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("ObjectKey cannot be null or empty");
        }
        
        if (startPartNumber <= 0) {
            throw new IllegalArgumentException("Start part number must be positive, got: " + startPartNumber);
        }
        
        if (endPartNumber < startPartNumber) {
            throw new IllegalArgumentException(
                    String.format("End part number (%d) must be greater than or equal to start part number (%d)", 
                            endPartNumber, startPartNumber));
        }
        
        // 限制一次请求的分片数量以防止过大的响应
        final int MAX_PARTS_PER_REQUEST = 100;
        int partsRequested = endPartNumber - startPartNumber + 1;
        if (partsRequested > MAX_PARTS_PER_REQUEST) {
            throw new IllegalArgumentException(
                    String.format("Cannot request more than %d parts in a single request. Requested: %d", 
                            MAX_PARTS_PER_REQUEST, partsRequested));
        }

        List<PresignedUrlResponse> presignedUrls = new ArrayList<>(partsRequested);
        Duration expiration = Duration.ofMinutes(uploadConfig.getPresignedUrlExpirationMinutes());
        Instant expiresAt = Instant.now().plus(expiration);

        // 为指定范围内的每个分片生成预签名URL
        for (int partNumber = startPartNumber; partNumber <= endPartNumber; partNumber++) {
            try {
                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(s3Config.getBucket())
                        .key(objectKey)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build();

                UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                        .signatureDuration(expiration)
                        .uploadPartRequest(uploadPartRequest)
                        .build();

                PresignedUploadPartRequest presignedRequest = s3Presigner.presignUploadPart(presignRequest);

                presignedUrls.add(new PresignedUrlResponse(
                        partNumber,
                        presignedRequest.url().toString(),
                        expiresAt
                ));
            } catch (Exception e) {
                log.error("Failed to generate presigned URL for part {} of uploadId: {}", partNumber, uploadId, e);
                throw new RuntimeException("Failed to generate presigned URL for part " + partNumber, e);
            }
        }

        log.info("Generated {} pre-signed URLs expiring at {}", presignedUrls.size(), expiresAt);
        return presignedUrls;
    }

    /**
     * 通过列出所有已上传的分片来获取分片上传的当前状态。
     * 
     * 此方法适用于：
     * - 通过识别已上传的分片来恢复中断的上传
     * - 向用户显示上传进度
     * - 在完成上传前验证所有分片是否已上传
     * 
     * @param uploadId 来自S3的分片上传ID
     * @param objectKey 上传的S3对象键
     * @return 包含每个已上传分片的分片编号、ETag和大小的UploadPartInfo列表
     */
    public List<UploadPartInfo> getUploadStatus(String uploadId, String objectKey) {
        log.info("Retrieving upload status for uploadId: {}, objectKey: {}", uploadId, objectKey);
        
        // 验证输入参数
        if (uploadId == null || uploadId.trim().isEmpty()) {
            throw new IllegalArgumentException("UploadId cannot be null or empty");
        }
        
        if (objectKey == null || objectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("ObjectKey cannot be null or empty");
        }
        
        try {
            ListPartsRequest listPartsRequest = ListPartsRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .build();

            ListPartsResponse listPartsResponse = s3Client.listParts(listPartsRequest);

            // 将S3的Part对象转换为我们的DTO格式，并预分配容量
            List<UploadPartInfo> uploadedParts = new ArrayList<>(listPartsResponse.parts().size());
            for (Part part : listPartsResponse.parts()) {
                uploadedParts.add(new UploadPartInfo(
                        part.partNumber(),
                        part.eTag(),
                        part.size()
                ));
            }

            log.info("Upload status retrieved: {} parts uploaded", uploadedParts.size());
            return uploadedParts;
        } catch (NoSuchUploadException e) {
            log.warn("Upload not found for uploadId: {}, objectKey: {}", uploadId, objectKey);
            throw new IllegalArgumentException("Upload not found or has been completed", e);
        } catch (Exception e) {
            log.error("Failed to retrieve upload status for uploadId: {}, objectKey: {}", uploadId, objectKey, e);
            throw new RuntimeException("Failed to retrieve upload status: " + e.getMessage(), e);
        }
    }

    /**
     * 完成分片上传并将元数据持久化到MySQL数据库。
     * 
     * 此方法执行以下操作：
     * 1. 将客户端提供的分片ETag转换为S3 CompletedPart格式
     * 2. 向S3/MinIO发送完成请求以最终确定上传
     * 3. 从S3获取最终对象元数据（大小、ETag）
     * 4. 将视频录制元数据持久化到MySQL以供将来查询
     * 5. 为已上传文件生成预签名下载URL
     * 
     * 此操作后，文件可从S3/MinIO下载，其元数据可通过MySQL搜索。
     * 
     * @param request 包含uploadId、objectKey和分片ETag的完成请求
     * @return 包含文件元数据和下载URL的CompleteUploadResponse
     * @throws S3Exception 如果S3操作失败（例如分片ETag无效）
     */
    @Transactional
    public CompleteUploadResponse completeUpload(CompleteUploadRequest request) {
        log.info("Completing upload for uploadId: {}, objectKey: {}", request.getUploadId(), request.getObjectKey());
        
        // 验证分片列表不为空
        if (request.getParts() == null || request.getParts().isEmpty()) {
            throw new IllegalArgumentException("Parts list cannot be empty");
        }
        
        // 验证分片编号的连续性和唯一性
        validateParts(request.getParts());
        
        // 检查上传是否已经完成（通过检查是否已存在数据库记录）
        if (videoRecordingRepository.existsByObjectKey(request.getObjectKey())) {
            log.warn("Upload already completed for objectKey: {}", request.getObjectKey());
            throw new IllegalStateException("Upload has already been completed");
        }
        
        try {
            // 将DTO转换为S3 SDK格式，并预分配容量
            List<CompletedPart> completedParts = new ArrayList<>(request.getParts().size());
            for (PartETag partETag : request.getParts()) {
                completedParts.add(CompletedPart.builder()
                        .partNumber(partETag.getPartNumber())
                        .eTag(partETag.getETag())
                        .build());
            }

            CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();

            // 在S3/MinIO中完成分片上传
            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(request.getObjectKey())
                    .uploadId(request.getUploadId())
                    .multipartUpload(completedUpload)
                    .build();

            CompleteMultipartUploadResponse completeResponse = s3Client.completeMultipartUpload(completeRequest);
            log.info("Multipart upload completed successfully with ETag: {}", completeResponse.eTag());

            // 从S3获取最终对象元数据
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(request.getObjectKey())
                    .build();

            HeadObjectResponse headResponse = s3Client.headObject(headRequest);

            // 从对象键提取文件名（/之后的最后一段）
            String filename = request.getObjectKey().substring(request.getObjectKey().lastIndexOf("/") + 1);

            // 使用构建器模式构建并将视频录制元数据持久化到MySQL
            VideoRecording recording = VideoRecording.builder()
                    .filename(filename)
                    .size(headResponse.contentLength())
                    .objectKey(request.getObjectKey())
                    .status(STATUS_COMPLETED)
                    .checksum(completeResponse.eTag())
                    .build();

            VideoRecording savedRecording = videoRecordingRepository.save(recording);
            log.info("Video recording metadata saved to database with id: {}", savedRecording.getId());

            // 为完成的上传生成具有时间限制的下载URL
            String downloadUrl = generateDownloadUrl(request.getObjectKey());

            return new CompleteUploadResponse(
                    savedRecording.getId(),
                    savedRecording.getFilename(),
                    savedRecording.getSize(),
                    savedRecording.getObjectKey(),
                    savedRecording.getStatus(),
                    downloadUrl,
                    savedRecording.getCreatedAt()
            );
        } catch (Exception e) {
            log.error("Failed to complete upload for uploadId: {}, objectKey: {}", 
                    request.getUploadId(), request.getObjectKey(), e);
            
            // 如果S3操作失败，尝试中止上传以清理分片
            try {
                abortUpload(new AbortUploadRequest(request.getUploadId(), request.getObjectKey()));
                log.info("Cleaned up failed upload: {}", request.getUploadId());
            } catch (Exception cleanupException) {
                log.error("Failed to cleanup upload after failure: {}", request.getUploadId(), cleanupException);
            }
            
            throw new RuntimeException("Failed to complete upload: " + e.getMessage(), e);
        }
    }

    /**
     * 中止分片上传并清理所有已上传的分片。
     * 
     * 此方法应在以下情况下调用：
     * - 用户取消上传
     * - 上传失败且无法重试
     * - 上传超过时间限制
     * 
     * 中止上传会从S3/MinIO存储中删除所有分片，防止被放弃的上传产生存储成本。
     * 
     * @param request 包含uploadId和objectKey的中止请求
     */
    public void abortUpload(AbortUploadRequest request) {
        log.info("Aborting upload for uploadId: {}, objectKey: {}", request.getUploadId(), request.getObjectKey());
        
        // 验证输入参数
        if (request.getUploadId() == null || request.getUploadId().trim().isEmpty()) {
            throw new IllegalArgumentException("UploadId cannot be null or empty");
        }
        
        if (request.getObjectKey() == null || request.getObjectKey().trim().isEmpty()) {
            throw new IllegalArgumentException("ObjectKey cannot be null or empty");
        }
        
        try {
            AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(request.getObjectKey())
                    .uploadId(request.getUploadId())
                    .build();

            s3Client.abortMultipartUpload(abortRequest);
            log.info("Upload aborted successfully, all parts removed from storage");
        } catch (NoSuchUploadException e) {
            log.warn("Upload not found for abort operation, uploadId: {}, objectKey: {}", 
                    request.getUploadId(), request.getObjectKey());
            // 上传不存在时不算错误，可能是已经被中止或完成
        } catch (Exception e) {
            log.error("Failed to abort upload for uploadId: {}, objectKey: {}", 
                    request.getUploadId(), request.getObjectKey(), e);
            throw new RuntimeException("Failed to abort upload: " + e.getMessage(), e);
        }
    }

    /**
     * 为已完成的上传生成预签名下载URL。
     * 
     * 生成的URL具有时间限制，允许从S3/MinIO直接下载而无需客户端进行身份验证。
     * 这对以下场景很有用：
     * - 与没有S3凭证的用户共享文件
     * - 在电子邮件或网页中嵌入下载链接
     * - 为已上传内容提供临时访问
     * 
     * @param objectKey 文件的S3对象键
     * @return 在配置的持续时间后过期的预签名URL字符串
     */
    private String generateDownloadUrl(String objectKey) {
        log.debug("Generating download URL for objectKey: {}", objectKey);
        
        if (objectKey == null || objectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("ObjectKey cannot be null or empty");
        }
        
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(objectKey)
                    .build();

            software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest =
                    software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(uploadConfig.getPresignedUrlExpirationMinutes()))
                            .getObjectRequest(getObjectRequest)
                            .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

            String downloadUrl = presignedRequest.url().toString();
            log.debug("Generated download URL for objectKey: {}, expires in {} minutes", 
                    objectKey, uploadConfig.getPresignedUrlExpirationMinutes());
            
            return downloadUrl;
        } catch (Exception e) {
            log.error("Failed to generate download URL for objectKey: {}", objectKey, e);
            throw new RuntimeException("Failed to generate download URL: " + e.getMessage(), e);
        }
    }
}
