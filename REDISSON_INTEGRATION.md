# Redisson 集成文档

## 概述

本项目已成功集成Redisson，提供了强大的Redis分布式功能支持。

## 版本信息

- **Redisson**: 3.25.0
- **Spring Boot**: 3.2.0
- **Java**: 17

## 配置说明

### 1. Maven依赖

已在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.25.0</version>
</dependency>
```

### 2. Redis配置

在 `application.yml` 中配置Redis连接信息：

```yaml
spring:
  redis:
    # Redis服务器地址
    host: ${REDIS_HOST:localhost}
    # Redis服务器端口
    port: ${REDIS_PORT:6379}
    # Redis数据库索引（0-15）
    database: ${REDIS_DATABASE:0}
    # Redis密码（如果没有设置密码，可以留空）
    password: ${REDIS_PASSWORD:}
    # 连接超时时间（毫秒）
    timeout: 10000
    # Lettuce连接池配置
    lettuce:
      pool:
        max-active: 8
        max-wait: -1
        max-idle: 8
        min-idle: 0
```

### 3. 环境变量配置

可以通过环境变量覆盖默认配置：

```bash
# Redis主机地址
export REDIS_HOST=192.168.1.100

# Redis端口
export REDIS_PORT=6379

# Redis数据库索引
export REDIS_DATABASE=0

# Redis密码（如果需要）
export REDIS_PASSWORD=your_password
```

### 4. 禁用Redisson

如果在某些环境下不需要Redis支持，可以通过配置禁用：

```yaml
spring:
  redis:
    enabled: false
```

或通过环境变量：

```bash
export SPRING_REDIS_ENABLED=false
```

## 核心组件

### 1. RedissonConfig

配置类位于：`com.example.minioupload.config.RedissonConfig`

**主要功能：**
- 创建和配置RedissonClient Bean
- 支持单机模式（可扩展为集群模式和哨兵模式）
- 自动重试和连接池管理
- 条件化配置，可通过开关启用/禁用

**配置特性：**
- 连接池大小：64
- 最小空闲连接：10
- 连接超时：3秒（可配置）
- 重试次数：3次
- 重试间隔：1.5秒

### 2. RedissonTestService

测试服务类位于：`com.example.minioupload.service.RedissonTestService`

**提供的方法：**
- `testSet(String key, String value)` - 设置键值对（5分钟过期）
- `testGet(String key)` - 获取键值
- `testDelete(String key)` - 删除键
- `testConnection()` - 测试Redis连接

### 3. RedisTestController

测试控制器位于：`com.example.minioupload.controller.RedisTestController`

**API端点：**

#### 测试连接
```bash
GET /api/redis/test-connection
```

**响应示例：**
```json
{
  "success": true,
  "message": "Redis连接成功"
}
```

#### 设置键值
```bash
POST /api/redis/set?key=test&value=hello
```

**响应示例：**
```json
{
  "success": true,
  "message": "设置成功",
  "key": "test",
  "value": "hello"
}
```

#### 获取键值
```bash
GET /api/redis/get?key=test
```

**响应示例：**
```json
{
  "success": true,
  "key": "test",
  "value": "hello",
  "message": "获取成功"
}
```

#### 删除键
```bash
DELETE /api/redis/delete?key=test
```

**响应示例：**
```json
{
  "success": true,
  "message": "删除成功",
  "key": "test"
}
```

## 使用示例

### 在Service中使用Redisson

```java
@Service
@Slf4j
public class YourService {

    @Autowired(required = false)
    private RedissonClient redissonClient;

