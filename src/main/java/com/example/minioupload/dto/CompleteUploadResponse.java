package com.example.minioupload.dto;

import java.time.LocalDateTime;

public class CompleteUploadResponse {

    private Long id;
    private String filename;
    private Long size;
    private String objectKey;
    private String status;
    private String downloadUrl;
    private LocalDateTime createdAt;

    public CompleteUploadResponse() {
    }

    public CompleteUploadResponse(Long id, String filename, Long size, String objectKey, String status, String downloadUrl, LocalDateTime createdAt) {
        this.id = id;
        this.filename = filename;
        this.size = size;
        this.objectKey = objectKey;
        this.status = status;
        this.downloadUrl = downloadUrl;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
