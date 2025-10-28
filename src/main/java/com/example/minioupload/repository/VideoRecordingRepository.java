package com.example.minioupload.repository;

import com.example.minioupload.model.VideoRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for VideoRecording entity operations.
 * Provides database access methods for video recording metadata using Spring Data JPA.
 * 
 * This repository leverages MySQL 8.0 features and indexes defined in the VideoRecording entity
 * for optimized query performance.
 */
@Repository
public interface VideoRecordingRepository extends JpaRepository<VideoRecording, Long> {
    
    /**
     * Finds a video recording by its S3/MinIO object key.
     * 
     * This method utilizes the unique index on object_key column for efficient lookups.
     * The object key serves as the unique identifier for files stored in S3/MinIO.
     * 
     * @param objectKey the S3/MinIO object key to search for (e.g., "uploads/uuid/filename.mp4")
     * @return Optional containing the VideoRecording if found, empty Optional otherwise
     */
    Optional<VideoRecording> findByObjectKey(String objectKey);
}
