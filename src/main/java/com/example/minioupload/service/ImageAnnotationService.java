package com.example.minioupload.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * 图片注解渲染服务
 * 在图片上绘制文字注解
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnnotationService {
    
    private final MinioStorageService minioStorageService;
    
    /**
     * 从MinIO下载图片并在指定位置渲染文字
     * 
     * @param imageObjectKey MinIO对象键
     * @param text 要渲染的文字
     * @param x 文字左上角X坐标
     * @param y 文字左上角Y坐标
     * @param width 文字区域宽度
     * @param height 文字区域高度
     * @param outputFile 输出文件
     * @return 渲染后的图片信息
     * @throws IOException 如果渲染失败
     */
    public RenderedImageInfo renderTextOnImage(
            String imageObjectKey, 
            String text, 
            double x, 
            double y, 
            double width, 
            double height, 
            File outputFile) throws IOException {
        
        log.debug("Rendering text on image: objectKey={}, text={}, x={}, y={}, width={}, height={}", 
            imageObjectKey, text, x, y, width, height);
        
        // 从MinIO下载图片
        BufferedImage originalImage = downloadImageFromMinio(imageObjectKey);
        if (originalImage == null) {
            throw new IOException("Failed to download image from MinIO: " + imageObjectKey);
        }
        
        // 创建图片副本用于绘制
        BufferedImage renderedImage = new BufferedImage(
            originalImage.getWidth(), 
            originalImage.getHeight(), 
            BufferedImage.TYPE_INT_RGB
        );
        
        Graphics2D g2d = renderedImage.createGraphics();
        try {
            // 绘制原始图片
            g2d.drawImage(originalImage, 0, 0, null);
            
            // 设置渲染质量
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            
            // 转换PDF坐标到图片坐标
            // PDF坐标系是左下角为原点，向上为Y正方向
            // 图片坐标系是左上角为原点，向下为Y正方向
            int imageHeight = originalImage.getHeight();
            int textX = (int) x;
            int textY = imageHeight - (int) y; // 转换Y坐标
            int textWidth = (int) width;
            int textHeight = (int) height;
            
            // 绘制背景矩形（半透明白色）
            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.fillRect(textX, textY - textHeight, textWidth, textHeight);
            
            // 绘制边框
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawRect(textX, textY - textHeight, textWidth, textHeight);
            
            // 设置字体和颜色
            Font font = getAppropriateFont(textWidth, textHeight, text);
            g2d.setFont(font);
            g2d.setColor(Color.BLACK);
            
            // 计算文字绘制位置（居中）
            FontMetrics fm = g2d.getFontMetrics();
            int textDrawX = textX + (textWidth - fm.stringWidth(text)) / 2;
            int textDrawY = textY - textHeight + (textHeight + fm.getAscent() - fm.getDescent()) / 2;
            
            // 绘制文字
            g2d.drawString(text, textDrawX, textDrawY);
            
            log.debug("Rendered text at image coordinates: x={}, y={}, width={}, height={}", 
                textX, textY - textHeight, textWidth, textHeight);
            
        } finally {
            g2d.dispose();
        }
        
        // 保存到输出文件
        String format = getImageFormat(outputFile.getName());
        ImageIO.write(renderedImage, format, outputFile);
        
        log.info("Successfully rendered text on image: {} -> {}", imageObjectKey, outputFile.getName());
        
        return RenderedImageInfo.builder()
            .width(renderedImage.getWidth())
            .height(renderedImage.getHeight())
            .fileSize(outputFile.length())
            .format(format)
            .build();
    }
    
    /**
     * 从MinIO下载图片
     */
    private BufferedImage downloadImageFromMinio(String objectKey) throws IOException {
        try (InputStream inputStream = minioStorageService.downloadFile(objectKey)) {
            return ImageIO.read(inputStream);
        } catch (Exception e) {
            log.error("Failed to download image from MinIO: {}", objectKey, e);
            throw new IOException("Failed to download image from MinIO", e);
        }
    }
    
    /**
     * 根据区域大小和文字长度选择合适的字体
     */
    private Font getAppropriateFont(int width, int height, String text) {
        // 计算合适的字体大小
        int maxFontSize = Math.min(width / (text.length() + 1), height - 10);
        maxFontSize = Math.max(12, Math.min(maxFontSize, 48)); // 限制在12-48之间
        
        // 尝试使用中文字体
        Font font = new Font("SimHei", Font.PLAIN, maxFontSize);
        if (font.getFamily().equals("SimHei")) {
            return font;
        }
        
        // 如果SimHei不可用，尝试其他中文字体
        String[] chineseFonts = {"Microsoft YaHei", "SimSun", "Arial Unicode MS", "Sans-Serif"};
        for (String fontName : chineseFonts) {
            font = new Font(fontName, Font.PLAIN, maxFontSize);
            if (font.canDisplayUpTo(text) == -1) {
                return font;
            }
        }
        
        // 默认字体
        return new Font("SansSerif", Font.PLAIN, maxFontSize);
    }
    
    /**
     * 获取图片格式
     */
    private String getImageFormat(String filename) {
        String lowerCase = filename.toLowerCase();
        if (lowerCase.endsWith(".png")) {
            return "PNG";
        } else if (lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg")) {
            return "JPEG";
        }
        return "PNG"; // 默认PNG
    }
    
    /**
     * 渲染后的图片信息
     */
    @lombok.Data
    @lombok.Builder
    public static class RenderedImageInfo {
        private Integer width;
        private Integer height;
        private Long fileSize;
        private String format;
    }
}
