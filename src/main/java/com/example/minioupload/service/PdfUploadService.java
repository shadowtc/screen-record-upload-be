package com.example.minioupload.service;

import com.example.minioupload.config.PdfConversionProperties;
import com.example.minioupload.dto.*;
import com.example.minioupload.model.PdfConversionTask;
import com.example.minioupload.model.PdfPageImage;
import com.example.minioupload.repository.PdfConversionTaskRepository;
import com.example.minioupload.repository.PdfPageImageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PdfUploadService {
    
    private final PdfConversionProperties properties;
    private final PdfToImageService pdfToImageService;
    private final Executor videoCompressionExecutor;
    private final PdfConversionTaskRepository taskRepository;
    private final PdfPageImageRepository pageImageRepository;
    private final ObjectMapper objectMapper;
    
    public PdfUploadService(
            PdfConversionProperties properties,
            PdfToImageService pdfToImageService,
            @Qualifier("videoCompressionExecutor") Executor videoCompressionExecutor,
            PdfConversionTaskRepository taskRepository,
            PdfPageImageRepository pageImageRepository,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.pdfToImageService = pdfToImageService;
        this.videoCompressionExecutor = videoCompressionExecutor;
        this.taskRepository = taskRepository;
        this.pageImageRepository = pageImageRepository;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public PdfUploadResponse uploadPdfAndConvertToImages(MultipartFile file, PdfConversionTaskRequest request) {
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
        
        if (request.getBusinessId() == null || request.getBusinessId().trim().isEmpty()) {
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message("Business ID is required")
                .build();
        }
        
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message("User ID is required")
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
        
        boolean isIncrementalConversion = request.getPages() != null && !request.getPages().isEmpty();
        
        if (isIncrementalConversion) {
            Optional<PdfConversionTask> baseTask = taskRepository.findByBusinessIdAndIsBaseTrue(request.getBusinessId());
            if (baseTask.isEmpty()) {
                return PdfUploadResponse.builder()
                    .status("ERROR")
                    .message("Base conversion not found. Please perform full conversion first (without pages parameter)")
                    .build();
            }
        }
        
        String taskId = UUID.randomUUID().toString();
        
        PdfConversionTask task = PdfConversionTask.builder()
            .taskId(taskId)
            .businessId(request.getBusinessId())
            .userId(request.getUserId())
            .filename(originalFilename)
            .totalPages(0)
            .status("SUBMITTED")
            .isBase(!isIncrementalConversion)
            .build();
        
        if (isIncrementalConversion) {
            try {
                task.setConvertedPages(objectMapper.writeValueAsString(request.getPages()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize pages list", e);
                return PdfUploadResponse.builder()
                    .status("ERROR")
                    .message("Failed to process pages parameter")
                    .build();
            }
        }
        
        taskRepository.save(task);
        
        final PdfConversionTaskRequest finalRequest = request;
        CompletableFuture.runAsync(() -> 
            executePdfToImageConversion(file, finalRequest, taskId), videoCompressionExecutor);
        
        return PdfUploadResponse.builder()
            .taskId(taskId)
            .status("PROCESSING")
            .message("PDF upload successful. Converting to images in background.")
            .build();
    }
    
    private void executePdfToImageConversion(MultipartFile file, PdfConversionTaskRequest request, String taskId) {
        long startTime = System.currentTimeMillis();
        
        try {
            updateTaskStatus(taskId, "PROCESSING", null);
            
            Path taskDir = Paths.get(properties.getTempDirectory(), taskId);
            Files.createDirectories(taskDir);
            
            File pdfFile = taskDir.resolve(file.getOriginalFilename()).toFile();
            file.transferTo(pdfFile);
            
            int pageCount = pdfToImageService.getPageCount(pdfFile);
            
            PdfConversionTask task = taskRepository.findByTaskId(taskId).orElseThrow();
            task.setTotalPages(pageCount);
            taskRepository.save(task);
            
            int dpi = request.getImageDpi() != null ? request.getImageDpi() : 
                properties.getImageRendering().getDpi();
            String format = request.getImageFormat() != null ? request.getImageFormat() : 
                properties.getImageRendering().getFormat();
            
            List<Integer> pagesToConvert = request.getPages();
            if (pagesToConvert == null || pagesToConvert.isEmpty()) {
                pagesToConvert = new ArrayList<>();
                for (int i = 1; i <= pageCount; i++) {
                    pagesToConvert.add(i);
                }
            }
            
            pagesToConvert = pagesToConvert.stream()
                .filter(p -> p >= 1 && p <= pageCount)
                .sorted()
                .collect(Collectors.toList());
            
            if (pagesToConvert.isEmpty()) {
                throw new IllegalArgumentException("No valid pages to convert");
            }
            
            Map<Integer, String> imageFiles = pdfToImageService.convertSpecificPagesToImages(
                pdfFile, taskId, pagesToConvert, dpi, format);
            
            savePageImages(taskId, request.getBusinessId(), request.getUserId(), 
                imageFiles, task.getIsBase());
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            updateTaskStatus(taskId, "COMPLETED", null);
            
            log.info("PDF to images conversion completed for taskId: {} in {}ms, pages: {}, images: {}", 
                taskId, processingTime, pagesToConvert.size(), imageFiles.size());
            
        } catch (Exception e) {
            log.error("PDF to images conversion failed for taskId: {}", taskId, e);
            updateTaskStatus(taskId, "FAILED", "Conversion failed: " + e.getMessage());
        }
    }
    
    @Transactional
    protected void savePageImages(String taskId, String businessId, String userId, 
                                   Map<Integer, String> imageFiles, boolean isBase) {
        for (Map.Entry<Integer, String> entry : imageFiles.entrySet()) {
            int pageNumber = entry.getKey();
            String imagePath = entry.getValue();
            
            File imageFile = new File(imagePath);
            
            PdfPageImage pageImage = PdfPageImage.builder()
                .taskId(taskId)
                .businessId(businessId)
                .userId(userId)
                .pageNumber(pageNumber)
                .imageObjectKey(imagePath)
                .isBase(isBase)
                .fileSize(imageFile.length())
                .build();
            
            try {
                BufferedImage image = ImageIO.read(imageFile);
                if (image != null) {
                    pageImage.setWidth(image.getWidth());
                    pageImage.setHeight(image.getHeight());
                }
            } catch (IOException e) {
                log.warn("Failed to read image dimensions for: {}", imageFile.getName(), e);
            }
            
            pageImageRepository.save(pageImage);
        }
    }
    
    @Transactional
    protected void updateTaskStatus(String taskId, String status, String errorMessage) {
        Optional<PdfConversionTask> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            PdfConversionTask task = taskOpt.get();
            task.setStatus(status);
            if (errorMessage != null) {
                task.setErrorMessage(errorMessage);
            }
            taskRepository.save(task);
        }
    }
    
    public PdfConversionProgress getProgress(String taskId) {
        Optional<PdfConversionTask> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            return PdfConversionProgress.builder()
                .jobId(taskId)
                .status("NOT_FOUND")
                .message("Task not found")
                .build();
        }
        
        PdfConversionTask task = taskOpt.get();
        
        int progressPercentage = 0;
        if ("COMPLETED".equals(task.getStatus())) {
            progressPercentage = 100;
        } else if ("PROCESSING".equals(task.getStatus())) {
            progressPercentage = 50;
        }
        
        return PdfConversionProgress.builder()
            .jobId(taskId)
            .status(task.getStatus())
            .currentPhase(task.getStatus())
            .progressPercentage(progressPercentage)
            .totalPages(task.getTotalPages())
            .message(task.getErrorMessage())
            .build();
    }
    
    public PdfConversionTaskResponse getTask(String taskId) {
        Optional<PdfConversionTask> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            return null;
        }
        
        PdfConversionTask task = taskOpt.get();
        List<Integer> convertedPages = null;
        
        if (task.getConvertedPages() != null) {
            try {
                convertedPages = objectMapper.readValue(task.getConvertedPages(), new TypeReference<List<Integer>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize converted pages", e);
            }
        }
        
        return PdfConversionTaskResponse.builder()
            .taskId(task.getTaskId())
            .businessId(task.getBusinessId())
            .userId(task.getUserId())
            .filename(task.getFilename())
            .totalPages(task.getTotalPages())
            .convertedPages(convertedPages)
            .status(task.getStatus())
            .isBase(task.getIsBase())
            .errorMessage(task.getErrorMessage())
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .build();
    }
    
    public List<PdfConversionTaskResponse> getTasks(String businessId, String userId) {
        List<PdfConversionTask> tasks;
        
        if (businessId != null && userId != null) {
            tasks = taskRepository.findByBusinessIdAndUserId(businessId, userId);
        } else if (businessId != null) {
            tasks = taskRepository.findByBusinessIdOrderByCreatedAtDesc(businessId);
        } else {
            tasks = new ArrayList<>();
        }
        
        return tasks.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    public PdfImageResponse getImages(String businessId, String userId, Integer startPage, Integer pageSize) {
        if (businessId == null || businessId.trim().isEmpty()) {
            return PdfImageResponse.builder()
                .status("ERROR")
                .message("Business ID is required")
                .build();
        }
        
        List<PdfPageImage> allImages;
        
        if (userId != null && !userId.trim().isEmpty()) {
            allImages = pageImageRepository.findMergedImages(businessId, userId);
            
            Map<Integer, PdfPageImage> mergedMap = new HashMap<>();
            for (PdfPageImage image : allImages) {
                int pageNum = image.getPageNumber();
                if (!mergedMap.containsKey(pageNum) || !image.getIsBase()) {
                    mergedMap.put(pageNum, image);
                }
            }
            allImages = new ArrayList<>(mergedMap.values());
            allImages.sort(Comparator.comparing(PdfPageImage::getPageNumber));
        } else {
            allImages = pageImageRepository.findByBusinessIdAndIsBaseTrueOrderByPageNumberAsc(businessId);
        }
        
        if (allImages.isEmpty()) {
            return PdfImageResponse.builder()
                .businessId(businessId)
                .userId(userId)
                .status("NOT_FOUND")
                .message("No images found for the specified business")
                .build();
        }
        
        int totalPages = allImages.size();
        int effectiveStartPage = startPage != null && startPage > 0 ? startPage : 1;
        int effectivePageSize = pageSize != null && pageSize > 0 ? pageSize : 10;
        
        if (effectiveStartPage > totalPages) {
            return PdfImageResponse.builder()
                .businessId(businessId)
                .userId(userId)
                .totalPages(totalPages)
                .startPage(effectiveStartPage)
                .pageSize(effectivePageSize)
                .returnedPages(0)
                .images(new ArrayList<>())
                .status("SUCCESS")
                .message("Start page exceeds total pages")
                .build();
        }
        
        int startIndex = effectiveStartPage - 1;
        int endIndex = Math.min(startIndex + effectivePageSize, totalPages);
        
        List<PdfPageImageInfo> pageImages = allImages.subList(startIndex, endIndex).stream()
            .map(img -> PdfPageImageInfo.builder()
                .pageNumber(img.getPageNumber())
                .imageObjectKey(img.getImageObjectKey())
                .isBase(img.getIsBase())
                .userId(img.getUserId())
                .width(img.getWidth())
                .height(img.getHeight())
                .fileSize(img.getFileSize())
                .build())
            .collect(Collectors.toList());
        
        return PdfImageResponse.builder()
            .businessId(businessId)
            .userId(userId)
            .totalPages(totalPages)
            .startPage(effectiveStartPage)
            .pageSize(effectivePageSize)
            .returnedPages(pageImages.size())
            .images(pageImages)
            .status("SUCCESS")
            .message("Successfully retrieved page images")
            .build();
    }
    
    private PdfConversionTaskResponse convertToResponse(PdfConversionTask task) {
        List<Integer> convertedPages = null;
        if (task.getConvertedPages() != null) {
            try {
                convertedPages = objectMapper.readValue(task.getConvertedPages(), new TypeReference<List<Integer>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize converted pages", e);
            }
        }
        
        return PdfConversionTaskResponse.builder()
            .taskId(task.getTaskId())
            .businessId(task.getBusinessId())
            .userId(task.getUserId())
            .filename(task.getFilename())
            .totalPages(task.getTotalPages())
            .convertedPages(convertedPages)
            .status(task.getStatus())
            .isBase(task.getIsBase())
            .errorMessage(task.getErrorMessage())
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .build();
    }
}
