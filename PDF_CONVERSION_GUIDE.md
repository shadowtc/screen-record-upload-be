# PDF转换服务使用指南

## 功能概述

PDF转换服务提供了强大的文件转PDF功能，支持多种格式的文件转换，并能自动将PDF页面渲染为高清图片。

### 主要特性

1. **多格式支持** - 支持Office文档、文本、图片等多种格式
2. **异步处理** - 后台异步转换，提高系统响应速度
3. **PDF转图片** - 自动将PDF每一页渲染为高清图片
4. **高清晰度** - 支持300 DPI的高质量图片输出
5. **友好提示** - 完善的错误处理和进度反馈
6. **本地存储** - 转换后的PDF和图片保存在本地文件系统

### 支持的文件格式

| 类别 | 格式 | 说明 |
|------|------|------|
| 文档 | doc, docx | Microsoft Word文档 |
| 文档 | txt | 纯文本文件 |
| 表格 | xls, xlsx | Microsoft Excel表格 |
| 演示 | ppt, pptx | Microsoft PowerPoint演示文稿 |
| 图片 | jpg, jpeg, png, bmp, gif | 常见图片格式 |
| PDF | pdf | PDF文档（仅转图片） |

## API接口

### 1. 获取支持的格式

```bash
GET /api/pdf/formats
```

**响应示例：**
```json
{
  "supportedFormats": [
    "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "txt", "jpg", "jpeg", "png", "bmp", "gif", "pdf"
  ],
  "count": 13,
  "categories": {
    "documents": ["doc", "docx", "txt", "pdf"],
    "spreadsheets": ["xls", "xlsx"],
    "presentations": ["ppt", "pptx"],
    "images": ["jpg", "jpeg", "png", "bmp", "gif"]
  }
}
```

### 2. 转换文件为PDF

```bash
POST /api/pdf/convert
Content-Type: multipart/form-data
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 要转换的文件 |
| request | JSON | 否 | 转换配置（见下方） |

**转换配置（request）：**

```json
{
  "outputFilename": "output.pdf",     // 输出PDF文件名（可选）
  "convertToImages": true,             // 是否转换为图片（默认true）
  "imageDpi": 300,                     // 图片DPI（默认300）
  "imageFormat": "PNG"                 // 图片格式（默认PNG）
}
```

**响应示例：**
```json
{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "PROCESSING",
  "message": "Conversion started. Use jobId to check progress."
}
```

**curl示例：**
```bash
curl -X POST "http://localhost:8080/api/pdf/convert" \
  -F "file=@document.docx" \
  -F 'request={"convertToImages":true,"imageDpi":300};type=application/json'
```

### 3. 查询转换进度

```bash
GET /api/pdf/progress/{jobId}
```

**响应示例：**
```json
{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "PROCESSING",
  "currentPhase": "Converting PDF pages to images",
  "totalPages": 5,
  "processedPages": 3,
  "progressPercentage": 70,
  "message": "Converting page 3 of 5",
  "startTime": 1700000000000,
  "elapsedTimeMs": 5230
}
```

**状态说明：**

| 状态 | 说明 |
|------|------|
| SUBMITTED | 任务已提交 |
| PROCESSING | 正在处理 |
| COMPLETED | 转换完成 |
| FAILED | 转换失败 |
| NOT_FOUND | 任务不存在 |

**当前阶段（currentPhase）：**

- `Initializing` - 初始化中
- `Converting to PDF` - 正在转换为PDF
- `Converting file format` - 格式转换中
- `PDF conversion completed` - PDF转换完成
- `Converting PDF pages to images` - 渲染图片中
- `Image conversion completed` - 图片转换完成
- `Completed` - 全部完成

### 4. 获取转换结果

```bash
GET /api/pdf/result/{jobId}
```

**响应示例：**
```json
{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "COMPLETED",
  "message": "Conversion completed successfully",
  "pdfFilePath": "D://ruoyi/pdf-conversion/123e4567.../output.pdf",
  "pdfFileSize": 245760,
  "pageCount": 5,
  "imageFilePaths": [
    "D://ruoyi/pdf-conversion/123e4567.../images/page_0001.png",
    "D://ruoyi/pdf-conversion/123e4567.../images/page_0002.png",
    "D://ruoyi/pdf-conversion/123e4567.../images/page_0003.png",
    "D://ruoyi/pdf-conversion/123e4567.../images/page_0004.png",
    "D://ruoyi/pdf-conversion/123e4567.../images/page_0005.png"
  ],
  "processingTimeMs": 8450
}
```

## 使用流程

### 完整示例

```bash
# 1. 提交转换任务
RESPONSE=$(curl -s -X POST "http://localhost:8080/api/pdf/convert" \
  -F "file=@document.docx" \
  -F 'request={"convertToImages":true,"imageDpi":300};type=application/json')

