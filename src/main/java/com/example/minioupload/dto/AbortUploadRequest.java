package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for aborting a multipart upload.
 * 
 * Used to cancel an upload and clean up all uploaded parts from S3/MinIO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbortUploadRequest {

    /**
     * Upload session ID from initialization.
     * Required field - must not be blank.
     */
    @NotBlank(message = "uploadId is required")
    private String uploadId;

    /**
     * S3 object key from initialization.
     * Required field - must not be blank.
     */
    @NotBlank(message = "objectKey is required")
    private String objectKey;
}
