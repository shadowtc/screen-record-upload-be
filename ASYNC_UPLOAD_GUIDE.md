# 异步分片上传API使用指南

## 概述

异步分片上传API提供了一种简化的文件上传方式，无需前端手动处理文件分片。前端只需上传整个文件，后端会自动在后台进行分片上传到MinIO。

### 与传统分片上传的对比

| 特性 | 传统分片上传 | 异步分片上传 |
|------|------------|------------|
| **前端复杂度** | 高 - 需要手动分片、计算ETag | 低 - 只需上传文件 |
| **上传方式** | 前端直接上传到MinIO | 后端自动上传到MinIO |
| **进度跟踪** | 前端自行管理 | 后端提供实时进度 |
| **错误处理** | 前端需处理每个分片的失败 | 后端统一处理，自动清理 |
| **适用场景** | 需要精细控制、断点续传 | 简化开发、快速集成 |

## API端点

### 1. 提交异步上传任务

提交一个文件进行异步分片上传。

**端点:** `POST /api/uploads/async`

**请求格式:** `multipart/form-data`

**请求参数:**
- `file` (required): 要上传的文件
- `chunkSize` (optional): 分片大小（字节），默认8MB
  - 最小值: 5MB (5242880 字节)
  - 最大值: 5GB (5368709120 字节)

**响应示例:**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "SUBMITTED",
  "progress": 0.0,
  "message": "Upload submitted, waiting to start...",
  "uploadedParts": 0,
  "totalParts": 15,
  "fileName": "video.mp4",
  "fileSize": 125829120,
  "startTime": "2024-11-19T10:30:00Z",
  "endTime": null,
  "uploadResponse": null
}
```

**cURL示例:**
```bash
curl -X POST http://localhost:8080/api/uploads/async \
  -F "file=@video.mp4" \
  -F "chunkSize=8388608"
```

**JavaScript示例:**
```javascript
async function submitAsyncUpload(file, chunkSize = 8388608) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('chunkSize', chunkSize);

  const response = await fetch('http://localhost:8080/api/uploads/async', {
    method: 'POST',
    body: formData
  });

  return await response.json();
}

// 使用示例
const fileInput = document.getElementById('fileInput');
const file = fileInput.files[0];
const result = await submitAsyncUpload(file);
console.log('Job ID:', result.jobId);
```

### 2. 查询上传进度

查询异步上传任务的实时进度。

**端点:** `GET /api/uploads/async/{jobId}/progress`

**路径参数:**
- `jobId`: 上传任务的唯一标识符（从提交接口获得）

**响应示例 - 上传中:**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "UPLOADING",
  "progress": 45.5,
  "message": "Uploading part 7/15...",
  "uploadedParts": 7,
  "totalParts": 15,
  "fileName": "video.mp4",
  "fileSize": 125829120,
  "startTime": "2024-11-19T10:30:00Z",
  "endTime": null,
  "uploadResponse": null
}
```

**响应示例 - 上传完成:**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "COMPLETED",
  "progress": 100.0,
  "message": "Upload completed successfully",
  "uploadedParts": 15,
  "totalParts": 15,
  "fileName": "video.mp4",
  "fileSize": 125829120,
  "startTime": "2024-11-19T10:30:00Z",
  "endTime": "2024-11-19T10:32:30Z",
  "uploadResponse": {
    "id": 123,
    "filename": "video.mp4",
    "size": 125829120,
    "objectKey": "uploads/uuid/video.mp4",
    "status": "COMPLETED",
    "downloadUrl": "https://minio:9000/...",
    "createdAt": "2024-11-19T10:32:30Z"
  }
}
```

**响应示例 - 上传失败:**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "FAILED",
  "progress": -1.0,
  "message": "Upload failed: Connection timeout",
  "uploadedParts": 5,
  "totalParts": 15,
  "fileName": "video.mp4",
  "fileSize": 125829120,
  "startTime": "2024-11-19T10:30:00Z",
  "endTime": "2024-11-19T10:31:15Z",
  "uploadResponse": null
}
```

