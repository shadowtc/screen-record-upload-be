package com.example.minioupload.service;

import com.example.minioupload.config.PdfConversionProperties;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
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
 * 4. 上传图片到MinIO
 * 
 * 技术实现：
 * - 使用PDFBox的PDFRenderer进行页面渲染
 * - 支持自定义DPI（分辨率）
 * - 支持多种图片格式（PNG、JPG等）
 * - RGB色彩模式
 * - 自动上传图片到MinIO存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfToImageService {
    
    private final PdfConversionProperties properties;
    private final MinioStorageService minioStorageService;
    
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
        if (format == null || format.trim().isEmpty()) {
            format = properties.getImageRendering().getFormat();
            log.warn("Format is null or empty, using default: {}", format);
        }
        
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
        if (format == null || format.trim().isEmpty()) {
            format = properties.getImageRendering().getFormat();
            log.warn("Format is null or empty, using default: {}", format);
        }
        
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
     * 转换PDF的指定页面为图片并上传到MinIO
     * 将页面转换为图片后立即上传到MinIO，减少本地磁盘占用
     * 
     * @param pdfFile PDF文件
     * @param userId 用户ID
     * @param businessId 业务ID
     * @param jobId 任务ID
     * @param pageNumbers 需要转换的页码列表（从1开始）
     * @param dpi 图片分辨率
     * @param format 图片格式
     * @return 页码到MinIO对象键的映射
     * @throws IOException 转换或上传失败时抛出
     */
    public Map<Integer, String> convertPagesToImagesAndUpload(File pdfFile, String userId, String businessId, 
                                                                String jobId, List<Integer> pageNumbers, 
                                                                int dpi, String format) throws IOException {
        if (format == null || format.trim().isEmpty()) {
            format = properties.getImageRendering().getFormat();
            log.warn("Format is null or empty, using default: {}", format);
        }
        
        log.info("Starting PDF to images conversion and upload for jobId: {}, Pages: {}, DPI: {}, Format: {}", 
            jobId, pageNumbers, dpi, format);
        
        Map<Integer, String> minioObjectKeys = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        Path imageDir = Paths.get(properties.getTempDirectory(), jobId, "images");
        Files.createDirectories(imageDir);
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            
            log.info("PDF has {} pages, converting and uploading {} specific pages...", pageCount, pageNumbers.size());
            
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
                
                String minioObjectKey = String.format("pdf-images/%s/%s/%s/%s", 
                    userId, businessId, jobId, imageFileName);
                
                minioStorageService.uploadFile(imageFile, minioObjectKey);
                minioObjectKeys.put(pageNumber, minioObjectKey);
                
                Files.deleteIfExists(imageFile.toPath());
                
                long pageTime = System.currentTimeMillis() - pageStartTime;
                log.debug("Page {} rendered and uploaded in {}ms, size: {} bytes, key: {}", 
                    pageNumber, pageTime, imageFile.length(), minioObjectKey);
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Successfully converted and uploaded {} pages to MinIO in {}ms", 
                minioObjectKeys.size(), totalTime);
            
        } catch (IOException e) {
            log.error("Failed to convert PDF pages to images and upload for jobId: {}", jobId, e);
            throw new IOException("PDF to images conversion and upload failed: " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(imageDir);
            } catch (IOException e) {
                log.warn("Failed to delete temp image directory: {}", imageDir, e);
            }
        }
        
        return minioObjectKeys;
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
    
    /**
     * 页面渲染信息，包含图片和PDF尺寸
     */
    @Data
    @Builder
    public static class PageRenderInfo {
        private Integer pageNumber;
        private String minioObjectKey;
        private Integer imageWidth;
        private Integer imageHeight;
        private Double pdfWidth;
        private Double pdfHeight;
        private Long fileSize;
    }
    
    /**
     * 转换PDF页面为图片并上传到MinIO（返回详细信息）
     * 
     * @param pdfFile PDF文件
     * @param userId 用户ID
     * @param businessId 业务ID
     * @param jobId 任务ID
     * @param pageNumbers 需要转换的页码列表（从1开始）
     * @param dpi 图片分辨率
     * @param format 图片格式
     * @return 页码到页面渲染信息的映射
     * @throws IOException 转换或上传失败时抛出
     */
    public Map<Integer, PageRenderInfo> convertPagesToImagesAndUploadWithInfo(
            File pdfFile, String userId, String businessId, 
            String jobId, List<Integer> pageNumbers, 
            int dpi, String format) throws IOException {
        if (format == null || format.trim().isEmpty()) {
            format = properties.getImageRendering().getFormat();
            log.warn("Format is null or empty, using default: {}", format);
        }
        
        log.info("Starting PDF to images conversion with info for jobId: {}, Pages: {}, DPI: {}, Format: {}", 
            jobId, pageNumbers, dpi, format);
        
        Map<Integer, PageRenderInfo> pageInfoMap = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        Path imageDir = Paths.get(properties.getTempDirectory(), jobId, "images");
        Files.createDirectories(imageDir);
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            
            log.info("PDF has {} pages, converting and uploading {} specific pages...", pageCount, pageNumbers.size());
            
            for (Integer pageNumber : pageNumbers) {
                if (pageNumber < 1 || pageNumber > pageCount) {
                    log.warn("Invalid page number: {}, skipping", pageNumber);
                    continue;
                }
                
                int pageIndex = pageNumber - 1;
                long pageStartTime = System.currentTimeMillis();
                
                // 获取PDF页面尺寸
                PDPage page = document.getPage(pageIndex);
                PDRectangle mediaBox = page.getMediaBox();
                double pdfWidth = mediaBox.getWidth();
                double pdfHeight = mediaBox.getHeight();
                
                // 渲染图片
                BufferedImage image = pdfRenderer.renderImageWithDPI(
                    pageIndex, 
                    dpi, 
                    ImageType.RGB
                );
                
                String imageFileName = String.format("page_%04d.%s", pageNumber, format.toLowerCase());
                File imageFile = imageDir.resolve(imageFileName).toFile();
                
                ImageIO.write(image, format, imageFile);
                
                String minioObjectKey = String.format("pdf-images/%s/%s/%s/%s", 
                    userId, businessId, jobId, imageFileName);
                
                minioStorageService.uploadFile(imageFile, minioObjectKey);
                
                // 构建页面信息
                PageRenderInfo pageInfo = PageRenderInfo.builder()
                    .pageNumber(pageNumber)
                    .minioObjectKey(minioObjectKey)
                    .imageWidth(image.getWidth())
                    .imageHeight(image.getHeight())
                    .pdfWidth(pdfWidth)
                    .pdfHeight(pdfHeight)
                    .fileSize(imageFile.length())
                    .build();
                
                pageInfoMap.put(pageNumber, pageInfo);
                
                Files.deleteIfExists(imageFile.toPath());
                
                long pageTime = System.currentTimeMillis() - pageStartTime;
                log.debug("Page {} rendered and uploaded in {}ms, PDF size: {}x{}, image size: {}x{}, key: {}", 
                    pageNumber, pageTime, pdfWidth, pdfHeight, image.getWidth(), image.getHeight(), minioObjectKey);
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Successfully converted and uploaded {} pages with info to MinIO in {}ms", 
                pageInfoMap.size(), totalTime);
            
        } catch (IOException e) {
            log.error("Failed to convert PDF pages to images with info for jobId: {}", jobId, e);
            throw new IOException("PDF to images conversion with info failed: " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(imageDir);
            } catch (IOException e) {
                log.warn("Failed to delete temp image directory: {}", imageDir, e);
            }
        }
        
        return pageInfoMap;
    }
}
