# MySQL 8.0 数据库初始化指南

本目录包含基于项目实体类 `VideoRecording` 的 MySQL 8.0 数据库初始化脚本。

## 文件说明

### 1. 完整初始化脚本
**文件**: `src/main/resources/db/migration/V1__Initialize_database.sql`

这是一个完整的数据库初始化脚本，包含：
- 数据库创建
- 表结构定义（基于 VideoRecording 实体）
- 索引优化（单索引和复合索引）
- 触发器（自动设置 created_at）
- 视图（统计视图）
- 存储过程（清理和查询）
- 示例数据
- 权限设置
- 性能优化

### 2. 快速初始化脚本
**文件**: `mysql8-init.sql`

这是一个简化的初始化脚本，包含：
- 数据库创建
- 基本表结构
- 必要索引
- 适合快速部署和测试

### 3. Docker 初始化脚本
**文件**: `docker-init.sh`

用于 Docker 容器中的自动化初始化，包含：
- 等待 MySQL 服务启动
- 执行 SQL 初始化
- 创建应用用户

### 4. 生产环境初始化脚本
**文件**: `mysql8-production-init.sql`

生产环境优化的初始化脚本，包含：
- 更严格的数据约束和检查
- 性能优化的表结构
- 完整的用户权限管理
- 事件调度器（定时清理）
- 系统健康监控存储过程
- 审计和日志功能

### 5. 测试验证脚本
**文件**: `test-mysql-init.sh`

自动化测试脚本，用于验证数据库初始化是否正确：
- 数据库连接测试
- 表结构验证
- 索引验证
- 数据插入和查询测试
- 约束验证
- 自动清理测试数据

## 使用方法

### 方法一：直接执行 SQL

```bash
# 连接到 MySQL 服务器
mysql -u root -p

# 执行完整初始化脚本
source /path/to/V1__Initialize_database.sql;

# 或者执行快速初始化脚本
source /path/to/mysql8-init.sql;
```

### 方法二：命令行执行

```bash
# 执行完整初始化
mysql -u root -p < src/main/resources/db/migration/V1__Initialize_database.sql

# 执行快速初始化
mysql -u root -p < mysql8-init.sql
```

### 方法三：Docker 部署

```bash
# 设置环境变量
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_ROOT_USER=root
export MYSQL_ROOT_PASSWORD=your_password
export MYSQL_APP_USER=app_user
export MYSQL_APP_PASSWORD=app_password

# 执行初始化脚本
./docker-init.sh
```

### 方法四：Docker Compose

在 `docker-compose.yml` 中添加：

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: minio_upload
      MYSQL_USER: app_user
      MYSQL_PASSWORD: app_password
    volumes:
      - ./mysql8-init.sql:/docker-entrypoint-initdb.d/mysql8-init.sql
      - ./docker-init.sh:/docker-entrypoint-initdb.d/docker-init.sh
    ports:
      - "3306:3306"
```

### 方法五：生产环境部署

```bash
# 执行生产环境初始化脚本
mysql -u root -p < mysql8-production-init.sql

# 或者分步执行
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS minio_upload;"
mysql -u root -p minio_upload < mysql8-production-init.sql
```

### 方法六：测试验证

```bash
# 运行测试脚本验证初始化
./test-mysql-init.sh

