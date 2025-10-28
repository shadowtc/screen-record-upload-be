package com.example.minioupload.dto;

public class InitUploadResponse {

    private String uploadId;
    private String objectKey;
    private long partSize;
    private int minPartNumber;
    private int maxPartNumber;

    public InitUploadResponse() {
    }

    public InitUploadResponse(String uploadId, String objectKey, long partSize, int minPartNumber, int maxPartNumber) {
        this.uploadId = uploadId;
        this.objectKey = objectKey;
        this.partSize = partSize;
        this.minPartNumber = minPartNumber;
        this.maxPartNumber = maxPartNumber;
    }

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

    public long getPartSize() {
        return partSize;
    }

    public void setPartSize(long partSize) {
        this.partSize = partSize;
    }

    public int getMinPartNumber() {
        return minPartNumber;
    }

    public void setMinPartNumber(int minPartNumber) {
        this.minPartNumber = minPartNumber;
    }

    public int getMaxPartNumber() {
        return maxPartNumber;
    }

    public void setMaxPartNumber(int maxPartNumber) {
        this.maxPartNumber = maxPartNumber;
    }
}
