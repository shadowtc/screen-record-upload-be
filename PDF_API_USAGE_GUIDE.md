# PDF转换服务API使用指南

## 概述

这是一个专注于PDF上传和图片转换的服务，支持多人签署场景的增量转换。主要特性：

- ✅ PDF上传并转换为高清图片
- ✅ 数据库持久化，重启不丢失数据
- ✅ 多人签署场景优化（基类+增量转换）
- ✅ 灵活查询（按任务ID、业务ID、用户ID）
- ✅ 分页查询图片
- ✅ 异步处理，支持进度查询

## 核心概念

### 业务场景

同一份PDF文档需要多人签署，每次签署只会更新特定页面：

1. **基类转换**：第一次上传，转换所有页面，作为基础图片
2. **增量转换**：后续上传，只转换变更的页面，节省存储资源
3. **智能合并**：查询时自动合并基类图片和用户特定页面

### 关键字段

- `businessId`：业务ID（必填），标识同一份文档
- `userId`：用户ID（必填），标识签署人
- `pages`：页面集合（可选），指定要转换的页面，如`[1, 3, 5]`
- `isBase`：是否为基类转换（系统自动判断）

## API接口

### 1. 上传PDF并转换为图片

**POST** `/api/pdf/upload`

#### 请求参数（Form Data）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | ✅ | PDF文件 |
| businessId | String | ✅ | 业务ID |
| userId | String | ✅ | 用户ID |
| pages | List<Integer> | ❌ | 页面集合（如[1,3,5]），不传则全量转换 |
| imageDpi | Integer | ❌ | 图片DPI，默认300 |
| imageFormat | String | ❌ | 图片格式，默认PNG |

#### 场景示例

**场景1：首次上传（全量转换）**
```bash
curl -X POST "http://localhost:8080/api/pdf/upload" \
  -F "file=@contract.pdf" \
  -F "businessId=CONTRACT-2024-001" \
  -F "userId=USER-001"
```

**场景2：第二人签署（增量转换）**
```bash
curl -X POST "http://localhost:8080/api/pdf/upload" \
  -F "file=@contract-signed-2.pdf" \
  -F "businessId=CONTRACT-2024-001" \
  -F "userId=USER-002" \
  -F "pages=1,5,10"
```

#### 响应示例

```json
{
  "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "PROCESSING",
  "message": "PDF upload successful. Converting to images in background."
}
```

#### 业务规则

- 如果`pages`参数为空，执行全量转换，`isBase=true`
- 如果`pages`参数有值：
  - 检查该`businessId`是否已有基类转换记录
  - 如果没有，返回错误，要求先执行全量转换
  - 如果有，执行增量转换，`isBase=false`

---

### 2. 查询任务详情

**GET** `/api/pdf/task/{taskId}`

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| taskId | String | 任务ID |

#### 请求示例

```bash
curl "http://localhost:8080/api/pdf/task/a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

#### 响应示例

```json
{
  "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "businessId": "CONTRACT-2024-001",
  "userId": "USER-001",
  "filename": "contract.pdf",
  "totalPages": 15,
  "convertedPages": null,
  "status": "COMPLETED",
  "isBase": true,
  "errorMessage": null,
  "createdAt": "2024-11-25T10:30:00",
  "updatedAt": "2024-11-25T10:30:45"
}
```

---

### 3. 查询任务列表

**GET** `/api/pdf/tasks`

#### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| businessId | String | ❌ | 业务ID |
| userId | String | ❌ | 用户ID |

#### 请求示例

```bash
# 查询某业务的所有任务
curl "http://localhost:8080/api/pdf/tasks?businessId=CONTRACT-2024-001"

# 查询某业务某用户的任务
curl "http://localhost:8080/api/pdf/tasks?businessId=CONTRACT-2024-001&userId=USER-002"
```

#### 响应示例

```json
[
  {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "businessId": "CONTRACT-2024-001",
    "userId": "USER-001",
    "filename": "contract.pdf",
    "totalPages": 15,
    "convertedPages": null,
    "status": "COMPLETED",
    "isBase": true,
    "createdAt": "2024-11-25T10:30:00",
    "updatedAt": "2024-11-25T10:30:45"
  },
  {
    "taskId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "businessId": "CONTRACT-2024-001",
    "userId": "USER-002",
    "filename": "contract-signed-2.pdf",
    "totalPages": 15,
    "convertedPages": [1, 5, 10],
    "status": "COMPLETED",
    "isBase": false,
    "createdAt": "2024-11-25T11:15:00",
    "updatedAt": "2024-11-25T11:15:20"
  }
]
```

---

### 4. 查询转换进度

**GET** `/api/pdf/progress/{taskId}`

#### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| taskId | String | 任务ID |

#### 请求示例

```bash
curl "http://localhost:8080/api/pdf/progress/a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

