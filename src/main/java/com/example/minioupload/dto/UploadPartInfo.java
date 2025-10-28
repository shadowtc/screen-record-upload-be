package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for uploaded part information.
 * 
 * Contains metadata about a successfully uploaded part from S3/MinIO.
 * Used to inform clients which parts have been uploaded for resumability.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadPartInfo {

    /**
     * Part number (1-based).
     */
    private int partNumber;

    /**
     * ETag of the uploaded part from S3/MinIO.
     * This is a hash/checksum used to verify part integrity.
     * Required for completing the multipart upload.
     */
    private String etag;

    /**
     * Size of the uploaded part in bytes.
     * Useful for calculating upload progress.
     */
    private long size;
}
