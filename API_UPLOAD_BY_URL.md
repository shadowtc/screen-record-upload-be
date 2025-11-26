# PDF Upload by URL API Documentation

## 概述

新增了一个通过URL上传PDF文件的接口，允许用户从远程URL下载PDF文件并自动转换为图像。该接口的处理逻辑与现有的文件上传接口 `/api/pdf/upload` 完全一致，只是输入方式不同。

## API端点

### POST /api/pdf/upload-by-url

通过URL下载PDF文件并转换为图像。

#### 请求方式
```
POST /api/pdf/upload-by-url
Content-Type: application/json
```

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| fileUrl | String | 是 | PDF文件的下载URL |
| businessId | String | 是 | 业务ID，用于标识不同的业务场景 |
| userId | String | 是 | 用户ID，标识操作用户 |
| pages | List<Integer> | 否 | 需要转换的页码列表，如果为空则转换所有页面 |
| imageDpi | Integer | 否 | 图像DPI分辨率，默认300 |
| imageFormat | String | 否 | 图像格式（PNG/JPG），默认PNG |

#### 请求示例

**全量转换（转换所有页面）**
```json
{
  "fileUrl": "https://example.com/document.pdf",
  "businessId": "project-123",
  "userId": "user-456",
  "imageDpi": 300,
  "imageFormat": "PNG"
}
```

**增量转换（只转换指定页面）**
```json
{
  "fileUrl": "https://example.com/document.pdf",
  "businessId": "project-123",
  "userId": "user-456",
  "pages": [1, 3, 5],
  "imageDpi": 300,
  "imageFormat": "PNG"
}
```

#### 响应示例

**成功响应**
```json
{
  "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "PROCESSING",
  "message": "PDF download successful. Converting to images in background."
}
```

**错误响应 - 缺少必填参数**
```json
{
  "status": "ERROR",
  "message": "Business ID is required"
}
```

**错误响应 - URL下载失败**
```json
{
  "status": "ERROR",
  "message": "Failed to download file from URL: Connection timeout"
}
```

**错误响应 - 文件不是PDF**
```json
{
  "status": "ERROR",
  "message": "Downloaded file is not a valid PDF"
}
```

**错误响应 - 文件大小超限**
```json
{
  "status": "ERROR",
  "message": "File size exceeds limit. Max: 100 MB, Actual: 125.50 MB"
}
```

## 功能特性

### 1. URL下载
- 支持HTTP和HTTPS协议
- 自动设置User-Agent头，避免某些服务器拒绝请求
- 连接超时：30秒
- 读取超时：60秒
- 支持从Content-Disposition头或URL路径中提取文件名

### 2. 文件验证
- **PDF格式验证**：检查文件头魔术字节（%PDF，十六进制：25 50 44 46）
- **文件大小验证**：检查下载前的Content-Length和下载后的文件大小
- **Content-Type检查**：提示非PDF的Content-Type（但不强制要求）

### 3. 转换模式

#### 全量转换
- 不指定`pages`参数时，转换PDF的所有页面
- 任务标记为基础转换（isBase=true）
- 适合首次上传PDF文件

#### 增量转换
- 指定`pages`参数时，只转换指定的页面
- 需要先完成全量转换
- 任务标记为增量转换（isBase=false）
- 适合更新特定页面

### 4. 异步处理
- 下载和转换在后台异步执行
- 立即返回任务ID
- 通过进度查询接口监控转换状态

### 5. MinIO存储
- PDF文件存储路径：`pdf/{userId}/{businessId}/{taskId}/{filename}`
- 图片存储路径：`pdf-images/{userId}/{businessId}/{taskId}/page_{pageNumber}.{format}`
- 自动生成presigned URL用于下载

### 6. 临时文件清理
- 转换完成后自动删除临时文件
- 异常情况下也会清理临时文件

## 使用流程

### 1. 上传PDF并启动转换
```bash
curl -X POST http://localhost:8080/api/pdf/upload-by-url \
  -H "Content-Type: application/json" \
  -d '{
    "fileUrl": "https://example.com/document.pdf",
    "businessId": "project-123",
    "userId": "user-456"
  }'
```

响应：
```json
{
  "taskId": "task-id-123",
  "status": "PROCESSING",
  "message": "PDF download successful. Converting to images in background."
}
```

### 2. 查询转换进度
```bash
curl http://localhost:8080/api/pdf/progress/task-id-123
```

响应：
```json
{
  "jobId": "task-id-123",
  "status": "COMPLETED",
  "currentPhase": "COMPLETED",
  "progressPercentage": 100,
  "totalPages": 10,
  "message": null
}
```

### 3. 获取任务详情
```bash
curl http://localhost:8080/api/pdf/task/task-id-123
```

