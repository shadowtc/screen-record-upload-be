package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfConversionRequest {
    
    private String outputFilename;
    
    private Boolean convertToImages;
    
    private Integer imageDpi;
    
    private String imageFormat;
}
