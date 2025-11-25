# 修复 PDF 上传临时文件找不到的问题

## 问题描述

在运行 `uploadPdfAndConvertToImages` 方法时，出现以下错误：

```
java.io.FileNotFoundException: C:\Users\cs520\AppData\Local\Temp\tomcat.9220.13717189454041595307\work\Tomcat\localhost\ROOT\upload_16265a1c_f973_447c_9753_a03acb0f31a7_00000001.tmp (系统找不到指定的文件。)
    at org.apache.catalina.core.ApplicationPart.write(ApplicationPart.java:119)
    at org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile.transferTo(StandardMultipartHttpServletRequest.java:254)
    at com.example.minioupload.service.PdfUploadService.executePdfToImageConversion(PdfUploadService.java:222)
```

## 根本原因

这是一个经典的异步处理 `MultipartFile` 时的问题：

1. **HTTP 请求生命周期**：当 HTTP 请求完成后，Tomcat 会自动清理 `MultipartFile` 关联的临时文件
2. **异步任务延迟**：异步任务在独立的线程池中执行，当它尝试访问 `MultipartFile` 时，底层的临时文件已经被 Tomcat 删除了
3. **transferTo() 失败**：调用 `file.transferTo(pdfFile)` 时，无法找到源临时文件，导致 `FileNotFoundException`

### 时间线
```
1. HTTP 请求到达 -> uploadPdfAndConvertToImages() 被调用
2. 创建异步任务 CompletableFuture.runAsync(...)
3. HTTP 响应返回给客户端
4. Tomcat 清理 MultipartFile 的临时文件  <-- 问题在此
5. 异步任务开始执行 executePdfToImageConversion()
6. 尝试调用 file.transferTo() -> 文件不存在 -> 异常！
```

## 解决方案

**关键原则**：在 HTTP 请求完成之前（即异步任务启动之前），必须将 `MultipartFile` 的内容保存到持久化的临时文件中。

### 修改内容

#### 1. 在同步阶段保存 MultipartFile

在 `uploadPdfAndConvertToImages()` 方法中，启动异步任务**之前**：

```java
// 在异步任务启动前，先将 MultipartFile 保存到持久化的临时文件
Path taskDir = null;
File tempPdfFile = null;
try {
    taskDir = Paths.get(properties.getTempDirectory(), taskId);
    Files.createDirectories(taskDir);
    
    tempPdfFile = taskDir.resolve(originalFilename).toFile();
    file.transferTo(tempPdfFile);  // 在 HTTP 请求期间完成
    log.debug("Saved MultipartFile to temp file: {}", tempPdfFile.getAbsolutePath());
} catch (IOException e) {
    log.error("Failed to save uploaded file to temp directory", e);
    updateTaskStatus(taskId, "FAILED", "Failed to save uploaded file: " + e.getMessage());
    return PdfUploadResponse.builder()
        .taskId(taskId)
        .status("ERROR")
        .message("Failed to save uploaded file: " + e.getMessage())
        .build();
}
```

#### 2. 修改异步方法签名

将 `executePdfToImageConversion` 的参数从 `MultipartFile` 改为 `File` 和 `Path`：

**修改前：**
```java
private void executePdfToImageConversion(MultipartFile file, PdfConversionTaskRequest request, String taskId)
```

**修改后：**
```java
private void executePdfToImageConversion(File pdfFile, Path taskDir, PdfConversionTaskRequest request, String taskId)
```

#### 3. 传递持久化的临时文件

启动异步任务时，传递已保存的临时文件：

```java
final PdfConversionTaskRequest finalRequest = request;
final File finalTempPdfFile = tempPdfFile;
final Path finalTaskDir = taskDir;
CompletableFuture.runAsync(() -> 
    executePdfToImageConversion(finalTempPdfFile, finalTaskDir, finalRequest, taskId), 
    videoCompressionExecutor);
```

#### 4. 更新异步方法内部逻辑

在异步方法中，直接使用传入的 `File` 对象，无需再调用 `transferTo()`：

**修改前：**
```java
taskDir = Paths.get(properties.getTempDirectory(), taskId);
Files.createDirectories(taskDir);

pdfFile = taskDir.resolve(file.getOriginalFilename()).toFile();
file.transferTo(pdfFile);  // 这里会失败！
```

**修改后：**
```java
// 文件已经在同步阶段保存，直接使用
String pdfObjectKey = String.format("pdf/%s/%s/%s/%s", 
    request.getUserId(), request.getBusinessId(), taskId, pdfFile.getName());
minioStorageService.uploadFile(pdfFile, pdfObjectKey);
```

## 修改后的完整流程

```
1. HTTP 请求到达 -> uploadPdfAndConvertToImages() 被调用
2. 创建临时目录
3. 将 MultipartFile 保存到持久化临时文件 (file.transferTo())  <-- 在 HTTP 请求期间完成
4. 创建异步任务，传递持久化临时文件的引用
5. HTTP 响应返回给客户端
6. Tomcat 清理 MultipartFile 的临时文件 (但我们的持久化文件不受影响)
7. 异步任务执行 executePdfToImageConversion()
8. 使用持久化的临时文件进行处理
9. 处理完成后清理持久化的临时文件
```

## 关键点总结

1. **时机关键**：必须在 HTTP 请求完成前保存 MultipartFile
2. **文件持久化**：保存到应用管理的临时目录（properties.getTempDirectory()）
3. **参数传递**：使用 `final` 变量确保 lambda 表达式可以访问
4. **清理责任**：异步任务完成后负责清理持久化的临时文件（finally 块中）
5. **错误处理**：如果保存失败，立即返回错误，不启动异步任务

## 影响范围

**修改文件：**
- `src/main/java/com/example/minioupload/service/PdfUploadService.java`

**修改方法：**
- `uploadPdfAndConvertToImages()` - 增加同步保存逻辑
- `executePdfToImageConversion()` - 修改方法签名和内部逻辑

**不影响：**
- API 接口
- 数据库结构
- 其他服务
- 客户端调用方式

## 测试验证

修复后，可以通过以下方式验证：

```bash
# 运行 PDF 上传测试脚本
./test-pdf-upload.sh

# 或使用 curl 测试
curl -X POST "http://localhost:9220/api/pdf/upload" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@test.pdf" \
  -F "businessId=test-business-123" \
  -F "userId=test-user-456"
```

应该能够成功上传并异步转换 PDF，不再出现 FileNotFoundException 错误。

## 相关问题

这个问题在其他异步文件处理场景中也很常见，比如：
- 异步视频处理
- 异步图片压缩
- 异步文件转换

**通用解决方案**：在异步任务启动前，先将 MultipartFile 保存到持久化存储（临时文件、数据库、对象存储等）。
