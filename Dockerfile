# 第一阶段：编译构建阶段
# 使用包含Maven的JDK镜像进行编译
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 复制Maven配置和源代码
COPY pom.xml .
COPY src ./src

# 安装Maven并编译项目
# apk add：从Alpine包管理器安装
# mvn clean package：清理并构建项目
# apk del maven：构建完成后删除Maven以减小镜像大小
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests && \
    apk del maven

# 第二阶段：运行时阶段
# 使用最小化的JRE镜像以减小最终镜像大小
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装FFmpeg和必要的库文件
# ffmpeg：视频处理核心依赖
# xvfb：虚拟X server（某些FFmpeg操作需要）
# ttf-freefont：字体库（视频处理时可能需要）
# 
# 注意：使用Alpine Linux可以大幅减小镜像大小
# 完整JDK镜像约350MB，使用Alpine JRE后约100MB
RUN apk add --no-cache \
    ffmpeg \
    xvfb \
    ttf-freefont \
    && rm -rf /var/cache/apk/*

# 创建视频压缩临时目录
# 此目录用于存储压缩中和已完成的视频文件
# chmod 777：设置所有用户可读写执行权限
# 注意：在生产环境中应限制权限以提高安全性
RUN mkdir -p /tmp/video-compression && \
    chmod 777 /tmp/video-compression

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

