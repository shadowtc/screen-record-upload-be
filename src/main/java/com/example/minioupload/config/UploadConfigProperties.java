package com.example.minioupload.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传约束和默认值的配置属性。
 * 
 * 此类将application.yml中的配置值（前缀：upload）绑定到强类型的Java属性。
 * 这些设置控制整个应用程序的上传行为和限制。
 * 
 * 所有值都可以通过环境变量覆盖，以实现部署灵活性。
 */
@Component
@ConfigurationProperties(prefix = "upload")
@Getter
@Setter
public class UploadConfigProperties {
    
    /**
     * 允许的最大文件大小（字节）。
     * 
     * 大于此大小的文件将在上传初始化时被拒绝。
     * 默认值：2GB（2147483648字节）
     * 
     * 设置此值时应考虑可用存储空间和网络带宽。
     */
    private long maxFileSize;
    
    /**
     * 分片上传的默认分片/块大小（字节）。
     * 
     * 当客户端未指定分片大小时使用此大小。
     * 较小的分片允许更精细的进度跟踪，但会增加HTTP请求数量。
     * 较大的分片减少开销，但可能影响慢速网络的断点续传能力。
     * 
     * 默认值：8MB（8388608字节）
     * S3最小值：5MB（最后一个分片除外）
     * S3最大值：5GB
     */
    private long defaultChunkSize;
    
    /**
     * 预签名URL的过期时间（分钟）。
     * 
     * 同时适用于上传（分片URL）和下载URL。
     * 过期后，URL无法使用，必须重新生成。
     * 
     * 默认值：60分钟
     * 
     * 需要在安全性（较短过期时间）和用户体验（较长过期时间以适应慢速上传）之间取得平衡。
     */
    private int presignedUrlExpirationMinutes;
    
    /**
     * 异步上传临时文件存储目录。
     * 
     * 用于存储正在上传的文件，支持断点续传功能。
     * 默认值：/tmp/async-uploads（Linux/Docker）或 D://ruoyi/async-uploads（Windows）
     * 
     * 注意：此目录应有足够的磁盘空间存储上传中的文件。
     */
    private String tempDirectory;
}
