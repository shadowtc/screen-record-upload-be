package com.example.minioupload.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S3/MinIO连接设置的配置属性。
 * 
 * 此类将application.yml中的配置值（前缀：s3）绑定到强类型的Java属性。
 * 所有值都可以通过环境变量覆盖，以实现部署灵活性。
 * 
 * 这些属性用于配置AWS SDK S3客户端和预签名器，
 * 以便与S3兼容的存储（MinIO、AWS S3等）进行通信。
 */
@Component
@ConfigurationProperties(prefix = "s3")
@Getter
@Setter
public class S3ConfigProperties {
    
    /**
     * S3/MinIO端点URL。
     * 对于MinIO: http://hostname:9000
     * 对于AWS S3: 保持默认或使用区域端点
     */
    private String endpoint;
    
    /**
     * S3/MinIO认证的访问密钥。
     * 等同于AWS访问密钥ID。
     */
    private String accessKey;
    
    /**
     * S3/MinIO认证的秘密密钥。
     * 等同于AWS秘密访问密钥。
     */
    private String secretKey;
    
    /**
     * 存储文件的S3存储桶名称。
     * 必须在应用程序启动前存在。
     */
    private String bucket;
    
    /**
     * S3操作的AWS区域。
     * 对于MinIO，可以是任何有效的区域字符串（例如us-east-1）。
     * 对于AWS S3，必须与存储桶的区域匹配。
     */
    private String region;
    
    /**
     * 是否对S3 URL使用路径样式访问。
     * 
     * 路径样式：http://endpoint/bucket/key
     * 虚拟托管样式：http://bucket.endpoint/key
     * 
     * MinIO需要路径样式访问（true）。
     * AWS S3支持两种方式，但推荐虚拟托管样式（false）。
     */
    private boolean pathStyleAccess;
}
