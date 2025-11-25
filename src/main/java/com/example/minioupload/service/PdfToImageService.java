package com.example.minioupload.service;

import com.example.minioupload.config.PdfConversionProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF转图片服务
 * 使用Apache PDFBox将PDF文档的页面渲染为高质量图片
 * 
 * 主要功能：
 * 1. 全量转换：将PDF所有页面转换为图片
 * 2. 指定页面转换：只转换指定的页码
 * 3. 获取PDF页数
 * 
 * 技术实现：
 * - 使用PDFBox的PDFRenderer进行页面渲染
 * - 支持自定义DPI（分辨率）
 * - 支持多种图片格式（PNG、JPG等）
 * - RGB色彩模式
 */
@Slf4j
@Service
public class PdfToImageService {
    
    private final PdfConversionProperties properties;
    
    public PdfToImageService(PdfConversionProperties properties) {
        this.properties = properties;
    }
    
    /**
     * 转换PDF为图片（使用默认配置）
     * 
     * @param pdfFile PDF文件
     * @param jobId 任务ID
     * @return 图片文件路径列表
     * @throws IOException 转换失败时抛出
     */
    public List<String> convertPdfToImages(File pdfFile, String jobId) throws IOException {
        return convertPdfToImages(pdfFile, jobId, properties.getImageRendering().getDpi(), 
                properties.getImageRendering().getFormat());
    }
    
    /**
     * 转换PDF为图片（自定义DPI和格式）
     * 将PDF的所有页面转换为图片文件
     * 
     * @param pdfFile PDF文件
     * @param jobId 任务ID，用于创建输出目录
     * @param dpi 图片分辨率（推荐300 DPI用于高质量输出）
     * @param format 图片格式（PNG、JPG等）
     * @return 图片文件路径列表，按页码顺序排列
     * @throws IOException 转换失败时抛出
     */
    public List<String> convertPdfToImages(File pdfFile, String jobId, int dpi, String format) throws IOException {
        log.info("Starting PDF to images conversion for jobId: {}, DPI: {}, Format: {}", jobId, dpi, format);
        
        List<String> imageFiles = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        Path imageDir = Paths.get(properties.getTempDirectory(), jobId, "images");
        Files.createDirectories(imageDir);
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            
            log.info("PDF has {} pages, starting rendering...", pageCount);
            
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                long pageStartTime = System.currentTimeMillis();
                
                BufferedImage image = pdfRenderer.renderImageWithDPI(
                    pageIndex, 
                    dpi, 
                    ImageType.RGB
                );
                
                String imageFileName = String.format("page_%04d.%s", pageIndex + 1, format.toLowerCase());
                File imageFile = imageDir.resolve(imageFileName).toFile();
                
                ImageIO.write(image, format, imageFile);
                imageFiles.add(imageFile.getAbsolutePath());
                
                long pageTime = System.currentTimeMillis() - pageStartTime;
                log.debug("Page {} rendered in {}ms, size: {} bytes", 
                    pageIndex + 1, pageTime, imageFile.length());
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Successfully converted {} pages to images in {}ms, total size: {} bytes", 
                pageCount, totalTime, 
                imageFiles.stream().mapToLong(path -> new File(path).length()).sum());
            
        } catch (IOException e) {
            log.error("Failed to convert PDF to images for jobId: {}", jobId, e);
            throw new IOException("PDF to images conversion failed: " + e.getMessage(), e);
        }
        
        return imageFiles;
    }
    
    /**
     * 转换PDF的指定页面为图片
     * 只转换指定的页码列表，适用于增量转换场景
     * 
     * @param pdfFile PDF文件
     * @param jobId 任务ID，用于创建输出目录
     * @param pageNumbers 需要转换的页码列表（从1开始）
     * @param dpi 图片分辨率
     * @param format 图片格式
     * @return 页码到图片路径的映射
     * @throws IOException 转换失败时抛出
     */
    public Map<Integer, String> convertSpecificPagesToImages(File pdfFile, String jobId, List<Integer> pageNumbers, int dpi, String format) throws IOException {
        log.info("Starting PDF to images conversion for jobId: {}, Pages: {}, DPI: {}, Format: {}", 
            jobId, pageNumbers, dpi, format);
        
        Map<Integer, String> imageFiles = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        Path imageDir = Paths.get(properties.getTempDirectory(), jobId, "images");
        Files.createDirectories(imageDir);
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            
            log.info("PDF has {} pages, converting {} specific pages...", pageCount, pageNumbers.size());
            
            for (Integer pageNumber : pageNumbers) {
                if (pageNumber < 1 || pageNumber > pageCount) {
                    log.warn("Invalid page number: {}, skipping", pageNumber);
                    continue;
                }
                
                int pageIndex = pageNumber - 1;
                long pageStartTime = System.currentTimeMillis();
                
                BufferedImage image = pdfRenderer.renderImageWithDPI(
                    pageIndex, 
                    dpi, 
                    ImageType.RGB
                );
                
                String imageFileName = String.format("page_%04d.%s", pageNumber, format.toLowerCase());
                File imageFile = imageDir.resolve(imageFileName).toFile();
                
                ImageIO.write(image, format, imageFile);
                imageFiles.put(pageNumber, imageFile.getAbsolutePath());
                
                long pageTime = System.currentTimeMillis() - pageStartTime;
                log.debug("Page {} rendered in {}ms, size: {} bytes", 
                    pageNumber, pageTime, imageFile.length());
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Successfully converted {} pages to images in {}ms", 
                imageFiles.size(), totalTime);
            
        } catch (IOException e) {
            log.error("Failed to convert PDF pages to images for jobId: {}", jobId, e);
            throw new IOException("PDF to images conversion failed: " + e.getMessage(), e);
        }
        
        return imageFiles;
    }
    
    /**
     * 获取PDF文档的页数
     * 
     * @param pdfFile PDF文件
     * @return PDF文档的总页数
     * @throws IOException 读取失败时抛出
     */
    public int getPageCount(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return document.getNumberOfPages();
        }
    }
}
