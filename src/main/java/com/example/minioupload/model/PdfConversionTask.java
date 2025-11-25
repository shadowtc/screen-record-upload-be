package com.example.minioupload.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pdf_conversion_task", indexes = {
    @Index(name = "idx_task_id", columnList = "task_id", unique = true),
    @Index(name = "idx_business_id", columnList = "business_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfConversionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true, length = 36)
    private String taskId;

    @Column(name = "business_id", nullable = false, length = 100)
    private String businessId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(name = "total_pages", nullable = false)
    private Integer totalPages;

    @Column(name = "converted_pages", columnDefinition = "TEXT")
    private String convertedPages;

    @Column(name = "pdf_object_key", length = 1000)
    private String pdfObjectKey;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "is_base", nullable = false)
    private Boolean isBase;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
