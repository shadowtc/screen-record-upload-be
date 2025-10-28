package com.example.minioupload.repository;

import com.example.minioupload.model.VideoRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VideoRecordingRepository extends JpaRepository<VideoRecording, Long> {
    Optional<VideoRecording> findByObjectKey(String objectKey);
}
