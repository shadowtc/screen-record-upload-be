package com.example.minioupload.controller;

import com.example.minioupload.service.RedissonTestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis测试控制器
 * 
 * 提供测试Redis连接和基本操作的API端点
 */
@Slf4j
@RestController
@RequestMapping("/api/redis")
@ConditionalOnBean(RedissonTestService.class)
public class RedisTestController {

    @Autowired(required = false)
    private RedissonTestService redissonTestService;

    /**
     * 测试Redis连接
     * 
     * @return 连接测试结果
     */
    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> response = new HashMap<>();
        
        if (redissonTestService == null) {
            response.put("success", false);
            response.put("message", "Redis服务未配置");
            return ResponseEntity.ok(response);
        }
        
        boolean connected = redissonTestService.testConnection();
        response.put("success", connected);
        response.put("message", connected ? "Redis连接成功" : "Redis连接失败");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 设置Redis键值
     * 
     * @param key   键
     * @param value 值
     * @return 操作结果
     */
    @PostMapping("/set")
    public ResponseEntity<Map<String, Object>> setKey(
            @RequestParam String key,
            @RequestParam String value) {
        Map<String, Object> response = new HashMap<>();
        
        if (redissonTestService == null) {
            response.put("success", false);
            response.put("message", "Redis服务未配置");
            return ResponseEntity.ok(response);
        }
        
        boolean success = redissonTestService.testSet(key, value);
        response.put("success", success);
        response.put("message", success ? "设置成功" : "设置失败");
        response.put("key", key);
        response.put("value", value);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取Redis键值
     * 
     * @param key 键
     * @return 键值
     */
    @GetMapping("/get")
    public ResponseEntity<Map<String, Object>> getKey(@RequestParam String key) {
        Map<String, Object> response = new HashMap<>();
        
        if (redissonTestService == null) {
            response.put("success", false);
            response.put("message", "Redis服务未配置");
            return ResponseEntity.ok(response);
        }
        
        String value = redissonTestService.testGet(key);
        response.put("success", value != null);
        response.put("key", key);
        response.put("value", value);
        response.put("message", value != null ? "获取成功" : "键不存在或获取失败");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 删除Redis键
     * 
     * @param key 键
     * @return 操作结果
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteKey(@RequestParam String key) {
        Map<String, Object> response = new HashMap<>();
        
        if (redissonTestService == null) {
            response.put("success", false);
            response.put("message", "Redis服务未配置");
            return ResponseEntity.ok(response);
        }
        
        boolean deleted = redissonTestService.testDelete(key);
        response.put("success", deleted);
        response.put("message", deleted ? "删除成功" : "删除失败或键不存在");
        response.put("key", key);
        
        return ResponseEntity.ok(response);
    }
}
