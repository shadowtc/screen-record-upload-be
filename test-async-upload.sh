#!/bin/bash

# 异步分片上传测试脚本
# 
# 此脚本演示如何使用异步上传API：
# 1. 提交上传任务
# 2. 轮询进度直到完成
# 3. 显示最终结果

set -e

# 配置
API_BASE_URL="http://localhost:8080/api/uploads"
VIDEO_FILE="test-video.mp4"
CHUNK_SIZE=8388608  # 8MB

echo "=========================================="
echo "异步分片上传测试"
echo "=========================================="
echo ""

# 检查测试文件是否存在
if [ ! -f "$VIDEO_FILE" ]; then
    echo "错误: 测试文件 $VIDEO_FILE 不存在"
    echo "请先创建或下载一个测试视频文件"
    exit 1
fi

echo "测试文件: $VIDEO_FILE"
echo "文件大小: $(ls -lh $VIDEO_FILE | awk '{print $5}')"
echo "分片大小: $CHUNK_SIZE 字节 (8MB)"
echo ""

# 步骤1: 提交异步上传任务
echo "步骤1: 提交异步上传任务..."
echo "----------------------------------------"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_BASE_URL/async" \
  -F "file=@$VIDEO_FILE" \
  -F "chunkSize=$CHUNK_SIZE")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" != "200" ]; then
    echo "错误: 提交上传任务失败 (HTTP $HTTP_CODE)"
    echo "$BODY"
    exit 1
fi

echo "响应: $BODY"
echo ""

# 提取jobId
JOB_ID=$(echo "$BODY" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$JOB_ID" ]; then
    echo "错误: 无法从响应中提取jobId"
    exit 1
fi

echo "任务ID: $JOB_ID"
echo "上传任务已提交，开始轮询进度..."
echo ""

# 步骤2: 轮询进度
echo "步骤2: 监控上传进度..."
echo "----------------------------------------"

COMPLETED=false
POLL_INTERVAL=2  # 每2秒轮询一次

while [ "$COMPLETED" = false ]; do
    # 查询进度
    PROGRESS_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$API_BASE_URL/async/$JOB_ID/progress")
    
    HTTP_CODE=$(echo "$PROGRESS_RESPONSE" | tail -n1)
    PROGRESS_BODY=$(echo "$PROGRESS_RESPONSE" | sed '$d')
    
    if [ "$HTTP_CODE" != "200" ]; then
        echo "错误: 查询进度失败 (HTTP $HTTP_CODE)"
        echo "$PROGRESS_BODY"
        exit 1
    fi
    
    # 提取进度信息
    STATUS=$(echo "$PROGRESS_BODY" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    PROGRESS=$(echo "$PROGRESS_BODY" | grep -o '"progress":[0-9.]*' | cut -d':' -f2)
    MESSAGE=$(echo "$PROGRESS_BODY" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
    UPLOADED_PARTS=$(echo "$PROGRESS_BODY" | grep -o '"uploadedParts":[0-9]*' | cut -d':' -f2)
    TOTAL_PARTS=$(echo "$PROGRESS_BODY" | grep -o '"totalParts":[0-9]*' | cut -d':' -f2)
    
    # 显示进度
    TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$TIMESTAMP] 状态: $STATUS | 进度: ${PROGRESS}% | 分片: $UPLOADED_PARTS/$TOTAL_PARTS | $MESSAGE"
    
    # 检查是否完成或失败
    if [ "$STATUS" = "COMPLETED" ]; then
        COMPLETED=true
        echo ""
        echo "上传完成！"
        echo "=========================================="
        echo "最终结果:"
        echo "=========================================="
        echo "$PROGRESS_BODY" | python3 -m json.tool 2>/dev/null || echo "$PROGRESS_BODY"
        
        # 提取下载URL
        DOWNLOAD_URL=$(echo "$PROGRESS_BODY" | grep -o '"downloadUrl":"[^"]*"' | cut -d'"' -f4)
        if [ -n "$DOWNLOAD_URL" ]; then
            echo ""
            echo "下载URL: $DOWNLOAD_URL"
        fi
        
        # 提取文件ID
        FILE_ID=$(echo "$PROGRESS_BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
        if [ -n "$FILE_ID" ]; then
            echo "文件ID: $FILE_ID"
        fi
        
    elif [ "$STATUS" = "FAILED" ]; then
        COMPLETED=true
        echo ""
        echo "上传失败！"
        echo "=========================================="
        echo "错误详情:"
        echo "=========================================="
        echo "$PROGRESS_BODY" | python3 -m json.tool 2>/dev/null || echo "$PROGRESS_BODY"
        exit 1
    else
        # 等待后继续轮询
        sleep $POLL_INTERVAL
    fi
done

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
