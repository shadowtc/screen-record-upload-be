FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN apk add --no-cache maven && \
    mvn clean package -DskipTests && \
    apk del maven

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install FFmpeg and necessary libraries
RUN apk add --no-cache \
    ffmpeg \
    xvfb \
    ttf-freefont \
    && rm -rf /var/cache/apk/*

# Create temp directory for video compression
RUN mkdir -p /tmp/video-compression && \
    chmod 777 /tmp/video-compression

COPY --from=builder /app/target/minio-multipart-upload-1.0.0.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
