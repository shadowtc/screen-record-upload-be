package com.example.minioupload.repository;

import com.example.minioupload.model.VideoRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * VideoRecording实体操作的仓库接口。
 * 使用Spring Data JPA提供视频录制元数据的数据库访问方法。
 * 
 * 此仓库利用VideoRecording实体中定义的MySQL 8.0功能和索引来优化查询性能。
 */
@Repository
public interface VideoRecordingRepository extends JpaRepository<VideoRecording, Long> {
    
    /**
     * 通过S3/MinIO对象键查找视频录制。
     * 
     * 此方法利用object_key列上的唯一索引进行高效查找。
     * 对象键用作存储在S3/MinIO中的文件的唯一标识符。
     * 
     * @param objectKey 要搜索的S3/MinIO对象键（例如"uploads/uuid/filename.mp4"）
     * @return 如果找到则包含VideoRecording的Optional，否则为空Optional
     */
    Optional<VideoRecording> findByObjectKey(String objectKey);
    
    /**
     * 检查是否存在具有指定S3/MinIO对象键的视频录制。
     * 
     * 此方法用于防止重复完成同一个上传。
     * 利用object_key列上的唯一索引进行高效的存在性检查。
     * 
     * @param objectKey 要检查的S3/MinIO对象键
     * @return 如果存在具有该对象键的记录则返回true，否则返回false
     */
    boolean existsByObjectKey(String objectKey);
}
