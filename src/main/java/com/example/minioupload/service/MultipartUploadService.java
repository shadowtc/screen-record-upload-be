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
import java.util.List;
import java.util.UUID;

/**
 * Service class for handling multipart upload operations to S3/MinIO.
 * 
 * This service provides comprehensive support for resumable multipart uploads including:
 * - Initialization of multipart uploads with validation
 * - Pre-signed URL generation for direct client-side uploads
 * - Upload status tracking and part verification
 * - Upload completion with metadata persistence to MySQL
 * - Upload abortion for cleanup
 * 
 * The service is optimized for large file uploads by:
 * - Using configurable chunk sizes
 * - Generating pre-signed URLs to avoid server-side upload bottlenecks
 * - Tracking upload progress in S3 for resumability
 * - Storing only metadata in MySQL for efficient querying
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MultipartUploadService {

    /**
     * S3 client for performing S3 operations (create, complete, abort multipart uploads)
     */
    private final S3Client s3Client;
    
    /**
     * S3 pre-signer for generating time-limited pre-signed URLs
     */
    private final S3Presigner s3Presigner;
    
    /**
     * Configuration properties for S3/MinIO connection
     */
    private final S3ConfigProperties s3Config;
    
    /**
     * Configuration properties for upload constraints and defaults
     */
    private final UploadConfigProperties uploadConfig;
    
    /**
     * Repository for persisting video recording metadata to MySQL
     */
    private final VideoRecordingRepository videoRecordingRepository;

    /**
     * Constant for the uploads directory prefix in S3
     */
    private static final String UPLOADS_PREFIX = "uploads/";
    
    /**
     * Constant for completed upload status
     */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /**
     * Initializes a new multipart upload session in S3/MinIO.
     * 
     * This method performs the following operations:
     * 1. Validates the file type (only video files allowed)
     * 2. Validates the file size against configured maximum
     * 3. Generates a unique S3 object key with UUID-based path
     * 4. Creates a multipart upload session in S3
     * 5. Calculates the optimal number of parts based on chunk size
     * 
     * @param request the upload initialization request containing file metadata
     * @return InitUploadResponse with uploadId, objectKey, and part information
     * @throws IllegalArgumentException if file type is not video or size exceeds limit
     */
    public InitUploadResponse initializeUpload(InitUploadRequest request) {
        log.info("Initializing upload for file: {}, size: {} bytes", request.getFileName(), request.getSize());
        
        // Validate file type - only video files are accepted
        if (!request.getContentType().startsWith("video/")) {
            log.warn("Upload rejected: invalid content type {}", request.getContentType());
            throw new IllegalArgumentException("Only video files are allowed");
        }

        // Validate file size against configured maximum
        if (request.getSize() > uploadConfig.getMaxFileSize()) {
            log.warn("Upload rejected: file size {} exceeds maximum {}", request.getSize(), uploadConfig.getMaxFileSize());
            throw new IllegalArgumentException("File size exceeds maximum allowed size");
        }

        // Determine chunk size: use client-provided value or fall back to configured default
        long chunkSize = request.getChunkSize() != null && request.getChunkSize() > 0
                ? request.getChunkSize()
                : uploadConfig.getDefaultChunkSize();

        // Generate unique object key with UUID to prevent collisions
        String objectKey = UPLOADS_PREFIX + UUID.randomUUID() + "/" + request.getFileName();

        // Create multipart upload session in S3/MinIO
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(s3Config.getBucket())
                .key(objectKey)
                .contentType(request.getContentType())
                .build();

        CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
        log.info("Multipart upload initialized with uploadId: {}, objectKey: {}", createResponse.uploadId(), objectKey);

        // Calculate total number of parts needed for the upload
        int maxPartNumber = (int) Math.ceil((double) request.getSize() / chunkSize);

        return new InitUploadResponse(
                createResponse.uploadId(),
                objectKey,
                chunkSize,
                1,
                maxPartNumber
        );
    }

    /**
     * Generates pre-signed URLs for uploading specific parts of a multipart upload.
     * 
     * Pre-signed URLs allow clients to upload parts directly to S3/MinIO without
     * going through the application server, which:
     * - Reduces server bandwidth and processing
     * - Improves upload performance
     * - Enables client-side retry logic for failed parts
     * 
     * Each URL is time-limited and specific to one part number.
     * 
     * @param uploadId the multipart upload ID from S3
     * @param objectKey the S3 object key for the upload
     * @param startPartNumber the first part number to generate URL for (inclusive)
     * @param endPartNumber the last part number to generate URL for (inclusive)
     * @return List of PresignedUrlResponse objects containing URLs and metadata
     */
    public List<PresignedUrlResponse> generatePresignedUrls(
            String uploadId,
            String objectKey,
            int startPartNumber,
            int endPartNumber) {
        
        log.info("Generating pre-signed URLs for uploadId: {}, parts: {} to {}", uploadId, startPartNumber, endPartNumber);

        List<PresignedUrlResponse> presignedUrls = new ArrayList<>(endPartNumber - startPartNumber + 1);
        Duration expiration = Duration.ofMinutes(uploadConfig.getPresignedUrlExpirationMinutes());
        Instant expiresAt = Instant.now().plus(expiration);

        // Generate a pre-signed URL for each part in the specified range
        for (int partNumber = startPartNumber; partNumber <= endPartNumber; partNumber++) {
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
        }

        log.info("Generated {} pre-signed URLs expiring at {}", presignedUrls.size(), expiresAt);
        return presignedUrls;
    }

    /**
     * Retrieves the current status of a multipart upload by listing all uploaded parts.
     * 
     * This method is useful for:
     * - Resuming interrupted uploads by identifying which parts are already uploaded
     * - Displaying upload progress to users
     * - Verifying all parts are uploaded before completing the upload
     * 
     * @param uploadId the multipart upload ID from S3
     * @param objectKey the S3 object key for the upload
     * @return List of UploadPartInfo containing part number, ETag, and size for each uploaded part
     */
    public List<UploadPartInfo> getUploadStatus(String uploadId, String objectKey) {
        log.info("Retrieving upload status for uploadId: {}, objectKey: {}", uploadId, objectKey);
        
        ListPartsRequest listPartsRequest = ListPartsRequest.builder()
                .bucket(s3Config.getBucket())
                .key(objectKey)
                .uploadId(uploadId)
                .build();

        ListPartsResponse listPartsResponse = s3Client.listParts(listPartsRequest);

        // Convert S3 Part objects to our DTO format with preallocated capacity
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
    }

    /**
     * Completes a multipart upload and persists metadata to MySQL database.
     * 
     * This method performs the following operations:
     * 1. Converts client-provided part ETags to S3 CompletedPart format
     * 2. Sends completion request to S3/MinIO to finalize the upload
     * 3. Retrieves final object metadata (size, ETag) from S3
     * 4. Persists video recording metadata to MySQL for future queries
     * 5. Generates a pre-signed download URL for the uploaded file
     * 
     * After this operation, the file becomes available for download from S3/MinIO
     * and its metadata is searchable via MySQL.
     * 
     * @param request the completion request containing uploadId, objectKey, and part ETags
     * @return CompleteUploadResponse with file metadata and download URL
     * @throws S3Exception if S3 operation fails (e.g., invalid part ETags)
     */
    @Transactional
    public CompleteUploadResponse completeUpload(CompleteUploadRequest request) {
        log.info("Completing upload for uploadId: {}, objectKey: {}", request.getUploadId(), request.getObjectKey());
        
        // Convert DTOs to S3 SDK format with preallocated capacity
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

        // Complete the multipart upload in S3/MinIO
        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(s3Config.getBucket())
                .key(request.getObjectKey())
                .uploadId(request.getUploadId())
                .multipartUpload(completedUpload)
                .build();

        CompleteMultipartUploadResponse completeResponse = s3Client.completeMultipartUpload(completeRequest);
        log.info("Multipart upload completed successfully with ETag: {}", completeResponse.eTag());

        // Retrieve final object metadata from S3
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(s3Config.getBucket())
                .key(request.getObjectKey())
                .build();

        HeadObjectResponse headResponse = s3Client.headObject(headRequest);

        // Extract filename from object key (last segment after /)
        String filename = request.getObjectKey().substring(request.getObjectKey().lastIndexOf("/") + 1);

        // Build and persist video recording metadata to MySQL using builder pattern
        VideoRecording recording = VideoRecording.builder()
                .filename(filename)
                .size(headResponse.contentLength())
                .objectKey(request.getObjectKey())
                .status(STATUS_COMPLETED)
                .checksum(completeResponse.eTag())
                .build();

        VideoRecording savedRecording = videoRecordingRepository.save(recording);
        log.info("Video recording metadata saved to database with id: {}", savedRecording.getId());

        // Generate time-limited download URL for the completed upload
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
    }

    /**
     * Aborts a multipart upload and cleans up all uploaded parts.
     * 
     * This method should be called when:
     * - User cancels the upload
     * - Upload fails and cannot be retried
     * - Upload exceeds time limits
     * 
     * Aborting an upload removes all parts from S3/MinIO storage,
     * preventing storage costs from abandoned uploads.
     * 
     * @param request the abort request containing uploadId and objectKey
     */
    public void abortUpload(AbortUploadRequest request) {
        log.info("Aborting upload for uploadId: {}, objectKey: {}", request.getUploadId(), request.getObjectKey());
        
        AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                .bucket(s3Config.getBucket())
                .key(request.getObjectKey())
                .uploadId(request.getUploadId())
                .build();

        s3Client.abortMultipartUpload(abortRequest);
        log.info("Upload aborted successfully, all parts removed from storage");
    }

    /**
     * Generates a pre-signed download URL for a completed upload.
     * 
     * The generated URL is time-limited and allows direct download from S3/MinIO
     * without requiring authentication from the client. This is useful for:
     * - Sharing files with users who don't have S3 credentials
     * - Embedding download links in emails or web pages
     * - Providing temporary access to uploaded content
     * 
     * @param objectKey the S3 object key for the file
     * @return pre-signed URL string that expires after configured duration
     */
    private String generateDownloadUrl(String objectKey) {
        log.debug("Generating download URL for objectKey: {}", objectKey);
        
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
    }
}
