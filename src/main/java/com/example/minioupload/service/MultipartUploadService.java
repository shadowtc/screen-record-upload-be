package com.example.minioupload.service;

import com.example.minioupload.config.S3ConfigProperties;
import com.example.minioupload.config.UploadConfigProperties;
import com.example.minioupload.dto.*;
import com.example.minioupload.model.AsyncUploadTask;
import com.example.minioupload.model.VideoRecording;
import com.example.minioupload.repository.AsyncUploadTaskRepository;
import com.example.minioupload.repository.VideoRecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

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
     * 用于持久化异步上传任务状态的仓库，支持断点续传
     */
    private final AsyncUploadTaskRepository asyncUploadTaskRepository;
    
    /**
     * 异步执行器，用于异步上传任务
     */
    private final Executor videoCompressionExecutor;

    /**
     * 异步上传任务进度追踪映射表，使用ConcurrentHashMap确保线程安全性
     * 用于内存中快速访问上传进度，定期与数据库同步
     */
    private final ConcurrentHashMap<String, AsyncUploadProgress> asyncUploadProgressMap = new ConcurrentHashMap<>();

    /**
     * S3中上传目录前缀的常量
     */
    private static final String UPLOADS_PREFIX = "uploads/";
    
    /**
     * 已完成上传状态的常量
     */
    private static final String STATUS_COMPLETED = "COMPLETED";
    
    /**
     * 异步上传状态常量
     */
    private static final String ASYNC_STATUS_SUBMITTED = "SUBMITTED";
    private static final String ASYNC_STATUS_UPLOADING = "UPLOADING";
    private static final String ASYNC_STATUS_PAUSED = "PAUSED";
    private static final String ASYNC_STATUS_COMPLETED = "COMPLETED";
    private static final String ASYNC_STATUS_FAILED = "FAILED";
    
    /**
     * 构造函数
     */
    public MultipartUploadService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3ConfigProperties s3Config,
            UploadConfigProperties uploadConfig,
            VideoRecordingRepository videoRecordingRepository,
            AsyncUploadTaskRepository asyncUploadTaskRepository,
            @Qualifier("videoCompressionExecutor") Executor videoCompressionExecutor) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Config = s3Config;
        this.uploadConfig = uploadConfig;
        this.videoRecordingRepository = videoRecordingRepository;
        this.asyncUploadTaskRepository = asyncUploadTaskRepository;
        this.videoCompressionExecutor = videoCompressionExecutor;
        
        // 初始化临时文件目录
        initTempDirectory();
        // 从数据库加载未完成的上传任务到内存
        loadUnfinishedTasks();
    }
    
    /**
     * 初始化临时文件目录
     */
    private void initTempDirectory() {
        try {
            Path tempDir = Path.of(uploadConfig.getTempDirectory());
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
                log.info("Created temp directory: {}", tempDir);
            }
        } catch (IOException e) {
            log.error("Failed to create temp directory", e);
            throw new RuntimeException("Failed to initialize temp directory", e);
        }
    }
    
    /**
     * 从数据库加载未完成的上传任务到内存
     * 应用启动时恢复中断的上传任务
     */
    private void loadUnfinishedTasks() {
        try {
            List<AsyncUploadTask> unfinishedTasks = asyncUploadTaskRepository.findByStatus(ASYNC_STATUS_UPLOADING);
            unfinishedTasks.addAll(asyncUploadTaskRepository.findByStatus(ASYNC_STATUS_PAUSED));
            
            for (AsyncUploadTask task : unfinishedTasks) {
                // 将任务状态设置为PAUSED，等待恢复
                task.setStatus(ASYNC_STATUS_PAUSED);
                task.setMessage("Upload paused, waiting for resume");
                asyncUploadTaskRepository.save(task);
                
                // 加载到内存
                AsyncUploadProgress progress = convertTaskToProgress(task);
                asyncUploadProgressMap.put(task.getJobId(), progress);
                
                log.info("Loaded paused upload task: {}", task.getJobId());
            }
            
            log.info("Loaded {} unfinished upload tasks", unfinishedTasks.size());
        } catch (Exception e) {
            log.error("Failed to load unfinished tasks", e);
        }
    }
    
    /**
     * 将AsyncUploadTask转换为AsyncUploadProgress
     */
    private AsyncUploadProgress convertTaskToProgress(AsyncUploadTask task) {
        return AsyncUploadProgress.builder()
                .jobId(task.getJobId())
                .status(task.getStatus())
                .progress(task.getProgress())
                .message(task.getMessage())
                .uploadedParts(task.getUploadedParts())
                .totalParts(task.getTotalParts())
                .fileName(task.getFileName())
                .fileSize(task.getFileSize())
                .startTime(task.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant())
                .endTime(task.getEndTime() != null ? 
                        task.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                .build();
    }

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
    
    /**
     * 提交异步分片上传任务
     * 
     * 此方法接收整个文件，然后在后台异步执行以下操作：
     * 1. 将文件保存到临时目录
     * 2. 在MinIO中初始化分片上传
     * 3. 将文件分片并逐个上传到MinIO
     * 4. 完成分片上传
     * 5. 将元数据持久化到数据库
     * 6. 清理临时文件
     * 
     * @param file 要上传的文件
     * @param chunkSize 可选的分片大小（字节），null表示使用默认值
     * @return 上传任务的唯一标识符，用于后续进度查询
     */
    public String submitAsyncUpload(MultipartFile file, Long chunkSize) {
        // 生成任务ID
        String jobId = UUID.randomUUID().toString();
        
        // 提取文件元数据
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();
        String contentType = file.getContentType();
        
        log.info("Submitting async upload job: {} for file: {}, size: {} bytes", jobId, fileName, fileSize);
        
        // 验证文件名不为空
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }
        
        // 验证文件类型 - 仅接受视频文件
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new IllegalArgumentException("Only video files are allowed");
        }
        
        // 验证文件大小
        if (fileSize > uploadConfig.getMaxFileSize()) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size");
        }
        
        // 确定分片大小
        long actualChunkSize = chunkSize != null && chunkSize > 0
                ? chunkSize
                : uploadConfig.getDefaultChunkSize();
        
        // 验证分片大小
        final long MIN_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB
        final long MAX_CHUNK_SIZE = 5L * 1024 * 1024 * 1024; // 5GB
        
        if (actualChunkSize < MIN_CHUNK_SIZE) {
            throw new IllegalArgumentException("Chunk size must be at least 5MB (5242880 bytes)");
        }
        
        if (actualChunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException("Chunk size cannot exceed 5GB (5368709120 bytes)");
        }
        
        // 计算总分片数
        int totalParts = (int) Math.ceil((double) fileSize / actualChunkSize);
        
        // 创建数据库任务记录
        AsyncUploadTask task = AsyncUploadTask.builder()
                .jobId(jobId)
                .status(ASYNC_STATUS_SUBMITTED)
                .progress(0.0)
                .message("Upload submitted, waiting to start...")
                .uploadedParts(0)
                .totalParts(totalParts)
                .fileName(fileName)
                .fileSize(fileSize)
                .contentType(contentType)
                .chunkSize(actualChunkSize)
                .startTime(java.time.LocalDateTime.now())
                .build();
        asyncUploadTaskRepository.save(task);
        
        // 初始化内存中的进度跟踪
        AsyncUploadProgress progress = AsyncUploadProgress.builder()
                .jobId(jobId)
                .status(ASYNC_STATUS_SUBMITTED)
                .progress(0.0)
                .message("Upload submitted, waiting to start...")
                .uploadedParts(0)
                .totalParts(totalParts)
                .fileName(fileName)
                .fileSize(fileSize)
                .startTime(Instant.now())
                .build();
        asyncUploadProgressMap.put(jobId, progress);
        
        // 异步执行上传任务
        CompletableFuture.runAsync(() -> executeAsyncUpload(file, actualChunkSize, jobId), videoCompressionExecutor);
        
        log.info("Async upload job submitted: {}, total parts: {}", jobId, totalParts);
        return jobId;
    }
    
    /**
     * 执行异步上传的内部方法
     * 
     * @param file 要上传的文件
     * @param chunkSize 分片大小
     * @param jobId 任务ID
     */
    private void executeAsyncUpload(MultipartFile file, long chunkSize, String jobId) {
        Path tempFile = null;
        String uploadId = null;
        String objectKey = null;
        
        try {
            // 更新状态：正在保存文件到临时目录
            updateProgress(jobId, ASYNC_STATUS_UPLOADING, 5.0, "Saving file to temporary directory...", 0);
            
            // 保存文件到持久化的临时目录
            Path tempDir = Path.of(uploadConfig.getTempDirectory());
            tempFile = tempDir.resolve(jobId + "-" + file.getOriginalFilename());
            file.transferTo(tempFile.toFile());
            log.info("File saved to temporary location: {}", tempFile);
            
            // 更新数据库中的临时文件路径
            final Path finalTempFile = tempFile;
            updateTaskInDatabase(jobId, task -> task.setTempFilePath(finalTempFile.toString()));
            
            // 更新状态：正在初始化MinIO上传
            updateProgress(jobId, ASYNC_STATUS_UPLOADING, 10.0, "Initializing MinIO upload...", 0);
            
            // 生成唯一的对象键
            objectKey = UPLOADS_PREFIX + UUID.randomUUID() + "/" + file.getOriginalFilename();
            
            // 在MinIO中初始化分片上传
            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .build();
            
            CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
            uploadId = createResponse.uploadId();
            log.info("MinIO multipart upload initialized with uploadId: {}", uploadId);
            
            // 更新数据库中的uploadId和objectKey
            final String finalUploadId = uploadId;
            final String finalObjectKey = objectKey;
            updateTaskInDatabase(jobId, task -> {
                task.setUploadId(finalUploadId);
                task.setObjectKey(finalObjectKey);
            });
            
            // 更新状态：正在上传分片
            updateProgress(jobId, ASYNC_STATUS_UPLOADING, 15.0, "Uploading parts to MinIO...", 0);
            
            // 上传分片
            List<CompletedPart> completedParts = uploadParts(tempFile, chunkSize, uploadId, objectKey, jobId);
            
            // 更新状态：正在完成上传
            updateProgress(jobId, ASYNC_STATUS_UPLOADING, 90.0, "Completing upload...", completedParts.size());
            
            // 完成分片上传
            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build();
            
            CompleteMultipartUploadResponse completeResponse = s3Client.completeMultipartUpload(completeRequest);
            log.info("MinIO multipart upload completed with ETag: {}", completeResponse.eTag());
            
            // 更新状态：正在保存元数据
            updateProgress(jobId, ASYNC_STATUS_UPLOADING, 95.0, "Saving metadata to database...", completedParts.size());
            
            // 获取对象元数据
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(objectKey)
                    .build();
            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            
            // 保存视频录制元数据到数据库
            String filename = file.getOriginalFilename();
            VideoRecording recording = VideoRecording.builder()
                    .filename(filename)
                    .size(headResponse.contentLength())
                    .objectKey(objectKey)
                    .status(STATUS_COMPLETED)
                    .checksum(completeResponse.eTag())
                    .build();
            
            VideoRecording savedRecording = videoRecordingRepository.save(recording);
            log.info("Video recording metadata saved to database with id: {}", savedRecording.getId());
            
            // 更新数据库中的视频录制ID
            final Long videoRecordingId = savedRecording.getId();
            updateTaskInDatabase(jobId, task -> task.setVideoRecordingId(videoRecordingId));
            
            // 生成下载URL
            String downloadUrl = generateDownloadUrl(objectKey);
            
            // 创建完成响应
            CompleteUploadResponse uploadResponse = new CompleteUploadResponse(
                    savedRecording.getId(),
                    savedRecording.getFilename(),
                    savedRecording.getSize(),
                    savedRecording.getObjectKey(),
                    savedRecording.getStatus(),
                    downloadUrl,
                    savedRecording.getCreatedAt()
            );
            
            // 更新状态：完成
            AsyncUploadProgress finalProgress = AsyncUploadProgress.builder()
                    .jobId(jobId)
                    .status(ASYNC_STATUS_COMPLETED)
                    .progress(100.0)
                    .message("Upload completed successfully")
                    .uploadedParts(completedParts.size())
                    .totalParts(completedParts.size())
                    .fileName(filename)
                    .fileSize(file.getSize())
                    .uploadResponse(uploadResponse)
                    .startTime(asyncUploadProgressMap.get(jobId).getStartTime())
                    .endTime(Instant.now())
                    .build();
            asyncUploadProgressMap.put(jobId, finalProgress);
            
            // 更新数据库状态
            updateTaskInDatabase(jobId, task -> {
                task.setStatus(ASYNC_STATUS_COMPLETED);
                task.setProgress(100.0);
                task.setMessage("Upload completed successfully");
                task.setEndTime(java.time.LocalDateTime.now());
            });
            
            log.info("Async upload job completed successfully: {}", jobId);
            
        } catch (Exception e) {
            log.error("Async upload job failed: {}", jobId, e);
            
            // 尝试中止上传
            if (uploadId != null && objectKey != null) {
                try {
                    abortUpload(new AbortUploadRequest(uploadId, objectKey));
                    log.info("Cleaned up failed async upload: {}", jobId);
                } catch (Exception cleanupException) {
                    log.error("Failed to cleanup async upload: {}", jobId, cleanupException);
                }
            }
            
            // 更新状态：失败
            AsyncUploadProgress progress = asyncUploadProgressMap.get(jobId);
            if (progress != null) {
                AsyncUploadProgress failedProgress = AsyncUploadProgress.builder()
                        .jobId(jobId)
                        .status(ASYNC_STATUS_FAILED)
                        .progress(-1.0)
                        .message("Upload failed: " + e.getMessage())
                        .uploadedParts(progress.getUploadedParts())
                        .totalParts(progress.getTotalParts())
                        .fileName(progress.getFileName())
                        .fileSize(progress.getFileSize())
                        .startTime(progress.getStartTime())
                        .endTime(Instant.now())
                        .build();
                asyncUploadProgressMap.put(jobId, failedProgress);
                
                // 更新数据库状态
                updateTaskInDatabase(jobId, task -> {
                    task.setStatus(ASYNC_STATUS_FAILED);
                    task.setProgress(-1.0);
                    task.setMessage("Upload failed: " + e.getMessage());
                    task.setEndTime(java.time.LocalDateTime.now());
                });
            }
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.info("Temporary file deleted: {}", tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary file: {}", tempFile, e);
                }
            }
            
            // 60分钟后清理进度信息
            CompletableFuture.delayedExecutor(60, java.util.concurrent.TimeUnit.MINUTES)
                    .execute(() -> asyncUploadProgressMap.remove(jobId));
        }
    }
    
    /**
     * 上传文件分片到MinIO
     * 
     * @param filePath 临时文件路径
     * @param chunkSize 分片大小
     * @param uploadId MinIO上传ID
     * @param objectKey 对象键
     * @param jobId 任务ID
     * @return 已完成的分片列表
     * @throws IOException 如果读取文件失败
     */
    private List<CompletedPart> uploadParts(Path filePath, long chunkSize, String uploadId, String objectKey, String jobId) throws IOException {
        List<CompletedPart> completedParts = new ArrayList<>();
        long fileSize = Files.size(filePath);
        int totalParts = (int) Math.ceil((double) fileSize / chunkSize);
        
        log.info("Starting to upload {} parts for job: {}", totalParts, jobId);
        
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            byte[] buffer = new byte[(int) Math.min(chunkSize, fileSize)];
            int partNumber = 1;
            long offset = 0;
            
            while (offset < fileSize) {
                // 计算当前分片的大小
                long currentChunkSize = Math.min(chunkSize, fileSize - offset);
                
                // 读取分片数据
                file.seek(offset);
                int bytesRead = file.read(buffer, 0, (int) currentChunkSize);
                
                if (bytesRead <= 0) {
                    break;
                }
                
                // 上传分片到MinIO
                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(s3Config.getBucket())
                        .key(objectKey)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) bytesRead)
                        .build();
                
                UploadPartResponse uploadPartResponse = s3Client.uploadPart(
                        uploadPartRequest,
                        RequestBody.fromBytes(Arrays.copyOf(buffer, bytesRead))
                );
                
                // 记录已完成的分片
                CompletedPart completedPart = CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build();
                completedParts.add(completedPart);
                
                // 更新进度
                double progress = 15.0 + (75.0 * partNumber / totalParts); // 15% - 90%
                String message = String.format("Uploading part %d/%d...", partNumber, totalParts);
                updateProgress(jobId, ASYNC_STATUS_UPLOADING, progress, message, partNumber);
                
                log.debug("Uploaded part {}/{} for job: {}, ETag: {}", partNumber, totalParts, jobId, uploadPartResponse.eTag());
                
                offset += bytesRead;
                partNumber++;
            }
        }
        
        log.info("All {} parts uploaded successfully for job: {}", completedParts.size(), jobId);
        return completedParts;
    }
    
    /**
     * 更新异步上传进度（内存和数据库）
     * 
     * @param jobId 任务ID
     * @param status 状态
     * @param progress 进度百分比
     * @param message 消息
     * @param uploadedParts 已上传的分片数
     */
    private void updateProgress(String jobId, String status, Double progress, String message, Integer uploadedParts) {
        // 更新内存中的进度
        AsyncUploadProgress currentProgress = asyncUploadProgressMap.get(jobId);
        if (currentProgress != null) {
            AsyncUploadProgress updatedProgress = AsyncUploadProgress.builder()
                    .jobId(jobId)
                    .status(status)
                    .progress(progress)
                    .message(message)
                    .uploadedParts(uploadedParts)
                    .totalParts(currentProgress.getTotalParts())
                    .fileName(currentProgress.getFileName())
                    .fileSize(currentProgress.getFileSize())
                    .startTime(currentProgress.getStartTime())
                    .build();
            asyncUploadProgressMap.put(jobId, updatedProgress);
        }
        
        // 更新数据库（异步，不阻塞上传）
        CompletableFuture.runAsync(() -> {
            updateTaskInDatabase(jobId, task -> {
                task.setStatus(status);
                task.setProgress(progress);
                task.setMessage(message);
                task.setUploadedParts(uploadedParts);
            });
        }, videoCompressionExecutor);
    }
    
    /**
     * 更新数据库中的任务信息
     * 
     * @param jobId 任务ID
     * @param updater 更新函数
     */
    private void updateTaskInDatabase(String jobId, java.util.function.Consumer<AsyncUploadTask> updater) {
        try {
            asyncUploadTaskRepository.findByJobId(jobId).ifPresent(task -> {
                updater.accept(task);
                asyncUploadTaskRepository.save(task);
            });
        } catch (Exception e) {
            log.error("Failed to update task in database: {}", jobId, e);
        }
    }
    
    /**
     * 获取异步上传任务的进度
     * 
     * @param jobId 任务ID
     * @return 上传进度，如果任务不存在则返回null
     */
    public AsyncUploadProgress getAsyncUploadProgress(String jobId) {
        log.debug("Retrieving progress for async upload job: {}", jobId);
        
        // 先从内存中查找
        AsyncUploadProgress progress = asyncUploadProgressMap.get(jobId);
        if (progress != null) {
            return progress;
        }
        
        // 内存中没有，从数据库加载
        return asyncUploadTaskRepository.findByJobId(jobId)
                .map(task -> {
                    AsyncUploadProgress dbProgress = convertTaskToProgress(task);
                    // 加载到内存中以便后续快速访问
                    asyncUploadProgressMap.put(jobId, dbProgress);
                    return dbProgress;
                })
                .orElse(null);
    }
    
    /**
     * 恢复暂停的上传任务
     * 
     * 此方法用于断点续传功能，可以恢复之前中断的上传任务。
     * 它会检查MinIO中已上传的分片，然后只上传剩余的分片。
     * 
     * @param jobId 要恢复的任务ID
     * @return 恢复后的上传进度
     * @throws IllegalArgumentException 如果任务不存在或无法恢复
     */
    public AsyncUploadProgress resumeAsyncUpload(String jobId) {
        log.info("Resuming async upload job: {}", jobId);
        
        // 从数据库加载任务
        AsyncUploadTask task = asyncUploadTaskRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Upload task not found: " + jobId));
        
        // 验证任务状态是否可以恢复
        if (!ASYNC_STATUS_PAUSED.equals(task.getStatus()) && !ASYNC_STATUS_FAILED.equals(task.getStatus())) {
            throw new IllegalArgumentException("Upload task cannot be resumed. Current status: " + task.getStatus());
        }
        
        // 验证临时文件是否存在
        if (task.getTempFilePath() == null || !Files.exists(Path.of(task.getTempFilePath()))) {
            throw new IllegalArgumentException("Temporary file not found for upload task: " + jobId);
        }
        
        // 验证uploadId和objectKey是否存在
        if (task.getUploadId() == null || task.getObjectKey() == null) {
            throw new IllegalArgumentException("Upload ID or object key not found for upload task: " + jobId);
        }
        
        // 更新状态为UPLOADING
        task.setStatus(ASYNC_STATUS_UPLOADING);
        task.setMessage("Resuming upload...");
        asyncUploadTaskRepository.save(task);
        
        // 更新内存中的进度
        AsyncUploadProgress progress = convertTaskToProgress(task);
        asyncUploadProgressMap.put(jobId, progress);
        
        // 异步执行恢复上传
        CompletableFuture.runAsync(() -> executeResumeUpload(task), videoCompressionExecutor);
        
        log.info("Async upload job resumed: {}", jobId);
        return progress;
    }
    
    /**
     * 执行恢复上传的内部方法
     * 
     * @param task 上传任务
     */
    private void executeResumeUpload(AsyncUploadTask task) {
        String jobId = task.getJobId();
        Path tempFile = Path.of(task.getTempFilePath());
        
        try {
            log.info("Starting resume upload for job: {}", jobId);
            
            // 查询MinIO中已上传的分片
            updateProgress(jobId, ASYNC_STATUS_UPLOADING, task.getProgress(), "Checking uploaded parts...", task.getUploadedParts());
            
            ListPartsRequest listPartsRequest = ListPartsRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(task.getObjectKey())
                    .uploadId(task.getUploadId())
                    .build();
            
            ListPartsResponse listPartsResponse = s3Client.listParts(listPartsRequest);
            List<CompletedPart> completedParts = new ArrayList<>();
            Set<Integer> uploadedPartNumbers = new HashSet<>();
            
            // 记录已上传的分片
            for (Part part : listPartsResponse.parts()) {
                uploadedPartNumbers.add(part.partNumber());
                completedParts.add(CompletedPart.builder()
                        .partNumber(part.partNumber())
                        .eTag(part.eTag())
                        .build());
            }
            
            log.info("Found {} already uploaded parts for job: {}", uploadedPartNumbers.size(), jobId);
            
            // 上传剩余的分片
            updateProgress(jobId, ASYNC_STATUS_UPLOADING, 15.0, 
                    String.format("Resuming upload from part %d...", uploadedPartNumbers.size() + 1), 
                    uploadedPartNumbers.size());
            
            List<CompletedPart> newParts = resumeUploadParts(
                    tempFile, 
                    task.getChunkSize(), 
                    task.getUploadId(), 
                    task.getObjectKey(), 
                    jobId, 
                    uploadedPartNumbers,
                    task.getTotalParts()
            );
            
            completedParts.addAll(newParts);
            completedParts.sort((p1, p2) -> Integer.compare(p1.partNumber(), p2.partNumber()));
            
            // 完成上传
            updateProgress(jobId, ASYNC_STATUS_UPLOADING, 90.0, "Completing upload...", completedParts.size());
            
            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(task.getObjectKey())
                    .uploadId(task.getUploadId())
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build();
            
            CompleteMultipartUploadResponse completeResponse = s3Client.completeMultipartUpload(completeRequest);
            log.info("MinIO multipart upload completed with ETag: {}", completeResponse.eTag());
            
            // 保存元数据到数据库
            updateProgress(jobId, ASYNC_STATUS_UPLOADING, 95.0, "Saving metadata to database...", completedParts.size());
            
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(task.getObjectKey())
                    .build();
            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            
            VideoRecording recording = VideoRecording.builder()
                    .filename(task.getFileName())
                    .size(headResponse.contentLength())
                    .objectKey(task.getObjectKey())
                    .status(STATUS_COMPLETED)
                    .checksum(completeResponse.eTag())
                    .build();
            
            VideoRecording savedRecording = videoRecordingRepository.save(recording);
            log.info("Video recording metadata saved to database with id: {}", savedRecording.getId());
            
            // 生成下载URL
            String downloadUrl = generateDownloadUrl(task.getObjectKey());
            
            // 创建完成响应
            CompleteUploadResponse uploadResponse = new CompleteUploadResponse(
                    savedRecording.getId(),
                    savedRecording.getFilename(),
                    savedRecording.getSize(),
                    savedRecording.getObjectKey(),
                    savedRecording.getStatus(),
                    downloadUrl,
                    savedRecording.getCreatedAt()
            );
            
            // 更新状态为完成
            AsyncUploadProgress finalProgress = AsyncUploadProgress.builder()
                    .jobId(jobId)
                    .status(ASYNC_STATUS_COMPLETED)
                    .progress(100.0)
                    .message("Upload completed successfully")
                    .uploadedParts(completedParts.size())
                    .totalParts(completedParts.size())
                    .fileName(task.getFileName())
                    .fileSize(task.getFileSize())
                    .uploadResponse(uploadResponse)
                    .startTime(task.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant())
                    .endTime(Instant.now())
                    .build();
            asyncUploadProgressMap.put(jobId, finalProgress);
            
            // 更新数据库状态
            updateTaskInDatabase(jobId, t -> {
                t.setStatus(ASYNC_STATUS_COMPLETED);
                t.setProgress(100.0);
                t.setMessage("Upload completed successfully");
                t.setVideoRecordingId(savedRecording.getId());
                t.setEndTime(java.time.LocalDateTime.now());
            });
            
            // 清理临时文件
            try {
                Files.deleteIfExists(tempFile);
                log.info("Temporary file deleted: {}", tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", tempFile, e);
            }
            
            log.info("Resume upload completed successfully: {}", jobId);
            
        } catch (Exception e) {
            log.error("Resume upload failed: {}", jobId, e);
            
            // 更新状态为失败
            AsyncUploadProgress failedProgress = AsyncUploadProgress.builder()
                    .jobId(jobId)
                    .status(ASYNC_STATUS_FAILED)
                    .progress(-1.0)
                    .message("Resume upload failed: " + e.getMessage())
                    .uploadedParts(task.getUploadedParts())
                    .totalParts(task.getTotalParts())
                    .fileName(task.getFileName())
                    .fileSize(task.getFileSize())
                    .startTime(task.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant())
                    .endTime(Instant.now())
                    .build();
            asyncUploadProgressMap.put(jobId, failedProgress);
            
            // 更新数据库状态
            updateTaskInDatabase(jobId, t -> {
                t.setStatus(ASYNC_STATUS_FAILED);
                t.setProgress(-1.0);
                t.setMessage("Resume upload failed: " + e.getMessage());
                t.setEndTime(java.time.LocalDateTime.now());
            });
        }
    }
    
    /**
     * 恢复上传剩余的分片
     * 
     * @param filePath 临时文件路径
     * @param chunkSize 分片大小
     * @param uploadId MinIO上传ID
     * @param objectKey 对象键
     * @param jobId 任务ID
     * @param uploadedPartNumbers 已上传的分片编号集合
     * @param totalParts 总分片数
     * @return 新上传的分片列表
     * @throws IOException 如果读取文件失败
     */
    private List<CompletedPart> resumeUploadParts(
            Path filePath, 
            long chunkSize, 
            String uploadId, 
            String objectKey, 
            String jobId,
            Set<Integer> uploadedPartNumbers,
            int totalParts) throws IOException {
        
        List<CompletedPart> completedParts = new ArrayList<>();
        long fileSize = Files.size(filePath);
        
        log.info("Resuming upload from part {} for job: {}", uploadedPartNumbers.size() + 1, jobId);
        
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            byte[] buffer = new byte[(int) Math.min(chunkSize, fileSize)];
            int partNumber = 1;
            long offset = 0;
            
            while (offset < fileSize) {
                // 跳过已上传的分片
                if (uploadedPartNumbers.contains(partNumber)) {
                    offset += Math.min(chunkSize, fileSize - offset);
                    partNumber++;
                    continue;
                }
                
                // 计算当前分片的大小
                long currentChunkSize = Math.min(chunkSize, fileSize - offset);
                
                // 读取分片数据
                file.seek(offset);
                int bytesRead = file.read(buffer, 0, (int) currentChunkSize);
                
                if (bytesRead <= 0) {
                    break;
                }
                
                // 上传分片到MinIO
                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(s3Config.getBucket())
                        .key(objectKey)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) bytesRead)
                        .build();
                
                UploadPartResponse uploadPartResponse = s3Client.uploadPart(
                        uploadPartRequest,
                        RequestBody.fromBytes(Arrays.copyOf(buffer, bytesRead))
                );
                
                // 记录已完成的分片
                CompletedPart completedPart = CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build();
                completedParts.add(completedPart);
                
                // 更新进度
                int totalUploaded = uploadedPartNumbers.size() + completedParts.size();
                double progress = 15.0 + (75.0 * totalUploaded / totalParts); // 15% - 90%
                String message = String.format("Uploading part %d/%d...", totalUploaded, totalParts);
                updateProgress(jobId, ASYNC_STATUS_UPLOADING, progress, message, totalUploaded);
                
                log.debug("Uploaded part {}/{} for job: {}, ETag: {}", totalUploaded, totalParts, jobId, uploadPartResponse.eTag());
                
                offset += bytesRead;
                partNumber++;
            }
        }
        
        log.info("Resumed {} new parts for job: {}", completedParts.size(), jobId);
        return completedParts;
    }
}
