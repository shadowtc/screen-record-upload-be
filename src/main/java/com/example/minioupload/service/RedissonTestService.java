package com.example.minioupload.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redisson测试服务
 * 
 * 提供Redis操作的示例方法，用于验证Redisson集成
 */
@Slf4j
@Service
@ConditionalOnBean(RedissonClient.class)
public class RedissonTestService {

    @Autowired(required = false)
    private RedissonClient redissonClient;

    /**
     * 测试Redis存储和读取
     * 
     * @param key   键
     * @param value 值
     * @return 是否成功
     */
    public boolean testSet(String key, String value) {
        try {
            if (redissonClient == null) {
                log.warn("RedissonClient未配置，跳过Redis操作");
                return false;
            }
            
            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.set(value, 300, TimeUnit.SECONDS);
            log.info("成功设置Redis键: {} = {}", key, value);
            return true;
        } catch (Exception e) {
            log.error("Redis设置失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从Redis获取值
     * 
     * @param key 键
     * @return 值，如果不存在返回null
     */
    public String testGet(String key) {
        try {
            if (redissonClient == null) {
                log.warn("RedissonClient未配置，跳过Redis操作");
                return null;
            }
            
            RBucket<String> bucket = redissonClient.getBucket(key);
            String value = bucket.get();
            log.info("成功获取Redis键: {} = {}", key, value);
            return value;
        } catch (Exception e) {
            log.error("Redis获取失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 删除Redis键
     * 
     * @param key 键
     * @return 是否成功
     */
    public boolean testDelete(String key) {
        try {
            if (redissonClient == null) {
                log.warn("RedissonClient未配置，跳过Redis操作");
                return false;
            }
            
            RBucket<String> bucket = redissonClient.getBucket(key);
            boolean deleted = bucket.delete();
            log.info("成功删除Redis键: {}, 结果: {}", key, deleted);
            return deleted;
        } catch (Exception e) {
            log.error("Redis删除失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 测试Redis连接
     * 
     * @return 是否连接成功
     */
    public boolean testConnection() {
        try {
            if (redissonClient == null) {
                log.warn("RedissonClient未配置");
                return false;
            }
            
            String testKey = "redisson:test:connection";
            String testValue = "test-" + System.currentTimeMillis();
            
            // 测试写入
            RBucket<String> bucket = redissonClient.getBucket(testKey);
            bucket.set(testValue, 10, TimeUnit.SECONDS);
            
            // 测试读取
            String readValue = bucket.get();
            
            // 测试删除
            bucket.delete();
            
            boolean success = testValue.equals(readValue);
            if (success) {
                log.info("Redis连接测试成功");
            } else {
                log.error("Redis连接测试失败：写入和读取的值不匹配");
            }
            
            return success;
        } catch (Exception e) {
            log.error("Redis连接测试失败: {}", e.getMessage(), e);
            return false;
        }
    }
}
