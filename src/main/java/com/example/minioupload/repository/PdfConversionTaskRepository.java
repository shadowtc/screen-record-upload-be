package com.example.minioupload.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.minioupload.model.PdfConversionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PdfConversionTaskRepository extends BaseMapper<PdfConversionTask> {
    
    @Select("SELECT * FROM pdf_conversion_task WHERE task_id = #{taskId}")
    PdfConversionTask findByTaskId(String taskId);
    
    @Select("SELECT * FROM pdf_conversion_task WHERE business_id = #{businessId}")
    List<PdfConversionTask> findByBusinessId(String businessId);
    
    @Select("SELECT * FROM pdf_conversion_task WHERE business_id = #{businessId} AND user_id = #{userId}")
    List<PdfConversionTask> findByBusinessIdAndUserId(String businessId, String userId);
    
    @Select("SELECT * FROM pdf_conversion_task WHERE business_id = #{businessId} AND is_base = 1 LIMIT 1")
    PdfConversionTask findByBusinessIdAndIsBaseTrue(String businessId);
    
    @Select("SELECT * FROM pdf_conversion_task WHERE business_id = #{businessId} AND tenant_id = #{tenantId} AND is_base = 1 LIMIT 1")
    PdfConversionTask findByBusinessIdAndTenantIdAndIsBaseTrue(@Param("businessId") String businessId, @Param("tenantId") String tenantId);
    
    @Select("SELECT * FROM pdf_conversion_task WHERE business_id = #{businessId} ORDER BY created_at DESC")
    List<PdfConversionTask> findByBusinessIdOrderByCreatedAtDesc(String businessId);
}
