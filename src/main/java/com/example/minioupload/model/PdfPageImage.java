package com.example.minioupload.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pdf_page_image", indexes = {
    @Index(name = "idx_task_id", columnList = "task_id"),
    @Index(name = "idx_business_id", columnList = "business_id"),
    @Index(name = "idx_is_base", columnList = "is_base")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfPageImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Column(name = "business_id", nullable = false, length = 100)
    private String businessId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "image_object_key", nullable = false, length = 1000)
    private String imageObjectKey;

    @Column(name = "is_base", nullable = false)
    private Boolean isBase;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
