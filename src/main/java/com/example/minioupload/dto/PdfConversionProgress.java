package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfConversionProgress {
    
    private String jobId;
    
    private String status;
    
    private String currentPhase;
    
    private Integer totalPages;
    
    private Integer processedPages;
    
    private Integer progressPercentage;
    
    private String message;
    
    private String errorMessage;
    
    private Long startTime;
    
    private Long elapsedTimeMs;
}
