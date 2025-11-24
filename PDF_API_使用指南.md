# PDF转换和上传API使用指南

## 概述

本指南涵盖三个主要功能：
1. **改进的DOCX转PDF转换** - 更好地保留文档格式
2. **PDF上传API** - 上传PDF文件并将其转换为图片
3. **分页图片查询API** - 根据任务ID分页检索PDF页面图片

---

## 1. 改进的DOCX转PDF转换

### 新增功能

DOCX转PDF转换已经改进，能够更好地保留文档格式：
- **段落对齐** (左对齐、居中、右对齐、两端对齐)
- **文本样式** (标题使用粗体和更大字号)
- **表格** (使用竖线分隔符格式化)
- **嵌入图片** (自动提取并包含在PDF中)

### API接口

```
POST /api/pdf/convert
```

### 请求示例

```bash
curl -X POST http://localhost:8080/api/pdf/convert \
  -F "file=@document.docx" \
  -F 'request={"convertToImages": true, "imageDpi": 300, "imageFormat": "png"}'
```

### 响应示例

```json
{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "PROCESSING",
  "message": "转换已开始。使用jobId查询进度。"
}
```

---

## 2. PDF上传并转换为图片API

### 功能说明

上传PDF文件，系统将自动异步将所有页面转换为高质量图片。

### API接口

```
POST /api/pdf/upload
```

### 参数说明

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| file | MultipartFile | 是 | - | 要上传的PDF文件(必须是.pdf扩展名) |
| imageDpi | Integer | 否 | 300 | 图片分辨率(72-600) |
| imageFormat | String | 否 | "png" | 图片格式(png, jpg, bmp) |

### 请求示例

```bash
curl -X POST http://localhost:8080/api/pdf/upload \
  -F "file=@document.pdf" \
  -F "imageDpi=300" \
  -F "imageFormat=png"
```

### 成功响应

```json
{
  "taskId": "abc12345-6789-0def-1234-567890abcdef",
  "status": "PROCESSING",
  "message": "PDF上传成功。正在后台转换为图片。"
}
```

### 错误响应示例

**文件类型无效：**
```json
{
  "status": "ERROR",
  "message": "只允许上传PDF文件"
}
```

**文件过大：**
```json
{
  "status": "ERROR",
  "message": "文件大小超过限制。最大: 100 MB, 实际: 150.25 MB"
}
```

---

## 3. 查询上传进度

### API接口

```
GET /api/pdf/upload/progress/{taskId}
```

### 请求示例

```bash
curl -X GET http://localhost:8080/api/pdf/upload/progress/abc12345-6789-0def-1234-567890abcdef
```

### 响应示例

```json
{
  "jobId": "abc12345-6789-0def-1234-567890abcdef",
  "status": "PROCESSING",
  "currentPhase": "正在将PDF页面转换为图片",
  "progressPercentage": 65,
  "message": null,
  "startTime": 1703001234567,
  "elapsedTimeMs": 15234,
  "totalPages": 10,
  "processedPages": 6,
  "errorMessage": null
}
```

### 状态值说明

- `SUBMITTED` - 任务已提交
- `PROCESSING` - 正在转换中
- `COMPLETED` - 转换完成
- `FAILED` - 转换失败
- `NOT_FOUND` - 任务ID不存在

---

## 4. 分页查询PDF图片API

### 功能说明

根据任务ID分页检索PDF页面图片。

### API接口

```
GET /api/pdf/images/{taskId}
```

### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| startPage | Integer | 否 | 1 | 起始页码(从1开始) |
| pageSize | Integer | 否 | 10 | 返回的页数 |

### 请求示例

**获取前10页：**
```bash
curl -X GET "http://localhost:8080/api/pdf/images/abc12345-6789-0def-1234-567890abcdef?startPage=1&pageSize=10"
```

**获取第11-20页：**
```bash
curl -X GET "http://localhost:8080/api/pdf/images/abc12345-6789-0def-1234-567890abcdef?startPage=11&pageSize=10"
```

**获取所有剩余页面：**
```bash
curl -X GET "http://localhost:8080/api/pdf/images/abc12345-6789-0def-1234-567890abcdef?startPage=1&pageSize=1000"
```

### 响应示例

```json
{
  "taskId": "abc12345-6789-0def-1234-567890abcdef",
  "totalPages": 25,
  "startPage": 1,
  "pageSize": 10,
  "returnedPages": 10,
  "status": "SUCCESS",
  "message": "成功获取页面图片",
  "images": [
    {
      "pageNumber": 1,
      "imagePath": "/path/to/temp/abc12345-6789-0def-1234-567890abcdef/images/page_0001.png",
      "fileSize": 524288,
      "width": 2480,
      "height": 3508
    },
    {
      "pageNumber": 2,
      "imagePath": "/path/to/temp/abc12345-6789-0def-1234-567890abcdef/images/page_0002.png",
      "fileSize": 498765,
      "width": 2480,
      "height": 3508
    }
    // ... 更多页面
  ]
}
```

### 错误响应示例

**任务不存在：**
```json
{
  "taskId": "invalid-task-id",
  "status": "NOT_FOUND",
  "message": "任务不存在"
}
```

**任务未完成：**
```json
{
  "taskId": "abc12345-6789-0def-1234-567890abcdef",
  "status": "PROCESSING",
  "message": "任务尚未完成。当前状态: PROCESSING",
  "totalPages": 25
}
```

