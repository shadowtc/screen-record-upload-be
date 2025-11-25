package com.example.minioupload.controller;

import com.example.minioupload.dto.*;
import com.example.minioupload.service.PdfUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfConversionController {
    
    private final PdfUploadService pdfUploadService;
    
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PdfUploadResponse> uploadPdf(
            @RequestPart("file") MultipartFile file,
            @RequestParam("businessId") String businessId,
            @RequestParam("userId") String userId,
            @RequestParam(value = "pages", required = false) List<Integer> pages,
            @RequestParam(value = "imageDpi", required = false) Integer imageDpi,
            @RequestParam(value = "imageFormat", required = false) String imageFormat) {
        
        log.info("Received PDF upload request - businessId: {}, userId: {}, file: {}, size: {} bytes, pages: {}", 
            businessId, userId, file.getOriginalFilename(), file.getSize(), pages);
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(PdfUploadResponse.builder()
                    .status("ERROR")
                    .message("File is empty")
                    .build());
        }
        
        PdfConversionTaskRequest request = PdfConversionTaskRequest.builder()
            .businessId(businessId)
            .userId(userId)
            .pages(pages)
            .imageDpi(imageDpi)
            .imageFormat(imageFormat)
            .build();
        
        try {
            PdfUploadResponse response = pdfUploadService.uploadPdfAndConvertToImages(file, request);
            
            if ("ERROR".equals(response.getStatus())) {
                return ResponseEntity.badRequest().body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to process PDF upload request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PdfUploadResponse.builder()
                    .status("ERROR")
                    .message("Failed to process request: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/task/{taskId}")
    public ResponseEntity<PdfConversionTaskResponse> getTask(@PathVariable String taskId) {
        log.debug("Getting task details for taskId: {}", taskId);
        
        PdfConversionTaskResponse task = pdfUploadService.getTask(taskId);
        
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(task);
    }
    
    @GetMapping("/tasks")
    public ResponseEntity<List<PdfConversionTaskResponse>> getTasks(
            @RequestParam(value = "businessId", required = false) String businessId,
            @RequestParam(value = "userId", required = false) String userId) {
        
        log.debug("Getting tasks for businessId: {}, userId: {}", businessId, userId);
        
        List<PdfConversionTaskResponse> tasks = pdfUploadService.getTasks(businessId, userId);
        
        return ResponseEntity.ok(tasks);
    }
    
    @GetMapping("/progress/{taskId}")
    public ResponseEntity<PdfConversionProgress> getProgress(@PathVariable String taskId) {
        log.debug("Getting progress for taskId: {}", taskId);
        
        PdfConversionProgress progress = pdfUploadService.getProgress(taskId);
        
        if ("NOT_FOUND".equals(progress.getStatus())) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(progress);
    }
    
    @GetMapping("/images")
    public ResponseEntity<PdfImageResponse> getImages(
            @RequestParam("businessId") String businessId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "startPage", required = false, defaultValue = "1") Integer startPage,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        
        log.info("Getting page images - businessId: {}, userId: {}, startPage: {}, pageSize: {}", 
            businessId, userId, startPage, pageSize);
        
        try {
            PdfImageResponse response = pdfUploadService.getImages(businessId, userId, startPage, pageSize);
            
            if ("NOT_FOUND".equals(response.getStatus())) {
                return ResponseEntity.notFound().build();
            }
            
            if ("ERROR".equals(response.getStatus())) {
                return ResponseEntity.badRequest().body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get page images - businessId: {}, userId: {}", businessId, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PdfImageResponse.builder()
                    .businessId(businessId)
                    .userId(userId)
                    .status("ERROR")
                    .message("Failed to retrieve page images: " + e.getMessage())
                    .build());
        }
    }
}
