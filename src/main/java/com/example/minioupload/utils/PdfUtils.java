package com.example.minioupload.utils;

import com.example.minioupload.model.enums.BasePointEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * @Description pdf工具类
 * @Author tongwl
 * @Date 2025/9/19 13:28
 * @Version 1.0
 **/
@Slf4j
public class PdfUtils {

    /**
     * 在PDF中写入文字
     *
     * @param pdfData PDF文件的base64编码数据
     * @param pageIndex 页码（从0开始）
     * @param x X坐标
     * @param y Y坐标
     * @param width 文字宽度
     * @param height 文字高度
     * @param text 文字内容
     * @param basePoint 坐标原点（LU/LD/RU/RD）
     * @return 写入文字后的PDF文件base64编码数据
     */
    public static String writeTextToPdf(String pdfData, int pageIndex, double x, double y, float width, float height, String text, String basePoint) {
        return writeTextToPdf(pdfData, pageIndex, x, y, width, height, text, BasePointEnum.fromCode(basePoint));
    }

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
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {

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

                    PDFont font = loadChineseFont(document);

                    log.info("使用字体：{}，字号：{}", font.getName(), fontSize);
                    contentStream.setFont(font, fontSize);

                    // 设置文字颜色为黑色
                    contentStream.setNonStrokingColor(0f, 0f, 0f);

                    // 开始写入文本
                    contentStream.beginText();

                    double[] convertedCoords = convertCoordinates(x, y, width, height, pageWidth, pageHeight, BasePointEnum.LU);
                    double fixedX = convertedCoords[0];
                    // 设置文本位置（左上角为原点），注意 setTextMatrix 需要 float
                    contentStream.setTextMatrix(new Matrix(1, 0, 0, 1, (float) fixedX, (float) fixedY));
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
        return null;
    }
    /**
     * 在PDF中写入文字（使用枚举）
     */
    public static String writeTextToPdf(String pdfData, int pageIndex, double x, double y, float width, float height, String text, BasePointEnum basePoint) {
        try {
            log.info("开始在PDF中写入文字，坐标原点：{}，坐标：({}, {})，尺寸：{}x{}，内容：{}", basePoint.getDescription(), x, y, width, height, text);

            // 解码base64 PDF数据
            byte[] pdfBytes = Base64.getDecoder().decode(pdfData);

            // 加载PDF文档
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {

                // 获取指定页面
                PDPage page = document.getPage(pageIndex);

                // 打印页面宽高
                double pageWidth = page.getMediaBox().getWidth();
                double pageHeight = page.getMediaBox().getHeight();
                log.info("页面尺寸：宽度={}，高度={}", pageWidth, pageHeight);

                // 根据坐标原点转换为PDF默认的左下角坐标系
                double[] convertedCoords = convertCoordinates(x, y, width, height, pageWidth, pageHeight, basePoint);
                double fixedX = convertedCoords[0];
                double fixedY = convertedCoords[1];
                log.info("页面尺寸：{}x{}，原始坐标：({}, {})，转换后坐标：({}, {})，坐标原点：{}", 
                        pageWidth, pageHeight, x, y, fixedX, fixedY, basePoint.getDescription());

                // 创建内容流
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true)) {

                    // 设置字体和字号
                    float fontSize = calculateFontSize(width, height, text);
                    log.info("准备写入的文本：[{}]，长度：{}，Unicode码点：{}",
                        text, text.length(), text.codePoints().mapToObj(Integer::toHexString).toArray());

                    PDFont font = loadChineseFont(document);

                    log.info("使用字体：{}，字号：{}", font.getName(), fontSize);
                    contentStream.setFont(font, fontSize);

                    // 设置文字颜色为黑色
                    contentStream.setNonStrokingColor(0f, 0f, 0f);

                    // 开始写入文本
                    contentStream.beginText();

                    // 设置文本位置，使用 Matrix 对象
                    // 注意：PDF文本基线在字符底部，所以Y坐标指向文本底部位置
                    contentStream.setTextMatrix(new Matrix(1, 0, 0, 1, (float) fixedX, (float) fixedY));
                    //写入文字
                    contentStream.showText(text);
                    // 结束文本
                    contentStream.endText();
                }

                // 保存结果到字节数组
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    document.save(outputStream);
                    String result = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                    log.info("PDF文字写入完成，坐标原点：{}，结果数据长度：{}", basePoint.getDescription(), result.length());
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
    private static PDFont loadChineseFont(PDDocument document) {
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
     *
     * @param pdfBase64 PDF文件的Base64编码
     * @param pageIndex 页码（从0开始）
     * @param x X坐标
     * @param y Y坐标
     * @param widthMm 图片宽度
     * @param heightMm 图片高度
     * @param imageBase64 图片的Base64编码
     * @param basePoint 坐标原点（LU/LD/RU/RD）
     * @return 写入图片后的PDF文件Base64编码
     */
    public static String writeImageToPdf(String pdfBase64, int pageIndex, float x, float y, 
                                         float widthMm, float heightMm, String imageBase64, String basePoint) {
        return writeImageToPdf(pdfBase64, pageIndex, x, y, widthMm, heightMm, imageBase64, BasePointEnum.fromCode(basePoint));
    }

    /**
     * 在PDF中写入图片（使用枚举）
     */
    public static String writeImageToPdf(String pdfBase64, int pageIndex, float x, float y, 
                                         float widthMm, float heightMm, String imageBase64, BasePointEnum basePoint) {
        try {
            log.info("开始在PDF中写入图片，坐标原点：{}，页码：{}，坐标：({}, {})，尺寸：{}x{}", 
                    basePoint.getDescription(), pageIndex, x, y, widthMm, heightMm);

            // 1. 解码PDF Base64
            byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);

            // 2. 加载PDF文档
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                
                // 验证页码
                if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                    throw new IllegalArgumentException("页码超出范围：" + pageIndex + "，总页数：" + document.getNumberOfPages());
                }

                // 获取页面
                PDPage page = document.getPage(pageIndex);
                PDRectangle pageSize = page.getMediaBox();

                // 根据坐标原点转换为PDF默认的左下角坐标系
                double[] convertedCoords = convertCoordinates(x, y, widthMm, heightMm, 
                        pageSize.getWidth(), pageSize.getHeight(), basePoint);
                float finalX = (float) convertedCoords[0];
                float finalY = (float) convertedCoords[1];
                log.info("页面尺寸：{}x{}，原始坐标：({}, {})，转换后坐标：({}, {})，坐标原点：{}", 
                        pageSize.getWidth(), pageSize.getHeight(), x, y, finalX, finalY, basePoint.getDescription());

                // 解码图片Base64（移除可能的data URI前缀）
                String cleanBase64 = imageBase64;
                if (imageBase64.contains(",")) {
                    // 移除 data:image/xxx;base64, 前缀
                    cleanBase64 = imageBase64.substring(imageBase64.indexOf(",") + 1);
                    log.info("检测到data URI前缀，已移除");
                }
                byte[] imageBytes = Base64.getDecoder().decode(cleanBase64);
                
                // 创建PDImageXObject
                PDImageXObject pdImage = createPDImageFromBytes(document, imageBytes);

                // 在PDF上绘制图片
                try (PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    
                    contentStream.drawImage(pdImage, finalX, finalY, widthMm, heightMm);
                    
                    log.info("成功添加图片到第{}页，位置：({}, {})，尺寸：{}x{}，坐标原点：{}", 
                            pageIndex + 1, finalX, finalY, widthMm, heightMm, basePoint.getDescription());
                }

                // 保存结果
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    document.save(outputStream);
                    String result = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                    log.info("PDF图片写入完成，坐标原点：{}，结果数据长度：{}", basePoint.getDescription(), result.length());
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

    /**
     * 坐标转换：将不同原点的坐标转换为PDF默认的左下角坐标系
     *
     * @param x 原始X坐标
     * @param y 原始Y坐标
     * @param width 元素宽度
     * @param height 元素高度
     * @param pageWidth 页面宽度
     * @param pageHeight 页面高度
     * @param basePoint 坐标原点
     * @return [转换后的X, 转换后的Y]
     */
    private static double[] convertCoordinates(double x, double y, double width, double height,
                                               double pageWidth, double pageHeight, BasePointEnum basePoint) {
        double convertedX = x;
        double convertedY = y;

        switch (basePoint) {
            case LU: // 左上角
                convertedX = x;
                convertedY = pageHeight - y - height;
                break;
            case LD: // 左下角（PDF默认）
                convertedX = x;
                convertedY = y;
                break;
            case RU: // 右上角
                convertedX = pageWidth - x - width;
                convertedY = pageHeight - y - height;
                break;
            case RD: // 右下角
                convertedX = pageWidth - x - width;
                convertedY = y;
                break;
        }

        return new double[]{convertedX, convertedY};
    }
}
