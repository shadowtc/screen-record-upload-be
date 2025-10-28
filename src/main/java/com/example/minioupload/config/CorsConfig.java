package com.example.minioupload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) configuration.
 * 
 * This configuration allows the API to be accessed from web applications
 * running on different domains. Essential for:
 * - Frontend applications hosted on different origins
 * - Development environments with separate frontend/backend servers
 * - Third-party integrations
 * 
 * Current configuration allows all origins (*) for simplicity.
 * In production, consider restricting to specific trusted origins.
 */
@Configuration
public class CorsConfig {

    /**
     * Configures CORS mappings for the application.
     * 
     * Current settings:
     * - Applies to all /api/** endpoints
     * - Allows all origins (*) - consider restricting in production
     * - Allows common HTTP methods (GET, POST, PUT, DELETE, OPTIONS)
     * - Allows all request headers
     * 
     * The OPTIONS method is included to support CORS preflight requests.
     * 
     * @return WebMvcConfigurer with CORS settings
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*") // TODO: Restrict to specific origins in production
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
