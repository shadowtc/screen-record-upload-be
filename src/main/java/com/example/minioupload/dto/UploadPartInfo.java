package com.example.minioupload.dto;

public class UploadPartInfo {

    private int partNumber;
    private String etag;
    private long size;

    public UploadPartInfo() {
    }

    public UploadPartInfo(int partNumber, String etag, long size) {
        this.partNumber = partNumber;
        this.etag = etag;
        this.size = size;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
