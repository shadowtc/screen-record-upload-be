package com.example.minioupload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "pdf.conversion")
public class PdfConversionProperties {
    
    private boolean enabled = true;
    
    private String tempDirectory = "D://ruoyi/pdf-conversion";
    
    private int maxConcurrentJobs = 3;
    
    private long maxFileSize = 104857600L;
    
    private int timeoutSeconds = 300;
    
    private ImageRenderingConfig imageRendering = new ImageRenderingConfig();
    
    @Data
    public static class ImageRenderingConfig {
        private int dpi = 300;
        
        private String format = "PNG";
        
        private float quality = 1.0f;
        
        private boolean antialiasing = true;
        
        private boolean renderText = true;
        
        private boolean renderImages = true;
    }
}