    public void yourMethod() {
        if (redissonClient != null) {
            // 使用RBucket存储简单值
            RBucket<String> bucket = redissonClient.getBucket("mykey");
            bucket.set("myvalue", 300, TimeUnit.SECONDS);
            
            // 获取值
            String value = bucket.get();
            
            // 使用RMap存储Map
            RMap<String, String> map = redissonClient.getMap("mymap");
            map.put("field1", "value1");
            
            // 使用分布式锁
            RLock lock = redissonClient.getLock("mylock");
            try {
                if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                    try {
                        // 执行需要加锁的业务逻辑
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

## Redisson功能特性

### 1. 分布式对象

- **RBucket** - 通用对象桶
- **RMap** - 分布式Map
- **RSet** - 分布式Set
- **RList** - 分布式List
- **RQueue** - 分布式队列
- **RDeque** - 分布式双端队列

### 2. 分布式锁

- **RLock** - 可重入锁
- **RFairLock** - 公平锁
- **RReadWriteLock** - 读写锁
- **RSemaphore** - 信号量
- **RCountDownLatch** - 倒计数锁

### 3. 分布式服务

- **RRemoteService** - 远程服务
- **RExecutorService** - 分布式执行器
- **RScheduledExecutorService** - 分布式调度器

### 4. 实时数据

- **RTopic** - 发布订阅
- **RPatternTopic** - 模式订阅
- **RBloomFilter** - 布隆过滤器
- **RBitSet** - 分布式位集

## 部署配置

### Docker环境

在 `docker-compose.yml` 中添加Redis服务：

```yaml
services:
  redis:
    image: redis:7-alpine
    container_name: redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes
    restart: unless-stopped

  app:
    # ... 应用配置
    environment:
      - REDIS_HOST=redis
      - REDIS_PORT=6379
    depends_on:
      - redis

volumes:
  redis-data:
```

### 生产环境建议

1. **启用密码认证**
   ```yaml
   spring:
     redis:
       password: ${REDIS_PASSWORD}
   ```

2. **使用Redis Sentinel（高可用）**
   ```yaml
   spring:
     redis:
       sentinel:
         master: mymaster
         nodes: 
           - sentinel1:26379
           - sentinel2:26379
           - sentinel3:26379
   ```

3. **使用Redis Cluster（分布式）**
   ```yaml
   spring:
     redis:
       cluster:
         nodes:
           - cluster1:6379
           - cluster2:6379
           - cluster3:6379
   ```

## 故障排查

### 连接失败

如果Redis连接失败，应用仍然可以启动，但相关功能将不可用。查看日志：

```
ERROR - Redisson客户端初始化失败: Unable to connect to Redis server
WARN  - 应用将在没有Redis支持的情况下继续运行
```

**解决方案：**
1. 检查Redis服务是否运行：`redis-cli ping`
2. 检查网络连接和防火墙设置
3. 验证host和port配置是否正确
4. 检查密码配置（如果需要）

### 超时问题

如果遇到超时，可以调整配置：

```yaml
spring:
  redis:
    timeout: 20000  # 增加到20秒
    connect-timeout: 5000  # 增加连接超时到5秒
```

## 性能优化

### 1. 连接池优化

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20  # 根据并发量调整
        max-idle: 10
        min-idle: 5
```

### 2. Pipeline批量操作

```java
RBatch batch = redissonClient.createBatch();
batch.getBucket("key1").setAsync("value1");
batch.getBucket("key2").setAsync("value2");
batch.execute();
```

### 3. 异步操作

```java
RBucket<String> bucket = redissonClient.getBucket("key");
RFuture<String> future = bucket.getAsync();
future.whenComplete((value, exception) -> {
    if (exception == null) {
        // 处理结果
    }
});
```

## 监控和管理

### 查看Redis状态

```bash
# 进入Redis CLI
redis-cli

# 查看信息
INFO

# 查看所有键
KEYS *

# 监控实时命令
MONITOR
```

### 集成Redis监控

可以使用以下工具：
- **Redis Commander** - Web管理界面
- **RedisInsight** - 官方可视化工具
- **Prometheus + Grafana** - 指标监控

## 测试脚本

创建测试脚本 `test-redis.sh`：

```bash
#!/bin/bash

BASE_URL="http://localhost:9220"

echo "=== 测试Redis连接 ==="
curl -X GET "${BASE_URL}/api/redis/test-connection"
echo ""

echo "=== 设置键值 ==="
curl -X POST "${BASE_URL}/api/redis/set?key=test&value=hello"
echo ""

echo "=== 获取键值 ==="
curl -X GET "${BASE_URL}/api/redis/get?key=test"
echo ""

echo "=== 删除键 ==="
curl -X DELETE "${BASE_URL}/api/redis/delete?key=test"
echo ""
```

## 最佳实践

1. **始终检查RedissonClient是否为null**
   ```java
   if (redissonClient != null) {
       // 使用Redis
   }
   ```

2. **设置合理的过期时间**
   ```java
   bucket.set(value, 300, TimeUnit.SECONDS);
   ```

3. **使用分布式锁时注意释放**
   ```java
   try {
       if (lock.tryLock()) {
           // 业务逻辑
       }
   } finally {
       if (lock.isHeldByCurrentThread()) {
           lock.unlock();
       }
   }
   ```

4. **异常处理**
   ```java
   try {
       // Redis操作
   } catch (Exception e) {
       log.error("Redis操作失败", e);
       // 降级处理
   }
   ```

## 参考资料

- [Redisson官方文档](https://github.com/redisson/redisson/wiki)
- [Redis官方文档](https://redis.io/documentation)
- [Spring Boot Redis集成](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.nosql.redis)

## 总结

Redisson已成功集成到项目中，提供了：

✅ 完整的Redis连接配置  
✅ 可选的启用/禁用开关  
✅ 测试API端点  
✅ 示例服务和控制器  
✅ 详细的文档和使用指南  

项目现在可以使用Redisson的所有强大功能，包括分布式锁、分布式对象、发布订阅等。
