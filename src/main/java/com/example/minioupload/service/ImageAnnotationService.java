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
     * @param pdfX PDF坐标X（左上角X）
     * @param pdfY PDF坐标Y（左上角Y）
     * @param pdfWidth PDF坐标宽度
     * @param pdfHeight PDF坐标高度
     * @param outputFile 输出文件
     * @param pagePdfWidth PDF页面宽度（点）
     * @param pagePdfHeight PDF页面高度（点）
     * @return 渲染后的图片信息
     * @throws IOException 如果渲染失败
     */
    public RenderedImageInfo renderTextOnImage(
            String imageObjectKey, 
            String text, 
            double pdfX, 
            double pdfY, 
            double pdfWidth, 
            double pdfHeight, 
            File outputFile,
            double pagePdfWidth,
            double pagePdfHeight) throws IOException {
        
        log.debug("Rendering text on image: objectKey={}, text={}, pdfCoords=[{},{},{},{}], pdfPageSize={}x{}", 
            imageObjectKey, text, pdfX, pdfY, pdfWidth, pdfHeight, pagePdfWidth, pagePdfHeight);
        
        // 从MinIO下载图片
        BufferedImage originalImage = downloadImageFromMinio(imageObjectKey);
        if (originalImage == null) {
            throw new IOException("Failed to download image from MinIO: " + imageObjectKey);
        }
        
        int imageWidth = originalImage.getWidth();
        int imageHeight = originalImage.getHeight();
        
        // 计算PDF坐标到图片坐标的缩放比例
        double scaleX = imageWidth / pagePdfWidth;
        double scaleY = imageHeight / pagePdfHeight;
        
        // 转换PDF坐标到图片坐标
        // PDF坐标系：左上角为原点，向右为X正方向，向下为Y正方向
        // 图片坐标系：左上角为原点，向右为X正方向，向下为Y正方向
        // 坐标系统一致，直接按比例缩放即可
        int imageX = (int) Math.round(pdfX * scaleX);
        int imageY = (int) Math.round(pdfY * scaleY);
        int rectWidth = (int) Math.round(pdfWidth * scaleX);
        int rectHeight = (int) Math.round(pdfHeight * scaleY);
        
        log.debug("Coordinate conversion: scale={}x{}, imageCoords=[{},{},{},{}]", 
            scaleX, scaleY, imageX, imageY, rectWidth, rectHeight);
        
        // 创建图片副本用于绘制
        BufferedImage renderedImage = new BufferedImage(
            imageWidth, 
            imageHeight, 
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
            
            // 绘制背景矩形（半透明白色）
            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.fillRect(imageX, imageY, rectWidth, rectHeight);
            
            // 绘制边框
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawRect(imageX, imageY, rectWidth, rectHeight);
            
            // 根据实际像素尺寸计算合适的字体大小
            Font font = getAppropriateFont(rectWidth, rectHeight, text);
            g2d.setFont(font);
            g2d.setColor(Color.BLACK);
            
            // 计算文字绘制位置（居中）
            FontMetrics fm = g2d.getFontMetrics();
            int textDrawX = imageX + (rectWidth - fm.stringWidth(text)) / 2;
            int textDrawY = imageY + (rectHeight + fm.getAscent() - fm.getDescent()) / 2;
            
            // 绘制文字
            g2d.drawString(text, textDrawX, textDrawY);
            
            log.debug("Rendered text at image coordinates: x={}, y={}, width={}, height={}, fontSize={}", 
                imageX, imageY, rectWidth, rectHeight, font.getSize());
            
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