#### 响应示例

```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "PROCESSING",
  "currentPhase": "PROCESSING",
  "progressPercentage": 50,
  "totalPages": 15,
  "processedPages": 0,
  "message": null,
  "startTime": 1700905800000,
  "elapsedTimeMs": 5000
}
```

#### 状态说明

| 状态 | 说明 |
|------|------|
| SUBMITTED | 已提交，等待处理 |
| PROCESSING | 正在转换 |
| COMPLETED | 转换完成 |
| FAILED | 转换失败 |
| NOT_FOUND | 任务不存在 |

---

### 5. 查询PDF图片（核心接口）

**GET** `/api/pdf/images`

#### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| businessId | String | ✅ | - | 业务ID |
| userId | String | ❌ | - | 用户ID |
| startPage | Integer | ❌ | 1 | 起始页码 |
| pageSize | Integer | ❌ | 10 | 每页数量 |

#### 场景示例

**场景1：查询基类图片（所有人共享）**
```bash
curl "http://localhost:8080/api/pdf/images?businessId=CONTRACT-2024-001&startPage=1&pageSize=5"
```

**场景2：查询特定用户的图片（基类+用户特定页面）**
```bash
curl "http://localhost:8080/api/pdf/images?businessId=CONTRACT-2024-001&userId=USER-002&startPage=1&pageSize=5"
```

#### 响应示例（无userId）

返回基类图片：

```json
{
  "businessId": "CONTRACT-2024-001",
  "userId": null,
  "totalPages": 15,
  "startPage": 1,
  "pageSize": 5,
  "returnedPages": 5,
  "images": [
    {
      "pageNumber": 1,
      "imageObjectKey": "/path/to/task1/images/page_0001.png",
      "isBase": true,
      "userId": "USER-001",
      "width": 2480,
      "height": 3508,
      "fileSize": 1234567
    },
    {
      "pageNumber": 2,
      "imageObjectKey": "/path/to/task1/images/page_0002.png",
      "isBase": true,
      "userId": "USER-001",
      "width": 2480,
      "height": 3508,
      "fileSize": 1245678
    }
    // ... 3 more images
  ],
  "status": "SUCCESS",
  "message": "Successfully retrieved page images"
}
```

#### 响应示例（有userId）

返回合并后的图片（特定页面使用用户的，其他使用基类）：

```json
{
  "businessId": "CONTRACT-2024-001",
  "userId": "USER-002",
  "totalPages": 15,
  "startPage": 1,
  "pageSize": 5,
  "returnedPages": 5,
  "images": [
    {
      "pageNumber": 1,
      "imageObjectKey": "/path/to/task2/images/page_0001.png",
      "isBase": false,
      "userId": "USER-002",
      "width": 2480,
      "height": 3508,
      "fileSize": 1256789
    },
    {
      "pageNumber": 2,
      "imageObjectKey": "/path/to/task1/images/page_0002.png",
      "isBase": true,
      "userId": "USER-001",
      "width": 2480,
      "height": 3508,
      "fileSize": 1245678
    },
    {
      "pageNumber": 3,
      "imageObjectKey": "/path/to/task1/images/page_0003.png",
      "isBase": true,
      "userId": "USER-001",
      "width": 2480,
      "height": 3508,
      "fileSize": 1234890
    }
    // ... 2 more images
  ],
  "status": "SUCCESS",
  "message": "Successfully retrieved page images"
}
```

#### 查询逻辑

1. **只有businessId**：返回基类图片（`isBase=true`）
2. **有businessId + userId**：
   - 查询基类图片和该用户的增量图片
   - 按页码合并，优先使用用户的增量图片
   - 其他页面使用基类图片

---

## 完整业务流程示例

### 场景：三人签署合同

#### 1. 第一人签署（全量转换）

```bash
# 上传原始合同
curl -X POST "http://localhost:8080/api/pdf/upload" \
  -F "file=@contract.pdf" \
  -F "businessId=CONTRACT-2024-001" \
  -F "userId=SIGNER-001"

# 响应
{
  "taskId": "task-001",
  "status": "PROCESSING"
}

# 查询进度
curl "http://localhost:8080/api/pdf/progress/task-001"

# 查询图片（基类）
curl "http://localhost:8080/api/pdf/images?businessId=CONTRACT-2024-001"
```

#### 2. 第二人签署（增量转换：第1页和第15页）