# 或者指定自定义参数
MYSQL_HOST=localhost MYSQL_PORT=3306 MYSQL_ROOT_USER=root MYSQL_ROOT_PASSWORD=your_password ./test-mysql-init.sh
```

## 数据库结构

### 主表：video_recordings

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | BIGINT | 主键 | AUTO_INCREMENT |
| user_id | VARCHAR(100) | 用户ID | 索引 |
| filename | VARCHAR(500) | 文件名 | NOT NULL |
| size | BIGINT | 文件大小（字节） | NOT NULL |
| duration | BIGINT | 视频时长（秒） | NULL |
| width | INT | 视频宽度 | NULL |
| height | INT | 视频高度 | NULL |
| codec | VARCHAR(50) | 视频编码 | NULL |
| object_key | VARCHAR(1000) | S3对象键 | NOT NULL, UNIQUE |
| status | VARCHAR(50) | 上传状态 | NOT NULL, 索引 |
| checksum | VARCHAR(255) | 文件校验和 | NULL |
| created_at | DATETIME | 创建时间 | NOT NULL, 索引 |
| updated_at | DATETIME | 更新时间 | NOT NULL |

#### 生产环境额外字段和约束

- **updated_at**: 自动更新的时间戳，用于跟踪数据变更
- **约束检查**: 
  - `chk_status`: 状态值约束（COMPLETED, FAILED, IN_PROGRESS, PENDING, ABORTED）
  - `chk_size_positive`: 文件大小必须为正数
  - `chk_duration_positive`: 时长必须为非负数
  - `chk_width_positive`: 宽度必须为正数
  - `chk_height_positive`: 高度必须为正数

### 索引说明

- **idx_object_key**: 对象键唯一索引
- **idx_user_id**: 用户ID索引
- **idx_status**: 状态索引
- **idx_created_at**: 创建时间索引
- **idx_user_status_created**: 用户+状态+时间复合索引
- **idx_status_created**: 状态+时间复合索引

### 视图

- **video_statistics**: 全局视频统计
- **user_video_statistics**: 用户视频统计

### 存储过程

#### 基础存储过程
- **cleanup_old_records**: 清理过期记录
- **get_user_videos**: 获取用户视频列表

#### 生产环境存储过程
- **sp_cleanup_old_records**: 生产环境清理过期记录（带事务）
- **sp_get_user_videos_paginated**: 分页获取用户视频列表
- **sp_get_system_health**: 获取系统健康状态统计

### 事件调度器（生产环境）

- **evt_cleanup_old_records**: 每日自动清理过期记录

## 配置说明

### Spring Boot 配置

在 `application.yml` 中已配置：

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/minio_upload?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root123}
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

### 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| DB_URL | jdbc:mysql://localhost:3306/minio_upload?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC | 数据库连接URL |
| DB_USERNAME | root | 数据库用户名 |
| DB_PASSWORD | root123 | 数据库密码 |

## 性能优化建议

1. **索引优化**: 脚本已创建必要的单索引和复合索引
2. **表压缩**: 启用了 InnoDB 行压缩
3. **字符集**: 使用 utf8mb4 支持完整 Unicode
4. **连接池**: 在 application.yml 中已配置 HikariCP

## 生产环境配置

### 用户权限管理

生产环境脚本会创建以下用户：

| 用户名 | 权限 | 用途 |
|--------|------|------|
| readonly_user | SELECT | 报表和监控 |
| app_user | SELECT, INSERT, UPDATE, DELETE | 应用程序 |
| backup_user | SELECT, LOCK TABLES, SHOW VIEW | 数据备份 |

### 安全建议

1. **密码安全**: 生产环境请修改默认密码
2. **网络访问**: 限制数据库访问IP范围
3. **SSL连接**: 生产环境建议启用SSL
4. **审计日志**: 启用MySQL审计日志
5. **定期备份**: 设置定期备份策略

### 监控指标

生产环境提供了系统健康监控存储过程 `sp_get_system_health`，可以获取：
- 总视频数量
- 待处理上传数量
- 失败上传数量
- 平均上传时间
- 磁盘使用量

## 常见问题

### Q: 如何重置数据库？
A: 删除数据库后重新执行初始化脚本：
```sql
DROP DATABASE IF EXISTS minio_upload;
```

### Q: 如何添加新字段？
A: 修改实体类后，Hibernate 会自动更新表结构（ddl-auto: update）

### Q: 如何备份数据？
A: 使用 mysqldump：
```bash
mysqldump -u root -p minio_upload > backup.sql
```

### Q: 如何优化性能？
A: 定期执行：
```sql
ANALYZE TABLE video_recordings;
OPTIMIZE TABLE video_recordings;
```

## 版本信息

- MySQL 版本: 8.0+
- Spring Boot 版本: 3.2.0
- Java 版本: 8+
- Hibernate 方言: MySQL8Dialect