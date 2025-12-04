package com.example.minioupload;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MinIO分片上传服务的主Spring Boot应用程序类。
 * 
 * 此应用程序提供RESTful API，用于使用具有断点续传功能的分片上传处理大文件上传到MinIO/S3。
 * 
 * 主要功能：
 * - 可恢复的MinIO/S3分片上传
 * - 用于客户端直接上传的预签名URL生成
 * - 上传状态跟踪和管理
 * - MySQL 8.0中的视频录制元数据存储
 * - 支持具有可配置分片大小的大文件上传
 * 
 * 应用程序使用：
 * - Spring Boot 3.2.0和Java 17
 * - MySQL 8.0用于持久化存储
 * - AWS SDK v2用于S3兼容操作
 * - HikariCP用于优化的数据库连接池
 * - MyBatis-Plus用于ORM框架
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.example.minioupload.repository")
public class MinioUploadApplication {

    /**
     * 应用程序入口点。
     * 初始化Spring Boot应用程序上下文并启动嵌入式Web服务器。
     * 
     * @param args 传递给应用程序的命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MinioUploadApplication.class, args);
    }
}
