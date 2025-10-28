package com.example.minioupload.controller;

import lombok.extern.slf4j.Slf4j;
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
 * Global exception handler for the entire application.
 * 
 * This class centralizes exception handling across all REST controllers,
 * providing consistent error responses to clients. It handles:
 * - Bean validation failures
 * - Business logic validation failures (IllegalArgumentException)
 * - S3/MinIO operation failures
 * - Unexpected runtime exceptions
 * 
 * All exceptions are logged and transformed into user-friendly error responses
 * with appropriate HTTP status codes.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles validation exceptions from Jakarta Bean Validation.
     * 
     * Triggered when request body validation fails (e.g., @NotNull, @NotBlank violations).
     * Returns a map of field names to error messages for all validation failures.
     * 
     * @param ex the validation exception containing all validation errors
     * @return 400 Bad Request with map of field-level error messages
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
     * Handles business logic validation exceptions.
     * 
     * Triggered when service layer throws IllegalArgumentException for invalid
     * business rules (e.g., file size too large, invalid content type).
     * 
     * @param ex the exception containing the business rule violation message
     * @return 400 Bad Request with error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Business validation failed: {}", ex.getMessage());
        
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles S3/MinIO operation failures.
     * 
     * Triggered when AWS SDK operations fail (e.g., network issues, invalid credentials,
     * bucket not found, insufficient permissions). Extracts detailed error information
     * from AWS error details.
     * 
     * @param ex the S3 exception from AWS SDK
     * @return 500 Internal Server Error with S3 error details
     */
    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<Map<String, String>> handleS3Exception(S3Exception ex) {
        log.error("S3 operation failed: {}", ex.awsErrorDetails().errorMessage(), ex);
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "S3 operation failed: " + ex.awsErrorDetails().errorMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handles all other unexpected exceptions.
     * 
     * This is the fallback handler for any exception not explicitly handled by other
     * methods. Logs the full exception for debugging and returns a generic error message
     * to avoid exposing internal implementation details.
     * 
     * @param ex the unexpected exception
     * @return 500 Internal Server Error with generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "An unexpected error occurred: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
