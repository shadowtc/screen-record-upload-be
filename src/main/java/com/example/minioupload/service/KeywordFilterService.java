package com.example.minioupload.service;

import com.example.minioupload.model.Keyword;
import com.example.minioupload.repository.KeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 高性能关键字过滤服务
 * 
 * 使用AC自动机（Aho-Corasick算法）实现高效的多模式匹配
 * 特点：
 * 1. 时间复杂度：O(n)，n为文本长度，与关键字数量无关
 * 2. 使用内存缓存，避免频繁查询数据库
 * 3. 使用读写锁保证线程安全
 * 4. 定期自动刷新关键字库（每5分钟）
 * 
 * AC自动机原理：
 * - 构建Trie树（前缀树）存储所有关键字
 * - 构建失败指针（类似KMP算法），实现高效的模式匹配
 * - 一次遍历即可找到所有匹配的关键字
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordFilterService implements KeywordFilter {

    private final KeywordRepository keywordRepository;
    
    /**
     * AC自动机根节点
     */
    private ACNode root;
    
    /**
     * 读写锁，保证并发安全
     * 读操作（contains方法）使用读锁，允许多线程同时读
     * 写操作（刷新关键字）使用写锁，独占访问
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    /**
     * AC自动机节点
     */
    private static class ACNode {
        /**
         * 子节点映射，key为字符，value为子节点
         */
        Map<Character, ACNode> children = new HashMap<>();
        
        /**
         * 失败指针，用于状态转移
         */
        ACNode fail;
        
        /**
         * 是否为某个关键字的结尾
         */
        boolean isEnd;
        
        /**
         * 当前节点代表的关键字（仅在isEnd为true时有值）
         */
        String keyword;
    }
    
    /**
     * 初始化：应用启动时加载关键字
     */
    @PostConstruct
    public void init() {
        log.info("Initializing keyword filter service...");
        refreshKeywords();
        log.info("Keyword filter service initialized successfully");
    }
    
    /**
     * 定期刷新关键字库
     * 每5分钟执行一次，从数据库重新加载启用的关键字
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void scheduledRefresh() {
        log.info("Scheduled refresh of keywords started");
        refreshKeywords();
    }
    
    /**
     * 手动刷新关键字库
     * 可在管理后台调用此方法立即刷新
     * 
     * @return 刷新后的关键字数量
     */
    public int refreshKeywords() {
        lock.writeLock().lock();
        try {
            List<Keyword> keywords = keywordRepository.findAllEnabled();
            buildAhoCorasick(keywords);
            log.info("Keywords refreshed successfully, total count: {}", keywords.size());
            return keywords.size();
        } catch (Exception e) {
            log.error("Failed to refresh keywords", e);
            return 0;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 构建AC自动机
     * 
     * @param keywords 关键字列表
     */
    private void buildAhoCorasick(List<Keyword> keywords) {
        // 初始化根节点
        root = new ACNode();
        
        // 第一步：构建Trie树
        for (Keyword keyword : keywords) {
            if (keyword.getKeyword() == null || keyword.getKeyword().isEmpty()) {
                continue;
            }
            insertKeyword(keyword.getKeyword());
        }
        
        // 第二步：构建失败指针（使用BFS）
        buildFailurePointers();
    }
    
    /**
     * 向Trie树中插入关键字
     * 
     * @param keyword 关键字
     */
    private void insertKeyword(String keyword) {
        ACNode current = root;
        for (char c : keyword.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new ACNode());
        }
        current.isEnd = true;
        current.keyword = keyword;
    }
    
    /**
     * 构建失败指针（BFS）
     * 失败指针的作用：当前节点匹配失败时，跳转到哪个节点继续匹配
     */
    private void buildFailurePointers() {
        Queue<ACNode> queue = new LinkedList<>();
        
        // 第一层节点的失败指针都指向根节点
        for (ACNode child : root.children.values()) {
            child.fail = root;
            queue.offer(child);
        }
        
        // BFS遍历，构建每个节点的失败指针
        while (!queue.isEmpty()) {
            ACNode current = queue.poll();
            
            for (Map.Entry<Character, ACNode> entry : current.children.entrySet()) {
                char c = entry.getKey();
                ACNode child = entry.getValue();
                
                // 寻找失败指针
                ACNode failNode = current.fail;
                while (failNode != null && !failNode.children.containsKey(c)) {
                    failNode = failNode.fail;
                }
                
                if (failNode == null) {
                    child.fail = root;
                } else {
                    child.fail = failNode.children.get(c);
                }
                
                queue.offer(child);
            }
        }
    }
    
    /**
     * 检测文本是否包含关键字
     * 
     * 这是主要的公共接口方法
     * 时间复杂度：O(n)，n为文本长度
     * 
     * @param text 待检测的文本
     * @return true表示包含关键字，false表示不包含
     */
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        lock.readLock().lock();
        try {
            if (root == null) {
                log.warn("AC automaton is not initialized");
                return false;
            }
            
            ACNode current = root;
            
            // 遍历文本的每个字符
            for (char c : text.toCharArray()) {
                // 根据失败指针进行状态转移
                while (current != root && !current.children.containsKey(c)) {
                    current = current.fail;
                }
                
                // 如果存在匹配的子节点，则移动到该节点
                if (current.children.containsKey(c)) {
                    current = current.children.get(c);
                }
                
                // 检查当前节点及其失败指针链上的所有节点
                ACNode temp = current;
                while (temp != root) {
                    if (temp.isEnd) {
                        log.debug("Keyword matched: {}", temp.keyword);
                        return true; // 找到关键字，立即返回
                    }
                    temp = temp.fail;
                }
            }
            
            return false; // 未找到任何关键字
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 查找文本中所有匹配的关键字
     * 
     * @param text 待检测的文本
     * @return 匹配的关键字列表
     */
    public List<String> findAll(String text) {
        List<String> matches = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return matches;
        }
        
        lock.readLock().lock();
        try {
            if (root == null) {
                log.warn("AC automaton is not initialized");
                return matches;
            }
            
            ACNode current = root;
            
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                
                // 根据失败指针进行状态转移
                while (current != root && !current.children.containsKey(c)) {
                    current = current.fail;
                }
                
                if (current.children.containsKey(c)) {
                    current = current.children.get(c);
                }
                
                // 检查当前节点及其失败指针链上的所有节点
                ACNode temp = current;
                while (temp != root) {
                    if (temp.isEnd) {
                        matches.add(temp.keyword);
                        log.debug("Keyword matched at position {}: {}", i, temp.keyword);
                    }
                    temp = temp.fail;
                }
            }
            
            return matches;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 替换文本中的敏感词为指定字符
     * 
     * @param text 原文本
     * @param replacement 替换字符，如"*"
     * @return 替换后的文本
     */
    public String replace(String text, char replacement) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        lock.readLock().lock();
        try {
            if (root == null) {
                log.warn("AC automaton is not initialized");
                return text;
            }
            
            char[] chars = text.toCharArray();
            ACNode current = root;
            
            for (int i = 0; i < chars.length; i++) {
                char c = chars[i];
                
                while (current != root && !current.children.containsKey(c)) {
                    current = current.fail;
                }
                
                if (current.children.containsKey(c)) {
                    current = current.children.get(c);
                }
                
                // 检查当前节点及其失败指针链
                ACNode temp = current;
                while (temp != root) {
                    if (temp.isEnd) {
                        // 替换关键字
                        int keywordLength = temp.keyword.length();
                        int startIndex = i - keywordLength + 1;
                        for (int j = startIndex; j <= i; j++) {
                            if (j >= 0 && j < chars.length) {
                                chars[j] = replacement;
                            }
                        }
                    }
                    temp = temp.fail;
                }
            }
            
            return new String(chars);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取当前关键字库中的关键字数量（仅用于监控）
     * 
     * @return 关键字数量
     */
    public int getKeywordCount() {
        lock.readLock().lock();
        try {
            return countKeywords(root);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 递归统计Trie树中的关键字数量
     */
    private int countKeywords(ACNode node) {
        if (node == null) {
            return 0;
        }
        int count = node.isEnd ? 1 : 0;
        for (ACNode child : node.children.values()) {
            count += countKeywords(child);
        }
        return count;
    }
}
