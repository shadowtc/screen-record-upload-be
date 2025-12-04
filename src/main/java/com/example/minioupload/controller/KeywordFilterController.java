package com.example.minioupload.controller;

import com.example.minioupload.service.KeywordFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 关键字过滤Controller
 * 提供关键字过滤相关的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/keyword-filter")
@RequiredArgsConstructor
public class KeywordFilterController {

    private final KeywordFilter keywordFilterService;

    /**
     * 检测文本是否包含关键字
     * 
     * 接口地址：POST /api/keyword-filter/contains
     * 请求体：{"text": "待检测的文本内容"}
     * 
     * @param request 包含待检测文本的请求对象
     * @return 检测结果
     */
    @PostMapping("/contains")
    public ResponseEntity<Map<String, Object>> contains(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        
        if (text == null || text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Text parameter is required"
            ));
        }
        
        long startTime = System.currentTimeMillis();
        boolean containsKeyword = keywordFilterService.contains(text);
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("containsKeyword", containsKeyword);
        response.put("textLength", text.length());
        response.put("duration", duration + "ms");
        
        log.info("Keyword detection: text length={}, contains={}, duration={}ms", 
                text.length(), containsKeyword, duration);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 查找文本中所有匹配的关键字
     * 
     * 接口地址：POST /api/keyword-filter/find-all
     * 请求体：{"text": "待检测的文本内容"}
     * 
     * @param request 包含待检测文本的请求对象
     * @return 所有匹配的关键字列表
     */
    @PostMapping("/find-all")
    public ResponseEntity<Map<String, Object>> findAll(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        
        if (text == null || text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Text parameter is required"
            ));
        }
        
        long startTime = System.currentTimeMillis();
        List<String> matches = keywordFilterService.findAll(text);
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("matches", matches);
        response.put("matchCount", matches.size());
        response.put("textLength", text.length());
        response.put("duration", duration + "ms");
        
        log.info("Keyword find all: text length={}, matches={}, duration={}ms", 
                text.length(), matches.size(), duration);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 替换文本中的敏感词
     * 
     * 接口地址：POST /api/keyword-filter/replace
     * 请求体：{"text": "待处理的文本内容", "replacement": "*"}
     * 
     * @param request 包含待处理文本和替换字符的请求对象
     * @return 替换后的文本
     */
    @PostMapping("/replace")
    public ResponseEntity<Map<String, Object>> replace(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String replacement = request.getOrDefault("replacement", "*");
        
        if (text == null || text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Text parameter is required"
            ));
        }
        
        if (replacement.length() != 1) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Replacement must be a single character"
            ));
        }
        
        long startTime = System.currentTimeMillis();
        String result = keywordFilterService.replace(text, replacement.charAt(0));
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("originalText", text);
        response.put("filteredText", result);
        response.put("duration", duration + "ms");
        
        log.info("Keyword replace: text length={}, duration={}ms", text.length(), duration);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 手动刷新关键字库
     * 
     * 接口地址：POST /api/keyword-filter/refresh
     * 
     * @return 刷新结果
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        long startTime = System.currentTimeMillis();
        int count = keywordFilterService.refreshKeywords();
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Keywords refreshed successfully");
        response.put("keywordCount", count);
        response.put("duration", duration + "ms");
        
        log.info("Keywords refreshed: count={}, duration={}ms", count, duration);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取关键字库统计信息
     * 
     * 接口地址：GET /api/keyword-filter/stats
     * 
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        int keywordCount = keywordFilterService.getKeywordCount();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("keywordCount", keywordCount);
        response.put("message", "Keyword filter service is running");
        
        return ResponseEntity.ok(response);
    }
}
