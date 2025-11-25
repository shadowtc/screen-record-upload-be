package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfPageImageInfo {
    
    private Integer pageNumber;
    
    private String imageObjectKey;
    
    private Boolean isBase;
    
    private String userId;
    
    private Integer width;
    
    private Integer height;
    
    private Long fileSize;
}
