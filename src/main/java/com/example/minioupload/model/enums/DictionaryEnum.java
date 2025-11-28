package com.example.minioupload.model.enums;

import java.util.stream.Stream;

/**
 * @Description 数据字典枚举类
 * @Author tongwl
 * @Date 9:13 2023/6/27
 * @Version 1.0
 **/
public interface DictionaryEnum {
    /**
     * 获取值
     * @return
     */
    String getValue();
    /**
     * 获取名称
     * @return
     */
    String getName();
    static  <T extends Enum<T> & DictionaryEnum> T typeOf(Class<T>  enumClass, String value) {
        return Stream.of(enumClass.getEnumConstants())
                .filter(p -> p.getValue().equals(value))
                .findAny()
                .orElse(null);
    }
}
