package com.example.minioupload.repository;

import com.example.minioupload.model.PdfPageImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PdfPageImageRepository extends JpaRepository<PdfPageImage, Long> {
    
    List<PdfPageImage> findByTaskId(String taskId);
    
    List<PdfPageImage> findByBusinessIdAndIsBaseTrueOrderByPageNumberAsc(String businessId);
    
    List<PdfPageImage> findByBusinessIdAndUserIdAndIsBaseFalseOrderByPageNumberAsc(String businessId, String userId);
    
    @Query("SELECT p FROM PdfPageImage p WHERE p.businessId = :businessId AND " +
           "((p.isBase = true) OR (p.userId = :userId AND p.isBase = false)) " +
           "ORDER BY p.pageNumber ASC")
    List<PdfPageImage> findMergedImages(@Param("businessId") String businessId, @Param("userId") String userId);
}
