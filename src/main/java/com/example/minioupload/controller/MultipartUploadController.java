package com.example.minioupload.controller;

import com.example.minioupload.dto.*;
import com.example.minioupload.service.MultipartUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for multipart upload operations to S3/MinIO.
 * 
 * This controller provides endpoints for the complete lifecycle of multipart uploads:
 * 1. Initialize a new upload session
 * 2. Generate pre-signed URLs for uploading parts
 * 3. Check upload status and retrieve uploaded parts
 * 4. Complete the upload and persist metadata
 * 5. Abort/cancel an upload
 * 
 * All endpoints follow RESTful conventions and return appropriate HTTP status codes.
 * Request validation is enforced using Jakarta Bean Validation annotations.
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Slf4j
public class MultipartUploadController {

    /**
     * Service layer for handling multipart upload business logic
     */
    private final MultipartUploadService multipartUploadService;

    /**
     * Initializes a new multipart upload session.
     * 
     * This endpoint validates the request and creates a new upload session in S3/MinIO.
     * The client receives an uploadId and objectKey to use for subsequent operations.
     * 
     * @param request validated request containing filename, size, content type, and optional chunk size
     * @return InitUploadResponse with uploadId, objectKey, part size, and part number range
     */
    @PostMapping("/init")
    public ResponseEntity<InitUploadResponse> initializeUpload(@Valid @RequestBody InitUploadRequest request) {
        log.info("POST /api/uploads/init - Initializing upload for file: {}", request.getFileName());
        InitUploadResponse response = multipartUploadService.initializeUpload(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Generates pre-signed URLs for uploading specific parts.
     * 
     * This endpoint returns time-limited URLs that clients can use to upload
     * parts directly to S3/MinIO without going through this server.
     * Parts can be uploaded in parallel for better performance.
     * 
     * @param uploadId the upload session ID from initialization
     * @param objectKey the S3 object key from initialization
     * @param startPartNumber first part number to generate URL for (1-based, inclusive)
     * @param endPartNumber last part number to generate URL for (1-based, inclusive)
     * @return List of pre-signed URLs with expiration timestamps
     */
    @GetMapping("/{uploadId}/parts")
    public ResponseEntity<List<PresignedUrlResponse>> getPresignedUrls(
            @PathVariable String uploadId,
            @RequestParam String objectKey,
            @RequestParam int startPartNumber,
            @RequestParam int endPartNumber) {
        log.info("GET /api/uploads/{}/parts - Generating URLs for parts {} to {}", uploadId, startPartNumber, endPartNumber);
        List<PresignedUrlResponse> presignedUrls = multipartUploadService.generatePresignedUrls(
                uploadId, objectKey, startPartNumber, endPartNumber);
        return ResponseEntity.ok(presignedUrls);
    }

    /**
     * Retrieves the current status of an upload.
     * 
     * This endpoint returns information about which parts have been successfully
     * uploaded to S3/MinIO. Useful for resuming interrupted uploads or displaying
     * progress to users.
     * 
     * @param uploadId the upload session ID from initialization
     * @param objectKey the S3 object key from initialization
     * @return List of uploaded part information including part numbers, ETags, and sizes
     */
    @GetMapping("/{uploadId}/status")
    public ResponseEntity<List<UploadPartInfo>> getUploadStatus(
            @PathVariable String uploadId,
            @RequestParam String objectKey) {
        log.info("GET /api/uploads/{}/status - Retrieving upload status", uploadId);
        List<UploadPartInfo> uploadedParts = multipartUploadService.getUploadStatus(uploadId, objectKey);
        return ResponseEntity.ok(uploadedParts);
    }

    /**
     * Completes a multipart upload.
     * 
     * This endpoint finalizes the upload by combining all parts into a single object
     * in S3/MinIO and persisting the metadata to MySQL. After completion, the file
     * becomes available for download.
     * 
     * @param request validated request containing uploadId, objectKey, and list of part ETags
     * @return CompleteUploadResponse with file metadata and pre-signed download URL
     */
    @PostMapping("/complete")
    public ResponseEntity<CompleteUploadResponse> completeUpload(@Valid @RequestBody CompleteUploadRequest request) {
        log.info("POST /api/uploads/complete - Completing upload {}", request.getUploadId());
        CompleteUploadResponse response = multipartUploadService.completeUpload(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Aborts a multipart upload.
     * 
     * This endpoint cancels an upload and cleans up all uploaded parts from S3/MinIO.
     * Should be called when the client decides to cancel the upload or when the upload
     * cannot be completed successfully.
     * 
     * @param request validated request containing uploadId and objectKey
     * @return 204 No Content on successful abortion
     */
    @PostMapping("/abort")
    public ResponseEntity<Void> abortUpload(@Valid @RequestBody AbortUploadRequest request) {
        log.info("POST /api/uploads/abort - Aborting upload {}", request.getUploadId());
        multipartUploadService.abortUpload(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
