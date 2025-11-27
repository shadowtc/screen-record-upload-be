# 修复多签名显示问题和坐标系统问题

## 修复日期
2024年（当前）

## 问题描述

### 问题1：一页中多个签名只显示一个
在同一个PDF页面中有多个签名注解时，只能看到第一个签名（受试者签字），后续的签名（研究者签字）无法显示。

**测试场景：**
```json
{
    "businessId": "20251127181501",
    "tenantId": "1",
    "totalAnnotations": 2,
    "pageAnnotations": {
        "1": [
            {
                "id": "annotation_1",
                "contents": "受试者签字",
                "pdf": [118, 116, 185, 166]
            },
            {
                "id": "annotation_2",
                "contents": "研究者签字",
                "pdf": [595, 108, 661, 158]
            }
        ]
    }
}
```

**预期结果：** 应该看到两个签名
**实际结果：** 只能看到"受试者签字"

### 问题2：坐标系统不正确
代码中使用的是PDF标准坐标系（左下角为原点），但前端提供的坐标是基于左上角为原点的坐标系统。这导致签名位置计算错误。

**错误的坐标转换：**
```java
// 旧代码：假设PDF坐标是左下角为原点
int imageY = (int) Math.round((pagePdfHeight - (pdfY + pdfHeight)) * scaleY);
```

**正确的坐标转换：**
```java
// 新代码：PDF坐标是左上角为原点
int imageY = (int) Math.round(pdfY * scaleY);
```

## 根本原因分析

1. **坐标系统不匹配**
   - 代码假设：PDF坐标系是左下角为原点（PDF标准）
   - 实际情况：前端UI提供的坐标是左上角为原点
   - 结果：Y坐标计算错误，导致签名位置不正确

2. **图片格式处理问题**
   - 代码使用 `format.toUpperCase()` 将 "jpg" 转换为 "JPG"
   - ImageIO期望的格式名称是 "JPEG"，不是 "JPG"
   - 可能导致图片保存失败

## 修复方案

### 1. 统一坐标系统为"左上角为原点"

**修改文件：**
- `ImageAnnotationService.java`
- `PdfUploadService.java`

**修改内容：**

#### ImageAnnotationService.java（第67-74行）
```java
// 修改前：
// PDF坐标系：左下角为原点，向右为X正方向，向上为Y正方向
// 图片坐标系：左上角为原点，向右为X正方向，向下为Y正方向
// pdfY是底边坐标，需要加上pdfHeight得到顶边坐标，然后转换
int imageX = (int) Math.round(pdfX * scaleX);
int imageY = (int) Math.round((pagePdfHeight - (pdfY + pdfHeight)) * scaleY);
int rectWidth = (int) Math.round(pdfWidth * scaleX);
int rectHeight = (int) Math.round(pdfHeight * scaleY);

// 修改后：
// PDF坐标系：左上角为原点，向右为X正方向，向下为Y正方向
// 图片坐标系：左上角为原点，向右为X正方向，向下为Y正方向
// 坐标系统一致，直接按比例缩放即可
int imageX = (int) Math.round(pdfX * scaleX);
int imageY = (int) Math.round(pdfY * scaleY);
int rectWidth = (int) Math.round(pdfWidth * scaleX);
int rectHeight = (int) Math.round(pdfHeight * scaleY);
```

#### PdfUploadService.java drawAnnotationOnImage方法（第1229-1236行）
```java
// 修改前：
// PDF坐标系：左下角为原点，向右为X正方向，向上为Y正方向
// 图片坐标系：左上角为原点，向右为X正方向，向下为Y正方向
// pdfY是底边坐标，需要加上pdfHeight得到顶边坐标，然后转换
int imageX = (int) Math.round(pdfX * scaleX);
int imageY = (int) Math.round((pagePdfHeight - (pdfY + pdfHeight)) * scaleY);
int rectWidth = (int) Math.round(pdfWidth * scaleX);
int rectHeight = (int) Math.round(pdfHeight * scaleY);

// 修改后：
// PDF坐标系：左上角为原点，向右为X正方向，向下为Y正方向
// 图片坐标系：左上角为原点，向右为X正方向，向下为Y正方向
// 坐标系统一致，直接按比例缩放即可
int imageX = (int) Math.round(pdfX * scaleX);
int imageY = (int) Math.round(pdfY * scaleY);
int rectWidth = (int) Math.round(pdfWidth * scaleX);
int rectHeight = (int) Math.round(pdfHeight * scaleY);
```

