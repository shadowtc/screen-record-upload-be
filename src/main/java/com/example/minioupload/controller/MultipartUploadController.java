package com.example.minioupload.controller;

import com.example.minioupload.dto.*;
import com.example.minioupload.service.MultipartUploadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/uploads")
public class MultipartUploadController {

    private final MultipartUploadService multipartUploadService;

    public MultipartUploadController(MultipartUploadService multipartUploadService) {
        this.multipartUploadService = multipartUploadService;
    }

    @PostMapping("/init")
    public ResponseEntity<InitUploadResponse> initializeUpload(@Valid @RequestBody InitUploadRequest request) {
        InitUploadResponse response = multipartUploadService.initializeUpload(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{uploadId}/parts")
    public ResponseEntity<List<PresignedUrlResponse>> getPresignedUrls(
            @PathVariable String uploadId,
            @RequestParam String objectKey,
            @RequestParam int startPartNumber,
            @RequestParam int endPartNumber) {
        List<PresignedUrlResponse> presignedUrls = multipartUploadService.generatePresignedUrls(
                uploadId, objectKey, startPartNumber, endPartNumber);
        return ResponseEntity.ok(presignedUrls);
    }

    @GetMapping("/{uploadId}/status")
    public ResponseEntity<List<UploadPartInfo>> getUploadStatus(
            @PathVariable String uploadId,
            @RequestParam String objectKey) {
        List<UploadPartInfo> uploadedParts = multipartUploadService.getUploadStatus(uploadId, objectKey);
        return ResponseEntity.ok(uploadedParts);
    }

    @PostMapping("/complete")
    public ResponseEntity<CompleteUploadResponse> completeUpload(@Valid @RequestBody CompleteUploadRequest request) {
        CompleteUploadResponse response = multipartUploadService.completeUpload(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/abort")
    public ResponseEntity<Void> abortUpload(@Valid @RequestBody AbortUploadRequest request) {
        multipartUploadService.abortUpload(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
