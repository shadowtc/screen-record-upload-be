package com.example.minioupload.repository;

import com.example.minioupload.model.AsyncUploadTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 异步上传任务数据访问层
 * 
 * 提供对AsyncUploadTask实体的CRUD操作和自定义查询方法。
 */
@Repository
public interface AsyncUploadTaskRepository extends JpaRepository<AsyncUploadTask, Long> {

    /**
     * 根据jobId查找上传任务
     * 
     * @param jobId 任务的唯一标识符
     * @return 上传任务的Optional包装
     */
    Optional<AsyncUploadTask> findByJobId(String jobId);

    /**
     * 检查jobId是否存在
     * 
     * @param jobId 任务的唯一标识符
     * @return 如果存在返回true
     */
    boolean existsByJobId(String jobId);

    /**
     * 查找特定状态的所有任务
     * 
     * @param status 任务状态
     * @return 匹配状态的任务列表
     */
    List<AsyncUploadTask> findByStatus(String status);

    /**
     * 查找在特定时间之前创建的任务
     * 用于清理旧任务
     * 
     * @param dateTime 时间阈值
     * @return 匹配条件的任务列表
     */
    List<AsyncUploadTask> findByCreatedAtBefore(LocalDateTime dateTime);

    /**
     * 查找在特定时间之前创建且处于特定状态的任务
     * 用于清理旧的已完成或失败任务
     * 
     * @param dateTime 时间阈值
     * @param status 任务状态
     * @return 匹配条件的任务列表
     */
    List<AsyncUploadTask> findByCreatedAtBeforeAndStatus(LocalDateTime dateTime, String status);
}