# 2. 获取JobID
JOB_ID=$(echo $RESPONSE | jq -r '.jobId')
echo "Job ID: $JOB_ID"

# 3. 轮询查询进度
while true; do
  PROGRESS=$(curl -s "http://localhost:8080/api/pdf/progress/$JOB_ID")
  STATUS=$(echo $PROGRESS | jq -r '.status')
  
  echo "Status: $STATUS"
  
  if [ "$STATUS" == "COMPLETED" ]; then
    break
  elif [ "$STATUS" == "FAILED" ]; then
    echo "Conversion failed!"
    exit 1
  fi
  
  sleep 2
done

# 4. 获取结果
curl -s "http://localhost:8080/api/pdf/result/$JOB_ID" | jq '.'
```

### JavaScript示例

```javascript
// 1. 提交转换任务
const formData = new FormData();
formData.append('file', fileInput.files[0]);
formData.append('request', new Blob([JSON.stringify({
  convertToImages: true,
  imageDpi: 300,
  imageFormat: 'PNG'
})], { type: 'application/json' }));

const response = await fetch('http://localhost:8080/api/pdf/convert', {
  method: 'POST',
  body: formData
});

const result = await response.json();
const jobId = result.jobId;

// 2. 轮询查询进度
const checkProgress = async () => {
  const progressResponse = await fetch(
    `http://localhost:8080/api/pdf/progress/${jobId}`
  );
  const progress = await progressResponse.json();
  
  console.log(`Progress: ${progress.progressPercentage}%`);
  console.log(`Phase: ${progress.currentPhase}`);
  
  if (progress.status === 'COMPLETED') {
    // 3. 获取结果
    const resultResponse = await fetch(
      `http://localhost:8080/api/pdf/result/${jobId}`
    );
    const finalResult = await resultResponse.json();
    
    console.log('PDF Path:', finalResult.pdfFilePath);
    console.log('Images:', finalResult.imageFilePaths);
  } else if (progress.status === 'FAILED') {
    console.error('Conversion failed:', progress.errorMessage);
  } else {
    setTimeout(checkProgress, 2000);
  }
};

checkProgress();
```

## 配置说明

### application.yml配置

```yaml
pdf:
  conversion:
    # 是否启用PDF转换功能
    enabled: true
    
    # 临时文件存储目录
    temp-directory: D://ruoyi/pdf-conversion
    
    # 最大并发任务数
    max-concurrent-jobs: 3
    
    # 文件大小限制（100MB）
    max-file-size: 104857600
    
    # 任务超时时间（秒）
    timeout-seconds: 300
    
    # 图片渲染配置
    image-rendering:
      # DPI设置（影响清晰度）
      # 72: 屏幕显示
      # 150: 标准打印
      # 300: 高质量打印（推荐）
      # 600: 专业级打印
      dpi: 300
      
      # 图片格式（PNG/JPG/BMP）
      format: PNG
      
      # 图片质量（0.0-1.0）
      quality: 1.0
      
      # 启用抗锯齿（提高清晰度）
      antialiasing: true
      
      # 渲染文本
      render-text: true
      
      # 渲染图片
      render-images: true
```

### 环境变量配置

```bash
# PDF转换配置
PDF_TEMP_DIR=/tmp/pdf-conversion
PDF_MAX_JOBS=3
PDF_MAX_SIZE=104857600
PDF_TIMEOUT=300

