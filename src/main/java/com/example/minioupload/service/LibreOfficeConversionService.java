package com.example.minioupload.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LibreOfficeConversionService {
    
    private static final int TIMEOUT_SECONDS = 300;
    private static final List<String> POSSIBLE_LIBREOFFICE_PATHS = List.of(
        "/usr/bin/soffice",
        "/usr/bin/libreoffice",
        "/usr/local/bin/soffice",
        "/usr/local/bin/libreoffice",
        "/opt/libreoffice/program/soffice",
        "soffice",
        "libreoffice"
    );
    
    private String libreOfficePath;
    private boolean available = false;
    
    public LibreOfficeConversionService() {
        detectLibreOffice();
    }
    
    private void detectLibreOffice() {
        for (String path : POSSIBLE_LIBREOFFICE_PATHS) {
            try {
                ProcessBuilder pb = new ProcessBuilder(path, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                String versionLine = reader.readLine();
                
                boolean completed = process.waitFor(5, TimeUnit.SECONDS);
                if (completed && process.exitValue() == 0 && versionLine != null) {
                    this.libreOfficePath = path;
                    this.available = true;
                    log.info("LibreOffice detected at: {} - Version: {}", path, versionLine);
                    return;
                }
            } catch (Exception e) {
                log.trace("LibreOffice not found at: {}", path);
            }
        }
        
        log.warn("LibreOffice not detected on this system. LibreOffice conversion will not be available.");
        log.info("To enable LibreOffice conversion, install it with: sudo apt-get install -y libreoffice");
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    public String getLibreOfficePath() {
        return libreOfficePath;
    }
    
    public File convertToPdf(File inputFile, File outputDir) throws IOException {
        if (!available) {
            throw new IOException("LibreOffice is not available on this system. " +
                "Please install it with: sudo apt-get install -y libreoffice");
        }
        
        if (!inputFile.exists()) {
            throw new IOException("Input file does not exist: " + inputFile.getAbsolutePath());
        }
        
        if (!outputDir.exists()) {
            Files.createDirectories(outputDir.toPath());
        }
        
        List<String> command = new ArrayList<>();
        command.add(libreOfficePath);
        command.add("--headless");
        command.add("--convert-to");
        command.add("pdf");
        command.add("--outdir");
        command.add(outputDir.getAbsolutePath());
        command.add(inputFile.getAbsolutePath());
        
        log.info("Executing LibreOffice conversion: {}", String.join(" ", command));
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        
        try {
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("LibreOffice: {}", line);
                }
            }
            
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("LibreOffice conversion timed out after " + 
                    TIMEOUT_SECONDS + " seconds");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("LibreOffice conversion failed with exit code " + 
                    exitCode + ". Output: " + output.toString());
            }
            
            String baseName = inputFile.getName();
            int lastDot = baseName.lastIndexOf('.');
            if (lastDot > 0) {
                baseName = baseName.substring(0, lastDot);
            }
            String pdfFileName = baseName + ".pdf";
            File outputFile = new File(outputDir, pdfFileName);
            
            if (!outputFile.exists()) {
                throw new IOException("LibreOffice conversion completed but output file not found: " + 
                    outputFile.getAbsolutePath());
            }
            
            log.info("LibreOffice conversion successful: {} -> {}", 
                inputFile.getName(), outputFile.getAbsolutePath());
            
            return outputFile;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice conversion was interrupted", e);
        }
    }
    
    public boolean supportsFormat(String extension) {
        if (!available) {
            return false;
        }
        
        extension = extension.toLowerCase();
        return extension.equals("doc") || 
               extension.equals("docx") || 
               extension.equals("xls") || 
               extension.equals("xlsx") || 
               extension.equals("ppt") || 
               extension.equals("pptx") ||
               extension.equals("odt") ||
               extension.equals("ods") ||
               extension.equals("odp") ||
               extension.equals("rtf");
    }
}
