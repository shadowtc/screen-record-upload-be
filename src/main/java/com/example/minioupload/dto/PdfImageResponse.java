package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfImageResponse {
    
    private String businessId;
    
    private String userId;
    
    private Integer totalPages;
    
    private Integer startPage;
    
    private Integer pageSize;
    
    private Integer returnedPages;
    
    private List<PdfPageImageInfo> images;
    
    private String status;
    
    private String message;
}
