package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for multipart upload initialization response.
 * 
 * Contains all information the client needs to proceed with uploading
 * file parts to S3/MinIO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitUploadResponse {

    /**
     * Unique upload session ID from S3/MinIO.
     * Must be included in all subsequent requests (get URLs, complete, abort).
     */
    private String uploadId;

    /**
     * S3 object key where the file will be stored.
     * Format: "uploads/{uuid}/{filename}"
     * Must be included in all subsequent requests.
     */
    private String objectKey;

    /**
     * Size of each part/chunk in bytes.
     * Client should split the file into chunks of this size for upload.
     * Last chunk may be smaller than this size.
     */
    private long partSize;

    /**
     * Minimum part number (always 1).
     * S3 part numbers are 1-based.
     */
    private int minPartNumber;

    /**
     * Maximum part number needed to complete the upload.
     * Calculated as: ceil(fileSize / partSize)
     * Client should upload parts 1 through maxPartNumber.
     */
    private int maxPartNumber;
}
