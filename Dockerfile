# 第一阶段：编译构建阶段
# 使用包含Maven的JDK镜像进行编译
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# 复制Maven配置和源代码
COPY pom.xml .
COPY src ./src

# 编译项目
# mvn clean package：清理并构建项目
RUN mvn clean package -DskipTests

# 第二阶段：运行时阶段
# 使用最小化的JRE镜像以减小最终镜像大小
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 暂时跳过FFmpeg安装，专注于测试分片上传功能
# 如需视频压缩功能，请后续手动安装FFmpeg
# RUN apk update && apk add --no-cache \
#     ffmpeg \
#     xvfb-run \
#     ttf-freefont \
#     && rm -rf /var/cache/apk/*

# 创建临时目录（用于上传和其他临时文件）
# 此目录用于存储临时文件
# chmod 777：设置所有用户可读写执行权限
# 注意：在生产环境中应限制权限以提高安全性
RUN mkdir -p /tmp/uploads && \
    chmod 777 /tmp/uploads

# 从构建阶段复制编译好的JAR文件
COPY --from=builder /app/target/minio-multipart-upload-1.0.0.jar app.jar

# 暴露8080端口（REST API服务端口）
EXPOSE 8080

# JVM启动参数
# 可通过-e JAVA_OPTS="-Xmx2g -Xms1g"等方式传递
# 推荐参数：
# - Xms1g：初始堆大小，根据可用内存调整
# - Xmx2g：最大堆大小，不超过容器内存限制的50-75%
# - XX:+UseG1GC：使用G1垃圾回收器（Java 11+推荐）
# - XX:MaxGCPauseMillis=200：最大GC停顿时间
ENV JAVA_OPTS=""

# 容器启动命令
# 使用sh -c允许${JAVA_OPTS}环境变量展开
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

