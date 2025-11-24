package com.example.minioupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageImageInfo {
    private Integer pageNumber;
    private String imagePath;
    private Long fileSize;
    private Integer width;
    private Integer height;
}
