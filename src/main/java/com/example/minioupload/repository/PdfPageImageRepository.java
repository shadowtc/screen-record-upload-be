package com.example.minioupload.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.minioupload.model.PdfPageImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PdfPageImageRepository extends BaseMapper<PdfPageImage> {
    
    @Select("SELECT * FROM pdf_page_image WHERE task_id = #{taskId}")
    List<PdfPageImage> findByTaskId(String taskId);
    
    @Select("SELECT * FROM pdf_page_image WHERE business_id = #{businessId} AND is_base = 1 ORDER BY page_number ASC")
    List<PdfPageImage> findByBusinessIdAndIsBaseTrueOrderByPageNumberAsc(String businessId);
    
    @Select("SELECT * FROM pdf_page_image WHERE business_id = #{businessId} AND tenant_id = #{tenantId} AND is_base = 1 ORDER BY page_number ASC")
    List<PdfPageImage> findByBusinessIdAndTenantIdAndIsBaseTrueOrderByPageNumberAsc(@Param("businessId") String businessId, @Param("tenantId") String tenantId);
    
    @Select("SELECT * FROM pdf_page_image WHERE business_id = #{businessId} AND user_id = #{userId} AND is_base = 0 ORDER BY page_number ASC")
    List<PdfPageImage> findByBusinessIdAndUserIdAndIsBaseFalseOrderByPageNumberAsc(@Param("businessId") String businessId, @Param("userId") String userId);
    
    @Select("SELECT * FROM pdf_page_image WHERE business_id = #{businessId} AND " +
            "((is_base = 1) OR (user_id = #{userId} AND is_base = 0)) " +
            "ORDER BY page_number ASC")
    List<PdfPageImage> findMergedImages(@Param("businessId") String businessId, @Param("userId") String userId);
    
    @Select("SELECT * FROM pdf_page_image WHERE business_id = #{businessId} AND tenant_id = #{tenantId} AND " +
            "((is_base = 1) OR (user_id = #{userId} AND is_base = 0)) " +
            "ORDER BY page_number ASC")
    List<PdfPageImage> findMergedImages(@Param("businessId") String businessId, @Param("tenantId") String tenantId, @Param("userId") String userId);
}
