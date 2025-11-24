package com.example.minioupload.controller;

import com.example.minioupload.dto.PdfConversionProgress;
import com.example.minioupload.dto.PdfConversionRequest;
import com.example.minioupload.dto.PdfConversionResponse;
import com.example.minioupload.service.PdfConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfConversionController {
    
    private final PdfConversionService pdfConversionService;
    
    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PdfConversionResponse> convertToPdf(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "request", required = false) PdfConversionRequest request) {
        
        log.info("Received PDF conversion request for file: {}, size: {} bytes", 
            file.getOriginalFilename(), file.getSize());
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(PdfConversionResponse.builder()
                    .status("ERROR")
                    .message("File is empty")
                    .build());
        }
        
        if (request == null) {
            request = new PdfConversionRequest();
        }
        
        if (request.getConvertToImages() == null) {
            request.setConvertToImages(true);
        }
        
        try {
            PdfConversionResponse response = pdfConversionService.convertToPdfAsync(file, request);
            
            if ("ERROR".equals(response.getStatus())) {
                return ResponseEntity.badRequest().body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to process PDF conversion request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PdfConversionResponse.builder()
                    .status("ERROR")
                    .message("Failed to process request: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/progress/{jobId}")
    public ResponseEntity<PdfConversionProgress> getProgress(@PathVariable String jobId) {
        log.debug("Getting progress for jobId: {}", jobId);
        
        PdfConversionProgress progress = pdfConversionService.getProgress(jobId);
        
        if ("NOT_FOUND".equals(progress.getStatus())) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(progress);
    }
    
    @GetMapping("/result/{jobId}")
    public ResponseEntity<PdfConversionResponse> getResult(@PathVariable String jobId) {
        log.debug("Getting result for jobId: {}", jobId);
        
        PdfConversionResponse result = pdfConversionService.getResult(jobId);
        
        if ("NOT_FOUND".equals(result.getStatus())) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/formats")
    public ResponseEntity<Map<String, Object>> getSupportedFormats() {
        Set<String> formats = pdfConversionService.getSupportedFormats();
        
        Map<String, Object> response = new HashMap<>();
        response.put("supportedFormats", formats);
        response.put("count", formats.size());
        response.put("categories", Map.of(
            "documents", Set.of("doc", "docx", "txt", "pdf"),
            "spreadsheets", Set.of("xls", "xlsx"),
            "presentations", Set.of("ppt", "pptx"),
            "images", Set.of("jpg", "jpeg", "png", "bmp", "gif")
        ));
        
        return ResponseEntity.ok(response);
    }
}
