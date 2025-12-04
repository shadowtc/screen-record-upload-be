package com.example.minioupload.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.minioupload.model.Keyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 关键字Repository
 * 提供关键字数据访问功能
 */
@Mapper
public interface KeywordRepository extends BaseMapper<Keyword> {
    
    /**
     * 查询所有启用的关键字
     * 
     * @return 启用的关键字列表
     */
    @Select("SELECT * FROM keyword WHERE enabled = 1")
    List<Keyword> findAllEnabled();
    
    /**
     * 根据分类查询启用的关键字
     * 
     * @param category 关键字分类
     * @return 指定分类的启用关键字列表
     */
    @Select("SELECT * FROM keyword WHERE category = #{category} AND enabled = 1")
    List<Keyword> findByCategory(String category);
    
    /**
     * 根据关键字内容查询
     * 
     * @param keyword 关键字内容
     * @return 关键字对象，不存在返回null
     */
    @Select("SELECT * FROM keyword WHERE keyword = #{keyword}")
    Keyword findByKeyword(String keyword);
}
