package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for part ETag information.
 * 
 * Used in the complete upload request to specify which parts
 * were uploaded and their corresponding ETags for verification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartETag {

    /**
     * Part number (1-based).
     * Must be positive and correspond to an uploaded part.
     */
    @Positive(message = "partNumber must be positive")
    private int partNumber;

    /**
     * ETag returned by S3/MinIO after uploading the part.
     * This is extracted from the ETag header in the upload response.
     * Required for S3 to verify and assemble the parts correctly.
     */
    @NotBlank(message = "eTag is required")
    private String eTag;
}