响应：
```json
{
  "taskId": "task-id-123",
  "businessId": "project-123",
  "userId": "user-456",
  "filename": "document.pdf",
  "totalPages": 10,
  "pdfObjectKey": "pdf/user-456/project-123/task-id-123/document.pdf",
  "pdfUrl": "https://minio.example.com/...",
  "status": "COMPLETED",
  "isBase": true,
  "createdAt": "2024-01-01T12:00:00",
  "updatedAt": "2024-01-01T12:01:00"
}
```

### 4. 获取转换后的图片
```bash
curl "http://localhost:8080/api/pdf/images?businessId=project-123&userId=user-456&startPage=1&pageSize=10"
```

响应：
```json
{
  "businessId": "project-123",
  "userId": "user-456",
  "totalPages": 10,
  "startPage": 1,
  "pageSize": 10,
  "returnedPages": 10,
  "status": "SUCCESS",
  "images": [
    {
      "pageNumber": 1,
      "imageObjectKey": "pdf-images/user-456/project-123/task-id-123/page_1.png",
      "imageUrl": "https://minio.example.com/...",
      "isBase": true,
      "userId": "user-456",
      "width": 2480,
      "height": 3508,
      "fileSize": 1048576
    }
  ]
}
```

## 与现有接口的对比

### POST /api/pdf/upload（现有接口）
- **输入方式**：MultipartFile（表单上传）
- **使用场景**：用户从本地选择文件上传
- **Content-Type**：multipart/form-data
- **参数传递**：@RequestPart 和 @RequestParam

### POST /api/pdf/upload-by-url（新接口）
- **输入方式**：JSON请求体（包含URL）
- **使用场景**：从远程URL下载PDF文件
- **Content-Type**：application/json
- **参数传递**：@RequestBody

### 共同点
- 处理逻辑完全相同
- 支持全量转换和增量转换
- 异步处理
- MinIO存储
- 相同的进度查询和结果获取接口

## 错误处理

### 网络错误
- URL格式错误
- 连接超时
- 服务器拒绝连接
- HTTP错误状态码

### 文件验证错误
- 下载的文件不是PDF格式
- 文件大小超过限制
- 文件为空

### 业务逻辑错误
- 缺少必填参数
- 增量转换时基础转换不存在
- PDF转换服务未启用

## 配置说明

在 `application.yml` 中配置：

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

## 性能考虑

### 下载超时设置
- 连接超时：30秒
- 读取超时：60秒
- 适合大多数网络环境

### 文件大小限制
- 默认100MB
- 可通过配置调整
- 下载前和下载后都会检查

### 并发处理
- 使用共享的线程池执行器（videoCompressionExecutor）
- 异步下载和转换，不阻塞HTTP请求

## 安全建议

1. **URL白名单**：建议在生产环境中限制允许的域名
2. **速率限制**：防止滥用下载功能
3. **文件扫描**：下载后的文件可以进行病毒扫描
4. **SSL验证**：确保HTTPS连接的证书验证
5. **超时控制**：防止长时间挂起的请求

## 测试脚本

项目提供了完整的测试脚本：`test-pdf-upload-by-url.sh`

运行测试：
```bash
# 使用默认PDF URL
./test-pdf-upload-by-url.sh

# 使用自定义PDF URL
./test-pdf-upload-by-url.sh "https://example.com/your-document.pdf"
```

测试内容：
1. 全量转换测试
2. 进度查询测试
3. 任务详情查询
4. 图片列表获取
5. 增量转换测试
6. 错误情况测试（无效URL、缺少参数）

## 常见问题

### Q: 支持哪些URL协议？
A: 支持HTTP和HTTPS协议。

### Q: 如何处理需要认证的URL？
A: 当前版本不支持需要认证的URL。可以在代码中添加Authorization头支持。

### Q: URL下载失败会怎样？
A: 立即返回错误响应，不会创建任务记录。

### Q: 可以下载非PDF文件吗？
A: 不可以。下载后会验证文件头，非PDF文件会被拒绝。

### Q: 下载超时时间可以调整吗？
A: 可以在代码中调整`connection.setConnectTimeout()`和`connection.setReadTimeout()`的值。

### Q: 图片存储在哪里？
A: 所有文件存储在MinIO对象存储中，不占用本地磁盘空间。

### Q: 如何获取转换后的图片？
A: 使用`/api/pdf/images`接口获取，返回的`imageUrl`是presigned URL，有效期60分钟。

## 代码位置

- **DTO**: `com.example.minioupload.dto.PdfUploadByUrlRequest`
- **Service**: `com.example.minioupload.service.PdfUploadService.uploadPdfFromUrlAndConvertToImages()`
- **Controller**: `com.example.minioupload.controller.PdfConversionController.uploadPdfByUrl()`
- **测试脚本**: `test-pdf-upload-by-url.sh`

## 相关API

- `POST /api/pdf/upload` - 文件上传接口
- `GET /api/pdf/progress/{taskId}` - 进度查询
- `GET /api/pdf/task/{taskId}` - 任务详情
- `GET /api/pdf/images` - 图片列表查询
- `GET /api/pdf/tasks` - 任务列表查询
