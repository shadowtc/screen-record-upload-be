package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data Transfer Object for pre-signed upload URL response.
 * 
 * Contains a time-limited URL that clients can use to upload
 * a specific part directly to S3/MinIO via HTTP PUT.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {

    /**
     * Part number this URL is for (1-based).
     * Must match the part number when uploading.
     */
    private int partNumber;

    /**
     * Pre-signed URL for uploading this part.
     * Client should send HTTP PUT request with part data to this URL.
     * The response will include an ETag header that must be saved for completion.
     */
    private String url;

    /**
     * Timestamp when this URL expires.
     * After expiration, the URL cannot be used and a new one must be requested.
     */
    private Instant expiresAt;
}
