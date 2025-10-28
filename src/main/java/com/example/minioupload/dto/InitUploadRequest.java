package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for initializing a multipart upload.
 * 
 * This request contains all necessary information to start a new upload session
 * including file metadata and optional upload configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitUploadRequest {

    /**
     * Original filename of the file to upload.
     * Required field - must not be blank.
     */
    @NotBlank(message = "fileName is required")
    private String fileName;

    /**
     * Total size of the file in bytes.
     * Required field - must be positive.
     * Used to calculate the number of parts needed and validate against max file size.
     */
    @NotNull(message = "size is required")
    @Positive(message = "size must be positive")
    private Long size;

    /**
     * MIME content type of the file (e.g., "video/mp4").
     * Required field - must not be blank.
     * Must start with "video/" for validation to pass.
     */
    @NotBlank(message = "contentType is required")
    private String contentType;

    /**
     * Optional custom chunk/part size in bytes.
     * If not provided, the server's default chunk size will be used.
     * Minimum: 5MB (S3 requirement)
     * Maximum: 5GB (S3 limit)
     */
    private Long chunkSize;
}
