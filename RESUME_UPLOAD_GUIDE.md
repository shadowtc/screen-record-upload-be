# 异步上传断点续传使用指南

## 概述

本系统提供了完整的异步上传断点续传功能，支持在上传中断后从断点处继续上传，无需重新上传整个文件。

## 特性

- ✅ **真正的断点续传**：上传中断后，只需上传剩余未完成的分片
- ✅ **持久化状态**：上传进度保存在数据库中，应用重启后可恢复
- ✅ **临时文件保留**：上传中的文件保存在持久化目录，支持恢复上传
- ✅ **自动恢复**：应用重启时自动将未完成的任务标记为PAUSED状态
- ✅ **智能续传**：自动检测已上传的分片，只上传缺失的部分
- ✅ **进度追踪**：实时查看上传进度和状态

## 工作流程

### 1. 正常上传流程

```
提交文件 → 保存临时文件 → 初始化MinIO上传 → 上传分片 → 完成上传 → 保存元数据 → 清理临时文件
```

### 2. 断点续传流程

```
查询任务状态 → 恢复上传 → 检查已上传分片 → 上传剩余分片 → 完成上传 → 保存元数据 → 清理临时文件
```

## API 使用说明

### 1. 提交异步上传任务

**请求：**
```http
POST /api/uploads/async
Content-Type: multipart/form-data

file: <your-video-file>
chunkSize: 8388608 (可选，默认8MB)
```

**响应：**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "SUBMITTED",
  "progress": 0.0,
  "message": "Upload submitted, waiting to start...",
  "uploadedParts": 0,
  "totalParts": 125,
  "fileName": "my-video.mp4",
  "fileSize": 1048576000,
  "startTime": "2024-01-01T10:00:00Z"
}
```

**cURL 示例：**
```bash
curl -X POST http://localhost:8080/api/uploads/async \
  -F "file=@/path/to/video.mp4" \
  -F "chunkSize=8388608"
```

### 2. 查询上传进度

**请求：**
```http
GET /api/uploads/async/{jobId}/progress
```

**响应：**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "UPLOADING",
  "progress": 45.5,
  "message": "Uploading part 57/125...",
  "uploadedParts": 57,
  "totalParts": 125,
  "fileName": "my-video.mp4",
  "fileSize": 1048576000,
  "startTime": "2024-01-01T10:00:00Z"
}
```

**状态说明：**
- `SUBMITTED`: 已提交，等待开始
- `UPLOADING`: 正在上传中
- `PAUSED`: 已暂停（等待恢复）
- `COMPLETED`: 上传完成
- `FAILED`: 上传失败

**cURL 示例：**
```bash
curl http://localhost:8080/api/uploads/async/{jobId}/progress
```

### 3. 恢复上传（断点续传）

**请求：**
```http
POST /api/uploads/async/{jobId}/resume
```

**响应：**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "UPLOADING",
  "progress": 45.5,
  "message": "Resuming upload...",
  "uploadedParts": 57,
  "totalParts": 125,
  "fileName": "my-video.mp4",
  "fileSize": 1048576000,
  "startTime": "2024-01-01T10:00:00Z"
}
```

**使用条件：**
- 任务状态必须是 `PAUSED` 或 `FAILED`
- 临时文件必须存在
- MinIO上传会话必须有效

**cURL 示例：**
```bash
curl -X POST http://localhost:8080/api/uploads/async/{jobId}/resume
```

## 使用场景

### 场景1：网络中断后恢复

```bash
# 1. 提交上传
RESPONSE=$(curl -X POST http://localhost:8080/api/uploads/async \
  -F "file=@large-video.mp4" \
  -s)
JOB_ID=$(echo $RESPONSE | jq -r '.jobId')