**cURL示例:**
```bash
JOB_ID="a1b2c3d4-e5f6-7890-abcd-ef1234567890"
curl http://localhost:8080/api/uploads/async/$JOB_ID/progress
```

**JavaScript示例:**
```javascript
async function getUploadProgress(jobId) {
  const response = await fetch(
    `http://localhost:8080/api/uploads/async/${jobId}/progress`
  );
  
  if (response.status === 404) {
    throw new Error('Upload job not found');
  }
  
  return await response.json();
}

// 轮询进度示例
async function pollUploadProgress(jobId, intervalMs = 2000) {
  return new Promise((resolve, reject) => {
    const interval = setInterval(async () => {
      try {
        const progress = await getUploadProgress(jobId);
        
        // 更新UI
        console.log(`Progress: ${progress.progress}% - ${progress.message}`);
        updateProgressBar(progress.progress);
        
        if (progress.status === 'COMPLETED') {
          clearInterval(interval);
          resolve(progress.uploadResponse);
        } else if (progress.status === 'FAILED') {
          clearInterval(interval);
          reject(new Error(progress.message));
        }
      } catch (error) {
        clearInterval(interval);
        reject(error);
      }
    }, intervalMs);
  });
}

// 使用示例
try {
  const uploadResult = await pollUploadProgress(jobId);
  console.log('Upload completed:', uploadResult);
  console.log('Download URL:', uploadResult.downloadUrl);
} catch (error) {
  console.error('Upload failed:', error);
}
```

## 状态说明

异步上传任务有以下几种状态：

| 状态 | 说明 | progress值 |
|------|------|-----------|
| **SUBMITTED** | 任务已提交，等待开始 | 0.0 |
| **UPLOADING** | 正在上传中 | 0.0 - 100.0 |
| **COMPLETED** | 上传完成 | 100.0 |
| **FAILED** | 上传失败 | -1.0 |

## 进度计算

上传过程的进度分为以下几个阶段：

1. **保存文件到临时目录** (0% - 5%)
2. **初始化MinIO上传** (5% - 10%)
3. **上传分片到MinIO** (10% - 15%)
4. **逐个上传分片** (15% - 90%) - 根据已上传分片数线性计算
5. **完成上传** (90% - 95%)
6. **保存元数据到数据库** (95% - 100%)

## 完整示例：React组件

```jsx
import React, { useState, useEffect } from 'react';

