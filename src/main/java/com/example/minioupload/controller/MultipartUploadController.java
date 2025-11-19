package com.example.minioupload.controller;

import com.example.minioupload.dto.*;
import com.example.minioupload.service.MultipartUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * S3/MinIO分片上传操作的REST控制器。
 * 
 * 此控制器为分片上传的完整生命周期提供端点：
 * 1. 初始化新的上传会话
 * 2. 生成用于上传分片的预签名URL
 * 3. 检查上传状态并获取已上传的分片
 * 4. 完成上传并持久化元数据
 * 5. 中止/取消上传
 * 
 * 所有端点都遵循RESTful约定并返回适当的HTTP状态码。
 * 使用Jakarta Bean Validation注解强制执行请求验证。
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Slf4j
public class MultipartUploadController {

    /**
     * 处理分片上传业务逻辑的服务层
     */
    private final MultipartUploadService multipartUploadService;

    /**
     * 初始化新的分片上传会话。
     * 
     * 此端点验证请求并在S3/MinIO中创建新的上传会话。
     * 客户端将收到uploadId和objectKey以用于后续操作。
     * 
     * @param file 要上传的文件（MultipartFile）
     * @param chunkSize 可选的自定义分片大小（字节）
     * @return 包含uploadId、objectKey、分片大小和分片编号范围的InitUploadResponse
     */
    @PostMapping("/init")
    public ResponseEntity<InitUploadResponse> initializeUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "chunkSize", required = false) Long chunkSize) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        log.info("POST /api/uploads/init - Initializing upload for file: {}", file.getOriginalFilename());
        
        InitUploadRequest request = new InitUploadRequest(file, chunkSize);
        InitUploadResponse response = multipartUploadService.initializeUpload(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 为上传特定分片生成预签名URL。
     * 
     * 此端点返回具有时间限制的URL，客户端可以使用这些URL直接上传分片到S3/MinIO，
     * 无需通过此服务器。分片可以并行上传以获得更好的性能。
     * 
     * @param uploadId 来自初始化的上传会话ID
     * @param objectKey 来自初始化的S3对象键
     * @param startPartNumber 要生成URL的第一个分片编号（从1开始，包含）
     * @param endPartNumber 要生成URL的最后一个分片编号（从1开始，包含）
     * @return 包含过期时间戳的预签名URL列表
     */
    @GetMapping("/{uploadId}/parts")
    public ResponseEntity<List<PresignedUrlResponse>> getPresignedUrls(
            @PathVariable String uploadId,
            @RequestParam String objectKey,
            @RequestParam int startPartNumber,
            @RequestParam int endPartNumber) {
        log.info("GET /api/uploads/{}/parts - Generating URLs for parts {} to {}", uploadId, startPartNumber, endPartNumber);
        List<PresignedUrlResponse> presignedUrls = multipartUploadService.generatePresignedUrls(
                uploadId, objectKey, startPartNumber, endPartNumber);
        return ResponseEntity.ok(presignedUrls);
    }

    /**
     * 获取上传的当前状态。
     * 
     * 此端点返回有关哪些分片已成功上传到S3/MinIO的信息。
     * 对于恢复中断的上传或向用户显示进度很有用。
     * 
     * @param uploadId 来自初始化的上传会话ID
     * @param objectKey 来自初始化的S3对象键
     * @return 包含分片编号、ETag和大小的已上传分片信息列表
     */
    @GetMapping("/{uploadId}/status")
    public ResponseEntity<List<UploadPartInfo>> getUploadStatus(
            @PathVariable String uploadId,
            @RequestParam String objectKey) {
        log.info("GET /api/uploads/{}/status - Retrieving upload status", uploadId);
        List<UploadPartInfo> uploadedParts = multipartUploadService.getUploadStatus(uploadId, objectKey);
        return ResponseEntity.ok(uploadedParts);
    }

    /**
     * 完成分片上传。
     * 
     * 此端点通过将所有分片合并为S3/MinIO中的单个对象并将元数据持久化到MySQL来完成上传。
     * 完成后，文件可供下载。
     * 
     * @param request 经过验证的请求，包含uploadId、objectKey和分片ETag列表
     * @return 包含文件元数据和预签名下载URL的CompleteUploadResponse
     */
    @PostMapping("/complete")
    public ResponseEntity<CompleteUploadResponse> completeUpload(@Valid @RequestBody CompleteUploadRequest request) {
        log.info("POST /api/uploads/complete - Completing upload {}", request.getUploadId());
        CompleteUploadResponse response = multipartUploadService.completeUpload(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 中止分片上传。
     * 
     * 此端点取消上传并从S3/MinIO清理所有已上传的分片。
     * 当客户端决定取消上传或无法成功完成上传时应调用此方法。
     * 
     * @param request 经过验证的请求，包含uploadId和objectKey
     * @return 成功中止时返回204 No Content
     */
    @PostMapping("/abort")
    public ResponseEntity<Void> abortUpload(@Valid @RequestBody AbortUploadRequest request) {
        log.info("POST /api/uploads/abort - Aborting upload {}", request.getUploadId());
        multipartUploadService.abortUpload(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    /**
     * 提交异步分片上传任务。
     * 
     * 此端点接收整个文件，然后在后台异步执行分片上传到MinIO。
     * 客户端将收到一个任务ID（jobId），可以使用该ID查询上传进度。
     * 
     * 与传统的分片上传流程不同：
     * - 无需前端计算分片并逐个上传
     * - 后端自动处理文件分片和上传
     * - 支持实时进度查询
     * - 上传完成后自动保存元数据到数据库
     * 
     * @param file 要上传的文件（MultipartFile）
     * @param chunkSize 可选的自定义分片大小（字节），默认使用配置的默认值
     * @return 包含任务ID的响应，客户端可使用该ID查询进度
     */
    @PostMapping("/async")
    public ResponseEntity<AsyncUploadProgress> submitAsyncUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "chunkSize", required = false) Long chunkSize) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        log.info("POST /api/uploads/async - Submitting async upload for file: {}", file.getOriginalFilename());
        
        String jobId = multipartUploadService.submitAsyncUpload(file, chunkSize);
        AsyncUploadProgress progress = multipartUploadService.getAsyncUploadProgress(jobId);
        
        return ResponseEntity.ok(progress);
    }
    
    /**
     * 查询异步上传任务的进度。
     * 
     * 客户端可以定期轮询此端点来获取上传任务的实时状态和进度。
     * 进度信息包括：
     * - 任务状态（SUBMITTED、UPLOADING、PAUSED、COMPLETED、FAILED）
     * - 进度百分比（0-100）
     * - 已上传的分片数和总分片数
     * - 详细的状态消息
     * - 上传完成后的文件信息和下载URL
     * 
     * @param jobId 上传任务的唯一标识符（从提交接口获得）
     * @return 包含任务进度和状态的AsyncUploadProgress对象，如果任务不存在则返回404
     */
    @GetMapping("/async/{jobId}/progress")
    public ResponseEntity<AsyncUploadProgress> getAsyncUploadProgress(@PathVariable String jobId) {
        log.info("GET /api/uploads/async/{}/progress - Retrieving progress", jobId);
        
        AsyncUploadProgress progress = multipartUploadService.getAsyncUploadProgress(jobId);
        if (progress == null) {
            log.warn("Async upload job not found: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        return ResponseEntity.ok(progress);
    }
    
    /**
     * 恢复暂停的异步上传任务（断点续传）。
     * 
     * 此端点用于恢复之前中断或暂停的上传任务。它会：
     * 1. 验证任务状态是否可以恢复（PAUSED或FAILED状态）
     * 2. 检查临时文件是否存在
     * 3. 查询MinIO中已上传的分片
     * 4. 只上传剩余未完成的分片
     * 5. 完成上传并保存元数据
     * 
     * 使用场景：
     * - 网络中断后恢复上传
     * - 应用重启后继续上传
     * - 用户主动暂停后恢复上传
     * - 上传失败后重试
     * 
     * @param jobId 要恢复的上传任务ID
     * @return 恢复后的AsyncUploadProgress对象，包含当前状态和进度
     */
    @PostMapping("/async/{jobId}/resume")
    public ResponseEntity<AsyncUploadProgress> resumeAsyncUpload(@PathVariable String jobId) {
        log.info("POST /api/uploads/async/{}/resume - Resuming upload", jobId);
        
        try {
            AsyncUploadProgress progress = multipartUploadService.resumeAsyncUpload(jobId);
            return ResponseEntity.ok(progress);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to resume upload {}: {}", jobId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
