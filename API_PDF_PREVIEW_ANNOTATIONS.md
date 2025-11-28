# PDF注解预览API文档

## 概述

此API用于在PDF基类图片上渲染注解文字，实现预览功能。系统会自动：
1. 根据businessId和tenantId查找isBase=1的基类任务
2. 获取基类的所有页面图片
3. 在指定页面上渲染注解文字
4. 返回所有页面图片URL（被渲染的页面使用新图片，其他页面使用原图）

## 接口信息

- **URL**: `/api/pdf/preview-with-annotations`
- **方法**: `POST`
- **Content-Type**: `application/json`

## 请求参数

### JSON请求体

```json
{
  "businessId": "project-123",           // 必填：业务ID
  "tenantId": "tenant-789",              // 必填：租户ID
  "userId": "user-456",                  // 可选：用户ID
  "exportTime": "2025-11-27T06:37:55.634Z",  // 可选：导出时间
  "totalAnnotations": 2,                 // 可选：总注解数量
  "pageAnnotations": {                   // 必填：按页码分组的注解信息
    "1": [                               // 页码（字符串）
      {
        "id": "annotation_1",            // 注解ID
        "index": 1,                      // 注解索引
        "contents": "受试者签字",         // 必填：要渲染的文字内容
        "markValue": "subjectSignature", // 标记值
        "pageNumber": "1",               // 页码
        "pdf": [214, 166, 280, 216],     // 必填：PDF坐标 [x1, y1, x2, y2]
        "scale": 1.2,                    // 缩放比例
        "normalized": {                  // 标准化坐标信息
          "x": "13496",
          "y": "7426",
          "width": "20.96",
          "height": "10.48",
          "basePoint": "LU"
        }
      }
    ],
    "3": [                               // 第3页的注解
      {
        "id": "annotation_2",
        "index": 2,
        "contents": "研究者签字",
        "markValue": "investigatorSignature",
        "pageNumber": "3",
        "pdf": [138, -1530, 204, -1481],
        "scale": 1.2,
        "normalized": {
          "x": "8717",
          "y": "-68167",
          "width": "20.96",
          "height": "10.48",
          "basePoint": "LU"
        }
      }
    ]
  }
}
```

> **markJson 说明**：该字段为字符串形式的 JSON，内容与 `pageAnnotations` 完全一致。系统会先使用 markJson 在 PDF 上完成注解填充，再将填充后的 PDF 转换成图片。如果未显式提供 markJson，后端会根据 `pageAnnotations` 自动生成同样的 JSON 字符串。

### 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| businessId | String | 是 | 业务ID，用于查找基类任务 |
| tenantId | String | 是 | 租户ID，用于租户隔离 |
| userId | String | 否 | 用户ID |
| exportTime | String | 否 | 导出时间（ISO 8601格式） |
| totalAnnotations | Integer | 否 | 总注解数量 |
| pageAnnotations | Map | 是 | 按页码分组的注解列表 |
| markJson | String | 否 | 与 pageAnnotations 内容一致的 JSON 字符串，优先用于在PDF上填充注解 |

### 注解对象 (PageAnnotation)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | String | 否 | 注解唯一标识 |
| index | Integer | 否 | 注解索引 |
| contents | String | 是 | 要渲染的文字内容 |
| markValue | String | 否 | 标记值 |
| pageNumber | String | 否 | 页码 |
| pdf | double[] | 是 | PDF坐标系坐标 [x1, y1, x2, y2] |
| scale | Double | 否 | 缩放比例 |
| normalized | Object | 否 | 标准化坐标信息 |

### PDF坐标说明

- `pdf` 数组包含4个元素：`[x1, y1, x2, y2]`
- `x1, y1`: 文字框左上角坐标（PDF坐标系）
- `x2, y2`: 文字框右下角坐标（PDF坐标系）
- PDF坐标系：左下角为原点(0,0)，向右为X正方向，向上为Y正方向
- 系统会自动转换为图片坐标系（左上角为原点，向右为X正方向，向下为Y正方向）

## 响应格式

### 成功响应

