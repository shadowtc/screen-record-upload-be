package com.example.minioupload.service;

import com.example.minioupload.config.PdfConversionProperties;
import com.example.minioupload.dto.PdfConversionProgress;
import com.example.minioupload.dto.PdfConversionRequest;
import com.example.minioupload.dto.PdfConversionResponse;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class PdfConversionService {
    
    private final PdfConversionProperties properties;
    private final PdfToImageService pdfToImageService;
    private final Executor videoCompressionExecutor;
    
    private final Map<String, PdfConversionProgress> progressMap = new ConcurrentHashMap<>();
    private final AtomicInteger activeJobs = new AtomicInteger(0);
    
    private static final Set<String> SUPPORTED_FORMATS = Set.of(
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", 
        "txt", "jpg", "jpeg", "png", "bmp", "gif", "pdf"
    );
    
    public PdfConversionService(
            PdfConversionProperties properties,
            PdfToImageService pdfToImageService,
            @Qualifier("videoCompressionExecutor") Executor videoCompressionExecutor) {
        this.properties = properties;
        this.pdfToImageService = pdfToImageService;
        this.videoCompressionExecutor = videoCompressionExecutor;
        
        try {
            Files.createDirectories(Paths.get(properties.getTempDirectory()));
            log.info("PDF conversion service initialized with temp directory: {}", 
                properties.getTempDirectory());
        } catch (IOException e) {
            log.error("Failed to create temp directory", e);
        }
    }
    
    public PdfConversionResponse convertToPdfAsync(MultipartFile file, PdfConversionRequest request) {
        if (!properties.isEnabled()) {
            return PdfConversionResponse.builder()
                .status("ERROR")
                .message("PDF conversion service is disabled")
                .build();
        }
        
        if (activeJobs.get() >= properties.getMaxConcurrentJobs()) {
            return PdfConversionResponse.builder()
                .status("ERROR")
                .message("Maximum concurrent jobs limit reached. Please try again later.")
                .build();
        }
        
        String originalFilename = file.getOriginalFilename();
        String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();
        
        if (!SUPPORTED_FORMATS.contains(extension)) {
            return PdfConversionResponse.builder()
                .status("ERROR")
                .message("Unsupported file format: " + extension + 
                        ". Supported formats: " + String.join(", ", SUPPORTED_FORMATS))
                .build();
        }
        
        if (file.getSize() > properties.getMaxFileSize()) {
            return PdfConversionResponse.builder()
                .status("ERROR")
                .message(String.format("File size exceeds limit. Max: %d MB, Actual: %.2f MB",
                    properties.getMaxFileSize() / 1024 / 1024,
                    file.getSize() / 1024.0 / 1024.0))
                .build();
        }
        
        String jobId = UUID.randomUUID().toString();
        
        PdfConversionProgress progress = PdfConversionProgress.builder()
            .jobId(jobId)
            .status("SUBMITTED")
            .currentPhase("Initializing")
            .progressPercentage(0)
            .message("Job submitted successfully")
            .startTime(System.currentTimeMillis())
            .build();
        
        progressMap.put(jobId, progress);
        
        CompletableFuture.runAsync(() -> 
            executeConversion(file, request, jobId), videoCompressionExecutor);
        
        return PdfConversionResponse.builder()
            .jobId(jobId)
            .status("PROCESSING")
            .message("Conversion started. Use jobId to check progress.")
            .build();
    }
    
    private void executeConversion(MultipartFile file, PdfConversionRequest request, String jobId) {
        activeJobs.incrementAndGet();
        long startTime = System.currentTimeMillis();
        
        try {
            updateProgress(jobId, "PROCESSING", "Converting to PDF", 10, null);
            
            Path jobDir = Paths.get(properties.getTempDirectory(), jobId);
            Files.createDirectories(jobDir);
            
            File inputFile = saveUploadedFile(file, jobDir);
            
            String outputFilename = request.getOutputFilename() != null ? 
                request.getOutputFilename() : 
                FilenameUtils.getBaseName(file.getOriginalFilename()) + ".pdf";
            
            File pdfFile = jobDir.resolve(outputFilename).toFile();
            
            updateProgress(jobId, "PROCESSING", "Converting file format", 30, null);
            
            convertFileToPdf(inputFile, pdfFile);
            
            updateProgress(jobId, "PROCESSING", "PDF conversion completed", 60, null);
            
            int pageCount = pdfToImageService.getPageCount(pdfFile);
            
            PdfConversionProgress progress = progressMap.get(jobId);
            progress.setTotalPages(pageCount);
            
            List<String> imageFiles = null;
            if (Boolean.TRUE.equals(request.getConvertToImages())) {
                updateProgress(jobId, "PROCESSING", "Converting PDF pages to images", 70, null);
                
                int dpi = request.getImageDpi() != null ? request.getImageDpi() : 
                    properties.getImageRendering().getDpi();
                String format = request.getImageFormat() != null ? request.getImageFormat() : 
                    properties.getImageRendering().getFormat();
                
                imageFiles = pdfToImageService.convertPdfToImages(pdfFile, jobId, dpi, format);
                
                updateProgress(jobId, "PROCESSING", "Image conversion completed", 95, null);
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            PdfConversionProgress finalProgress = progressMap.get(jobId);
            finalProgress.setStatus("COMPLETED");
            finalProgress.setCurrentPhase("Completed");
            finalProgress.setProgressPercentage(100);
            finalProgress.setMessage("Conversion completed successfully");
            finalProgress.setElapsedTimeMs(processingTime);
            finalProgress.setProcessedPages(pageCount);
            
            log.info("PDF conversion completed for jobId: {} in {}ms, pages: {}, images: {}", 
                jobId, processingTime, pageCount, imageFiles != null ? imageFiles.size() : 0);
            
        } catch (Exception e) {
            log.error("PDF conversion failed for jobId: {}", jobId, e);
            updateProgress(jobId, "FAILED", "Conversion failed", 0, 
                "Conversion failed: " + e.getMessage());
        } finally {
            activeJobs.decrementAndGet();
        }
    }
    
    private File saveUploadedFile(MultipartFile file, Path jobDir) throws IOException {
        String originalFilename = file.getOriginalFilename();
        File targetFile = jobDir.resolve("input_" + originalFilename).toFile();
        file.transferTo(targetFile);
        log.info("Saved uploaded file: {}, size: {} bytes", targetFile.getAbsolutePath(), targetFile.length());
        return targetFile;
    }
    
    private void convertFileToPdf(File inputFile, File outputPdfFile) throws IOException {
        String extension = FilenameUtils.getExtension(inputFile.getName()).toLowerCase();
        
        log.info("Converting {} file to PDF: {}", extension, inputFile.getName());
        
        switch (extension) {
            case "pdf":
                Files.copy(inputFile.toPath(), outputPdfFile.toPath());
                break;
            case "txt":
                convertTextToPdf(inputFile, outputPdfFile);
                break;
            case "doc":
                convertDocToPdf(inputFile, outputPdfFile);
                break;
            case "docx":
                convertDocxToPdf(inputFile, outputPdfFile);
                break;
            case "xls":
                convertXlsToPdf(inputFile, outputPdfFile);
                break;
            case "xlsx":
                convertXlsxToPdf(inputFile, outputPdfFile);
                break;
            case "ppt":
                convertPptToPdf(inputFile, outputPdfFile);
                break;
            case "pptx":
                convertPptxToPdf(inputFile, outputPdfFile);
                break;
            case "jpg":
            case "jpeg":
            case "png":
            case "bmp":
            case "gif":
                convertImageToPdf(inputFile, outputPdfFile);
                break;
            default:
                throw new IOException("Unsupported file format: " + extension);
        }
        
        log.info("PDF conversion completed: {}, size: {} bytes", 
            outputPdfFile.getAbsolutePath(), outputPdfFile.length());
    }
    
    private void convertTextToPdf(File inputFile, File outputPdfFile) throws IOException {
        try (PdfWriter writer = new PdfWriter(outputPdfFile);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {
            
            String content = Files.readString(inputFile.toPath());
            document.add(new Paragraph(content));
        }
    }
    
    private void convertDocToPdf(File inputFile, File outputPdfFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             HWPFDocument doc = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(doc);
             PdfWriter writer = new PdfWriter(outputPdfFile);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {
            
            String text = extractor.getText();
            document.add(new Paragraph(text));
        }
    }
    
    private void convertDocxToPdf(File inputFile, File outputPdfFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             XWPFDocument docx = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(docx);
             PdfWriter writer = new PdfWriter(outputPdfFile);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {
            
            String text = extractor.getText();
            String[] paragraphs = text.split("\n");
            for (String para : paragraphs) {
                if (!para.trim().isEmpty()) {
                    document.add(new Paragraph(para));
                }
            }
        }
    }
    
    private void convertXlsToPdf(File inputFile, File outputPdfFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             Workbook workbook = new HSSFWorkbook(fis);
             PdfWriter writer = new PdfWriter(outputPdfFile);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {
            
            convertExcelToPdf(workbook, document);
        }
    }
    
    private void convertXlsxToPdf(File inputFile, File outputPdfFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             Workbook workbook = new XSSFWorkbook(fis);
             PdfWriter writer = new PdfWriter(outputPdfFile);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {
            
            convertExcelToPdf(workbook, document);
        }
    }
    
    private void convertExcelToPdf(Workbook workbook, Document document) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            document.add(new Paragraph("Sheet: " + sheet.getSheetName())
                .setBold()
                .setFontSize(14));
            
            for (Row row : sheet) {
                StringBuilder rowText = new StringBuilder();
                for (Cell cell : row) {
                    String cellValue = getCellValueAsString(cell);
                    rowText.append(cellValue).append("\t");
                }
                if (rowText.length() > 0) {
                    document.add(new Paragraph(rowText.toString()));
                }
            }
            document.add(new Paragraph("\n"));
        }
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    private void convertPptToPdf(File inputFile, File outputPdfFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             HSLFSlideShow ppt = new HSLFSlideShow(fis)) {
            
            convertPresentationToPdf(ppt, outputPdfFile);
        }
    }
    
    private void convertPptxToPdf(File inputFile, File outputPdfFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             XMLSlideShow pptx = new XMLSlideShow(fis)) {
            
            convertPresentationToPdf(pptx, outputPdfFile);
        }
    }
    
    private void convertPresentationToPdf(Object slideShow, File outputPdfFile) throws IOException {
        try (PdfWriter writer = new PdfWriter(outputPdfFile);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {
            
            Dimension pageSize;
            int slideCount;
            
            if (slideShow instanceof HSLFSlideShow) {
                HSLFSlideShow hslf = (HSLFSlideShow) slideShow;
                pageSize = hslf.getPageSize();
                slideCount = hslf.getSlides().size();
            } else if (slideShow instanceof XMLSlideShow) {
                XMLSlideShow xslf = (XMLSlideShow) slideShow;
                pageSize = xslf.getPageSize();
                slideCount = xslf.getSlides().size();
            } else {
                throw new IOException("Unsupported presentation type");
            }
            
            for (int i = 0; i < slideCount; i++) {
                BufferedImage slideImage = new BufferedImage(
                    pageSize.width, pageSize.height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = slideImage.createGraphics();
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, pageSize.width, pageSize.height);
                
                if (slideShow instanceof HSLFSlideShow) {
                    ((HSLFSlideShow) slideShow).getSlides().get(i).draw(graphics);
                } else {
                    ((XMLSlideShow) slideShow).getSlides().get(i).draw(graphics);
                }
                
                graphics.dispose();
                
                File tempImageFile = File.createTempFile("slide_" + i, ".png");
                javax.imageio.ImageIO.write(slideImage, "PNG", tempImageFile);
                
                Image pdfImage = new Image(ImageDataFactory.create(tempImageFile.getAbsolutePath()));
                pdfImage.setAutoScale(true);
                document.add(pdfImage);
                
                tempImageFile.delete();
            }
        }
    }
    
    private void convertImageToPdf(File inputFile, File outputPdfFile) throws IOException {
        try (PdfWriter writer = new PdfWriter(outputPdfFile);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {
            
            Image image = new Image(ImageDataFactory.create(inputFile.getAbsolutePath()));
            image.setAutoScale(true);
            document.add(image);
        }
    }
    
    private void updateProgress(String jobId, String status, String phase, 
                                 int percentage, String errorMessage) {
        PdfConversionProgress progress = progressMap.get(jobId);
        if (progress != null) {
            progress.setStatus(status);
            progress.setCurrentPhase(phase);
            progress.setProgressPercentage(percentage);
            if (errorMessage != null) {
                progress.setErrorMessage(errorMessage);
            }
            progress.setElapsedTimeMs(System.currentTimeMillis() - progress.getStartTime());
            
            log.debug("Progress update for jobId {}: {} - {}% - {}", 
                jobId, status, percentage, phase);
        }
    }
    
    public PdfConversionProgress getProgress(String jobId) {
        PdfConversionProgress progress = progressMap.get(jobId);
        if (progress == null) {
            return PdfConversionProgress.builder()
                .jobId(jobId)
                .status("NOT_FOUND")
                .message("Job not found")
                .build();
        }
        return progress;
    }
    
    public PdfConversionResponse getResult(String jobId) {
        PdfConversionProgress progress = progressMap.get(jobId);
        if (progress == null) {
            return PdfConversionResponse.builder()
                .status("NOT_FOUND")
                .message("Job not found")
                .build();
        }
        
        if (!"COMPLETED".equals(progress.getStatus())) {
            return PdfConversionResponse.builder()
                .jobId(jobId)
                .status(progress.getStatus())
                .message(progress.getMessage())
                .build();
        }
        
        Path jobDir = Paths.get(properties.getTempDirectory(), jobId);
        File pdfFile = null;
        List<String> imageFiles = new ArrayList<>();
        
        try {
            Optional<File> pdf = Files.walk(jobDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".pdf"))
                .map(Path::toFile)
                .findFirst();
            
            if (pdf.isPresent()) {
                pdfFile = pdf.get();
            }
            
            Path imageDir = jobDir.resolve("images");
            if (Files.exists(imageDir)) {
                Files.walk(imageDir)
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .forEach(imageFiles::add);
            }
        } catch (IOException e) {
            log.error("Failed to get result files for jobId: {}", jobId, e);
        }
        
        return PdfConversionResponse.builder()
            .jobId(jobId)
            .status("COMPLETED")
            .message("Conversion completed successfully")
            .pdfFilePath(pdfFile != null ? pdfFile.getAbsolutePath() : null)
            .pdfFileSize(pdfFile != null ? pdfFile.length() : null)
            .pageCount(progress.getTotalPages())
            .imageFilePaths(imageFiles.isEmpty() ? null : imageFiles)
            .processingTimeMs(progress.getElapsedTimeMs())
            .build();
    }
    
    public Set<String> getSupportedFormats() {
        return SUPPORTED_FORMATS;
    }
}
