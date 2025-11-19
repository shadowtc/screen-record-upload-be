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
    private final com.example.minioupload.service.ServerSideMultipartUploadService serverSideMultipartUploadService;

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
     * 新增：服务端异步分片上传入口
     * 前端仅需上传文件，后端异步分片并上传到MinIO
     */
    @PostMapping("/server/async")
    public ResponseEntity<com.example.minioupload.dto.AsyncUploadStartResponse> serverSideAsyncUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "chunkSize", required = false) Long chunkSize) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        // 将文件保存到临时目录，避免请求结束后临时文件被清理导致异步任务无法读取
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("server-upload-", "-" + file.getOriginalFilename());
        file.transferTo(tempFile.toFile());
        
        com.example.minioupload.dto.AsyncUploadStartResponse response = serverSideMultipartUploadService
                .submitAsyncUpload(tempFile, file.getOriginalFilename(), file.getContentType(), chunkSize);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * 新增：查询服务端异步分片上传状态
     */
    @GetMapping("/server/status/{jobId}")
    public ResponseEntity<com.example.minioupload.dto.AsyncUploadStatusResponse> getServerUploadStatus(@PathVariable String jobId) {
        com.example.minioupload.dto.AsyncUploadStatusResponse status = serverSideMultipartUploadService.getStatus(jobId);
        return ResponseEntity.ok(status);
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
}
