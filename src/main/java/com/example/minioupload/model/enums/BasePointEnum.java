package com.example.minioupload.model.enums;

/**
 * PDF坐标原点枚举
 * @Description 定义PDF坐标系统的基准点位置
 * @Author tongwl
 * @Date 2025/11/26
 * @Version 1.0
 */
public enum BasePointEnum {
    /**
     * 左上角 (Left-Up)
     */
    LU("LU", "左上角"),
    
    /**
     * 左下角 (Left-Down)
     */
    LD("LD", "左下角"),
    
    /**
     * 右上角 (Right-Up)
     */
    RU("RU", "右上角"),
    
    /**
     * 右下角 (Right-Down)
     */
    RD("RD", "右下角");

    private final String code;
    private final String description;

    BasePointEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举
     */
    public static BasePointEnum fromCode(String code) {
        for (BasePointEnum basePoint : values()) {
            if (basePoint.code.equalsIgnoreCase(code)) {
                return basePoint;
            }
        }
        throw new IllegalArgumentException("不支持的坐标原点类型: " + code);
    }
}
