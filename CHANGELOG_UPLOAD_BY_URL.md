# 更新日志 - PDF通过URL上传功能

## 版本信息
- **功能**: 新增PDF通过URL上传接口
- **分支**: feat/upload-pdf-api-file-url-business-id-user-id
- **日期**: 2024

## 功能概述

新增了一个通过URL上传PDF文件的API接口，允许用户提供远程URL地址，系统自动下载PDF文件并转换为图像。该功能与现有的文件上传接口 `/api/pdf/upload` 逻辑完全一致，只是输入方式不同。

## 新增文件

### 1. DTO类
**文件**: `src/main/java/com/example/minioupload/dto/PdfUploadByUrlRequest.java`

新增请求DTO，包含以下字段：
- `fileUrl` (String, 必填): PDF文件的下载URL
- `businessId` (String, 必填): 业务ID
- `userId` (String, 必填): 用户ID
- `pages` (List<Integer>, 可选): 需要转换的页码列表
- `imageDpi` (Integer, 可选): 图像DPI分辨率
- `imageFormat` (String, 可选): 图像格式

### 2. 测试脚本
**文件**: `test-pdf-upload-by-url.sh`

完整的API测试脚本，包含：
- 全量转换测试
- 增量转换测试
- 进度查询测试
- 错误场景测试

### 3. API文档
**文件**: `API_UPLOAD_BY_URL.md`

详细的API使用文档，包含：
- 接口说明
- 请求/响应示例
- 使用流程
- 错误处理
- 常见问题

## 修改文件

### 1. Service层
**文件**: `src/main/java/com/example/minioupload/service/PdfUploadService.java`

#### 新增导入
```java
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.StandardCopyOption;
```

#### 新增方法

1. **uploadPdfFromUrlAndConvertToImages(PdfUploadByUrlRequest request)**
   - 主入口方法，处理URL上传请求
   - 功能：
     - 参数验证（URL、businessId、userId）
     - 从URL下载文件
     - 文件格式和大小验证
     - 创建任务并启动异步转换
   - 返回：PdfUploadResponse

2. **extractFilenameFromUrl(String urlString, HttpURLConnection connection)**
   - 从URL或Content-Disposition头中提取文件名
   - 支持多种文件名来源
   - 失败时生成默认文件名

3. **isPdfFile(File file)**
   - 验证文件是否为PDF格式
   - 检查文件头魔术字节（%PDF = 25 50 44 46）
   - 防止非PDF文件被处理

4. **cleanupTempFiles(File tempFile, Path tempDir)**
   - 清理临时文件和目录
   - 异常情况下确保资源释放

### 2. Controller层
**文件**: `src/main/java/com/example/minioupload/controller/PdfConversionController.java`

#### 新增端点

**POST /api/pdf/upload-by-url**
```java
@PostMapping(value = "/upload-by-url", consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<PdfUploadResponse> uploadPdfByUrl(@RequestBody PdfUploadByUrlRequest request)
```

功能：
- 接收JSON格式的请求
- 调用Service层方法处理
- 返回统一的响应格式
- 错误处理和日志记录

## 技术实现细节

### 1. URL下载机制
```java
URL url = new URL(request.getFileUrl());
HttpURLConnection connection = (HttpURLConnection) url.openConnection();
connection.setRequestMethod("GET");
connection.setConnectTimeout(30000);    // 连接超时30秒
connection.setReadTimeout(60000);       // 读取超时60秒
connection.setRequestProperty("User-Agent", "Mozilla/5.0");
```

### 2. 文件验证
- **Content-Type检查**: 警告非PDF类型但不强制拒绝
- **文件大小检查**: 下载前检查Content-Length，下载后检查实际大小
- **PDF格式验证**: 读取文件头4字节，验证魔术字节

### 3. 文件名提取策略
1. 优先从Content-Disposition头获取
2. 其次从URL路径提取
3. 最后使用时间戳生成默认名称

### 4. 异步处理
```java
CompletableFuture.runAsync(() -> 
    executePdfToImageConversion(finalTempPdfFile, finalTaskDir, conversionRequest, taskId), 
    videoCompressionExecutor);
```
复用现有的线程池执行器和转换逻辑。

## API使用示例

### 基本用法
```bash
curl -X POST http://localhost:8080/api/pdf/upload-by-url \
  -H "Content-Type: application/json" \
  -d '{
    "fileUrl": "https://example.com/document.pdf",
    "businessId": "project-123",
    "userId": "user-456"
  }'
```

