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
import java.util.List;

@Slf4j
@Service
public class PdfToImageService {
    
    private final PdfConversionProperties properties;
    
    public PdfToImageService(PdfConversionProperties properties) {
        this.properties = properties;
    }
    
    public List<String> convertPdfToImages(File pdfFile, String jobId) throws IOException {
        return convertPdfToImages(pdfFile, jobId, properties.getImageRendering().getDpi(), 
                properties.getImageRendering().getFormat());
    }
    
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
    
    public int getPageCount(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return document.getNumberOfPages();
        }
    }
}
