package com.example.minioupload.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Entity class representing a video recording uploaded to S3/MinIO storage.
 * This entity tracks metadata about uploaded videos including file information,
 * storage location, upload status, and video properties.
 * 
 * Uses MySQL 8.0 as the persistence layer with optimized indexes for common queries.
 */
@Entity
@Table(name = "video_recordings", indexes = {
    @Index(name = "idx_object_key", columnList = "object_key", unique = true),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoRecording {

    /**
     * Primary key - Auto-generated unique identifier for the video recording
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User ID associated with this video recording
     * Indexed for efficient user-based queries
     */
    @Column(name = "user_id", length = 100)
    private String userId;

    /**
     * Original filename of the uploaded video
     * Required field that stores the name of the video file
     */
    @Column(nullable = false, length = 500)
    private String filename;

    /**
     * Size of the video file in bytes
     * Required field used for storage tracking and validation
     */
    @Column(nullable = false)
    private Long size;

    /**
     * Duration of the video in seconds
     * Optional field for video playback information
     */
    private Long duration;

    /**
     * Video width in pixels
     * Optional field for video resolution information
     */
    private Integer width;

    /**
     * Video height in pixels
     * Optional field for video resolution information
     */
    private Integer height;

    /**
     * Video codec information (e.g., H.264, VP9)
     * Optional field for video encoding details
     */
    @Column(length = 50)
    private String codec;

    /**
     * S3/MinIO object key - unique storage identifier
     * Required and unique field that represents the full path in object storage
     * Indexed for efficient lookups when accessing stored videos
     */
    @Column(name = "object_key", nullable = false, unique = true, length = 1000)
    private String objectKey;

    /**
     * Upload status of the video (e.g., COMPLETED, FAILED, IN_PROGRESS)
     * Required field tracked throughout the upload lifecycle
     * Indexed for efficient status-based queries
     */
    @Column(nullable = false, length = 50)
    private String status;

    /**
     * Checksum/ETag of the uploaded file for integrity verification
     * Stores the S3 ETag returned after successful upload
     */
    @Column(length = 255)
    private String checksum;

    /**
     * Timestamp when the record was created
     * Automatically set on entity creation
     * Indexed for efficient time-based queries and sorting
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA lifecycle callback - executed before entity is persisted to database
     * Automatically sets the creation timestamp
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
