package com.example.minioupload.utils;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.dromara.common.core.exception.ServiceException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;

/**
 * @Description pdf工具类
 * @Author tongwl
 * @Date 2025/9/19 13:28
 * @Version 1.0
 **/
@Slf4j
public class PdfUtils {
    private static final float JPEG_QUALITY = 90f;
    private static final int DPI = 200;

    /**
     * 在PDF中写入文字
     *
     * @param pdfData PDF文件的base64编码数据
     * @param x 左上角X坐标
     * @param y 左上角Y坐标
     * @param width 文字宽度
     * @param height 文字高度
     * @param text 文字内容
     * @return 写入文字后的PDF文件base64编码数据
     */
    public static String writeTextToPdf(String pdfData, int pageIndex, double x, double y, float width, float height, String text) {
        try {
            log.info("开始在PDF中写入文字（左上角为原点），坐标：({}, {})，尺寸：{}x{}，内容：{}", x, y, width, height, text);

            // 解码base64 PDF数据
            byte[] pdfBytes = Base64.getDecoder().decode(pdfData);

            // 加载PDF文档
            try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {

                // 获取指定页面
                PDPage page = document.getPage(pageIndex);

                // 打印页面宽高
                double pageWidth = page.getMediaBox().getWidth();
                double pageHeight = page.getMediaBox().getHeight();
                log.info("页面尺寸：宽度={}，高度={}", pageWidth, pageHeight);

                // 将坐标从左上角转换为PDF默认的左下角坐标系
                double fixedY = pageHeight - y;
                log.info("页面高度={}，原始Y={}，转换后Y={}", pageHeight, y, fixedY);

                // 创建内容流
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true)) {

                    // 设置字体和字号
                    float fontSize = calculateFontSize(width, height, text);
                    log.info("准备写入的文本：[{}]，长度：{}，Unicode码点：{}",
                        text, text.length(), text.codePoints().mapToObj(Integer::toHexString).toArray());

                    PDFont font = loadChineseFont(document, text);

                    log.info("使用字体：{}，字号：{}", font.getName(), fontSize);
                    contentStream.setFont(font, fontSize);

                    // 设置文字颜色为黑色
                    contentStream.setNonStrokingColor(0f, 0f, 0f);

                    // 开始写入文本
                    contentStream.beginText();

                    // 设置文本位置（左上角为原点），注意 setTextMatrix 需要 float
                    contentStream.setTextMatrix(1, 0, 0, 1, (float) x, (float) fixedY+10);
                    //写入文字
                    contentStream.showText(text);
                    // 结束文本
                    contentStream.endText();
                }

                // 保存结果到字节数组
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    document.save(outputStream);
                    String result = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                    log.info("PDF文字写入完成（左上角坐标系），结果数据长度：{}", result.length());
                    return result;
                }
            }

        } catch (Exception e) {
            log.error("PDF文字写入失败：{}", e.getMessage(), e);
        }
    }
    /**
     * 根据文字区域计算合适的字体大小
     */
    private static float calculateFontSize(float width, float height, String text) {
        // 简单的字体大小计算，可以根据需要优化
        float maxFontSize = Math.min(width / text.length() * 2, height * 0.8f);
        return Math.max(8, Math.min(maxFontSize, 72)); // 限制在8-72之间
    }

    /**
     * 加载统一字体（中文和英文都使用同一个字体）
     */
    private static PDFont loadChineseFont(PDDocument document, String text) {
        log.info("统一使用Noto Sans SC字体");
        try {
            return loadSystemFont(document);
        } catch (Exception e) {
            log.error("字体加载失败：{}", e.getMessage(), e);
            throw new ServiceException("无法加载字体，请确保字体文件存在：" + e.getMessage());
        }
    }



    /**
     * 加载统一字体（优先使用项目资源目录的字体）
     */
    private static PDFont loadSystemFont(PDDocument document) throws Exception {
        // 优先从项目资源目录加载字体
        try {
            PDFont resourceFont = loadResourceFont(document);
            if (resourceFont != null) {
                log.info("成功从资源目录加载Noto Sans SC字体");
                return resourceFont;
            }
        } catch (Exception e) {
            log.warn("从资源目录加载字体失败：{}", e.getMessage());
        }

        // 如果资源目录字体加载失败，尝试系统字体
        String osName = System.getProperty("os.name").toLowerCase();
        log.info("当前操作系统：{}，尝试系统字体", osName);

        String[] fontPaths = getFontPathsByOS(osName);
        for (String fontPath : fontPaths) {
            File fontFile = new File(fontPath);
            if (fontFile.exists()) {
                try {
                    log.info("成功加载系统字体：{}", fontPath);
                    return PDType0Font.load(document, fontFile);
                } catch (Exception e) {
                    log.warn("字体文件加载失败：{}，错误：{}", fontPath, e.getMessage());
                    continue;
                }
            }
        }

        throw new Exception("未找到可用的字体，请确保 NotoSansSC-VariableFont_wght.ttf 文件存在于 resources/fonts/ 目录");
    }

    /**
     * 从项目资源目录加载字体
     */
    private static PDFont loadResourceFont(PDDocument document) throws Exception {
        String[] resourceFontPaths = {
            "/fonts/NotoSansSC-VariableFont_wght.ttf"
        };

        for (String resourcePath : resourceFontPaths) {
            try {
                InputStream fontStream = PdfUtils.class.getResourceAsStream(resourcePath);
                if (fontStream != null) {
                    log.info("找到资源字体：{}", resourcePath);
                    return PDType0Font.load(document, fontStream);
                }
            } catch (Exception e) {
                log.debug("资源字体加载失败：{}，错误：{}", resourcePath, e.getMessage());
            }
        }

        return null;
    }

    /**
     * 根据操作系统获取字体路径列表
     */
    private static String[] getFontPathsByOS(String osName) {
        if (osName.contains("windows")) {
            return new String[]{
                "C:/Windows/Fonts/msyh.ttc"         // 微软雅黑
            };
        } else if (osName.contains("linux")) {
            return new String[]{
                "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc"
            };
        } else {
            return new String[]{
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
            };
        }
    }
    public static Double pxToPt(Double px) {
        return px * 72d / 96d; // px 转 pt
    }
    /**
     * 仅处理存在旋转的页面，其他页面保持原样
     */
    public static ByteArrayInputStream fixRotatedImagePages(InputStream inputPdf) throws Exception {
        try (PDDocument originalDoc = PDDocument.load(inputPdf)) {
            if (originalDoc.isEncrypted()) {
                originalDoc.setAllSecurityToBeRemoved(true);
            }

            PDFRenderer renderer = new PDFRenderer(originalDoc);
            renderer.setSubsamplingAllowed(false);

            try (PDDocument newDoc = new PDDocument()) {
                // 按原顺序遍历所有页面
                for (int pageIndex = 0; pageIndex < originalDoc.getNumberOfPages(); pageIndex++) {
                    PDPage originalPage = originalDoc.getPage(pageIndex);
                    int rotation = originalPage.getRotation();
                    log.info("页面 {} 原始旋转角度：{}°", pageIndex + 1, rotation);

                    if (rotation != 0) {
                        // 处理旋转页面（清空属性+纠正图片）
                        processRotatedPage(newDoc, pageIndex, renderer);
                    } else {
                        // 正常页面：复制原页面的属性和内容（修复内容流复制错误）
                        PDPage copiedPage = copyPage(newDoc, originalPage);
                        newDoc.addPage(copiedPage);
                        log.info("页面 {} 无旋转，直接保留原内容", pageIndex + 1);
                    }
                }

                // 将新文档写入字节数组输出流，转换为输入流返回
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                newDoc.save(outputStream);
                return new ByteArrayInputStream(outputStream.toByteArray());
            }
        }
    }
    /**
     * 复制页面
     */
    private static PDPage copyPage(PDDocument newDoc, PDPage originalPage) throws Exception {
        // 1. 复制页面基本属性（尺寸、旋转角度等）
        PDRectangle mediaBox = originalPage.getMediaBox();
        PDPage newPage = new PDPage(mediaBox);
        newPage.setRotation(originalPage.getRotation()); // 复制旋转角度（正常页面应为0）
        newPage.setCropBox(originalPage.getCropBox());
        newPage.setBleedBox(originalPage.getBleedBox());
        newPage.setArtBox(originalPage.getArtBox());
        newPage.setTrimBox(originalPage.getTrimBox());

        // 2. 复制页面内容流（处理多层内容流）
        InputStream originalContentStream = originalPage.getContents();
        if (originalContentStream != null) {
            // 创建新的内容流
            try (PDPageContentStream contentStream = new PDPageContentStream(
                newDoc, newPage, PDPageContentStream.AppendMode.OVERWRITE, true)) {
                // 读取原内容流的所有数据
                try (InputStream in = originalContentStream;
                     OutputStream out = new ByteArrayOutputStream()) {
                    // 将原内容流数据写入字节数组
                    IOUtils.copy(in, out);
                    byte[] content = ((ByteArrayOutputStream) out).toByteArray();
                    // 写入新内容流
                    contentStream.appendRawCommands(content);
                }
            }
        }

        // 3. 复制页面资源（字体、图片等）
        newPage.setResources(originalPage.getResources());
        return newPage;
    }
    /**
     * 处理旋转页面
     */
    private static void processRotatedPage(PDDocument newDoc,
                                           int pageIndex,
                                           PDFRenderer renderer) throws Exception {
        // 1. 渲染原页面为图片
        float scale = (float) DPI / 72f;
        BufferedImage correctedImage = renderer.renderImage(pageIndex, scale, ImageType.RGB);

        // 2. 纠正图片旋转
        //BufferedImage correctedImage = correctImageRotation(pageImage, rotation);

        // 3. 计算纠正后的页面尺寸（基于图片实际尺寸，转换为PDF 72DPI）
        float targetWidth = correctedImage.getWidth() * 72f / DPI;
        float targetHeight = correctedImage.getHeight() * 72f / DPI;
        PDRectangle targetSize = new PDRectangle(targetWidth, targetHeight);

        // 4. 新建干净页面（无任何原始属性）
        PDPage newPage = new PDPage(targetSize);
        newPage.setRotation(0); // 强制旋转为0，清空旋转属性
        newDoc.addPage(newPage);

        // 5. 压缩并插入纠正后的图片
        ByteArrayOutputStream imageStream = new ByteArrayOutputStream();
        Thumbnails.of(correctedImage)
            .outputFormat("jpg")
            .scale(1.0)
            .outputQuality(JPEG_QUALITY / 100f)
            .toOutputStream(imageStream);

        // 6. 在新页面绘制图片（确保无原始内容干扰）
        try (PDPageContentStream cs = new PDPageContentStream(
            newDoc, newPage, PDPageContentStream.AppendMode.OVERWRITE, true)) {
            // 填充白色背景，清除可能的透明区域
            cs.setNonStrokingColor(Color.WHITE);
            cs.addRect(0, 0, targetSize.getWidth(), targetSize.getHeight());
            cs.fill();

            // 插入图片（铺满新页面）
            PDImageXObject image = PDImageXObject.createFromByteArray(
                newDoc, imageStream.toByteArray(), "page_" + (pageIndex + 1));
            cs.drawImage(image, 0, 0, targetSize.getWidth(), targetSize.getHeight());
        }

        log.info("页面 {} 已处理：旋转纠正，属性清空，新尺寸：{}x{}",
            pageIndex + 1, (int) targetWidth, (int) targetHeight);
    }

    public static void main(String[] args) throws Exception{
        String inputPdf = "D:/1760602601222.pdf";         // 原PDF路径
        String outputPdf = "D:/1760602601222_output.pdf"; // 处理后PDF路径

        try (ByteArrayInputStream inputStream = fixRotatedImagePages(new FileInputStream(inputPdf));
             OutputStream outputStream = new FileOutputStream(outputPdf)) {

            // 写入输出文件
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            log.info("旋转图片页处理完成！输出路径：{}", outputPdf);
        }
//        byte[] fileBytes = FileUtil.readBytes("D:/1760602601222_output.pdf");
//
//        // 2. 将字节数组转为Base64字符串
//        String base64Str = cn.hutool.core.codec.Base64.encode(fileBytes);
//        String s = PdfUtils.writeTextToPdf(base64Str, 2, 300.1, 300.1, 300, 300, "啦啦啦");
//        // 2. 将字节数组写入文件
//        FileUtil.writeBytes(cn.hutool.core.codec.Base64.decode(s),
//            "D:/1760602601222_output-fill.pdf");

    }
}