### 2. 修复图片格式处理

#### PdfUploadService.java renderPageWithAnnotations方法（第1184-1186行）
```java
// 修改前：
ImageIO.write(image, format.toUpperCase(), tempFile);

// 修改后：
// 使用正确的格式名称：jpg -> JPEG, png -> PNG
String imageFormat = format.equals("jpg") ? "JPEG" : "PNG";
ImageIO.write(image, imageFormat, tempFile);
```

## 坐标系统说明

### 前端UI坐标系（左上角为原点）
```
(0,0) ─────────────> X
  │
  │    ┌─────────────┐
  │    │             │
  │    │   PDF页面   │
  │    │             │
  │    └─────────────┘
  ↓
  Y
```

### PDF坐标格式
```json
"pdf": [x1, y1, x2, y2]
```
其中：
- `x1, y1` = 左上角坐标
- `x2, y2` = 右下角坐标
- `width = x2 - x1`
- `height = y2 - y1`

### 坐标转换公式
```
图片坐标 = PDF坐标 × 缩放比例

scaleX = 图片宽度 / PDF页面宽度
scaleY = 图片高度 / PDF页面高度

imageX = pdfX × scaleX
imageY = pdfY × scaleY
imageWidth = pdfWidth × scaleX
imageHeight = pdfHeight × scaleY
```

## 测试方法

### 1. 启动应用
```bash
mvn clean package -DskipTests
java -jar target/minio-multipart-upload-1.0.0.jar
```

### 2. 运行测试脚本
```bash
./test-multi-signature.sh
```

### 3. 验证结果
测试脚本会：
1. 发送包含2个签名的注解预览请求
2. 下载生成的预览图片
3. 保存为 `page_1_multi_signature_preview.png`

打开生成的图片，应该能看到：
- ✅ 受试者签字（左侧位置，坐标 [118, 116, 185, 166]）
- ✅ 研究者签字（右侧位置，坐标 [595, 108, 661, 158]）

## 影响范围

### 修改的文件
1. `ImageAnnotationService.java` - 坐标转换逻辑
2. `PdfUploadService.java` - 多注解渲染和坐标转换逻辑

### 影响的功能
1. ✅ PDF注解预览 - `/api/pdf/preview-annotations`
2. ✅ 多签名渲染 - 同一页面多个签名
3. ✅ 坐标系统 - 统一使用左上角为原点

### 不影响的功能
1. PDF上传和转换
2. PDF页面图片生成
3. 其他PDF相关API

## 兼容性说明

### 前端要求
前端在调用注解预览API时，必须提供**左上角为原点**的坐标：
```json
{
    "pdf": [x1, y1, x2, y2]
}
```
其中 `(x1, y1)` 是签名区域的左上角，`(x2, y2)` 是右下角。

### 旧版本数据
如果之前有使用"左下角为原点"坐标系统的数据，需要进行坐标转换：
```
// 旧坐标（左下角为原点）-> 新坐标（左上角为原点）
newY1 = pageHeight - oldY2
newY2 = pageHeight - oldY1
```

## 相关文档

- [PDF注解预览API文档](./API_DOCUMENTATION.md)
- [坐标系统说明](./COORDINATE_SYSTEM.md)
- [测试脚本](./test-multi-signature.sh)

## 总结

本次修复解决了两个关键问题：
1. **多签名显示问题** - 现在可以在同一页面正确渲染多个签名
2. **坐标系统统一** - 统一使用左上角为原点，与前端UI保持一致

修复后的代码逻辑更加清晰，坐标转换更加简单，避免了复杂的Y轴翻转计算。
