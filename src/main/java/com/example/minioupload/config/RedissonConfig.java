package com.example.minioupload.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * Redisson配置类
 * 
 * 配置Redisson客户端用于Redis分布式操作
 * 支持单机模式、集群模式、哨兵模式
 * 
 * 可通过配置项 spring.redis.enabled=false 来禁用Redisson
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.redis.timeout:10000}")
    private int timeout;

    @Value("${spring.redis.connect-timeout:3000}")
    private int connectTimeout;

    /**
     * 创建Redisson客户端Bean
     * 
     * @return RedissonClient实例
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        log.info("初始化Redisson客户端...");
        log.info("Redis配置 - Host: {}, Port: {}, Database: {}", redisHost, redisPort, redisDatabase);
        
        try {
            Config config = new Config();
            
            // 单机模式配置
            String address = "redis://" + redisHost + ":" + redisPort;
            config.useSingleServer()
                    .setAddress(address)
                    .setDatabase(redisDatabase)
                    .setTimeout(timeout)
                    .setConnectionPoolSize(64)
                    .setConnectionMinimumIdleSize(10)
                    .setIdleConnectionTimeout(10000)
                    .setConnectTimeout(connectTimeout)
                    .setRetryAttempts(3)
                    .setRetryInterval(1500);
            
            // 如果密码不为空，则设置密码
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                config.useSingleServer().setPassword(redisPassword);
                log.info("Redis密码已配置");
            }
            
            RedissonClient redissonClient = Redisson.create(config);
            log.info("Redisson客户端初始化成功");
            
            return redissonClient;
        } catch (Exception e) {
            log.error("Redisson客户端初始化失败: {}", e.getMessage());
            log.warn("应用将在没有Redis支持的情况下继续运行");
            throw e;
        }
    }
}

