package com.example.minioupload.service;

import com.example.minioupload.config.S3ConfigProperties;
import com.example.minioupload.config.UploadConfigProperties;
import com.example.minioupload.dto.AsyncUploadStartResponse;
import com.example.minioupload.dto.AsyncUploadStatusResponse;
import com.example.minioupload.model.VideoRecording;
import com.example.minioupload.repository.VideoRecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 服务端直传 MinIO（异步分片上传）服务
 *
 * 前端仅上传一次到后端，后端在后台将文件拆分为分片并上传到 MinIO，
 * 完成后在数据库记录元数据，并提供下载链接。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ServerSideMultipartUploadService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3ConfigProperties s3Config;
    private final UploadConfigProperties uploadConfig;
    private final VideoRecordingRepository videoRecordingRepository;

    private final @Qualifier("multipartUploadExecutor") Executor multipartUploadExecutor;

    private static final String UPLOADS_PREFIX = "uploads/";

    private enum JobStatus { QUEUED, UPLOADING, COMPLETED, FAILED }

    private static class JobProgress {
        String jobId;
        JobStatus status;
        double progress; // 0-100
        int uploadedParts;
        int totalParts;
        long uploadedBytes;
        long totalBytes;
        String objectKey;
        String uploadId;
        String errorMessage;
        String downloadUrl;
        Path tempFile;
    }

    private final ConcurrentHashMap<String, JobProgress> jobs = new ConcurrentHashMap<>();

    public AsyncUploadStartResponse submitAsyncUpload(Path tempFilePath, String originalFilename, String contentType, Long requestedChunkSize) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new IllegalArgumentException("Only video files are allowed");
        }
        if (tempFilePath == null || !Files.exists(tempFilePath)) {
            throw new IllegalArgumentException("Temporary file does not exist");
        }

        try {
            long fileSize = Files.size(tempFilePath);
            if (fileSize > uploadConfig.getMaxFileSize()) {
                throw new IllegalArgumentException("File size exceeds maximum allowed size");
            }

            long chunkSize = (requestedChunkSize != null && requestedChunkSize > 0) ? requestedChunkSize : uploadConfig.getDefaultChunkSize();
            final long MIN_CHUNK_SIZE = 5L * 1024 * 1024; // 5MB
            final long MAX_CHUNK_SIZE = 5L * 1024 * 1024 * 1024; // 5GB
            if (chunkSize < MIN_CHUNK_SIZE) {
                throw new IllegalArgumentException("Chunk size must be at least 5MB (5242880 bytes)");
            }
            if (chunkSize > MAX_CHUNK_SIZE) {
                throw new IllegalArgumentException("Chunk size cannot exceed 5GB (5368709120 bytes)");
            }

            String objectKey = UPLOADS_PREFIX + UUID.randomUUID() + "/" + originalFilename;
            String jobId = UUID.randomUUID().toString();

            JobProgress progress = new JobProgress();
            progress.jobId = jobId;
            progress.status = JobStatus.QUEUED;
            progress.progress = 0.0;
            progress.uploadedParts = 0;
            progress.totalParts = (int) Math.ceil((double) fileSize / chunkSize);
            progress.uploadedBytes = 0;
            progress.totalBytes = fileSize;
            progress.objectKey = objectKey;
            progress.tempFile = tempFilePath;
            jobs.put(jobId, progress);

            CompletableFuture.runAsync(() -> doMultipartUpload(progress, originalFilename, contentType, chunkSize), multipartUploadExecutor);

            log.info("Submitted server-side async upload job: {}, file: {}, size: {} bytes, parts: {}", jobId, originalFilename, fileSize, progress.totalParts);
            return new AsyncUploadStartResponse(jobId, objectKey, "Upload job submitted");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read temp file size", e);
        }
    }

    private void doMultipartUpload(JobProgress job, String originalFilename, String contentType, long chunkSize) {
        CreateMultipartUploadResponse createResponse = null;
        try (FileInputStream fis = new FileInputStream(job.tempFile.toFile())) {
            job.status = JobStatus.UPLOADING;

            // 1) Create multipart upload
            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(job.objectKey)
                    .contentType(contentType)
                    .build();
            createResponse = s3Client.createMultipartUpload(createRequest);
            job.uploadId = createResponse.uploadId();
            log.info("[{}] Created multipart upload. uploadId={}", job.jobId, job.uploadId);

            // 2) Upload parts
            List<CompletedPart> completedParts = new ArrayList<>(job.totalParts);
            long remaining = job.totalBytes;
            int partNumber = 1;
            byte[] buffer = new byte[(int) Math.min(chunkSize, Integer.MAX_VALUE)];

            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int readTotal = 0;
                while (readTotal < toRead) {
                    int r = fis.read(buffer, readTotal, toRead - readTotal);
                    if (r == -1) break;
                    readTotal += r;
                }
                if (readTotal <= 0) break;

                byte[] partBytes = (readTotal == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, readTotal);

                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(s3Config.getBucket())
                        .key(job.objectKey)
                        .uploadId(job.uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) readTotal)
                        .build();

                UploadPartResponse uploadPartResponse = s3Client.uploadPart(uploadPartRequest, RequestBody.fromBytes(partBytes));
                String eTag = uploadPartResponse.eTag();

                completedParts.add(CompletedPart.builder().partNumber(partNumber).eTag(eTag).build());

                job.uploadedBytes += readTotal;
                job.uploadedParts = partNumber;
                remaining -= readTotal;
                partNumber++;

                job.progress = (job.totalBytes > 0) ? (job.uploadedBytes * 100.0 / job.totalBytes) : 0.0;
                log.debug("[{}] Uploaded part {} ({} bytes). Progress: {}%", job.jobId, partNumber - 1, readTotal, String.format("%.2f", job.progress));
            }

            // 3) Complete multipart upload
            CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();

            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(job.objectKey)
                    .uploadId(job.uploadId)
                    .multipartUpload(completedUpload)
                    .build();

            CompleteMultipartUploadResponse completeResponse = s3Client.completeMultipartUpload(completeRequest);
            log.info("[{}] Multipart upload completed. ETag={}", job.jobId, completeResponse.eTag());

            // 4) Head object for metadata
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(job.objectKey)
                    .build());

            // 5) Persist metadata
            String filename = originalFilename;
            VideoRecording recording = VideoRecording.builder()
                    .filename(filename)
                    .size(head.contentLength())
                    .objectKey(job.objectKey)
                    .status("COMPLETED")
                    .checksum(completeResponse.eTag())
                    .build();
            videoRecordingRepository.save(recording);

            // 6) Generate download URL
            job.downloadUrl = generateDownloadUrl(job.objectKey);
            job.status = JobStatus.COMPLETED;
            job.progress = 100.0;
        } catch (Exception e) {
            log.error("[{}] Server-side multipart upload failed: {}", job.jobId, e.getMessage(), e);
            job.status = JobStatus.FAILED;
            job.errorMessage = e.getMessage();
            // Try to abort multipart upload to cleanup if created
            if (job.uploadId != null) {
                try {
                    s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                            .bucket(s3Config.getBucket())
                            .key(job.objectKey)
                            .uploadId(job.uploadId)
                            .build());
                    log.info("[{}] Aborted multipart upload after failure", job.jobId);
                } catch (Exception abortEx) {
                    log.warn("[{}] Failed to abort multipart upload after failure", job.jobId, abortEx);
                }
            }
        } finally {
            // delete temp file
            try {
                Files.deleteIfExists(job.tempFile);
            } catch (IOException ioException) {
                log.warn("[{}] Failed to delete temp file {}", job.jobId, job.tempFile, ioException);
            }
            // cleanup job record later
            CompletableFuture.delayedExecutor(60, java.util.concurrent.TimeUnit.MINUTES)
                    .execute(() -> jobs.remove(job.jobId));
        }
    }

    public AsyncUploadStatusResponse getStatus(String jobId) {
        JobProgress p = jobs.get(jobId);
        if (p == null) {
            return AsyncUploadStatusResponse.builder()
                    .jobId(jobId)
                    .status("NOT_FOUND")
                    .progress(-1)
                    .errorMessage("Job not found or already cleaned up")
                    .build();
        }
        return AsyncUploadStatusResponse.builder()
                .jobId(p.jobId)
                .status(p.status.name())
                .progress(p.progress)
                .uploadedParts(p.uploadedParts)
                .totalParts(p.totalParts)
                .uploadedBytes(p.uploadedBytes)
                .totalBytes(p.totalBytes)
                .objectKey(p.objectKey)
                .errorMessage(p.errorMessage)
                .downloadUrl(p.downloadUrl)
                .build();
    }

    private String generateDownloadUrl(String objectKey) {
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
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.warn("Failed to generate download URL for {}", objectKey, e);
            return null;
        }
    }
}
