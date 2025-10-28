package com.example.minioupload.dto;

import java.time.Instant;

public class PresignedUrlResponse {

    private int partNumber;
    private String url;
    private Instant expiresAt;

    public PresignedUrlResponse() {
    }

    public PresignedUrlResponse(int partNumber, String url, Instant expiresAt) {
        this.partNumber = partNumber;
        this.url = url;
        this.expiresAt = expiresAt;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
