package com.example.minioupload.service;

import java.util.List;

/**
 * 关键字过滤接口
 * 定义关键字检测相关的方法
 */
public interface KeywordFilter {

    /**
     * 检测文本是否包含关键字
     *
     * @param text 待检测文本
     * @return true表示包含关键字
     */
    boolean contains(String text);

    /**
     * 查找文本中所有匹配的关键字
     *
     * @param text 待检测文本
     * @return 匹配的关键字列表
     */
    List<String> findAll(String text);

    /**
     * 替换文本中的关键字
     *
     * @param text 原文本
     * @param replacement 替换字符
     * @return 替换后的文本
     */
    String replace(String text, char replacement);

    /**
     * 刷新关键字库
     *
     * @return 刷新后的关键字数量
     */
    int refreshKeywords();

    /**
     * 获取关键字数量
     *
     * @return 关键字数量
     */
    int getKeywordCount();
}