function AsyncUploadComponent() {
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const handleFileChange = (e) => {
    setFile(e.target.files[0]);
    setProgress(null);
    setResult(null);
    setError(null);
  };

  const handleUpload = async () => {
    if (!file) return;

    setUploading(true);
    setError(null);

    try {
      // 提交上传任务
      const formData = new FormData();
      formData.append('file', file);
      formData.append('chunkSize', 8388608); // 8MB

      const response = await fetch('http://localhost:8080/api/uploads/async', {
        method: 'POST',
        body: formData
      });

      const initialProgress = await response.json();
      setProgress(initialProgress);

      // 轮询进度
      const jobId = initialProgress.jobId;
      const pollInterval = setInterval(async () => {
        try {
          const progressResponse = await fetch(
            `http://localhost:8080/api/uploads/async/${jobId}/progress`
          );
          const currentProgress = await progressResponse.json();
          setProgress(currentProgress);

          if (currentProgress.status === 'COMPLETED') {
            clearInterval(pollInterval);
            setResult(currentProgress.uploadResponse);
            setUploading(false);
          } else if (currentProgress.status === 'FAILED') {
            clearInterval(pollInterval);
            setError(currentProgress.message);
            setUploading(false);
          }
        } catch (err) {
          clearInterval(pollInterval);
          setError(err.message);
          setUploading(false);
        }
      }, 2000);

    } catch (err) {
      setError(err.message);
      setUploading(false);
    }
  };

  return (
    <div>
      <h2>异步文件上传</h2>
      
      <input 
        type="file" 
        onChange={handleFileChange}
        accept="video/*"
        disabled={uploading}
      />
      
      <button 
        onClick={handleUpload}
        disabled={!file || uploading}
      >
        {uploading ? '上传中...' : '开始上传'}
      </button>

      {progress && (
        <div>
          <h3>上传进度</h3>
          <p>状态: {progress.status}</p>
          <p>进度: {progress.progress.toFixed(2)}%</p>
          <p>消息: {progress.message}</p>
          <p>分片: {progress.uploadedParts}/{progress.totalParts}</p>
          
          <div style={{ 
            width: '100%', 
            backgroundColor: '#e0e0e0',
            borderRadius: '4px',
            overflow: 'hidden'
          }}>
            <div style={{
              width: `${progress.progress}%`,
              height: '24px',
              backgroundColor: progress.status === 'FAILED' ? '#f44336' : '#4caf50',
              transition: 'width 0.3s ease'
            }} />
          </div>
        </div>
      )}

      {result && (
        <div>
          <h3>上传成功！</h3>
          <p>文件ID: {result.id}</p>
          <p>文件名: {result.filename}</p>
          <p>大小: {(result.size / 1024 / 1024).toFixed(2)} MB</p>
          <a href={result.downloadUrl} target="_blank" rel="noopener noreferrer">
            下载文件
          </a>
        </div>
      )}

      {error && (
        <div style={{ color: 'red' }}>
          <h3>上传失败</h3>
          <p>{error}</p>
        </div>
      )}
    </div>
  );
}

export default AsyncUploadComponent;
```

## 注意事项

1. **文件类型限制**: 目前仅支持视频文件（content-type以`video/`开头）

2. **文件大小限制**: 受配置文件中`upload.max-file-size`限制，默认可能是500MB或更大

3. **分片大小要求**: 
   - 最小: 5MB (S3协议要求)
   - 最大: 5GB (S3协议限制)
   - 推荐: 8MB - 64MB

4. **进度缓存**: 上传进度信息会在任务完成或失败后保留60分钟，之后自动清理

5. **并发限制**: 受线程池配置限制，默认最多4个并发上传任务

6. **超时处理**: 长时间无响应的任务可能会超时失败，建议监控进度变化

7. **错误恢复**: 如果上传失败，系统会自动清理MinIO中已上传的分片

## 故障排查

### 问题: 提交上传后长时间停留在SUBMITTED状态

**可能原因:**
- 线程池已满，任务在队列中等待
- 系统资源不足

**解决方案:**
- 检查服务器日志
- 增加线程池配置
- 等待当前任务完成

### 问题: 上传进度长时间不更新

**可能原因:**
- MinIO连接问题
- 网络带宽限制
- 大文件上传耗时较长

**解决方案:**
- 检查MinIO连接状态
- 查看服务器日志
- 增加轮询间隔时间

### 问题: 查询进度返回404

**可能原因:**
- jobId错误
- 进度信息已过期（60分钟后清理）

**解决方案:**
- 验证jobId是否正确
- 确保在任务完成后60分钟内查询

## 测试脚本

项目提供了测试脚本 `test-async-upload.sh`，可用于测试异步上传功能：

```bash
# 运行测试脚本
./test-async-upload.sh

# 使用自定义测试文件
VIDEO_FILE="my-video.mp4" ./test-async-upload.sh
```

## 性能建议

1. **分片大小选择:**
   - 小文件（< 50MB）: 5-8MB分片
   - 中等文件（50MB - 500MB）: 8-16MB分片
   - 大文件（> 500MB）: 16-64MB分片

2. **轮询间隔:**
   - 小文件: 1-2秒
   - 大文件: 3-5秒

3. **UI更新:**
   - 使用防抖避免频繁更新UI
   - 显示预估剩余时间提升用户体验

4. **错误处理:**
   - 实现重试机制（最多3次）
   - 提供友好的错误提示
   - 记录错误日志便于排查
