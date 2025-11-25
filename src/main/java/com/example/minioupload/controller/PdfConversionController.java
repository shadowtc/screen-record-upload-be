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

/**
 * PDF转换控制器
 * 提供PDF文件上传、转换任务管理、进度查询和图像获取等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfConversionController {
    
    private final PdfUploadService pdfUploadService;

    /**
     * 上传PDF文件并转换为图像
     *
     * @param file         要上传的PDF文件
     * @param businessId   业务ID，用于标识不同的业务场景
     * @param userId       用户ID，标识操作用户
     * @param pages        可选参数，指定需要转换的页面列表
     * @param imageDpi     可选参数，设置输出图像的DPI分辨率
     * @param imageFormat  可选参数，指定输出图像格式（如JPEG、PNG等）
     * @return             返回PDF上传和转换结果响应对象
     */
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

    /**
     * 根据任务ID获取单个转换任务详情
     *
     * @param taskId 任务ID
     * @return 返回对应的任务信息
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<PdfConversionTaskResponse> getTask(@PathVariable String taskId) {
        log.debug("Getting task details for taskId: {}", taskId);
        
        PdfConversionTaskResponse task = pdfUploadService.getTask(taskId);
        
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(task);
    }

    /**
     * 获取符合指定条件的转换任务列表
     *
     * @param businessId 可选参数，根据业务ID筛选任务
     * @param userId     可选参数，根据用户ID筛选任务
     * @return 返回符合条件的任务列表
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<PdfConversionTaskResponse>> getTasks(
            @RequestParam(value = "businessId", required = false) String businessId,
            @RequestParam(value = "userId", required = false) String userId) {
        
        log.debug("Getting tasks for businessId: {}, userId: {}", businessId, userId);
        
        List<PdfConversionTaskResponse> tasks = pdfUploadService.getTasks(businessId, userId);
        
        return ResponseEntity.ok(tasks);
    }

    /**
     * 获取指定任务的转换进度
     *
     * @param taskId 任务ID
     * @return 返回任务的当前进度状态
     */
    @GetMapping("/progress/{taskId}")
    public ResponseEntity<PdfConversionProgress> getProgress(@PathVariable String taskId) {
        log.debug("Getting progress for taskId: {}", taskId);
        
        PdfConversionProgress progress = pdfUploadService.getProgress(taskId);
        
        if ("NOT_FOUND".equals(progress.getStatus())) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(progress);
    }

    /**
     * 获取已转换的PDF页面图像
     *
     * @param businessId 业务ID
     * @param userId     可选参数，用户ID
     * @param startPage  可选参数，起始页码，默认为1
     * @param pageSize   可选参数，每页图像数量，默认为10
     * @return           返回页面图像信息响应对象
     */
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
