package com.example.minioupload.controller;

import com.example.minioupload.dto.CompressionProgress;
import com.example.minioupload.dto.VideoCompressionRequest;
import com.example.minioupload.dto.VideoCompressionResponse;
import com.example.minioupload.service.VideoCompressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
@Validated
public class VideoCompressionController {
    
    private final VideoCompressionService videoCompressionService;
    
    @PostMapping("/compress")
    public ResponseEntity<VideoCompressionResponse> compressVideo(
            @Valid @RequestBody VideoCompressionRequest request) {
        
        log.info("Received video compression request for file: {}", request.getInputFilePath());
        
        try {
            VideoCompressionResponse response = videoCompressionService.compressVideo(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Video compression failed", e);
            VideoCompressionResponse errorResponse = VideoCompressionResponse.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @PostMapping("/compress/async")
    public CompletableFuture<ResponseEntity<VideoCompressionResponse>> compressVideoAsync(
            @Valid @RequestBody VideoCompressionRequest request) {
        
        log.info("Received async video compression request for file: {}", request.getInputFilePath());
        
        return videoCompressionService.compressVideoAsync(request)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> {
                log.error("Async video compression failed", throwable);
                VideoCompressionResponse errorResponse = VideoCompressionResponse.builder()
                    .success(false)
                    .errorMessage(throwable.getCause() != null ? 
                        throwable.getCause().getMessage() : throwable.getMessage())
                    .build();
                return ResponseEntity.internalServerError().body(errorResponse);
            });
    }
    
    @GetMapping("/progress/{jobId}")
    public ResponseEntity<CompressionProgress> getCompressionProgress(@PathVariable String jobId) {
        CompressionProgress progress = videoCompressionService.getCompressionProgress(jobId);
        
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(progress);
    }
    
    @GetMapping("/presets")
    public ResponseEntity<Object> getAvailablePresets() {
        // This would return available compression presets
        // Implementation depends on how you want to expose the configuration
        return ResponseEntity.ok(new Object() {
            public final String[] presets = {"high-quality", "balanced", "high-compression"};
            public final String[] resolutions = {"480p", "720p", "1080p"};
            public final String[] encoderPresets = {
                "ultrafast", "superfast", "veryfast", "faster", 
                "fast", "medium", "slow", "slower", "veryslow"
            };
        });
    }
}