### 指定转换参数
```bash
curl -X POST http://localhost:8080/api/pdf/upload-by-url \
  -H "Content-Type: application/json" \
  -d '{
    "fileUrl": "https://example.com/document.pdf",
    "businessId": "project-123",
    "userId": "user-456",
    "imageDpi": 300,
    "imageFormat": "PNG"
  }'
```

### 增量转换（只转换指定页面）
```bash
curl -X POST http://localhost:8080/api/pdf/upload-by-url \
  -H "Content-Type: application/json" \
  -d '{
    "fileUrl": "https://example.com/document.pdf",
    "businessId": "project-123",
    "userId": "user-456",
    "pages": [1, 3, 5]
  }'
```

## 响应示例

### 成功
```json
{
  "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "PROCESSING",
  "message": "PDF download successful. Converting to images in background."
}
```

### 失败
```json
{
  "status": "ERROR",
  "message": "Failed to download file from URL: Connection timeout"
}
```

## 错误处理

接口会处理以下错误情况：
1. 缺少必填参数（fileUrl、businessId、userId）
2. URL格式错误或无法访问
3. HTTP响应状态码非200
4. 文件大小超过限制
5. 下载的文件不是PDF格式
6. 增量转换时基础转换不存在
7. PDF转换服务未启用

## 与现有接口的对比

| 特性 | POST /api/pdf/upload | POST /api/pdf/upload-by-url |
|------|----------------------|------------------------------|
| 输入方式 | MultipartFile | JSON (fileUrl) |
| Content-Type | multipart/form-data | application/json |
| 文件来源 | 本地上传 | 远程URL下载 |
| 参数传递 | @RequestPart/@RequestParam | @RequestBody |
| 处理逻辑 | 完全相同 | 完全相同 |
| 异步处理 | 是 | 是 |
| MinIO存储 | 是 | 是 |

## 配置说明

使用现有配置，无需额外配置：

```yaml
pdf:
  conversion:
    enabled: true
    temp-directory: D://ruoyi/pdf-conversion
    max-file-size: 104857600  # 100MB
    image-rendering:
      dpi: 300
      format: PNG
```

## 安全建议

1. **URL白名单**: 生产环境建议限制允许的域名
2. **速率限制**: 防止滥用下载功能
3. **文件扫描**: 下载后进行病毒扫描
4. **SSL验证**: 确保HTTPS证书验证
5. **超时控制**: 防止长时间挂起

## 测试

运行测试脚本：
```bash
# 使用默认URL测试
./test-pdf-upload-by-url.sh

# 使用自定义URL测试
./test-pdf-upload-by-url.sh "https://example.com/your-document.pdf"
```

## 后续查询接口（复用现有）

1. **查询进度**: `GET /api/pdf/progress/{taskId}`
2. **获取任务详情**: `GET /api/pdf/task/{taskId}`
3. **获取图片列表**: `GET /api/pdf/images?businessId={businessId}&userId={userId}`
4. **查询任务列表**: `GET /api/pdf/tasks?businessId={businessId}&userId={userId}`

## 依赖关系

新功能复用了以下现有组件：
- PdfUploadService.executePdfToImageConversion() - PDF转换逻辑
- PdfToImageService - PDF页面渲染
- MinioStorageService - 文件存储
- PdfConversionTaskRepository - 任务数据访问
- PdfPageImageRepository - 图片数据访问
- videoCompressionExecutor - 异步执行器

## 注意事项

1. **临时文件**: 下载的文件会先保存到临时目录，转换完成后自动清理
2. **内存使用**: 大文件下载时注意内存使用
3. **网络延迟**: URL下载受网络速度影响
4. **文件验证**: 下载后会严格验证PDF格式
5. **异步处理**: 转换在后台进行，需要通过进度接口查询状态

## 相关文档

- `API_UPLOAD_BY_URL.md` - 详细API文档
- `test-pdf-upload-by-url.sh` - 测试脚本
- 现有文档: 参考 `/api/pdf/upload` 接口的使用说明

## 总结

本次更新新增了通过URL上传PDF的能力，完美复用了现有的转换和存储逻辑，提供了灵活的远程文件处理方式。接口设计简洁，错误处理完善，文档齐全，易于集成和使用。
