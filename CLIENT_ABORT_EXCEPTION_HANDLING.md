# 客户端连接中止异常处理说明

## 问题描述

在运行视频压缩服务时，您可能会遇到以下错误：

```
org.apache.catalina.connector.ClientAbortException: java.io.IOException: 你的主机中的软件中止了一个已建立的连接。
```

## 原因分析

这个异常**不是服务器错误**，而是正常的客户端行为。它发生在以下情况：

### 1. 长时间运行的操作
- 视频压缩是一个耗时的操作，特别是对于大文件
- 客户端（浏览器/HTTP客户端）可能会有默认的超时设置
- 如果服务器在超时时间内没有返回响应，客户端会主动断开连接

### 2. 用户操作
- 用户关闭了浏览器标签页
- 用户停止/取消了请求
- 用户刷新了页面

### 3. 网络问题
- 网络连接不稳定导致中断
- 防火墙或代理服务器强制断开连接
- 路由器/交换机超时

## 已实施的解决方案

### 1. 全局异常处理器改进

在 `GlobalExceptionHandler` 中添加了专门的 `ClientAbortException` 处理器：

```java
@ExceptionHandler(ClientAbortException.class)
public void handleClientAbortException(ClientAbortException ex) {
    log.info("Client aborted connection: {}", ex.getMessage());
}
```

**关键改进：**
- 将日志级别从 `ERROR` 改为 `INFO`
- 不返回响应（因为客户端已断开，无法接收）
- 避免将正常的客户端行为记录为服务器错误

### 2. 日志输出变化

**之前：**
```
ERROR ... GlobalExceptionHandler : Unexpected error occurred: java.io.IOException: 你的主机中的软件中止了一个已建立的连接。
```

**之后：**
```
INFO ... GlobalExceptionHandler : Client aborted connection: java.io.IOException: 你的主机中的软件中止了一个已建立的连接。
```

## 最佳实践建议

### 1. 使用异步端点处理长时间操作

对于视频压缩等耗时操作，强烈建议使用异步端点：

**推荐：异步压缩（立即返回任务ID）**
```bash
# 提交异步任务
curl -X POST http://localhost:8080/api/video/compress/async \
  -H "Content-Type: application/json" \
  -d '{
    "inputFilePath": "/path/to/video.mp4",
    "preset": "balanced"
  }'

# 响应（立即返回）
{
  "jobId": "f2c2e00a-64d0-4791-b3ce-a14b563e7f23",
  "success": true,
  "status": "SUBMITTED"
}

# 轮询进度
curl http://localhost:8080/api/video/progress/f2c2e00a-64d0-4791-b3ce-a14b563e7f23
```

**不推荐：同步压缩（会阻塞直到完成）**
```bash
curl -X POST http://localhost:8080/api/video/compress \
  -H "Content-Type: application/json" \
  -d '{
    "inputFilePath": "/path/to/video.mp4",
    "preset": "balanced"
  }'
# 会阻塞直到压缩完成，容易超时
```

### 2. 调整客户端超时设置

如果必须使用同步端点，请增加客户端超时时间：

**cURL：**
```bash
curl --max-time 3600 -X POST http://localhost:8080/api/video/compress ...
```

**JavaScript (Fetch)：**
```javascript
const controller = new AbortController();
const timeout = setTimeout(() => controller.abort(), 3600000); // 1小时

fetch('http://localhost:8080/api/video/compress', {
  signal: controller.signal,
  // ...
});
```

**Java (RestTemplate)：**
```java
RestTemplate restTemplate = new RestTemplate();
HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
factory.setConnectTimeout(3600000); // 1小时
factory.setReadTimeout(3600000);
restTemplate.setRequestFactory(factory);
```

### 3. 监控和日志

现在 `ClientAbortException` 会以 `INFO` 级别记录，不会污染错误日志。但您仍然可以监控这些事件：

```bash
# 查看客户端中止事件
grep "Client aborted connection" application.log

# 统计中止次数
grep -c "Client aborted connection" application.log
```

## 服务端后台处理

**重要：** 即使客户端断开连接，服务器端的压缩任务仍会继续执行！

- 异步任务会完整执行，不受客户端断开影响
- 可以通过 `/api/video/progress/{jobId}` 随时查询任务状态
- 压缩完成后，结果文件会保存在临时目录中

## 故障排查

如果频繁出现客户端中止异常，检查以下几点：

1. **文件大小**：是否尝试压缩特别大的文件？
2. **客户端超时设置**：客户端是否有合理的超时时间？
3. **网络稳定性**：是否存在网络不稳定的情况？
4. **使用正确的端点**：是否对大文件使用了同步端点？

## 总结

- `ClientAbortException` 是正常现象，不是错误
- 已优化日志级别，避免误报错误
- 推荐使用异步端点处理耗时操作
- 服务器端任务不受客户端断开影响，会继续执行完成
