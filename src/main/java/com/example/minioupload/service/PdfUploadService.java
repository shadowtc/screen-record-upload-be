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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * PDF上传与转换服务
 * 
 * 核心功能：
 * 1. PDF文件上传和验证
 * 2. PDF页面转图片（全量转换和增量转换）
 * 3. 任务进度查询
 * 4. 图片分页查询
 * 5. 增量转换图片合并
 * 
 * 业务流程：
 * 1. 全量转换：上传PDF -> 转换所有页面 -> 保存图片 -> 标记为基础转换（isBase=true）
 * 2. 增量转换：上传PDF -> 只转换指定页面 -> 保存图片 -> 标记为增量转换（isBase=false）
 * 3. 图片查询：按业务ID查询，增量转换的图片覆盖基础转换的同页码图片
 * 
 * 技术特点：
 * - 异步处理：使用CompletableFuture进行后台转换
 * - 事务管理：确保数据一致性
 * - 进度跟踪：实时查询转换进度
 * - 分页支持：支持大量页面的分页查询
 */
@Slf4j
@Service
public class PdfUploadService {
    
    private final PdfConversionProperties properties;
    private final PdfToImageService pdfToImageService;
    private final MinioStorageService minioStorageService;
    private final Executor videoCompressionExecutor;
    private final PdfConversionTaskRepository taskRepository;
    private final PdfPageImageRepository pageImageRepository;
    private final ObjectMapper objectMapper;
    
