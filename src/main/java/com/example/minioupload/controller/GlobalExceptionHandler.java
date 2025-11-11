package com.example.minioupload.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.HashMap;
import java.util.Map;

/**
 * 整个应用程序的全局异常处理器。
 * 
 * 此类集中处理所有REST控制器的异常，为客户端提供一致的错误响应。它处理：
 * - Bean验证失败
 * - 业务逻辑验证失败（IllegalArgumentException）
 * - S3/MinIO操作失败
 * - 意外的运行时异常
 * 
 * 所有异常都会被记录并转换为用户友好的错误响应，带有适当的HTTP状态码。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理来自Jakarta Bean Validation的验证异常。
     * 
     * 当请求体验证失败时触发（例如@NotNull、@NotBlank违规）。
     * 为所有验证失败返回字段名到错误消息的映射。
     * 
     * @param ex 包含所有验证错误的验证异常
     * @return 带有字段级错误消息映射的400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * 处理业务逻辑验证异常。
     * 
     * 当服务层为无效的业务规则抛出IllegalArgumentException时触发
     * （例如文件大小过大、内容类型无效）。
     * 
     * @param ex 包含业务规则违规消息的异常
     * @return 带有错误消息的400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Business validation failed: {}", ex.getMessage());
        
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * 处理S3/MinIO操作失败。
     * 
     * 当AWS SDK操作失败时触发（例如网络问题、无效凭证、存储桶未找到、权限不足）。
     * 从AWS错误详情中提取详细的错误信息。
     * 
     * @param ex 来自AWS SDK的S3异常
     * @return 带有S3错误详情的500 Internal Server Error
     */
    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<Map<String, String>> handleS3Exception(S3Exception ex) {
        log.error("S3 operation failed: {}", ex.awsErrorDetails().errorMessage(), ex);
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "S3 operation failed: " + ex.awsErrorDetails().errorMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * 处理客户端连接中止异常。
     * 
     * 当客户端在服务器发送响应之前断开连接时触发（例如用户取消请求、浏览器超时、网络中断）。
     * 这通常发生在长时间运行的操作（如视频压缩、大文件上传）中。
     * 
     * 这不是服务器错误，只需记录信息级别日志。不返回响应，因为客户端已断开连接。
     * 
     * @param ex 客户端中止异常
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException ex) {
        log.info("Client aborted connection: {}", ex.getMessage());
    }

    /**
     * 处理所有其他意外异常。
     * 
     * 这是未被其他方法显式处理的任何异常的后备处理器。
     * 记录完整的异常以供调试，并返回通用错误消息以避免暴露内部实现细节。
     * 
     * @param ex 意外异常
     * @return 带有通用错误消息的500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "An unexpected error occurred: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
