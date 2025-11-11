package com.example.minioupload.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompressionProgress {
    
    private String jobId;
    private double progress; // 0-100, -1 indicates error
    private String status;
    private long timestamp;
    
    public CompressionProgress(String jobId, double progress, String status) {
        this.jobId = jobId;
        this.progress = progress;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }
    
    public boolean isError() {
        return progress < 0;
    }
    
    public boolean isComplete() {
        return progress >= 100;
    }
}