```json
{
  "status": "SUCCESS",
  "message": "Preview generated successfully",
  "businessId": "project-123",
  "tenantId": "tenant-789",
  "userId": "user-456",
  "totalPages": 10,
  "renderedPages": 2,
  "images": [
    {
      "pageNumber": 1,
      "imageUrl": "https://minio.example.com/bucket/pdf-images-preview/user-456/project-123/page_0001_annotated.png?X-Amz-...",
      "imageObjectKey": "pdf-images-preview/user-456/project-123/page_0001_annotated.png",
      "isRendered": true,
      "isBase": false,
      "width": 2480,
      "height": 3508
    },
    {
      "pageNumber": 2,
      "imageUrl": "https://minio.example.com/bucket/pdf-images/user-456/project-123/task-xyz/page_0002.png?X-Amz-...",
      "imageObjectKey": "pdf-images/user-456/project-123/task-xyz/page_0002.png",
      "isRendered": false,
      "isBase": true,
      "width": 2480,
      "height": 3508
    },
    {
      "pageNumber": 3,
      "imageUrl": "https://minio.example.com/bucket/pdf-images-preview/user-456/project-123/page_0003_annotated.png?X-Amz-...",
      "imageObjectKey": "pdf-images-preview/user-456/project-123/page_0003_annotated.png",
      "isRendered": true,
      "isBase": false,
      "width": 2480,
      "height": 3508
    }
  ]
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| status | String | 响应状态：SUCCESS, ERROR, NOT_FOUND |
| message | String | 响应消息 |
| businessId | String | 业务ID |
| tenantId | String | 租户ID |
| userId | String | 用户ID |
| totalPages | Integer | 总页数 |
| renderedPages | Integer | 渲染的页面数量 |
| images | Array | 页面图片列表 |

### 图片对象 (PageImageInfo)

| 字段 | 类型 | 说明 |
|------|------|------|
| pageNumber | Integer | 页码 |
| imageUrl | String | 图片访问URL（预签名URL，有效期60分钟） |
| imageObjectKey | String | MinIO对象键 |
| isRendered | Boolean | 是否为渲染后的图片 |
| isBase | Boolean | 是否来自基类任务 |
| width | Integer | 图片宽度（像素） |
| height | Integer | 图片高度（像素） |

### 错误响应

#### 基类任务未找到

```json
{
  "status": "NOT_FOUND",
  "message": "Base task not found for businessId: project-123",
  "businessId": "project-123",
  "tenantId": "tenant-789"
}
```

#### 参数错误

```json
{
  "status": "ERROR",
  "message": "Business ID is required",
  "businessId": null,
  "tenantId": null
}
```

#### 渲染失败

```json
{
  "status": "ERROR",
  "message": "Failed to generate preview: Invalid PDF coordinates",
  "businessId": "project-123",
  "tenantId": "tenant-789"
}
```

## 渲染效果

系统会复用 `/api/pdf/getFillPdf` 的坐标解析逻辑，先在 PDF 上完成文字填充，再将填充后的 PDF 页面转换为图片：
1. 解析 markJson/pageAnnotations（x 取 `pdf[0]`、y 取 `pdf[3]`，宽高取 normalized.width/height）
2. 调用与 getFillPdf 相同的 PDF 填充代码，将文字写入临时 PDF
3. 仅渲染包含注解的页面，保持与基类图片相同的 DPI 和格式
4. 将新的页面图片上传到 MinIO（路径：`pdf-images-preview/{userId}/{businessId}/`）
5. 其他未修改页面继续复用原始基类图片

## 使用流程

1. **上传PDF文件**
   ```bash
   curl -X POST "http://localhost:8080/api/pdf/upload" \
     -F "file=@document.pdf" \
     -F "businessId=project-123" \
     -F "userId=user-456" \
     -F "tenantId=tenant-789"
   ```

2. **等待转换完成**
   - 系统会自动转换PDF为图片
   - 基类任务的`isBase`字段为true

3. **预览带注解的图片**
   ```bash
   curl -X POST "http://localhost:8080/api/pdf/preview-with-annotations" \
     -H "Content-Type: application/json" \
     -d @request.json
   ```

4. **查看渲染结果**
   - 在浏览器中打开返回的`imageUrl`
   - 被渲染的页面会显示注解文字
   - 未被渲染的页面显示原始图片

## 性能说明

- **速度**：仅对包含注解的页面执行 PDF 填充 + 单页渲染，避免重新生成全部页面
- **一致性**：复用 `/api/pdf/getFillPdf` 的坐标与填充逻辑，图片预览与正式填充保持 100% 匹配
- **并发**：支持多个预览请求同时处理
- **缓存**：渲染后的图片上传到MinIO，可重复访问
- **临时文件**：处理完成后自动清理
- **URL有效期**：预签名URL有效期60分钟

## 注意事项

1. **基类任务必须存在**
   - 必须先上传PDF并完成全量转换（不指定pages参数）
   - 系统会自动标记为基类任务（isBase=1）

2. **租户隔离**
   - 必须提供正确的tenantId
   - 系统只会查找匹配租户的基类任务

3. **坐标解析与 getFillPdf 一致**
   - x 取 `pdf[0]`，y 取 `pdf[3]`（底部坐标），宽高取 normalized.width/height
   - 后端先在 PDF 中完成填充，再将页面转换为图片，无需前端额外转换

4. **图片格式**
   - 支持PNG和JPG格式
   - 保持与基类图片相同的格式
   - 推荐使用PNG格式（无损压缩）

5. **多注解渲染**
   - 一个页面可以有多个注解
   - 按数组顺序依次渲染
   - 后渲染的注解可能覆盖先渲染的注解

6. **URL有效期**
   - 预签名URL有效期60分钟
   - 过期后需要重新调用API获取新URL
   - 前端应缓存URL，避免频繁请求

7. **markJson 与 pageAnnotations 同步**
   - markJson 是 `pageAnnotations` 的 JSON 字符串版本，后端会优先使用 markJson 在 PDF 中完成注解后再渲染
   - 如果未提供 markJson，系统会按 `pageAnnotations` 自动生成，确保与 `/api/pdf/getFillPdf` 一致

## 测试脚本

使用提供的测试脚本：

```bash
# 运行测试
./test-pdf-preview-annotations.sh project-123 tenant-789

