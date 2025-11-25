package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfConversionTaskResponse {
    
    private String taskId;
    
    private String businessId;
    
    private String userId;
    
    private String filename;
    
    private Integer totalPages;
    
    private List<Integer> convertedPages;
    
    private String status;
    
    private Boolean isBase;
    
    private String errorMessage;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
