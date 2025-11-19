package com.example.minioupload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务执行配置
 * 
 * 为@Async注解提供自定义线程池，用于异步执行视频压缩等耗时操作。
 * 通过分离的线程池确保异步任务不会阻塞其他请求处理。
 * 
 * @see org.springframework.scheduling.annotation.EnableAsync
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * 创建视频压缩专用的线程池执行器
     * 
     * 此线程池用于异步执行视频压缩任务，配置如下：
     * - 核心线程数2：基础线程数，系统启动时创建
     * - 最大线程数4：高负载时最多可创建4个线程
     * - 队列容量100：异步任务队列最多存储100个待执行任务
     * - 拒绝策略：CallerRunsPolicy（队列满时，由调用线程直接执行任务）
     * 
     * 线程名称前缀：VideoCompression-
     * 便于在日志和监控中识别压缩相关的异步任务
     * 
     * 适用场景：
     * - 异步视频压缩请求（/api/video/compress/async）
     * - 长时间运行的后台任务
     * - 耗时的I/O操作
     * 
     * @return 配置完成的ThreadPoolTaskExecutor线程池执行器
     */
    @Bean(name = "videoCompressionExecutor")
    public Executor videoCompressionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 设置核心线程数
        // 即使没有任务，这些线程也会保持存活
        executor.setCorePoolSize(2);
        
        // 设置最大线程数
        // 当队列满且任务继续增加时，线程池扩展到此数值
        executor.setMaxPoolSize(4);
        
        // 设置任务队列容量
        // 100个任务可在队列中等待执行
        executor.setQueueCapacity(100);
        
        // 设置线程名称前缀
        // 便于在日志中追踪异步任务
        executor.setThreadNamePrefix("VideoCompression-");
        
        // 设置拒绝执行处理策略
        // CallerRunsPolicy：当队列满时，由提交任务的线程直接执行
        // 这确保在极端负载下任务不会丢失，但可能阻塞调用线程
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        // 初始化线程池
        executor.initialize();
        
        return executor;
    }

    /**
     * 服务端分片上传专用线程池
     */
    @Bean(name = "multipartUploadExecutor")
    public Executor multipartUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("MultipartUpload-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}