# 查看帮助
./test-pdf-preview-annotations.sh
```

测试脚本会：
1. 发送预览请求
2. 保存响应到JSON文件
3. 显示渲染结果统计
4. 列出所有页面图片URL

## 示例代码

### JavaScript/Fetch

```javascript
const previewWithAnnotations = async (businessId, tenantId, annotations) => {
  const response = await fetch('http://localhost:8080/api/pdf/preview-with-annotations', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      businessId,
      tenantId,
      exportTime: new Date().toISOString(),
      totalAnnotations: Object.values(annotations).flat().length,
      pageAnnotations: annotations,
    }),
  });
  
  const result = await response.json();
  
  if (result.status === 'SUCCESS') {
    console.log(`Total pages: ${result.totalPages}`);
    console.log(`Rendered pages: ${result.renderedPages}`);
    
    result.images.forEach(image => {
      console.log(`Page ${image.pageNumber}: ${image.isRendered ? 'Rendered' : 'Original'}`);
      console.log(`  URL: ${image.imageUrl}`);
    });
  } else {
    console.error(`Error: ${result.message}`);
  }
  
  return result;
};

// 使用示例
const annotations = {
  "1": [
    {
      "contents": "受试者签字",
      "pdf": [214, 166, 280, 216]
    }
  ],
  "3": [
    {
      "contents": "研究者签字",
      "pdf": [138, -1530, 204, -1481]
    }
  ]
};

previewWithAnnotations('project-123', 'tenant-789', annotations);
```

### Python/Requests

```python
import requests
import json
from datetime import datetime

def preview_with_annotations(business_id, tenant_id, annotations):
    url = 'http://localhost:8080/api/pdf/preview-with-annotations'
    
    payload = {
        'businessId': business_id,
        'tenantId': tenant_id,
        'exportTime': datetime.utcnow().isoformat() + 'Z',
        'totalAnnotations': sum(len(annots) for annots in annotations.values()),
        'pageAnnotations': annotations
    }
    
    response = requests.post(url, json=payload)
    result = response.json()
    
    if result['status'] == 'SUCCESS':
        print(f"Total pages: {result['totalPages']}")
        print(f"Rendered pages: {result['renderedPages']}")
        
        for image in result['images']:
            status = 'Rendered' if image['isRendered'] else 'Original'
            print(f"Page {image['pageNumber']}: {status}")
            print(f"  URL: {image['imageUrl']}")
    else:
        print(f"Error: {result['message']}")
    
    return result

# 使用示例
annotations = {
    "1": [
        {
            "contents": "受试者签字",
            "pdf": [214, 166, 280, 216]
        }
    ],
    "3": [
        {
            "contents": "研究者签字",
            "pdf": [138, -1530, 204, -1481]
        }
    ]
}

preview_with_annotations('project-123', 'tenant-789', annotations)
```

## 相关接口

- `POST /api/pdf/upload` - 上传PDF并转换为图片
- `POST /api/pdf/upload-by-url` - 通过URL上传PDF
- `GET /api/pdf/images` - 获取页面图片（不含注解）
- `GET /api/pdf/progress/{taskId}` - 查询转换进度

## 更新日志

### v1.0.0 (2025-11-27)
- 首次发布
- 支持在基类图片上渲染注解
- 支持多页面、多注解渲染
- 支持租户隔离
- 自动坐标系转换
- 临时文件自动清理
