package com.example.minioupload.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class WordToImageService {
    
    private static final int DEFAULT_DPI = 150;
    private static final int DEFAULT_PAGE_WIDTH = 2480;
    private static final int DEFAULT_PAGE_HEIGHT = 3508;
    private static final int MARGIN = 120;
    private static final int LINE_HEIGHT = 40;
    private static final Font DEFAULT_FONT = new Font("SansSerif", Font.PLAIN, 24);
    private static final Font HEADING_FONT = new Font("SansSerif", Font.BOLD, 32);
    private static final Font TABLE_FONT = new Font("Monospaced", Font.PLAIN, 20);
    
    public File convertDocxToPdfViaImages(File inputFile, File outputPdfFile) throws IOException {
        log.info("Converting DOCX to PDF via images: {}", inputFile.getName());
        
        try (FileInputStream fis = new FileInputStream(inputFile);
             XWPFDocument docx = new XWPFDocument(fis)) {
            
            List<BufferedImage> pageImages = renderDocxToImages(docx);
            
            if (pageImages.isEmpty()) {
                throw new IOException("No content to render from DOCX file");
            }
            
            convertImagesToPdf(pageImages, outputPdfFile);
            
            log.info("Successfully converted DOCX to PDF via {} images", pageImages.size());
            return outputPdfFile;
        }
    }
    
    private List<BufferedImage> renderDocxToImages(XWPFDocument docx) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        
        BufferedImage currentPage = createNewPage();
        Graphics2D g2d = currentPage.createGraphics();
        setupGraphics(g2d);
        
        int currentY = MARGIN;
        int maxY = DEFAULT_PAGE_HEIGHT - MARGIN;
        
        for (XWPFParagraph para : docx.getParagraphs()) {
            String text = para.getText();
            if (text == null || text.trim().isEmpty()) {
                currentY += LINE_HEIGHT / 2;
                continue;
            }
            
            boolean isHeading = para.getStyle() != null && 
                (para.getStyle().contains("Heading") || para.getStyle().contains("Title"));
            Font font = isHeading ? HEADING_FONT : DEFAULT_FONT;
            g2d.setFont(font);
            
            List<String> wrappedLines = wrapText(text, g2d, DEFAULT_PAGE_WIDTH - 2 * MARGIN);
            
            for (String line : wrappedLines) {
                if (currentY + LINE_HEIGHT > maxY) {
                    g2d.dispose();
                    images.add(currentPage);
                    
                    currentPage = createNewPage();
                    g2d = currentPage.createGraphics();
                    setupGraphics(g2d);
                    currentY = MARGIN;
                }
                
                int x = MARGIN;
                String alignment = para.getAlignment() != null ? para.getAlignment().toString() : "LEFT";
                if ("CENTER".equals(alignment)) {
                    FontMetrics fm = g2d.getFontMetrics();
                    int textWidth = fm.stringWidth(line);
                    x = (DEFAULT_PAGE_WIDTH - textWidth) / 2;
                } else if ("RIGHT".equals(alignment)) {
                    FontMetrics fm = g2d.getFontMetrics();
                    int textWidth = fm.stringWidth(line);
                    x = DEFAULT_PAGE_WIDTH - MARGIN - textWidth;
                }
                
                g2d.drawString(line, x, currentY);
                currentY += LINE_HEIGHT;
            }
            
            currentY += LINE_HEIGHT / 4;
        }
        
        for (XWPFTable table : docx.getTables()) {
            currentY += LINE_HEIGHT / 2;
            
            g2d.setFont(TABLE_FONT);
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawString("[Table]", MARGIN, currentY);
            currentY += LINE_HEIGHT;
            
            g2d.setFont(DEFAULT_FONT);
            g2d.setColor(Color.BLACK);
            
            for (int row = 0; row < table.getRows().size(); row++) {
                StringBuilder rowText = new StringBuilder();
                for (int col = 0; col < table.getRows().get(row).getTableCells().size(); col++) {
                    String cellText = table.getRows().get(row).getTableCells().get(col).getText();
                    rowText.append(cellText != null ? cellText : "").append(" | ");
                }
                
                String rowString = rowText.toString();
                List<String> wrappedLines = wrapText(rowString, g2d, DEFAULT_PAGE_WIDTH - 2 * MARGIN);
                
                for (String line : wrappedLines) {
                    if (currentY + LINE_HEIGHT > maxY) {
                        g2d.dispose();
                        images.add(currentPage);
                        
                        currentPage = createNewPage();
                        g2d = currentPage.createGraphics();
                        setupGraphics(g2d);
                        currentY = MARGIN;
                    }
                    
                    g2d.drawString(line, MARGIN, currentY);
                    currentY += LINE_HEIGHT;
                }
            }
            
            currentY += LINE_HEIGHT / 2;
        }
        
        List<XWPFPictureData> pictures = docx.getAllPictures();
        for (XWPFPictureData picData : pictures) {
            try {
                byte[] imageData = picData.getData();
                if (imageData != null && imageData.length > 0) {
                    java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageData);
                    BufferedImage img = ImageIO.read(bais);
                    
                    if (img != null) {
                        int imgWidth = Math.min(img.getWidth(), DEFAULT_PAGE_WIDTH - 2 * MARGIN);
                        int imgHeight = (int) ((double) img.getHeight() * imgWidth / img.getWidth());
                        
                        if (currentY + imgHeight > maxY) {
                            g2d.dispose();
                            images.add(currentPage);
                            
                            currentPage = createNewPage();
                            g2d = currentPage.createGraphics();
                            setupGraphics(g2d);
                            currentY = MARGIN;
                        }
                        
                        g2d.drawImage(img, MARGIN, currentY, imgWidth, imgHeight, null);
                        currentY += imgHeight + LINE_HEIGHT / 2;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to render embedded image: {}", e.getMessage());
            }
        }
        
        g2d.dispose();
        images.add(currentPage);
        
        return images;
    }
    
    private BufferedImage createNewPage() {
        return new BufferedImage(DEFAULT_PAGE_WIDTH, DEFAULT_PAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
    }
    
    private void setupGraphics(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, DEFAULT_PAGE_WIDTH, DEFAULT_PAGE_HEIGHT);
        g2d.setColor(Color.BLACK);
    }
    
    private List<String> wrapText(String text, Graphics2D g2d, int maxWidth) {
        List<String> lines = new ArrayList<>();
        FontMetrics fm = g2d.getFontMetrics();
        
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            int width = fm.stringWidth(testLine);
            
            if (width <= maxWidth) {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            }
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }
    
    private void convertImagesToPdf(List<BufferedImage> images, File outputPdfFile) throws IOException {
        try (PdfWriter writer = new PdfWriter(outputPdfFile);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc, new PageSize(DEFAULT_PAGE_WIDTH, DEFAULT_PAGE_HEIGHT))) {
            
            document.setMargins(0, 0, 0, 0);
            
            for (int i = 0; i < images.size(); i++) {
                File tempImageFile = File.createTempFile("docx_page_" + i, ".png");
                try {
                    ImageIO.write(images.get(i), "PNG", tempImageFile);
                    
                    Image pdfImage = new Image(ImageDataFactory.create(tempImageFile.getAbsolutePath()));
                    pdfImage.setWidth(DEFAULT_PAGE_WIDTH);
                    pdfImage.setHeight(DEFAULT_PAGE_HEIGHT);
                    
                    if (i > 0) {
                        document.add(new com.itextpdf.layout.element.AreaBreak());
                    }
                    
                    document.add(pdfImage);
                } finally {
                    tempImageFile.delete();
                }
            }
        }
    }
}
