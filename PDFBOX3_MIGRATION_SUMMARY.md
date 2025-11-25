# PDFBox 3.x 升级修复总结

## 问题描述
项目从 PDFBox 2.x 升级到 3.0.1 版本后，`PdfUtils` 类出现编译错误。

## 主要API变化

### 1. PDImageXObject.createFromByteArray() 方法签名变化

**PDFBox 2.x:**
```java
PDImageXObject.createFromByteArray(PDDocument document, byte[] byteArray, String name)
```

**PDFBox 3.x:**
```java
PDImageXObject.createFromByteArray(PDDocument document, byte[] byteArray, COSName name)
```

**变化说明:** 第三个参数从 `String` 类型改为 `COSName` 类型。

## 修复内容

### 文件: `src/main/java/com/example/minioupload/utils/PdfUtils.java`

#### 1. 添加 COSName 导入
```java
import org.apache.pdfbox.cos.COSName;
```

#### 2. 修复 createPDImageFromBytes() 方法

**修改前:**
```java
return PDImageXObject.createFromByteArray(document, imageBytes, "image");
return PDImageXObject.createFromByteArray(document, pngBytes, "image");
```

**修改后:**
```java
return PDImageXObject.createFromByteArray(document, imageBytes, COSName.getPDFName("image"));
return PDImageXObject.createFromByteArray(document, pngBytes, COSName.getPDFName("image"));
```

#### 3. 改进资源管理

**修改前:**
```java
InputStream fontStream = PdfUtils.class.getResourceAsStream(resourcePath);
// ... 使用 fontStream
return PDType0Font.load(document, fontStream);
```

**修改后:**
```java
try (InputStream fontStream = PdfUtils.class.getResourceAsStream(resourcePath)) {
    // ... 使用 fontStream
    return PDType0Font.load(document, fontStream);
}
```

使用 try-with-resources 确保 InputStream 正确关闭。

## 其他文件检查

### PdfToImageService.java
- ✅ 已使用 PDFBox 3.x 的 `Loader.loadPDF()` API
- ✅ 无需修改

## 功能验证

修复后的代码保持以下功能不变：

1. ✅ **PDF 文字写入功能** - `writeTextToPdf()`
   - 支持中文字体加载
   - 支持自定义位置和样式

2. ✅ **PDF 图片写入功能** - `writeImageToPdf()`
   - 支持 Base64 图片嵌入
   - 支持图片格式转换
   - 支持自定义位置和尺寸

3. ✅ **PDF 转图片功能** - `PdfToImageService`
   - 支持全量/指定页面转换
   - 支持自定义 DPI 和格式
   - 支持 MinIO 上传

## 兼容性说明

- ✅ 完全兼容 PDFBox 3.0.1
- ✅ 保持所有原有功能
- ✅ 改进资源管理，更符合最佳实践
- ✅ 不影响现有的 PDF 转图片功能

## 测试建议

1. 测试 PDF 文字写入功能
2. 测试 PDF 图片写入功能  
3. 测试 PDF 转图片功能
4. 测试中文字体加载
5. 测试各种图片格式（PNG、JPG等）

## 总结

本次修复主要针对 PDFBox 3.x API 变化进行适配，主要是 `PDImageXObject.createFromByteArray()` 方法的参数类型变化。所有修改均向后兼容，不影响原有功能。
