package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for completed upload response.
 * 
 * Contains metadata about the successfully uploaded file including
 * database record information and a download URL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadResponse {

    /**
     * Database ID of the video recording record.
     * Can be used for future queries and updates.
     */
    private Long id;

    /**
     * Original filename of the uploaded file.
     * Extracted from the object key.
     */
    private String filename;

    /**
     * Total size of the uploaded file in bytes.
     * Retrieved from S3/MinIO after completion.
     */
    private Long size;

    /**
     * S3 object key where the file is stored.
     * Can be used to access the file programmatically.
     */
    private String objectKey;

    /**
     * Upload status (e.g., "COMPLETED").
     * Indicates the final state of the upload.
     */
    private String status;

    /**
     * Pre-signed download URL for accessing the file.
     * Time-limited URL that expires after configured duration.
     * Allows direct download from S3/MinIO without authentication.
     */
    private String downloadUrl;

    /**
     * Timestamp when the record was created in the database.
     * Automatically set during persistence.
     */
    private LocalDateTime createdAt;
}
