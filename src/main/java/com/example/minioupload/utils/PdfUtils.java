package com.example.minioupload.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;


@Slf4j
public class PdfUtils {
    /**
     * 毫米转换为点（PDF使用点作为单位，1英寸=72点，1英寸=25.4毫米）
     */
    private static final float MM_TO_POINTS = 72f / 25.4f;

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
            throw new RuntimeException("PDF文字写入失败：" + e.getMessage(), e);
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
     * 加载字体（从项目资源目录）
     */
    private static PDFont loadChineseFont(PDDocument document, String text) {
        String resourcePath = "/fonts/NotoSansSC-VariableFont_wght.ttf";
        try {
            InputStream fontStream = PdfUtils.class.getResourceAsStream(resourcePath);
            if (fontStream == null) {
                throw new RuntimeException("字体文件不存在：" + resourcePath + "，请确保字体文件存在于 resources/fonts/ 目录");
            }
            log.info("成功加载字体：{}", resourcePath);
            return PDType0Font.load(document, fontStream);
        } catch (Exception e) {
            log.error("字体加载失败：{}", e.getMessage(), e);
            throw new RuntimeException("无法加载字体文件：" + resourcePath + "，错误：" + e.getMessage(), e);
        }
    }
    public static Double pxToPt(Double px) {
        return px * 72d / 96d; // px 转 pt
    }

    /**
     * 在PDF中写入图片（支持Base64图片）
     * 坐标系统：左上角为原点，与writeTextToPdf保持一致
     *
     * @param pdfBase64 PDF文件的Base64编码
     * @param pageIndex 页码（从0开始）
     * @param x 左上角X坐标
     * @param y 左上角Y坐标
     * @param widthMm 图片宽度（毫米）
     * @param heightMm 图片高度（毫米）
     * @param imageBase64 图片的Base64编码
     * @return 写入图片后的PDF文件Base64编码
     */
    public static String writeImageToPdf(String pdfBase64, int pageIndex, float x, float y, 
                                         float widthMm, float heightMm, String imageBase64) {
        try {
            log.info("开始在PDF中写入图片（左上角为原点），页码：{}，坐标：({}, {})，尺寸：{}x{} mm", 
                    pageIndex, x, y, widthMm, heightMm);

            // 1. 解码PDF Base64
            byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);

            // 2. 加载PDF文档
            try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
                
                // 验证页码
                if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                    throw new IllegalArgumentException("页码超出范围：" + pageIndex + "，总页数：" + document.getNumberOfPages());
                }

                // 获取页面
                PDPage page = document.getPage(pageIndex);
                PDRectangle pageSize = page.getMediaBox();

                // 打印页面尺寸
                log.info("页面尺寸：宽度={}，高度={}", pageSize.getWidth(), pageSize.getHeight());

                // 转换尺寸：毫米 -> 点
                float widthPt = widthMm * MM_TO_POINTS;
                float heightPt = heightMm * MM_TO_POINTS;

                // 将坐标从左上角转换为PDF默认的左下角坐标系
                float finalY = pageSize.getHeight() - y - heightPt;
                log.info("页面高度={}，原始Y={}，转换后Y={}", pageSize.getHeight(), y, finalY);

                // 解码图片Base64
                byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
                
                // 创建PDImageXObject
                PDImageXObject pdImage = createPDImageFromBytes(document, imageBytes);

                // 在PDF上绘制图片
                try (PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    
                    contentStream.drawImage(pdImage, x, finalY, widthPt, heightPt);
                    
                    log.info("成功添加图片到第{}页，位置：({}, {})，尺寸：{}x{} mm", 
                            pageIndex + 1, x, finalY, widthMm, heightMm);
                }

                // 保存结果
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    document.save(outputStream);
                    String result = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                    log.info("PDF图片写入完成（左上角坐标系），结果数据长度：{}", result.length());
                    return result;
                }
            }

        } catch (Exception e) {
            log.error("PDF图片写入失败：{}", e.getMessage(), e);
            throw new RuntimeException("PDF图片写入失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从字节数组创建PDImageXObject
     */
    private static PDImageXObject createPDImageFromBytes(PDDocument document, byte[] imageBytes) throws IOException {
        try {
            // 尝试直接创建PDImageXObject
            return PDImageXObject.createFromByteArray(document, imageBytes, "image");
        } catch (Exception e) {
            // 如果失败，尝试通过BufferedImage转换
            log.debug("直接创建PDImageXObject失败，尝试通过BufferedImage转换");
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            BufferedImage bufferedImage = javax.imageio.ImageIO.read(bis);
            
            if (bufferedImage == null) {
                throw new IOException("无法读取图片数据");
            }
            
            // 将BufferedImage转换为PNG格式的字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedImage, "PNG", baos);
            byte[] pngBytes = baos.toByteArray();
            
            return PDImageXObject.createFromByteArray(document, pngBytes, "image");
        }
    }
}
