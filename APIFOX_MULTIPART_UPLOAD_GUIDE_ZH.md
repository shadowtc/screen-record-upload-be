# MinIO 分片上传 API 测试指南 - Apifox 版

## 目录
- [概述](#概述)
- [分片上传执行流程](#分片上传执行流程)
- [业务逻辑说明](#业务逻辑说明)
- [Apifox 接口测试步骤](#apifox-接口测试步骤)
- [完整测试示例](#完整测试示例)
- [常见问题](#常见问题)

---

## 概述

分片上传（Multipart Upload）是一种将大文件分割成多个小块（分片）分别上传，最后再合并的上传方式。它具有以下优势：

- **断点续传**：网络中断后可以从失败的分片继续上传，无需重新上传整个文件
- **并行上传**：多个分片可以同时上传，提高上传速度
- **大文件支持**：突破单次请求文件大小限制，支持 GB 级别的文件上传
- **节省带宽**：失败时只需重传失败的分片，而非整个文件

---

## 分片上传执行流程

### 整体流程图

```
┌─────────────────────────────────────────────────────────────┐
│                      1. 初始化上传                          │
│  POST /api/uploads/init (上传文件获取 uploadId 和分片信息) │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                   2. 获取预签名 URL                         │
│  GET /api/uploads/{uploadId}/parts (获取用于上传的 URL)    │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                   3. 上传分片数据                           │
│  PUT {presignedUrl} (直接上传到 MinIO，保存 ETag)          │
│  可并行上传多个分片                                         │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                4. 检查上传状态（可选）                      │
│  GET /api/uploads/{uploadId}/status (查看已上传的分片)     │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                    5. 完成上传                              │
│  POST /api/uploads/complete (提交所有分片的 ETag 合并文件) │
└─────────────────────────────────────────────────────────────┘
```

### 取消流程

如果上传过程中需要取消，可以调用：
```
POST /api/uploads/abort (中止上传并清理已上传的分片)
```

---

## 业务逻辑说明

### 1. 初始化上传（Initialize Upload）

**目的**：向服务器声明要上传一个文件，获取上传会话 ID 和分片配置。

**处理流程**：
1. 客户端通过 `multipart/form-data` 方式提交文件
2. 服务器提取文件元数据：文件名、文件大小、内容类型
3. 验证文件类型（仅接受视频文件，content-type 必须是 `video/*`）
4. 验证文件大小（默认限制 2GB）
5. 验证分片大小（5MB - 5GB 之间）
6. 生成唯一的对象存储路径：`uploads/{UUID}/{文件名}`
7. 在 MinIO 中创建分片上传会话
8. 计算需要的分片总数：`总大小 ÷ 分片大小（向上取整）`
9. 返回 `uploadId`、`objectKey`、`partSize`、分片编号范围

**返回数据**：
- `uploadId`: 本次上传的唯一标识符，后续所有操作都需要它
- `objectKey`: 文件在 MinIO 中的存储路径
- `partSize`: 每个分片的大小（字节）
- `minPartNumber`: 起始分片编号（总是 1）
- `maxPartNumber`: 最大分片编号（总分片数）

### 2. 获取预签名 URL（Get Presigned URLs）

**目的**：获取临时的、带签名的 URL，用于直接上传分片到 MinIO。

**处理流程**：
1. 客户端请求指定范围的分片编号（如第 1-5 片）
2. 服务器验证参数（uploadId、objectKey、分片编号范围）
3. 限制单次请求最多 100 个分片
4. 为每个分片生成一个预签名的 PUT URL
5. URL 默认有效期 60 分钟（可配置）
6. 返回分片编号、URL 和过期时间

**为什么使用预签名 URL？**
- **减少服务器负载**：数据直接从客户端传到 MinIO，不经过应用服务器
- **提高性能**：避免应用服务器成为瓶颈
- **安全性**：URL 有时间限制，无法被长期滥用
- **支持并行**：客户端可以同时上传多个分片

### 3. 上传分片（Upload Parts）

**目的**：将文件分片直接上传到 MinIO。

**处理流程**：
1. 客户端将文件分割成指定大小的分片
2. 使用预签名 URL 发送 PUT 请求
3. 请求头必须包含 `Content-Type: application/octet-stream`
4. MinIO 接收数据并返回 `ETag` 响应头
5. **重要**：客户端必须保存每个分片的 `ETag`，完成上传时需要提交

**注意事项**：
- 除最后一个分片外，每个分片必须至少 5MB（MinIO/S3 限制）
- 最后一个分片可以小于 5MB
- 分片可以并行上传，但必须记录好分片编号和对应的 ETag
- 如果上传失败，可以重新获取该分片的预签名 URL 再次上传

### 4. 检查上传状态（Check Upload Status）

**目的**：查询哪些分片已经成功上传，用于断点续传。

**处理流程**：
1. 客户端提供 `uploadId` 和 `objectKey`
2. 服务器查询 MinIO，获取已上传的分片列表
3. 返回每个分片的编号、ETag 和大小
4. 客户端对比本地记录，找出未上传或上传失败的分片

**使用场景**：
- 网络中断后恢复上传
- 监控上传进度
- 完成前验证所有分片是否都已上传

### 5. 完成上传（Complete Upload）

**目的**：通知 MinIO 所有分片已上传完毕，请求合并成完整文件。

**处理流程**：
1. 客户端提交 `uploadId`、`objectKey` 和所有分片的 ETag 列表
2. 服务器验证分片列表：
   - 分片编号必须从 1 开始
   - 分片编号必须连续（1, 2, 3...）
   - 不能有重复的分片编号
   - 每个分片必须有有效的 ETag
3. 检查数据库，确保该上传未曾完成（防止重复提交）
4. 向 MinIO 发送完成请求，MinIO 合并所有分片
5. 从 MinIO 获取最终文件的元数据（大小、ETag）
6. 将文件元数据保存到 MySQL 数据库
7. 生成下载链接（预签名 URL）
8. 返回文件信息和下载链接

**如果完成失败**：
- 服务器会自动调用中止接口清理已上传的分片
- 避免产生无用的存储占用

### 6. 中止上传（Abort Upload）

**目的**：取消上传，清理已上传的分片，释放存储空间。

**处理流程**：
1. 客户端提交 `uploadId` 和 `objectKey`
2. 服务器向 MinIO 发送中止请求
3. MinIO 删除该上传会话的所有已上传分片
4. 返回 204 No Content 表示成功

**应该何时中止**：
- 用户主动取消上传
- 上传失败且不打算重试
- 上传超时

---

## Apifox 接口测试步骤

### 前置准备

1. **确保服务运行**：
   ```bash
   # 启动 Spring Boot 应用
   mvn spring-boot:run
   ```
   默认端口：8080

2. **准备测试文件**：
   准备一个视频文件用于测试，例如 `test-video.mp4`（项目根目录已包含）

3. **在 Apifox 中配置环境变量**：
   - 打开 Apifox，创建新环境（如 "Local"）
   - 添加以下变量：
     ```
     base_url = http://localhost:8080
     upload_id = (空，后续自动设置)
     object_key = (空，后续自动设置)
     max_part_number = (空，后续自动设置)
     part_size = (空，后续自动设置)
     presigned_url_part1 = (空，后续自动设置)
     etag_part1 = (空，后续自动设置)
     presigned_url_part2 = (空，后续自动设置)
     etag_part2 = (空，后续自动设置)
     ```

---

### 步骤 1：初始化上传

#### 创建请求

1. **请求类型**：`POST`
2. **URL**：`{{base_url}}/api/uploads/init`
3. **Body 类型**：`form-data`
4. **Body 参数**：
   | 参数名 | 类型 | 必填 | 说明 |
   |--------|------|------|------|
   | file | File | 是 | 选择要上传的视频文件 |
   | chunkSize | Text | 否 | 分片大小（字节），如 8388608（8MB）|

5. **后置操作（Post-processors）**：
   在 Apifox 的"后置操作"标签页中添加脚本，自动提取响应数据到环境变量：
   ```javascript
   // 提取响应数据到环境变量
   const response = pm.response.json();
   pm.environment.set("upload_id", response.uploadId);
   pm.environment.set("object_key", response.objectKey);
   pm.environment.set("max_part_number", response.maxPartNumber);
   pm.environment.set("part_size", response.partSize);
   console.log("Upload initialized:", response);
   ```

#### 示例响应

```json
{
  "uploadId": "abc123xyz",
  "objectKey": "uploads/550e8400-e29b-41d4-a716-446655440000/test-video.mp4",
  "partSize": 8388608,
  "minPartNumber": 1,
  "maxPartNumber": 63
}
```

---

### 步骤 2：获取预签名 URL（第 1 片）

#### 创建请求

1. **请求类型**：`GET`
2. **URL**：`{{base_url}}/api/uploads/{{upload_id}}/parts`
3. **Query 参数**：
   | 参数名 | 值 | 说明 |
   |--------|-----|------|
   | objectKey | `{{object_key}}` | 从步骤 1 获取 |
   | startPartNumber | `1` | 起始分片编号 |
   | endPartNumber | `1` | 结束分片编号 |

4. **后置操作**：
   ```javascript
   const response = pm.response.json();
   if (response && response.length > 0) {
       pm.environment.set("presigned_url_part1", response[0].url);
       console.log("Presigned URL for part 1:", response[0].url);
       console.log("Expires at:", response[0].expiresAt);
   }
   ```

#### 示例响应

```json
[
  {
    "partNumber": 1,
    "url": "http://192.168.0.245:9000/remote-consent/uploads/550e8400-e29b-41d4-a716-446655440000/test-video.mp4?uploadId=abc123xyz&partNumber=1&X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
    "expiresAt": "2024-01-01T12:00:00Z"
  }
]
```

---

### 步骤 3：上传第 1 个分片

#### 准备分片文件

在命令行中将测试文件分割成分片（根据初始化返回的 partSize）：

```bash
# Linux/Mac
split -b 8388608 test-video.mp4 part_

# Windows (使用 PowerShell)
# 创建 split.ps1 文件，内容如下：
param($FilePath, $ChunkSize)
$file = [System.IO.File]::OpenRead($FilePath)
$buffer = New-Object byte[] $ChunkSize
$partNum = 1
while (($read = $file.Read($buffer, 0, $ChunkSize)) -gt 0) {
    $outFile = "part_$partNum.bin"
    [System.IO.File]::WriteAllBytes($outFile, $buffer[0..($read-1)])
    $partNum++
}
$file.Close()

# 运行脚本
.\split.ps1 -FilePath "test-video.mp4" -ChunkSize 8388608
```

这会生成 `part_aa`, `part_ab`, `part_ac`... 等分片文件（Linux/Mac）或 `part_1.bin`, `part_2.bin`... （Windows）。

#### 创建请求

1. **请求类型**：`PUT`
2. **URL**：`{{presigned_url_part1}}` （使用环境变量）
3. **Headers**：
   | Header | 值 |
   |--------|-----|
   | Content-Type | application/octet-stream |
4. **Body 类型**：`binary` （二进制）
5. **Body**：选择分片文件 `part_aa` 或 `part_1.bin`

6. **后置操作**：
   ```javascript
   // 从响应头中提取 ETag
   const etag = pm.response.headers.get("ETag");
   if (etag) {
       // 移除引号（如果有）
       const cleanEtag = etag.replace(/"/g, "");
       pm.environment.set("etag_part1", cleanEtag);
       console.log("Part 1 uploaded, ETag:", cleanEtag);
   } else {
       console.error("No ETag found in response headers");
   }
   ```

#### 注意事项

- **重要**：响应头中的 `ETag` 必须保存，完成上传时需要
- ETag 格式示例：`"d41d8cd98f00b204e9800998ecf8427e"` （可能带引号）
- 上传成功通常返回 200 或 204 状态码
- 如果返回 403，说明 URL 已过期或签名错误

---

### 步骤 4：获取预签名 URL（第 2 片）并上传

重复步骤 2 和步骤 3，但修改参数：

#### 获取第 2 片的预签名 URL

- **Query 参数**：
  - `startPartNumber`: `2`
  - `endPartNumber`: `2`
- **后置操作**：保存到 `presigned_url_part2`

#### 上传第 2 片

- **URL**：`{{presigned_url_part2}}`
- **Body**：选择 `part_ab` 或 `part_2.bin`
- **后置操作**：保存到 `etag_part2`

**提示**：实际应用中，可以一次获取多个分片的 URL（如 1-10），然后并行上传。

---

### 步骤 5：检查上传状态（可选）

#### 创建请求

1. **请求类型**：`GET`
2. **URL**：`{{base_url}}/api/uploads/{{upload_id}}/status`
3. **Query 参数**：
   | 参数名 | 值 |
   |--------|-----|
   | objectKey | `{{object_key}}` |

#### 示例响应

```json
[
  {
    "partNumber": 1,
    "etag": "d41d8cd98f00b204e9800998ecf8427e",
    "size": 8388608
  },
  {
    "partNumber": 2,
    "etag": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
    "size": 8388608
  }
]
```

这一步可以验证分片是否真的上传成功了。

---

### 步骤 6：完成上传

#### 创建请求

1. **请求类型**：`POST`
2. **URL**：`{{base_url}}/api/uploads/complete`
3. **Body 类型**：`JSON`
4. **Body 内容**：
   ```json
   {
     "uploadId": "{{upload_id}}",
     "objectKey": "{{object_key}}",
     "parts": [
       {
         "partNumber": 1,
         "eTag": "{{etag_part1}}"
       },
       {
         "partNumber": 2,
         "eTag": "{{etag_part2}}"
       }
     ]
   }
   ```

   **重要说明**：
   - `parts` 数组必须包含所有分片的信息
   - 分片编号必须从 1 开始，连续递增
   - ETag 必须与上传时返回的完全一致
   - 如果文件有更多分片，需要添加到数组中

#### 示例响应

```json
{
  "id": 1,
  "filename": "test-video.mp4",
  "size": 524288000,
  "objectKey": "uploads/550e8400-e29b-41d4-a716-446655440000/test-video.mp4",
  "status": "COMPLETED",
  "downloadUrl": "http://192.168.0.245:9000/remote-consent/uploads/550e8400-e29b-41d4-a716-446655440000/test-video.mp4?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
  "createdAt": "2024-01-01T10:00:00"
}
```

此时文件已经完整上传并合并成功，可以使用 `downloadUrl` 下载文件验证。

---

### 步骤 7（可选）：中止上传

如果需要取消上传，可以使用此接口。

#### 创建请求

1. **请求类型**：`POST`
2. **URL**：`{{base_url}}/api/uploads/abort`
3. **Body 类型**：`JSON`
4. **Body 内容**：
   ```json
   {
     "uploadId": "{{upload_id}}",
     "objectKey": "{{object_key}}"
   }
   ```

#### 响应

- **状态码**：204 No Content（无响应内容）
- 表示中止成功，已清理所有分片

---

## 完整测试示例

### 使用 Apifox 的"测试场景"功能

Apifox 支持创建测试场景（Test Scenario），可以按顺序执行多个接口。

#### 创建测试场景

1. 在 Apifox 中点击"测试管理" → "新建场景"
2. 命名为"分片上传完整流程"
3. 按顺序添加以下步骤：
   - **步骤 1**：POST /api/uploads/init
   - **步骤 2**：GET /api/uploads/{uploadId}/parts（分片 1）
   - **步骤 3**：PUT 预签名 URL（上传分片 1）
   - **步骤 4**：GET /api/uploads/{uploadId}/parts（分片 2）
   - **步骤 5**：PUT 预签名 URL（上传分片 2）
   - **步骤 6**：GET /api/uploads/{uploadId}/status（可选）
   - **步骤 7**：POST /api/uploads/complete

4. 配置每个步骤的后置操作脚本（如前面所述）

5. 点击"运行场景"，自动执行完整流程

---

### 测试不同场景

#### 场景 1：小文件上传（单个分片）

- **文件大小**：< 8MB
- **预期**：`maxPartNumber = 1`，只需上传一个分片

#### 场景 2：大文件上传（多个分片）

- **文件大小**：> 100MB
- **预期**：多个分片，测试并行上传性能

#### 场景 3：断点续传

1. 上传部分分片（如 1-5）
2. 停止上传
3. 调用 `/status` 接口查看已上传的分片
4. 继续上传剩余分片（6-N）
5. 完成上传

#### 场景 4：取消上传

1. 初始化上传
2. 上传几个分片
3. 调用 `/abort` 接口
4. 验证分片已被清理（再次调用 `/status` 应返回错误）

#### 场景 5：错误处理

- **测试 ETag 错误**：完成上传时提供错误的 ETag
- **测试分片缺失**：完成上传时缺少某些分片
- **测试分片顺序错误**：parts 数组中分片编号不连续

---

## 常见问题

### Q1: 为什么需要手动分割文件？

**A**: 分片上传的设计理念是客户端负责分割文件，这样可以：
- 减少服务器内存占用（不需要在内存中缓存整个文件）
- 支持并行上传（客户端可以同时上传多个分片）
- 提高效率（不需要服务器端中转）

在实际的前端应用中，可以使用 `Blob.slice()` 方法在浏览器中分割文件，无需命令行工具。

### Q2: 预签名 URL 的有效期是多久？

**A**: 默认 60 分钟，可以在 `application.yml` 中配置 `upload.presigned-url-expiration-minutes`。

如果 URL 过期，可以重新调用 `/api/uploads/{uploadId}/parts` 接口获取新的 URL。

### Q3: 如何处理网络中断？

**A**: 
1. 调用 `/api/uploads/{uploadId}/status` 查询已上传的分片
2. 对比本地记录，找出未上传的分片
3. 重新获取这些分片的预签名 URL
4. 上传缺失的分片
5. 完成上传

### Q4: ETag 是什么？为什么重要？

**A**: ETag（Entity Tag）是对象的唯一标识符，类似文件的 MD5 校验和。MinIO 用它来：
- 验证分片数据的完整性
- 防止数据损坏
- 确保客户端提交的分片列表与实际上传的分片一致

如果提供错误的 ETag，完成上传时会失败。

### Q5: 可以一次获取所有分片的预签名 URL 吗？

**A**: 可以，但有限制：
- 单次请求最多 100 个分片
- 如果文件有 200 个分片，需要分 2 次请求

示例：
```
GET /api/uploads/{uploadId}/parts?objectKey={key}&startPartNumber=1&endPartNumber=100
GET /api/uploads/{uploadId}/parts?objectKey={key}&startPartNumber=101&endPartNumber=200
```

### Q6: 如何在 Apifox 中测试并行上传？

**A**: Apifox 默认串行执行请求。要测试并行上传：
1. 在"测试场景"中，将多个上传请求的"等待上一步完成"选项取消勾选
2. 或者开多个 Apifox 窗口同时执行上传请求

实际应用中，客户端（浏览器、移动端）会使用多线程/多 Promise 实现并行上传。

### Q7: 分片大小应该设置为多少？

**A**: 建议：
- **最小值**：5MB（MinIO/S3 限制）
- **推荐值**：8MB - 16MB（平衡性能和网络稳定性）
- **最大值**：5GB（MinIO/S3 限制）

较小的分片：
- ✅ 网络中断时损失小
- ❌ 分片数量多，管理复杂

较大的分片：
- ✅ 分片数量少，管理简单
- ❌ 网络中断时损失大

### Q8: 完成上传后，文件保存在哪里？

**A**: 
- **物理存储**：MinIO 服务器（默认 http://192.168.0.245:9000）
- **对象路径**：`uploads/{UUID}/{文件名}`
- **元数据**：MySQL 数据库的 `video_recording` 表

可以通过以下方式访问：
- 使用返回的 `downloadUrl`（有效期 60 分钟）
- 登录 MinIO 控制台（http://192.168.0.245:9000）查看

### Q9: 如何验证上传是否成功？

**A**: 方法 1 - 使用下载链接：
```bash
curl -O "DOWNLOAD_URL_FROM_RESPONSE"
```

方法 2 - 对比文件大小：
```bash
ls -lh test-video.mp4  # 原始文件
# 对比响应中的 size 字段
```

方法 3 - 使用 MinIO 客户端：
```bash
mc ls myminio/remote-consent/uploads/
```

### Q10: Apifox 中如何保存测试用例？

**A**: 
1. 将所有接口请求保存到一个"接口集合"中
2. 命名为"MinIO 分片上传"
3. 为每个接口添加示例响应和说明文档
4. 导出为 JSON 或 HAR 格式，方便团队共享
5. 使用 Apifox 的"数据模型"功能定义 DTO 结构

---

## 总结

分片上传虽然流程较复杂，但提供了强大的功能：

1. **初始化** → 获取上传会话 ID
2. **获取 URL** → 拿到直传 MinIO 的临时链接
3. **上传分片** → 直接传到 MinIO，记录 ETag
4. **检查状态** → 断点续传的关键
5. **完成上传** → 提交 ETag 列表，合并文件
6. **中止上传** → 清理临时数据

在 Apifox 中测试时，善用环境变量和后置脚本，可以大大简化测试流程。

如有疑问，请查看以下文档：
- [README.md](./README.md) - 完整 API 文档
- [QUICK_START.md](./QUICK_START.md) - 快速开始指南
- [MULTIPART_FILE_INIT_CHANGE.md](./MULTIPART_FILE_INIT_CHANGE.md) - API 变更说明
