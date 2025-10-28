package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class CompleteUploadRequest {

    @NotBlank(message = "uploadId is required")
    private String uploadId;

    @NotBlank(message = "objectKey is required")
    private String objectKey;

    @NotEmpty(message = "parts list cannot be empty")
    private List<PartETag> parts;

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public List<PartETag> getParts() {
        return parts;
    }

    public void setParts(List<PartETag> parts) {
        this.parts = parts;
    }
}
