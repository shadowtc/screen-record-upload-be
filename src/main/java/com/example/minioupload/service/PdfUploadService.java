package com.example.minioupload.service;

import com.example.minioupload.config.PdfConversionProperties;
import com.example.minioupload.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PdfUploadService {
    
    private final PdfConversionProperties properties;
    private final PdfToImageService pdfToImageService;
    private final Executor videoCompressionExecutor;
    
    private final Map<String, PdfConversionProgress> progressMap = new ConcurrentHashMap<>();
    
    public PdfUploadService(
            PdfConversionProperties properties,
            PdfToImageService pdfToImageService,
            @Qualifier("videoCompressionExecutor") Executor videoCompressionExecutor) {
        this.properties = properties;
        this.pdfToImageService = pdfToImageService;
        this.videoCompressionExecutor = videoCompressionExecutor;
    }
    
    public PdfUploadResponse uploadPdfAndConvertToImages(MultipartFile file, PdfUploadRequest request) {
        if (!properties.isEnabled()) {
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message("PDF conversion service is disabled")
                .build();
        }
        
        if (file.isEmpty()) {
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message("File is empty")
                .build();
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message("Only PDF files are allowed")
                .build();
        }
        
        if (file.getSize() > properties.getMaxFileSize()) {
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message(String.format("File size exceeds limit. Max: %d MB, Actual: %.2f MB",
                    properties.getMaxFileSize() / 1024 / 1024,
                    file.getSize() / 1024.0 / 1024.0))
                .build();
        }
        
        String taskId = UUID.randomUUID().toString();
        
        PdfConversionProgress progress = PdfConversionProgress.builder()
            .jobId(taskId)
            .status("SUBMITTED")
            .currentPhase("Initializing")
            .progressPercentage(0)
            .message("PDF upload submitted successfully")
            .startTime(System.currentTimeMillis())
            .build();
        
        progressMap.put(taskId, progress);
        
        final PdfUploadRequest finalRequest = request != null ? request : new PdfUploadRequest();
        CompletableFuture.runAsync(() -> 
            executePdfToImageConversion(file, finalRequest, taskId), videoCompressionExecutor);
        
        return PdfUploadResponse.builder()
            .taskId(taskId)
            .status("PROCESSING")
            .message("PDF upload successful. Converting to images in background.")
            .build();
    }
    
    private void executePdfToImageConversion(MultipartFile file, PdfUploadRequest request, String taskId) {
        long startTime = System.currentTimeMillis();
        
        try {
            updateProgress(taskId, "PROCESSING", "Saving PDF file", 10, null);
            
            Path taskDir = Paths.get(properties.getTempDirectory(), taskId);
            Files.createDirectories(taskDir);
            
            File pdfFile = taskDir.resolve(file.getOriginalFilename()).toFile();
            file.transferTo(pdfFile);
            
            updateProgress(taskId, "PROCESSING", "Getting PDF page count", 20, null);
            
            int pageCount = pdfToImageService.getPageCount(pdfFile);
            
            PdfConversionProgress progress = progressMap.get(taskId);
            progress.setTotalPages(pageCount);
            
            updateProgress(taskId, "PROCESSING", "Converting PDF pages to images", 30, null);
            
            int dpi = request.getImageDpi() != null ? request.getImageDpi() : 
                properties.getImageRendering().getDpi();
            String format = request.getImageFormat() != null ? request.getImageFormat() : 
                properties.getImageRendering().getFormat();
            
            List<String> imageFiles = pdfToImageService.convertPdfToImages(pdfFile, taskId, dpi, format);
            
            updateProgress(taskId, "PROCESSING", "Image conversion completed", 95, null);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            PdfConversionProgress finalProgress = progressMap.get(taskId);
            finalProgress.setStatus("COMPLETED");
            finalProgress.setCurrentPhase("Completed");
            finalProgress.setProgressPercentage(100);
            finalProgress.setMessage("PDF to images conversion completed successfully");
            finalProgress.setElapsedTimeMs(processingTime);
            finalProgress.setProcessedPages(pageCount);
            
            log.info("PDF to images conversion completed for taskId: {} in {}ms, pages: {}, images: {}", 
                taskId, processingTime, pageCount, imageFiles.size());
            
        } catch (Exception e) {
            log.error("PDF to images conversion failed for taskId: {}", taskId, e);
            updateProgress(taskId, "FAILED", "Conversion failed", 0, 
                "Conversion failed: " + e.getMessage());
        }
    }
    
    private void updateProgress(String taskId, String status, String phase, 
                                 int percentage, String errorMessage) {
        PdfConversionProgress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.setStatus(status);
            progress.setCurrentPhase(phase);
            progress.setProgressPercentage(percentage);
            if (errorMessage != null) {
                progress.setErrorMessage(errorMessage);
            }
            progress.setElapsedTimeMs(System.currentTimeMillis() - progress.getStartTime());
            
            log.debug("Progress update for taskId {}: {} - {}% - {}", 
                taskId, status, percentage, phase);
        }
    }
    
    public PdfConversionProgress getProgress(String taskId) {
        PdfConversionProgress progress = progressMap.get(taskId);
        if (progress == null) {
            return PdfConversionProgress.builder()
                .jobId(taskId)
                .status("NOT_FOUND")
                .message("Task not found")
                .build();
        }
        return progress;
    }
    
    public PageImageResponse getPageImages(String taskId, Integer startPage, Integer pageSize) {
        PdfConversionProgress progress = progressMap.get(taskId);
        if (progress == null) {
            return PageImageResponse.builder()
                .taskId(taskId)
                .status("NOT_FOUND")
                .message("Task not found")
                .build();
        }
        
        if (!"COMPLETED".equals(progress.getStatus())) {
            return PageImageResponse.builder()
                .taskId(taskId)
                .status(progress.getStatus())
                .message("Task is not completed yet. Current status: " + progress.getStatus())
                .totalPages(progress.getTotalPages())
                .build();
        }
        
        Path taskDir = Paths.get(properties.getTempDirectory(), taskId);
        Path imageDir = taskDir.resolve("images");
        
        if (!Files.exists(imageDir)) {
            return PageImageResponse.builder()
                .taskId(taskId)
                .status("ERROR")
                .message("Image directory not found")
                .build();
        }
        
        try {
            List<File> allImageFiles = Files.walk(imageDir)
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .sorted((f1, f2) -> f1.getName().compareTo(f2.getName()))
                .collect(Collectors.toList());
            
            int totalPages = allImageFiles.size();
            int effectiveStartPage = startPage != null && startPage > 0 ? startPage : 1;
            int effectivePageSize = pageSize != null && pageSize > 0 ? pageSize : 10;
            
            if (effectiveStartPage > totalPages) {
                return PageImageResponse.builder()
                    .taskId(taskId)
                    .status("SUCCESS")
                    .message("Start page exceeds total pages")
                    .totalPages(totalPages)
                    .startPage(effectiveStartPage)
                    .pageSize(effectivePageSize)
                    .returnedPages(0)
                    .images(new ArrayList<>())
                    .build();
            }
            
            int startIndex = effectiveStartPage - 1;
            int endIndex = Math.min(startIndex + effectivePageSize, totalPages);
            
            List<PageImageInfo> pageImages = new ArrayList<>();
            for (int i = startIndex; i < endIndex; i++) {
                File imageFile = allImageFiles.get(i);
                
                PageImageInfo imageInfo = PageImageInfo.builder()
                    .pageNumber(i + 1)
                    .imagePath(imageFile.getAbsolutePath())
                    .fileSize(imageFile.length())
                    .build();
                
                try {
                    BufferedImage image = ImageIO.read(imageFile);
                    if (image != null) {
                        imageInfo.setWidth(image.getWidth());
                        imageInfo.setHeight(image.getHeight());
                    }
                } catch (IOException e) {
                    log.warn("Failed to read image dimensions for: {}", imageFile.getName(), e);
                }
                
                pageImages.add(imageInfo);
            }
            
            return PageImageResponse.builder()
                .taskId(taskId)
                .status("SUCCESS")
                .message("Successfully retrieved page images")
                .totalPages(totalPages)
                .startPage(effectiveStartPage)
                .pageSize(effectivePageSize)
                .returnedPages(pageImages.size())
                .images(pageImages)
                .build();
            
        } catch (IOException e) {
            log.error("Failed to get page images for taskId: {}", taskId, e);
            return PageImageResponse.builder()
                .taskId(taskId)
                .status("ERROR")
                .message("Failed to retrieve page images: " + e.getMessage())
                .build();
        }
    }
}
