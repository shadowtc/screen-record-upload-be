package com.example.minioupload.repository;

import com.example.minioupload.model.PdfConversionTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PdfConversionTaskRepository extends JpaRepository<PdfConversionTask, Long> {
    
    Optional<PdfConversionTask> findByTaskId(String taskId);
    
    List<PdfConversionTask> findByBusinessId(String businessId);
    
    List<PdfConversionTask> findByBusinessIdAndUserId(String businessId, String userId);
    
    Optional<PdfConversionTask> findByBusinessIdAndIsBaseTrue(String businessId);
    
    List<PdfConversionTask> findByBusinessIdOrderByCreatedAtDesc(String businessId);
}
