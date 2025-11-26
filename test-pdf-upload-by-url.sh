#!/bin/bash

# 测试通过URL上传PDF文件的接口
# 使用方法：./test-pdf-upload-by-url.sh [pdf_url]

BASE_URL="http://localhost:8080"
PDF_URL="${1:-https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf}"
BUSINESS_ID="test-business-$(date +%s)"
USER_ID="test-user-123"

echo "=========================================="
echo "测试 PDF 通过URL上传接口"
echo "=========================================="
echo "PDF URL: $PDF_URL"
echo "Business ID: $BUSINESS_ID"
echo "User ID: $USER_ID"
echo ""

# 1. 通过URL上传PDF（全量转换）
echo "1. 通过URL上传PDF并转换所有页面..."
RESPONSE=$(curl -s -X POST "$BASE_URL/api/pdf/upload-by-url" \
  -H "Content-Type: application/json" \
  -d "{
    \"fileUrl\": \"$PDF_URL\",
    \"businessId\": \"$BUSINESS_ID\",
    \"userId\": \"$USER_ID\",
    \"imageDpi\": 300,
    \"imageFormat\": \"PNG\"
  }")

echo "响应: $RESPONSE"
TASK_ID=$(echo $RESPONSE | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TASK_ID" ]; then
    echo "错误: 未能获取任务ID"
    exit 1
fi

echo "任务ID: $TASK_ID"
echo ""

# 2. 查询转换进度
echo "2. 查询转换进度..."
for i in {1..10}; do
    PROGRESS=$(curl -s "$BASE_URL/api/pdf/progress/$TASK_ID")
    echo "进度查询 $i: $PROGRESS"
    
    STATUS=$(echo $PROGRESS | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    
    if [ "$STATUS" == "COMPLETED" ]; then
        echo "转换完成！"
        break
    elif [ "$STATUS" == "FAILED" ]; then
        echo "转换失败！"
        break
    fi
    
    sleep 2
done
echo ""

# 3. 获取任务详情
echo "3. 获取任务详情..."
TASK_DETAIL=$(curl -s "$BASE_URL/api/pdf/task/$TASK_ID")
echo "任务详情: $TASK_DETAIL"
echo ""

# 4. 获取转换后的图片列表
echo "4. 获取转换后的图片列表..."
IMAGES=$(curl -s "$BASE_URL/api/pdf/images?businessId=$BUSINESS_ID&userId=$USER_ID&startPage=1&pageSize=10")
echo "图片列表: $IMAGES"
echo ""

# 5. 测试增量转换（只转换第1页）
echo "5. 测试增量转换（只转换第1页）..."
INCREMENTAL_RESPONSE=$(curl -s -X POST "$BASE_URL/api/pdf/upload-by-url" \
  -H "Content-Type: application/json" \
  -d "{
    \"fileUrl\": \"$PDF_URL\",
    \"businessId\": \"$BUSINESS_ID\",
    \"userId\": \"$USER_ID\",
    \"pages\": [1],
    \"imageDpi\": 300,
    \"imageFormat\": \"PNG\"
  }")

echo "增量转换响应: $INCREMENTAL_RESPONSE"
INCREMENTAL_TASK_ID=$(echo $INCREMENTAL_RESPONSE | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)

if [ -n "$INCREMENTAL_TASK_ID" ]; then
    echo "增量转换任务ID: $INCREMENTAL_TASK_ID"
    
    # 等待增量转换完成
    echo "等待增量转换完成..."
    for i in {1..5}; do
        PROGRESS=$(curl -s "$BASE_URL/api/pdf/progress/$INCREMENTAL_TASK_ID")
        STATUS=$(echo $PROGRESS | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        
        if [ "$STATUS" == "COMPLETED" ]; then
            echo "增量转换完成！"
            break
        fi
        
        sleep 2
    done
fi
echo ""

# 6. 测试错误情况：无效的URL
echo "6. 测试错误情况：无效的URL..."
ERROR_RESPONSE=$(curl -s -X POST "$BASE_URL/api/pdf/upload-by-url" \
  -H "Content-Type: application/json" \
  -d "{
    \"fileUrl\": \"http://invalid-url-test-123.com/notfound.pdf\",
    \"businessId\": \"$BUSINESS_ID\",
    \"userId\": \"$USER_ID\"
  }")

echo "错误响应: $ERROR_RESPONSE"
echo ""

# 7. 测试错误情况：缺少必填参数
echo "7. 测试错误情况：缺少businessId参数..."
MISSING_PARAM_RESPONSE=$(curl -s -X POST "$BASE_URL/api/pdf/upload-by-url" \
  -H "Content-Type: application/json" \
  -d "{
    \"fileUrl\": \"$PDF_URL\",
    \"userId\": \"$USER_ID\"
  }")

echo "缺少参数响应: $MISSING_PARAM_RESPONSE"
echo ""

echo "=========================================="
echo "测试完成！"
echo "=========================================="
echo ""
echo "总结："
echo "- 全量转换任务ID: $TASK_ID"
echo "- 增量转换任务ID: $INCREMENTAL_TASK_ID"
echo "- Business ID: $BUSINESS_ID"
echo ""
echo "可以使用以下命令查询任务状态："
echo "curl $BASE_URL/api/pdf/task/$TASK_ID"
echo "curl $BASE_URL/api/pdf/images?businessId=$BUSINESS_ID&userId=$USER_ID"
