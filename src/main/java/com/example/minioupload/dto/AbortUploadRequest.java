package com.example.minioupload.dto;

import jakarta.validation.constraints.NotBlank;

public class AbortUploadRequest {

    @NotBlank(message = "uploadId is required")
    private String uploadId;

    @NotBlank(message = "objectKey is required")
    private String objectKey;

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
}
