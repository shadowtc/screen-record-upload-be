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
public class PdfConversionTaskRequest {
    
    private String businessId;
    
    private String userId;
    
    private List<Integer> pages;
    
    private Integer imageDpi;
    
    private String imageFormat;
}