# 图片渲染配置
PDF_IMAGE_DPI=300
PDF_IMAGE_FORMAT=PNG
PDF_IMAGE_QUALITY=1.0
PDF_IMAGE_ANTIALIASING=true
PDF_RENDER_TEXT=true
PDF_RENDER_IMAGES=true
```

## 性能优化

### 1. 并发控制

系统通过`max-concurrent-jobs`参数限制同时处理的任务数，避免资源耗尽：

- **2核4GB**: 建议设置为 2-3
- **4核8GB**: 建议设置为 4-6
- **8核16GB**: 建议设置为 8-10

### 2. 异步处理

所有转换操作都在后台异步执行，不阻塞API响应：

- 转换任务提交后立即返回jobId
- 客户端通过轮询获取进度
- PDF转图片异步执行，进一步提高效率

### 3. 资源管理

- 使用现有的`videoCompressionExecutor`线程池
- 自动清理完成的任务（可配置）
- 临时文件统一管理

### 4. 图片质量与性能平衡

| DPI | 文件大小 | 处理时间 | 适用场景 |
|-----|---------|---------|---------|
| 72  | 小      | 快      | 屏幕预览 |
| 150 | 中      | 中      | 普通打印 |
| 300 | 大      | 慢      | 高质量打印（推荐） |
| 600 | 很大    | 很慢    | 专业打印 |

## 错误处理

### 常见错误及解决方案

1. **"PDF conversion service is disabled"**
   - 原因：服务未启用
   - 解决：设置`pdf.conversion.enabled=true`

2. **"Maximum concurrent jobs limit reached"**
   - 原因：并发任务数达到上限
   - 解决：等待其他任务完成或增加`max-concurrent-jobs`

3. **"Unsupported file format"**
   - 原因：文件格式不支持
   - 解决：使用支持的格式或转换文件格式

4. **"File size exceeds limit"**
   - 原因：文件太大
   - 解决：压缩文件或增加`max-file-size`限制

5. **"Conversion failed"**
   - 原因：文件损坏、格式错误等
   - 解决：检查原始文件是否正常

## 测试脚本

使用提供的测试脚本快速验证功能：

```bash
# 运行测试脚本
./test-pdf-conversion.sh
```

测试脚本会：
1. 查询支持的格式
2. 创建测试文本文件
3. 提交转换任务
4. 轮询查询进度
5. 获取并验证结果
6. 检查生成的PDF和图片文件

## 文件存储结构

```
pdf-conversion/
├── {jobId}/
│   ├── input_原始文件名
│   ├── 输出文件.pdf
│   └── images/
│       ├── page_0001.png
│       ├── page_0002.png
│       ├── page_0003.png
│       └── ...
```

## 最佳实践

1. **文件大小控制**
   - 建议单个文件不超过100MB
   - 大文件考虑分批处理

2. **DPI选择**
   - 屏幕显示：72-150 DPI
   - 普通打印：150-200 DPI
   - 高质量打印：300 DPI
   - 专业用途：600 DPI

3. **格式选择**
   - PNG：最佳质量，支持透明（推荐）
   - JPG：较小文件，适合照片
   - BMP：无损但文件大

4. **进度查询**
   - 建议每2-3秒查询一次
   - 避免过于频繁的请求

5. **错误重试**
   - 转换失败后可以重新提交
   - 检查原始文件是否正常

## 技术架构

### 依赖库

- **Apache POI**: Office文档处理
- **Apache PDFBox**: PDF操作和渲染
- **iText**: PDF生成
- **Commons IO**: 文件操作

### 核心组件

1. **PdfConversionService**: 主要转换逻辑
2. **PdfToImageService**: PDF转图片服务
3. **PdfConversionController**: REST API控制器
4. **PdfConversionProperties**: 配置管理

### 异步处理

使用Spring的异步机制和CompletableFuture实现：
- 共享`videoCompressionExecutor`线程池
- 独立的进度跟踪Map
- 并发控制和任务队列

## 监控与日志

### 日志级别

```yaml
logging:
  level:
    com.example.minioupload.service.PdfConversionService: DEBUG
    com.example.minioupload.service.PdfToImageService: DEBUG
```

### 关键日志

- 任务提交
- 转换开始/完成
- 每页渲染进度
- 错误和异常
- 性能指标

## 常见问题

**Q: 支持批量转换吗？**
A: 当前版本支持单文件转换，批量转换可以通过多次调用API实现。

**Q: 转换后的文件保存多久？**
A: 默认永久保存，可以根据需要配置自动清理策略。

**Q: 可以自定义输出格式吗？**
A: 目前输出格式固定为PDF，图片格式支持PNG、JPG、BMP。

**Q: 转换失败后文件会被删除吗？**
A: 不会，失败的任务文件会保留以便调试。

**Q: 并发限制如何工作？**
A: 超过`max-concurrent-jobs`的请求会立即返回错误，不会排队。

## 更新日志

### v1.0.0 (当前版本)
- ✅ 支持多种文件格式转PDF
- ✅ 异步转换处理
- ✅ PDF页面转高清图片
- ✅ 进度实时查询
- ✅ 完善的错误处理
- ✅ 本地文件存储
