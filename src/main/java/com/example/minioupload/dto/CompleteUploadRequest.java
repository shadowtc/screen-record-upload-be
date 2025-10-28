package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object for completing a multipart upload.
 * 
 * Contains all information needed to finalize the upload by
 * assembling the uploaded parts into a single object in S3/MinIO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRequest {

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

    /**
     * List of all uploaded parts with their ETags.
     * Must contain at least one part.
     * Parts should be in sequential order (1, 2, 3, ...).
     * Each ETag must match what S3/MinIO returned during part upload.
     */
    @NotEmpty(message = "parts list cannot be empty")
    private List<PartETag> parts;
}