# 2. 监控进度
while true; do
  PROGRESS=$(curl -s http://localhost:8080/api/uploads/async/$JOB_ID/progress)
  STATUS=$(echo $PROGRESS | jq -r '.status')
  PERCENT=$(echo $PROGRESS | jq -r '.progress')
  
  echo "Status: $STATUS, Progress: $PERCENT%"
  
  if [ "$STATUS" == "COMPLETED" ] || [ "$STATUS" == "FAILED" ]; then
    break
  fi
  
  sleep 5
done

# 3. 如果失败或暂停，恢复上传
if [ "$STATUS" == "PAUSED" ] || [ "$STATUS" == "FAILED" ]; then
  echo "Resuming upload..."
  curl -X POST http://localhost:8080/api/uploads/async/$JOB_ID/resume
fi
```

### 场景2：应用重启后恢复

应用启动时会自动将所有未完成的上传任务（`UPLOADING`状态）标记为`PAUSED`状态。

```bash
# 1. 应用重启前，某个上传任务正在进行中（UPLOADING状态）

# 2. 应用重启

# 3. 重启后，任务自动变为PAUSED状态

# 4. 查询任务状态
curl http://localhost:8080/api/uploads/async/{jobId}/progress

# 5. 恢复上传
curl -X POST http://localhost:8080/api/uploads/async/{jobId}/resume
```

### 场景3：主动暂停和恢复

虽然当前版本不提供主动暂停API，但可以通过重启应用或让上传任务失败来模拟暂停：

```bash
# 1. 提交上传
JOB_ID=$(curl -X POST http://localhost:8080/api/uploads/async \
  -F "file=@video.mp4" \
  -s | jq -r '.jobId')

# 2. 重启应用（任务会自动变为PAUSED状态）

# 3. 恢复上传
curl -X POST http://localhost:8080/api/uploads/async/$JOB_ID/resume
```

## 完整示例脚本

### upload-with-resume.sh

```bash
#!/bin/bash

# 配置
API_URL="http://localhost:8080/api/uploads/async"
VIDEO_FILE="$1"

if [ -z "$VIDEO_FILE" ]; then
  echo "Usage: $0 <video-file>"
  exit 1
fi

if [ ! -f "$VIDEO_FILE" ]; then
  echo "Error: File not found: $VIDEO_FILE"
  exit 1
fi

echo "Starting upload: $VIDEO_FILE"

# 提交上传
RESPONSE=$(curl -X POST $API_URL \
  -F "file=@$VIDEO_FILE" \
  -s)

JOB_ID=$(echo $RESPONSE | jq -r '.jobId')
echo "Job ID: $JOB_ID"

# 监控进度
monitor_progress() {
  while true; do
    PROGRESS=$(curl -s $API_URL/$JOB_ID/progress)
    STATUS=$(echo $PROGRESS | jq -r '.status')
    PERCENT=$(echo $PROGRESS | jq -r '.progress')
    MESSAGE=$(echo $PROGRESS | jq -r '.message')
    
    echo "[$(date '+%H:%M:%S')] Status: $STATUS, Progress: $PERCENT%, Message: $MESSAGE"
    
    if [ "$STATUS" == "COMPLETED" ]; then
      echo "Upload completed successfully!"
      DOWNLOAD_URL=$(echo $PROGRESS | jq -r '.uploadResponse.downloadUrl')
      echo "Download URL: $DOWNLOAD_URL"
      exit 0
    fi
    
    if [ "$STATUS" == "FAILED" ]; then
      echo "Upload failed. Attempting to resume..."
      curl -X POST $API_URL/$JOB_ID/resume -s
      sleep 5
    fi
    
    if [ "$STATUS" == "PAUSED" ]; then
      echo "Upload paused. Resuming..."
      curl -X POST $API_URL/$JOB_ID/resume -s
      sleep 5
    fi
    
    sleep 5
  done
}

# 启动监控
monitor_progress
```

**使用方法：**
```bash
chmod +x upload-with-resume.sh
./upload-with-resume.sh my-video.mp4
```

## 配置说明

### application.yml

```yaml
upload:
  # 临时文件存储目录（用于断点续传）
  # Linux/Docker: /tmp/async-uploads
  # Windows: D://ruoyi/async-uploads
  temp-directory: ${UPLOAD_TEMP_DIR:D://ruoyi/async-uploads}
  
  # 最大文件大小（2GB）
  max-file-size: 2147483648
  
  # 默认分片大小（8MB）
  default-chunk-size: 8388608
```

### 环境变量

```bash
# 设置临时文件目录
export UPLOAD_TEMP_DIR=/data/async-uploads

# 设置最大文件大小（2GB）
export UPLOAD_MAX_SIZE=2147483648

# 设置默认分片大小（8MB）
export UPLOAD_CHUNK_SIZE=8388608
```

## 数据库表结构

### async_upload_tasks

用于存储异步上传任务的完整信息：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| job_id | VARCHAR(36) | 任务ID（UUID） |
| status | VARCHAR(20) | 状态 |
| progress | DOUBLE | 进度百分比 |
| message | VARCHAR(1000) | 状态消息 |
| uploaded_parts | INT | 已上传分片数 |
| total_parts | INT | 总分片数 |
| file_name | VARCHAR(500) | 文件名 |
| file_size | BIGINT | 文件大小 |
| content_type | VARCHAR(100) | 内容类型 |
| chunk_size | BIGINT | 分片大小 |
| upload_id | VARCHAR(500) | MinIO上传ID |
| object_key | VARCHAR(1000) | 对象键 |
| temp_file_path | VARCHAR(1000) | 临时文件路径 |
| video_recording_id | BIGINT | 视频录制ID |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## 注意事项

1. **临时文件管理**
   - 临时文件会占用磁盘空间，确保有足够的存储空间
   - 上传完成后临时文件会自动删除
   - 失败的任务临时文件会保留，以便恢复上传

2. **任务清理**
   - 建议定期清理旧的已完成或失败任务
   - 可以使用数据库存储过程 `cleanup_old_upload_tasks` 清理旧任务
   - 清理前请确认任务已不再需要恢复

3. **MinIO上传会话**
   - MinIO的分片上传会话有有效期限制
   - 如果间隔时间过长，上传会话可能失效
   - 建议在24小时内完成上传或恢复

4. **并发限制**
   - 系统使用共享的线程池处理异步任务
   - 默认最大并发任务数由视频压缩配置控制
   - 避免同时提交大量上传任务

## 故障排查

### 问题1：恢复上传时提示"任务不存在"

**原因：** 数据库中没有该任务记录

**解决：** 检查jobId是否正确，或任务是否已被清理

### 问题2：恢复上传时提示"临时文件不存在"

**原因：** 临时文件已被删除或移动

**解决：** 无法恢复，需要重新上传整个文件

### 问题3：恢复上传时提示"任务无法恢复"

**原因：** 任务状态不是PAUSED或FAILED

**解决：** 
- 如果是UPLOADING状态，等待当前上传完成
- 如果是COMPLETED状态，无需恢复
- 如果是SUBMITTED状态，等待任务开始

### 问题4：应用重启后任务没有自动变为PAUSED

**原因：** 数据库连接失败或loadUnfinishedTasks方法异常

**解决：** 检查应用日志，确认数据库连接正常

## 最佳实践

1. **定期轮询进度**
   - 建议每5-10秒查询一次进度
   - 避免频繁查询造成服务器负载

2. **自动重试机制**
   - 检测到FAILED状态时自动调用resume接口
   - 限制重试次数，避免无限重试

3. **用户体验**
   - 显示实时进度和预计剩余时间
   - 提供暂停/恢复按钮（需要额外实现暂停API）
   - 网络中断时自动重试

4. **监控和告警**
   - 监控长时间处于UPLOADING状态的任务
   - 监控临时文件目录的磁盘使用情况
   - 监控失败任务的数量和原因

## 总结

本系统提供了完整的断点续传功能，可以有效处理大文件上传中的网络中断、应用重启等场景。通过合理使用API和配置，可以为用户提供流畅的上传体验。