```bash
# 上传签署后的合同，只转换签名页
curl -X POST "http://localhost:8080/api/pdf/upload" \
  -F "file=@contract-signed-by-signer2.pdf" \
  -F "businessId=CONTRACT-2024-001" \
  -F "userId=SIGNER-002" \
  -F "pages=1,15"

# 响应
{
  "taskId": "task-002",
  "status": "PROCESSING"
}

# 查询SIGNER-002的图片（基类+增量）
curl "http://localhost:8080/api/pdf/images?businessId=CONTRACT-2024-001&userId=SIGNER-002"
```

#### 3. 第三人签署（增量转换：第1页和第15页）

```bash
# 上传签署后的合同
curl -X POST "http://localhost:8080/api/pdf/upload" \
  -F "file=@contract-signed-by-signer3.pdf" \
  -F "businessId=CONTRACT-2024-001" \
  -F "userId=SIGNER-003" \
  -F "pages=1,15"

# 查询SIGNER-003的图片
curl "http://localhost:8080/api/pdf/images?businessId=CONTRACT-2024-001&userId=SIGNER-003"
```

#### 4. 查询不同阶段的合同

```bash
# 查询基类（原始版本）
curl "http://localhost:8080/api/pdf/images?businessId=CONTRACT-2024-001"

# 查询SIGNER-002看到的版本
curl "http://localhost:8080/api/pdf/images?businessId=CONTRACT-2024-001&userId=SIGNER-002"

# 查询SIGNER-003看到的版本
curl "http://localhost:8080/api/pdf/images?businessId=CONTRACT-2024-001&userId=SIGNER-003"
```

---

## 数据库设计

### pdf_conversion_task（任务表）

```sql
CREATE TABLE pdf_conversion_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id VARCHAR(36) UNIQUE NOT NULL,
  business_id VARCHAR(100) NOT NULL,
  user_id VARCHAR(100) NOT NULL,
  filename VARCHAR(500) NOT NULL,
  total_pages INT NOT NULL,
  converted_pages TEXT,  -- JSON数组，如[1,3,5]
  pdf_object_key VARCHAR(1000),
  status VARCHAR(20) NOT NULL,
  is_base BOOLEAN NOT NULL DEFAULT FALSE,
  error_message TEXT,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_business_id (business_id),
  INDEX idx_business_user (business_id, user_id)
);
```

### pdf_page_image（图片表）

```sql
CREATE TABLE pdf_page_image (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id VARCHAR(36) NOT NULL,
  business_id VARCHAR(100) NOT NULL,
  user_id VARCHAR(100) NOT NULL,
  page_number INT NOT NULL,
  image_object_key VARCHAR(1000) NOT NULL,
  is_base BOOLEAN NOT NULL DEFAULT FALSE,
  width INT,
  height INT,
  file_size BIGINT,
  created_at DATETIME NOT NULL,
  INDEX idx_business_id (business_id),
  INDEX idx_business_page (business_id, page_number),
  INDEX idx_business_user_page (business_id, user_id, page_number)
);
```

---

## 配置说明

### application.yml

```yaml
pdf:
  conversion:
    enabled: true
    temp-directory: /tmp/pdf-conversion
    max-file-size: 104857600  # 100MB
    image-rendering:
      dpi: 300
      format: PNG
```

---

## 错误处理

### 常见错误

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| File is empty | 未上传文件 | 检查file参数 |
| Only PDF files are allowed | 文件类型不是PDF | 确保上传PDF文件 |
| Business ID is required | 缺少businessId | 添加businessId参数 |
| User ID is required | 缺少userId | 添加userId参数 |
| Base conversion not found | 尝试增量转换但没有基类 | 先执行全量转换（不传pages参数） |
| Task not found | 任务ID不存在 | 检查taskId是否正确 |

---

## 性能优化建议

1. **首次转换**：全量转换所有页面，作为基类
2. **后续转换**：只转换变更的页面，节省时间和存储
3. **查询优化**：使用数据库索引，支持快速查询
4. **分页查询**：大文档使用分页，避免一次加载太多图片
5. **异步处理**：转换任务异步执行，不阻塞客户端

---

## 总结

这套API专为多人签署场景设计，核心优势：

- ✅ **存储优化**：基类+增量，避免重复存储
- ✅ **查询灵活**：支持按业务、用户、页码查询
- ✅ **数据持久化**：MySQL存储，重启不丢失
- ✅ **高性能**：异步处理，支持并发
- ✅ **易于集成**：RESTful API，简单易用

适用场景：电子合同签署、文档审批、多方协作等。
