package com.example.minioupload.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3/MinIO客户端Bean的Spring配置类。
 * 
 * 此配置创建并配置AWS SDK v2的以下Bean：
 * 1. S3Client - 用于标准S3操作（创建、完成、中止上传）
 * 2. S3Presigner - 用于生成预签名URL
 * 
 * 两个Bean均使用来自S3ConfigProperties的相同凭证和端点进行配置，
 * 确保整个应用程序的行为一致。
 * 
 * 此配置同时支持AWS S3和S3兼容存储（如MinIO）。
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class S3Config {

    /**
     * 注入的S3/MinIO配置属性
     */
    private final S3ConfigProperties s3ConfigProperties;

    /**
     * 创建并配置S3客户端Bean。
     * 
     * 此客户端用于标准的S3操作，例如：
     * - 创建分片上传
     * - 完成分片上传
     * - 中止分片上传
     * - 列出上传的分片
     * - 获取对象元数据
     * 
     * 配置包括：
     * - 用于MinIO兼容性的自定义端点
     * - AWS区域设置
     * - 静态凭证（访问密钥 + 秘密密钥）
     * - MinIO的路径样式访问
     * 
     * @return 配置好的S3Client实例
     */
    @Bean
    public S3Client s3Client() {
        log.info("Initializing S3Client with endpoint: {}, region: {}, bucket: {}", 
                s3ConfigProperties.getEndpoint(), 
                s3ConfigProperties.getRegion(),
                s3ConfigProperties.getBucket());
        
        // 从配置创建凭证
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3ConfigProperties.getAccessKey(),
                s3ConfigProperties.getSecretKey()
        );

        // S3服务配置（启用路径样式访问以兼容MinIO）
        S3Configuration serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(s3ConfigProperties.isPathStyleAccess())
                .build();

        // 构建和配置S3客户端
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(s3ConfigProperties.getEndpoint()))
                .region(Region.of(s3ConfigProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(serviceConfig)
                .build();
        
        log.info("S3Client initialized successfully");
        return client;
    }

    /**
     * 创建并配置S3预签名器Bean。
     * 
     * 此预签名器用于生成具有时间限制的签名URL，允许：
     * - 客户端直接上传分片到S3/MinIO
     * - 客户端直接从S3/MinIO下载文件
     * 
     * 预签名URL消除了客户端拥有AWS凭证的需要，
     * 并通过启用客户端到S3的直接传输来减少服务器带宽。
     * 
     * 配置包括：
     * - 用于MinIO兼容性的自定义端点
     * - AWS区域设置
     * - 静态凭证（访问密钥 + 秘密密钥）
     * - MinIO的路径样式访问（确保预签名URL为 path-style）
     * 
     * @return 配置好的S3Presigner实例
     */
    @Bean
    public S3Presigner s3Presigner() {
        log.info("Initializing S3Presigner");
        
        // 从配置创建凭证
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3ConfigProperties.getAccessKey(),
                s3ConfigProperties.getSecretKey()
        );

        // S3服务配置（启用路径样式访问以兼容MinIO）
        S3Configuration serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(s3ConfigProperties.isPathStyleAccess())
                .build();

        // 构建和配置S3预签名器
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(s3ConfigProperties.getEndpoint()))
                .region(Region.of(s3ConfigProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(serviceConfig)
                .build();
        
        log.info("S3Presigner initialized successfully");
        return presigner;
    }
}
