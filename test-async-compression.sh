#!/bin/bash

# 测试异步视频压缩接口是否立即返回任务ID
# 这个脚本验证修复后的异步接口是否能够立即返回而不是等待50秒

echo "=== 测试异步视频压缩接口响应时间 ==="
echo ""

# 设置API基础URL
BASE_URL="http://localhost:8080/api/video"

# 创建一个测试视频文件（如果不存在）
TEST_VIDEO="/tmp/test-video.mp4"
if [ ! -f "$TEST_VIDEO" ]; then
    echo "创建测试视频文件..."
    # 使用FFmpeg创建一个10秒的测试视频
    ffmpeg -f lavfi -i testsrc=duration=10:size=320x240:rate=30 -f lavfi -i anullsrc=channel_layout=stereo:sample_rate=44100 -t 10 -c:v libx264 -c:a aac -y "$TEST_VIDEO" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "测试视频创建成功: $TEST_VIDEO"
    else
        echo "无法创建测试视频，请确保FFmpeg已安装"
        exit 1
    fi
fi

# 准备请求数据
REQUEST_DATA="{
    \"inputFilePath\": \"$TEST_VIDEO\",
    \"preset\": \"balanced\"
}"

echo "发送异步压缩请求..."
echo "请求体: $REQUEST_DATA"
echo ""

# 记录开始时间
START_TIME=$(date +%s%3N)

# 发送异步压缩请求
RESPONSE=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    -d "$REQUEST_DATA" \
    "$BASE_URL/compress/async")

# 记录结束时间
END_TIME=$(date +%s%3N)

# 计算响应时间
RESPONSE_TIME=$((END_TIME - START_TIME))

echo "响应时间: ${RESPONSE_TIME}ms"
echo "响应内容: $RESPONSE"
echo ""

# 检查响应是否包含jobId
if echo "$RESPONSE" | grep -q '"jobId"'; then
    echo "✅ 成功：响应包含jobId，异步接口工作正常"
    
    # 提取jobId
    JOB_ID=$(echo "$RESPONSE" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)
    echo "任务ID: $JOB_ID"
    
    # 检查响应时间是否在合理范围内（应该小于1秒）
    if [ $RESPONSE_TIME -lt 1000 ]; then
        echo "✅ 成功：响应时间在合理范围内 (${RESPONSE_TIME}ms < 1000ms)"
        echo "🎉 异步接口修复成功！现在能够立即返回任务ID。"
    else
        echo "⚠️  警告：响应时间仍然较长 (${RESPONSE_TIME}ms)，可能仍有问题"
    fi
    
    # 可选：查询任务进度
    echo ""
    echo "查询任务进度..."
    sleep 2
    PROGRESS_RESPONSE=$(curl -s "$BASE_URL/progress/$JOB_ID")
    echo "进度响应: $PROGRESS_RESPONSE"
    
else
    echo "❌ 失败：响应不包含jobId，异步接口可能有问题"
    if [ $RESPONSE_TIME -gt 5000 ]; then
        echo "❌ 错误：响应时间过长 (${RESPONSE_TIME}ms)，说明仍然是同步执行"
    fi
fi

echo ""
echo "=== 测试完成 ==="