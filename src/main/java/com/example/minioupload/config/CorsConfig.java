package com.example.minioupload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS（跨域资源共享）配置。
 * 
 * 此配置允许来自不同域的Web应用程序访问API。适用于以下场景：
 * - 托管在不同域的前端应用程序
 * - 前后端分离的开发环境
 * - 第三方集成
 * 
 * 当前配置为简化而允许所有源（*）。
 * 在生产环境中，应考虑限制为特定的可信源。
 */
@Configuration
public class CorsConfig {

    /**
     * 为应用程序配置CORS映射。
     * 
     * 当前设置：
     * - 应用于所有 /api/** 端点
     * - 允许所有源（*）- 生产环境中应考虑限制
     * - 允许常用的HTTP方法（GET、POST、PUT、DELETE、OPTIONS）
     * - 允许所有请求头
     * 
     * 包含OPTIONS方法以支持CORS预检请求。
     * 
     * @return 配置了CORS设置的WebMvcConfigurer
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*") // TODO: 在生产环境中限制为特定源
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
