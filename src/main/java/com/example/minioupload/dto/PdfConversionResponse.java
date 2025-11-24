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
public class PdfConversionResponse {
    
    private String jobId;
    
    private String status;
    
    private String message;
    
    private String pdfFilePath;
    
    private Long pdfFileSize;
    
    private Integer pageCount;
    
    private List<String> imageFilePaths;
    
    private Long processingTimeMs;
}