    public PdfUploadService(
            PdfConversionProperties properties,
            PdfToImageService pdfToImageService,
            MinioStorageService minioStorageService,
            @Qualifier("videoCompressionExecutor") Executor videoCompressionExecutor,
            PdfConversionTaskRepository taskRepository,
            PdfPageImageRepository pageImageRepository,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.pdfToImageService = pdfToImageService;
        this.minioStorageService = minioStorageService;
        this.videoCompressionExecutor = videoCompressionExecutor;
        this.taskRepository = taskRepository;
        this.pageImageRepository = pageImageRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 上传PDF并转换为图片
     * 
     * 这是主入口方法，负责：
     * 1. 参数验证（文件、业务ID、用户ID）
     * 2. 文件格式验证（仅接受PDF）
     * 3. 文件大小验证
     * 4. 判断全量/增量转换模式
     * 5. 创建任务记录
     * 6. 启动异步转换
     * 
     * @param file PDF文件（MultipartFile）
     * @param request 转换请求参数（业务ID、用户ID、页码等）
     * @return 上传响应（包含任务ID和状态）
     */
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
        
        if (request.getTenantId() == null || request.getTenantId().trim().isEmpty()) {
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message("Tenant ID is required")
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
            PdfConversionTask baseTask = taskRepository.findByBusinessIdAndTenantIdAndIsBaseTrue(
                request.getBusinessId(), request.getTenantId());
            if (baseTask == null) {
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
            .tenantId(request.getTenantId())
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
        
        taskRepository.insert(task);
        
        Path taskDir = null;
        File tempPdfFile = null;
        try {
            taskDir = Paths.get(properties.getTempDirectory(), taskId);
            Files.createDirectories(taskDir);
            
            tempPdfFile = taskDir.resolve(originalFilename).toFile();
            file.transferTo(tempPdfFile);
            log.debug("Saved MultipartFile to temp file: {}", tempPdfFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save uploaded file to temp directory", e);
            updateTaskStatus(taskId, "FAILED", "Failed to save uploaded file: " + e.getMessage());
            return PdfUploadResponse.builder()
                .taskId(taskId)
                .status("ERROR")
                .message("Failed to save uploaded file: " + e.getMessage())
                .build();
        }
        
        final PdfConversionTaskRequest finalRequest = request;
        final File finalTempPdfFile = tempPdfFile;
        final Path finalTaskDir = taskDir;
        CompletableFuture.runAsync(() -> 
            executePdfToImageConversion(finalTempPdfFile, finalTaskDir, finalRequest, taskId), videoCompressionExecutor);
        
        return PdfUploadResponse.builder()
            .taskId(taskId)
            .status("PROCESSING")
            .message("PDF upload successful. Converting to images in background.")
            .build();
    }
    
    /**
     * 通过URL上传PDF并转换为图片
     * 
     * 从远程URL下载PDF文件，然后执行与uploadPdfAndConvertToImages相同的处理流程：
     * 1. 参数验证（URL、业务ID、用户ID）
     * 2. 从URL下载文件
     * 3. 文件格式验证（仅接受PDF）
     * 4. 文件大小验证
     * 5. 判断全量/增量转换模式
     * 6. 创建任务记录
     * 7. 启动异步转换
     * 
     * @param request URL上传请求参数（包含fileUrl、业务ID、用户ID、页码等）
     * @return 上传响应（包含任务ID和状态）
     */
    @Transactional
    public PdfUploadResponse uploadPdfFromUrlAndConvertToImages(PdfUploadByUrlRequest request) {
        if (!properties.isEnabled()) {
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message("PDF conversion service is disabled")
                .build();
        }
        
        if (request.getFileUrl() == null || request.getFileUrl().trim().isEmpty()) {
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message("File URL is required")
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
        
        if (request.getTenantId() == null || request.getTenantId().trim().isEmpty()) {
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message("Tenant ID is required")
                .build();
        }
        
        boolean isIncrementalConversion = request.getPages() != null && !request.getPages().isEmpty();
        
        if (isIncrementalConversion) {
            PdfConversionTask baseTask = taskRepository.findByBusinessIdAndTenantIdAndIsBaseTrue(
                request.getBusinessId(), request.getTenantId());
            if (baseTask == null) {
                return PdfUploadResponse.builder()
                    .status("ERROR")
                    .message("Base conversion not found. Please perform full conversion first (without pages parameter)")
                    .build();
            }
        }
        
        String taskId = UUID.randomUUID().toString();
        Path taskDir = null;
        File tempPdfFile = null;
        String filename = null;
        
        try {
            URL url = new URL(request.getFileUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return PdfUploadResponse.builder()
                    .status("ERROR")
                    .message("Failed to download file from URL. HTTP status: " + responseCode)
                    .build();
            }
            
            String contentType = connection.getContentType();
            if (contentType != null && !contentType.toLowerCase().contains("pdf") && 
                !contentType.toLowerCase().contains("application/octet-stream")) {
                log.warn("Content-Type is not PDF: {}", contentType);
            }
            
            long contentLength = connection.getContentLengthLong();
            if (contentLength > properties.getMaxFileSize()) {
                return PdfUploadResponse.builder()
                    .status("ERROR")
                    .message(String.format("File size exceeds limit. Max: %d MB, Actual: %.2f MB",
                        properties.getMaxFileSize() / 1024 / 1024,
                        contentLength / 1024.0 / 1024.0))
                    .build();
            }
            
            filename = extractFilenameFromUrl(request.getFileUrl(), connection);
            if (!filename.toLowerCase().endsWith(".pdf")) {
                filename = filename + ".pdf";
            }
            
            taskDir = Paths.get(properties.getTempDirectory(), taskId);
            Files.createDirectories(taskDir);
            
            tempPdfFile = taskDir.resolve(filename).toFile();
            
            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, tempPdfFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Downloaded PDF from URL to temp file: {}, size: {} bytes", 
                    tempPdfFile.getAbsolutePath(), tempPdfFile.length());
            }
            
            if (tempPdfFile.length() > properties.getMaxFileSize()) {
                return PdfUploadResponse.builder()
                    .status("ERROR")
                    .message(String.format("File size exceeds limit. Max: %d MB, Actual: %.2f MB",
                        properties.getMaxFileSize() / 1024 / 1024,
                        tempPdfFile.length() / 1024.0 / 1024.0))
                    .build();
            }
            
            if (!isPdfFile(tempPdfFile)) {
                return PdfUploadResponse.builder()
                    .status("ERROR")
                    .message("Downloaded file is not a valid PDF")
                    .build();
            }
            
        } catch (IOException e) {
            log.error("Failed to download file from URL: {}", request.getFileUrl(), e);
            cleanupTempFiles(tempPdfFile, taskDir);
            return PdfUploadResponse.builder()
                .status("ERROR")
                .message("Failed to download file from URL: " + e.getMessage())
                .build();
        }
        
        PdfConversionTask task = PdfConversionTask.builder()
            .taskId(taskId)
            .businessId(request.getBusinessId())
            .userId(request.getUserId())
            .tenantId(request.getTenantId())
            .filename(filename)
            .totalPages(0)
            .status("SUBMITTED")
            .isBase(!isIncrementalConversion)
            .build();
        
        if (isIncrementalConversion) {
            try {
                task.setConvertedPages(objectMapper.writeValueAsString(request.getPages()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize pages list", e);
                cleanupTempFiles(tempPdfFile, taskDir);
                return PdfUploadResponse.builder()
                    .status("ERROR")
                    .message("Failed to process pages parameter")
                    .build();
            }
        }
        
        taskRepository.insert(task);
        
        PdfConversionTaskRequest conversionRequest = PdfConversionTaskRequest.builder()
            .businessId(request.getBusinessId())
            .userId(request.getUserId())
            .tenantId(request.getTenantId())
            .pages(request.getPages())
            .imageDpi(request.getImageDpi())
            .imageFormat(request.getImageFormat())
            .build();
        
        final File finalTempPdfFile = tempPdfFile;
        final Path finalTaskDir = taskDir;
        CompletableFuture.runAsync(() -> 
            executePdfToImageConversion(finalTempPdfFile, finalTaskDir, conversionRequest, taskId), 
            videoCompressionExecutor);
        
        return PdfUploadResponse.builder()
            .taskId(taskId)
            .status("PROCESSING")
            .message("PDF download successful. Converting to images in background.")
            .build();
    }
    
    /**
     * 从URL或响应头中提取文件名
     * 
     * @param urlString URL字符串
     * @param connection HTTP连接
     * @return 文件名
     */
    private String extractFilenameFromUrl(String urlString, HttpURLConnection connection) {
        String contentDisposition = connection.getHeaderField("Content-Disposition");
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String filename = contentDisposition.substring(contentDisposition.indexOf("filename=") + 9);
            filename = filename.replaceAll("\"", "").trim();
            if (!filename.isEmpty()) {
                return filename;
            }
        }
        
        String path = urlString;
        int queryIndex = path.indexOf('?');
        if (queryIndex > 0) {
            path = path.substring(0, queryIndex);
        }
        
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < path.length() - 1) {
            return path.substring(lastSlashIndex + 1);
        }
        
        return "downloaded_" + System.currentTimeMillis() + ".pdf";
    }
    
    /**
     * 验证文件是否为PDF格式
     * 
     * @param file 文件
     * @return 是否为PDF文件
     */
    private boolean isPdfFile(File file) {
        try {
            byte[] header = new byte[4];
            try (InputStream is = Files.newInputStream(file.toPath())) {
                int bytesRead = is.read(header);
                if (bytesRead < 4) {
                    return false;
                }
            }
            return header[0] == 0x25 && header[1] == 0x50 && 
                   header[2] == 0x44 && header[3] == 0x46;
        } catch (IOException e) {
            log.error("Failed to read file header", e);
            return false;
        }
    }
    
    /**
     * 清理临时文件和目录
     * 
     * @param tempFile 临时文件
     * @param tempDir 临时目录
     */
    private void cleanupTempFiles(File tempFile, Path tempDir) {
        if (tempFile != null && tempFile.exists()) {
            try {
                Files.deleteIfExists(tempFile.toPath());
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath(), e);
            }
        }
        
        if (tempDir != null && Files.exists(tempDir)) {
            try {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", path, e);
                        }
                    });
            } catch (IOException e) {
                log.warn("Failed to clean up temp directory: {}", tempDir, e);
            }
        }
    }

            /**
            * 执行PDF转图片转换（异步执行）
            *
            * 转换流程：
            * 1. 上传PDF到MinIO
            * 2. 获取PDF总页数
            * 3. 确定需要转换的页面（全量或增量）
            * 4. 调用PdfToImageService进行页面渲染并上传到MinIO
            * 5. 保存图片元数据到数据库
            * 6. 更新任务状态
            * 7. 清理临时文件
            *
            * @param pdfFile PDF临时文件（已保存到磁盘）
            * @param taskDir 任务临时目录
            * @param request 转换请求
            * @param taskId 任务ID
            */
            private void executePdfToImageConversion(File pdfFile, Path taskDir, PdfConversionTaskRequest request, String taskId) {
        long startTime = System.currentTimeMillis();
        
        try {
            updateTaskStatus(taskId, "PROCESSING", null);
            
            String pdfObjectKey = String.format("pdf/%s/%s/%s/%s", 
                request.getUserId(), request.getBusinessId(), taskId, pdfFile.getName());
            minioStorageService.uploadFile(pdfFile, pdfObjectKey);
            log.info("PDF uploaded to MinIO: {}", pdfObjectKey);
            
            PdfConversionTask task = taskRepository.findByTaskId(taskId);
            if (task == null) {
                throw new RuntimeException("Task not found: " + taskId);
            }
            task.setPdfObjectKey(pdfObjectKey);
            taskRepository.updateById(task);
            
            int pageCount = pdfToImageService.getPageCount(pdfFile);
            
            task.setTotalPages(pageCount);
            taskRepository.updateById(task);
            
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
            
            Map<Integer, String> minioObjectKeys = pdfToImageService.convertPagesToImagesAndUpload(
                pdfFile, request.getUserId(), request.getBusinessId(), taskId, pagesToConvert, dpi, format);
            
            savePageImages(taskId, request.getBusinessId(), request.getUserId(), request.getTenantId(),
                minioObjectKeys, task.getIsBase());
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            updateTaskStatus(taskId, "COMPLETED", null);
            
            log.info("PDF to images conversion completed for taskId: {} in {}ms, pages: {}, images: {}", 
                taskId, processingTime, pagesToConvert.size(), minioObjectKeys.size());
            
        } catch (Exception e) {
            log.error("PDF to images conversion failed for taskId: {}", taskId, e);
            updateTaskStatus(taskId, "FAILED", "Conversion failed: " + e.getMessage());
        } finally {
            if (pdfFile != null && pdfFile.exists()) {
                try {
                    Files.deleteIfExists(pdfFile.toPath());
                    log.debug("Deleted temp PDF file: {}", pdfFile.getAbsolutePath());
                } catch (IOException e) {
                    log.warn("Failed to delete temp PDF file: {}", pdfFile.getAbsolutePath(), e);
                }
            }
            
            if (taskDir != null && Files.exists(taskDir)) {
                try {
                    Files.walk(taskDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete: {}", path, e);
                            }
                        });
                    log.debug("Cleaned up temp directory: {}", taskDir);
                } catch (IOException e) {
                    log.warn("Failed to clean up temp directory: {}", taskDir, e);
                }
            }
        }
    }
    
    /**
     * 保存页面图片元数据到数据库
     * 
     * 为每个转换后的图片创建数据库记录，包含：
     * - 任务ID、业务ID、用户ID、租户ID
     * - 页码、MinIO对象键
     * - 是否为基础转换标识
     * 
     * @param taskId 任务ID
     * @param businessId 业务ID
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param minioObjectKeys 页码到MinIO对象键的映射
     * @param isBase 是否为基础转换
     */
    @Transactional
    protected void savePageImages(String taskId, String businessId, String userId, String tenantId,
                                   Map<Integer, String> minioObjectKeys, boolean isBase) {
        for (Map.Entry<Integer, String> entry : minioObjectKeys.entrySet()) {
            int pageNumber = entry.getKey();
            String objectKey = entry.getValue();
            
            PdfPageImage pageImage = PdfPageImage.builder()
                .taskId(taskId)
                .businessId(businessId)
                .userId(userId)
                .tenantId(tenantId)
                .pageNumber(pageNumber)
                .imageObjectKey(objectKey)
                .isBase(isBase)
                .build();
            
            pageImageRepository.insert(pageImage);
            
            log.debug("Saved page image metadata: taskId={}, page={}, objectKey={}", 
                taskId, pageNumber, objectKey);
        }
    }
    
    /**
     * 更新任务状态
     * 
     * @param taskId 任务ID
     * @param status 新状态
     * @param errorMessage 错误信息（可选）
     */
    @Transactional
    protected void updateTaskStatus(String taskId, String status, String errorMessage) {
        PdfConversionTask task = taskRepository.findByTaskId(taskId);
        if (task != null) {
            task.setStatus(status);
            if (errorMessage != null) {
                task.setErrorMessage(errorMessage);
            }
            taskRepository.updateById(task);
        }
    }
    
    /**
     * 查询转换进度
     * 
     * @param taskId 任务ID
     * @return 进度信息
     */
    public PdfConversionProgress getProgress(String taskId) {
        PdfConversionTask task = taskRepository.findByTaskId(taskId);
        if (task == null) {
            return PdfConversionProgress.builder()
                .jobId(taskId)
                .status("NOT_FOUND")
                .message("Task not found")
                .build();
        }
        
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

    /**
     * 获取任务详情
     *
     * @param taskId 任务ID
     * @return 任务响应信息，未找到时返回null
     */
    public PdfConversionTaskResponse getTask(String taskId) {
        PdfConversionTask task = taskRepository.findByTaskId(taskId);
        if (task == null) {
            return null;
        }
        List<Integer> convertedPages = null;
        
        if (task.getConvertedPages() != null) {
            try {
                convertedPages = objectMapper.readValue(task.getConvertedPages(), new TypeReference<List<Integer>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize converted pages", e);
            }
        }
        
        String pdfUrl = null;
        if (task.getPdfObjectKey() != null && !task.getPdfObjectKey().isEmpty()) {
            try {
                pdfUrl = minioStorageService.getPresignedUrl(task.getPdfObjectKey(), 60);
            } catch (Exception e) {
                log.warn("Failed to generate presigned URL for PDF: {}", task.getPdfObjectKey(), e);
            }
        }
        
        return PdfConversionTaskResponse.builder()
            .taskId(task.getTaskId())
            .businessId(task.getBusinessId())
            .userId(task.getUserId())
            .filename(task.getFilename())
            .totalPages(task.getTotalPages())
            .convertedPages(convertedPages)
            .pdfObjectKey(task.getPdfObjectKey())
            .pdfUrl(pdfUrl)
            .status(task.getStatus())
            .isBase(task.getIsBase())
            .errorMessage(task.getErrorMessage())
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .build();
    }
    
    /**
     * 查询任务列表
     * 
     * @param businessId 业务ID（可选）
     * @param userId 用户ID（可选）
     * @return 任务列表
     */
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
    
    /**
     * 分页查询PDF页面图片
     * 
     * 查询逻辑：
     * 1. 如果提供userId，则合并全量和增量转换的图片（增量覆盖全量）
     * 2. 如果只提供businessId和tenantId，只返回基础转换的图片
     * 3. 支持分页查询，默认每页10条
     * 
     * @param businessId 业务ID（必填）
     * @param tenantId 租户ID（必填）
     * @param userId 用户ID（可选）
     * @param startPage 起始页码（从1开始）
     * @param pageSize 每页大小
     * @return 图片响应，包含分页信息和图片列表
     */
    public PdfImageResponse getImages(String businessId, String tenantId, String userId, Integer startPage, Integer pageSize) {
        if (businessId == null || businessId.trim().isEmpty()) {
            return PdfImageResponse.builder()
                .status("ERROR")
                .message("Business ID is required")
                .build();
        }
        
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return PdfImageResponse.builder()
                .status("ERROR")
                .message("Tenant ID is required")
                .build();
        }
        
        List<PdfPageImage> allImages;
        
        if (userId != null && !userId.trim().isEmpty()) {
            allImages = pageImageRepository.findMergedImages(businessId, tenantId, userId);
            
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
            allImages = pageImageRepository.findByBusinessIdAndTenantIdAndIsBaseTrueOrderByPageNumberAsc(businessId, tenantId);
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
            .map(img -> {
                String presignedUrl = minioStorageService.getPresignedUrl(img.getImageObjectKey(), 60);
                return PdfPageImageInfo.builder()
                    .pageNumber(img.getPageNumber())
                    .imageObjectKey(img.getImageObjectKey())
                    .imageUrl(presignedUrl)
                    .isBase(img.getIsBase())
                    .userId(img.getUserId())
                    .width(img.getWidth())
                    .height(img.getHeight())
                    .fileSize(img.getFileSize())
                    .build();
            })
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

            /**
            * 将任务实体转换为响应DTO
            *
            * @param task 任务实体
            * @return 任务响应DTO
            */
            private PdfConversionTaskResponse convertToResponse(PdfConversionTask task) {
        List<Integer> convertedPages = null;
        if (task.getConvertedPages() != null) {
            try {
                convertedPages = objectMapper.readValue(task.getConvertedPages(), new TypeReference<List<Integer>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize converted pages", e);
            }
        }
        
        String pdfUrl = null;
        if (task.getPdfObjectKey() != null && !task.getPdfObjectKey().isEmpty()) {
            try {
                pdfUrl = minioStorageService.getPresignedUrl(task.getPdfObjectKey(), 60);
            } catch (Exception e) {
                log.warn("Failed to generate presigned URL for PDF: {}", task.getPdfObjectKey(), e);
            }
        }
        
        return PdfConversionTaskResponse.builder()
            .taskId(task.getTaskId())
            .businessId(task.getBusinessId())
            .userId(task.getUserId())
            .filename(task.getFilename())
            .totalPages(task.getTotalPages())
            .convertedPages(convertedPages)
            .pdfObjectKey(task.getPdfObjectKey())
            .pdfUrl(pdfUrl)
            .status(task.getStatus())
            .isBase(task.getIsBase())
            .errorMessage(task.getErrorMessage())
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .build();
    }
}
