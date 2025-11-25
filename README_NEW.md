# PDF转换服务 - 多人签署场景

## 概述

这是一个专注于PDF上传并转换为图片的Spring Boot服务，特别针对多人签署场景进行优化。

### 核心特性

- ✅ **PDF转图片**：高质量PDF页面转换为PNG/JPG图片
- ✅ **数据持久化**：使用MySQL存储任务和图片信息，重启不丢失
- ✅ **多人签署优化**：支持基类+增量转换，节省存储资源
- ✅ **异步处理**：后台转换，不阻塞客户端
- ✅ **灵活查询**：支持按任务ID、业务ID、用户ID查询
- ✅ **分页支持**：大文档分页查询，性能优异

## 业务场景

### 多人签署流程

1. **第一人签署**：上传原始PDF，全量转换所有页面（基类图片）
2. **第二人签署**：上传签署后的PDF，只转换变更的页面（增量图片）
3. **第三人签署**：上传签署后的PDF，只转换变更的页面（增量图片）
4. **查询展示**：根据用户ID查询，自动合并基类图片和用户特定图片

### 存储优化

- 基类图片：全量页面，所有人共享
- 增量图片：只存储变更页面，按用户区分
- 查询合并：自动组合基类+增量，呈现完整文档

## 快速开始

### 1. 环境要求

- Java 17
- MySQL 8.0+
- Maven 3.8+

### 2. 配置数据库

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/minio_upload?useUnicode=true&characterEncoding=utf-8
    username: root
    password: your_password
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### 3. 启动服务

```bash
mvn spring-boot:run
```

### 4. 测试接口

```bash
# 第一人签署（全量转换）
curl -X POST "http://localhost:8080/api/pdf/upload" \
  -F "file=@contract.pdf" \
  -F "businessId=CONTRACT-001" \
  -F "userId=SIGNER-001"

# 第二人签署（增量转换）
curl -X POST "http://localhost:8080/api/pdf/upload" \
  -F "file=@contract-signed-2.pdf" \
  -F "businessId=CONTRACT-001" \
  -F "userId=SIGNER-002" \
  -F "pages=1,10,15"

# 查询SIGNER-002的图片
curl "http://localhost:8080/api/pdf/images?businessId=CONTRACT-001&userId=SIGNER-002"
```

## API文档

详见 [PDF_API_USAGE_GUIDE.md](PDF_API_USAGE_GUIDE.md)

### 主要接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/pdf/upload` | POST | 上传PDF并转换 |
| `/api/pdf/task/{taskId}` | GET | 查询任务详情 |
| `/api/pdf/tasks` | GET | 查询任务列表 |
| `/api/pdf/progress/{taskId}` | GET | 查询转换进度 |
| `/api/pdf/images` | GET | 查询PDF图片 |

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
  converted_pages TEXT,  -- JSON数组
  status VARCHAR(20) NOT NULL,
  is_base BOOLEAN NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
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
  is_base BOOLEAN NOT NULL,
  width INT,
  height INT,
  file_size BIGINT,
  created_at DATETIME NOT NULL
);
```

## 技术架构

### 核心技术栈

- **Spring Boot 3.2.0**：应用框架
- **Spring Data JPA**：数据访问层
- **MySQL 8.0**：数据存储
- **Flyway**：数据库迁移
- **Apache PDFBox 3.0.1**：PDF渲染
- **Jackson**：JSON处理

### 项目结构

```
src/main/java/com/example/minioupload/
├── config/              # 配置类
├── controller/          # REST控制器
│   ├── PdfConversionController.java
│   └── GlobalExceptionHandler.java
├── dto/                 # 数据传输对象
│   ├── PdfConversionTaskRequest.java
│   ├── PdfConversionTaskResponse.java
│   ├── PdfImageResponse.java
│   └── PdfPageImageInfo.java
├── model/               # 实体类
│   ├── PdfConversionTask.java
│   └── PdfPageImage.java
├── repository/          # 数据访问层
│   ├── PdfConversionTaskRepository.java
│   └── PdfPageImageRepository.java
└── service/             # 业务逻辑层
    ├── PdfUploadService.java
    └── PdfToImageService.java
```

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

### 参数说明

- `enabled`：是否启用PDF转换功能
- `temp-directory`：临时文件存储目录
- `max-file-size`：最大文件大小限制
- `dpi`：图片渲染DPI（越高越清晰）
- `format`：图片格式（PNG/JPG）

## 测试

### 自动化测试脚本

```bash
# 运行多人签署场景测试
./test-pdf-multi-signer.sh
```

### 手动测试

1. 准备一个PDF文件 `test-contract.pdf`
2. 运行测试脚本
3. 查看返回的任务ID和图片信息

## 性能指标

- **转换速度**：约1-2秒/页（300 DPI）
- **图片大小**：约1-2MB/页（PNG格式）
- **并发能力**：支持多任务并发处理
- **数据持久化**：所有数据存储在MySQL

## 最佳实践

1. **首次上传**：不传`pages`参数，执行全量转换
2. **后续上传**：传`pages`参数，只转换变更页面
3. **查询优化**：使用分页查询，避免一次加载过多数据
4. **错误处理**：检查API返回的status字段，处理错误情况
5. **并发控制**：同一businessId可以并发上传，但建议按顺序执行

## 故障排查

### 常见问题

1. **转换失败**
   - 检查PDF文件是否损坏
   - 检查磁盘空间是否充足
   - 查看日志获取详细错误信息

2. **查询不到图片**
   - 确认任务状态是否为COMPLETED
   - 检查businessId和userId是否正确
   - 确认基类转换是否已完成

3. **增量转换失败**
   - 确保该businessId已有基类转换记录
   - 检查pages参数格式是否正确

## 许可证

MIT License

## 作者

MinIO Upload Team

## 更新日志

### v3.0.0 (2024-11-25)
- ✅ 重构为专注PDF转换的服务
- ✅ 新增多人签署场景支持
- ✅ 实现基类+增量转换机制
- ✅ 数据库持久化存储
- ✅ 完善的查询和分页功能

### v2.0.0
- 视频压缩功能（已移除）
- 多段上传功能（已移除）

### v1.0.0
- 初始版本