**起始页超出范围：**
```json
{
  "taskId": "abc12345-6789-0def-1234-567890abcdef",
  "totalPages": 25,
  "startPage": 30,
  "pageSize": 10,
  "returnedPages": 0,
  "status": "SUCCESS",
  "message": "起始页超出总页数",
  "images": []
}
```

---

## 完整工作流程示例

### 1. 上传PDF并转换为图片

```bash
#!/bin/bash

# 上传PDF
RESPONSE=$(curl -s -X POST http://localhost:8080/api/pdf/upload \
  -F "file=@document.pdf" \
  -F "imageDpi=300" \
  -F "imageFormat=png")

TASK_ID=$(echo $RESPONSE | jq -r '.taskId')
echo "任务ID: $TASK_ID"
```

### 2. 监控进度

```bash
# 每2秒轮询一次进度
while true; do
  PROGRESS=$(curl -s -X GET "http://localhost:8080/api/pdf/upload/progress/$TASK_ID")
  STATUS=$(echo $PROGRESS | jq -r '.status')
  PERCENTAGE=$(echo $PROGRESS | jq -r '.progressPercentage')
  
  echo "状态: $STATUS - 进度: $PERCENTAGE%"
  
  if [ "$STATUS" == "COMPLETED" ]; then
    break
  fi
  
  if [ "$STATUS" == "FAILED" ]; then
    echo "转换失败!"
    exit 1
  fi
  
  sleep 2
done
```

### 3. 分批获取图片

```bash
# 获取前5页
curl -s -X GET "http://localhost:8080/api/pdf/images/$TASK_ID?startPage=1&pageSize=5" | jq '.'

# 获取接下来的5页
curl -s -X GET "http://localhost:8080/api/pdf/images/$TASK_ID?startPage=6&pageSize=5" | jq '.'
```

### 4. 使用测试脚本

提供了一个现成的测试脚本：

```bash
./test-pdf-upload.sh document.pdf 300 png
```

---

## 配置说明

PDF转换设置可以在 `application.yml` 中配置：

```yaml
pdf:
  conversion:
    enabled: true
    temp-directory: D://ruoyi/pdf-conversion
    max-concurrent-jobs: 3
    max-file-size: 104857600  # 100MB
    image-rendering:
      dpi: 300
      format: png
      quality: 0.95
      antialiasing: true
```

---

## 最佳实践

1. **DPI选择：**
   - 72 DPI: 屏幕查看(文件最小)
   - 150 DPI: 基本打印
   - 300 DPI: 高质量打印(推荐)
   - 600 DPI: 专业打印(文件最大)

2. **图片格式：**
   - PNG: 最佳质量，无损压缩，文件较大(推荐)
   - JPG: 良好质量，有损压缩，文件较小
   - BMP: 未压缩，文件非常大

3. **分页策略：**
   - 使用合理的页面大小(10-50页/请求)
   - 对于大型PDF不要一次请求所有页面
   - 在客户端实现分页以提升用户体验

4. **错误处理：**
   - 始终检查响应中的 `status` 字段
   - 定期轮询进度接口(每1-2秒)
   - 为长时间运行的转换实现超时逻辑

5. **文件管理：**
   - 转换结果存储在临时目录中
   - 定期清理旧任务
   - 考虑实现24小时后自动清理

---

## 故障排除

### 问题："只允许上传PDF文件"
**解决方案：** 确保上传的文件具有 `.pdf` 扩展名。

### 问题："文件大小超过限制"
**解决方案：** 压缩PDF或在配置中增加 `pdf.conversion.max-file-size`。

### 问题：图片文件太大
**解决方案：** 降低DPI值(尝试150而不是300)。

### 问题：服务器重启后找不到任务
**解决方案：** 任务进度存储在内存中。重启后需要重新上传PDF。

### 问题：图片质量差
**解决方案：** 将DPI提高到300或600，并使用PNG格式。

---

## API接口总览

| 接口 | 方法 | 用途 |
|------|------|------|
| `/api/pdf/convert` | POST | 将任意支持的格式转换为PDF并生成图片 |
| `/api/pdf/upload` | POST | 上传PDF并转换为图片 |
| `/api/pdf/upload/progress/{taskId}` | GET | 查询PDF上传和转换进度 |
| `/api/pdf/images/{taskId}` | GET | 获取分页的PDF页面图片 |
| `/api/pdf/progress/{jobId}` | GET | 查询文件转换进度 |
| `/api/pdf/result/{jobId}` | GET | 获取转换结果 |
| `/api/pdf/formats` | GET | 列出支持的文件格式 |

---

## 技术特性

- ✅ 异步处理，不阻塞主线程
- ✅ 实时进度跟踪
- ✅ 支持大文件(最大100MB可配置)
- ✅ 高质量图片渲染(最高600 DPI)
- ✅ 分页查询，节省带宽
- ✅ 完善的错误处理
- ✅ 线程安全的实现
- ✅ RESTful API设计

---

## 使用场景

1. **文档预览服务** - 将PDF转换为图片用于在线预览
2. **文档管理系统** - 为上传的PDF生成缩略图
3. **打印服务** - 高DPI图片用于打印
4. **移动应用** - 分页加载PDF页面图片
5. **文档审批流程** - 在线批注PDF页面图片
