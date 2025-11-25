#!/bin/bash

# PDF多人签署场景测试脚本

set -e

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
BUSINESS_ID="CONTRACT-2024-$(date +%s)"

echo "=========================================="
echo "PDF多人签署场景测试"
echo "=========================================="
echo "API Base URL: $API_BASE_URL"
echo "Business ID: $BUSINESS_ID"
echo ""

# 创建测试PDF文件（如果不存在）
if [ ! -f "test-contract.pdf" ]; then
    echo "创建测试PDF文件..."
    echo "如果没有测试PDF，请手动提供test-contract.pdf文件"
    echo "可以使用任何PDF文件作为测试"
    exit 1
fi

echo "=========================================="
echo "场景1: 第一人签署（全量转换）"
echo "=========================================="

echo "上传PDF - 用户: SIGNER-001"
RESPONSE=$(curl -s -X POST "$API_BASE_URL/api/pdf/upload" \
  -F "file=@test-contract.pdf" \
  -F "businessId=$BUSINESS_ID" \
  -F "userId=SIGNER-001")

echo "响应: $RESPONSE"
TASK_ID_1=$(echo $RESPONSE | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TASK_ID_1" ]; then
    echo "错误: 无法获取任务ID"
    exit 1
fi

echo "任务ID: $TASK_ID_1"
echo ""

echo "等待转换完成..."
sleep 5

echo "查询任务进度..."
curl -s "$API_BASE_URL/api/pdf/progress/$TASK_ID_1" | jq '.'
echo ""

echo "查询任务详情..."
curl -s "$API_BASE_URL/api/pdf/task/$TASK_ID_1" | jq '.'
echo ""

echo "查询基类图片（前5页）..."
curl -s "$API_BASE_URL/api/pdf/images?businessId=$BUSINESS_ID&startPage=1&pageSize=5" | jq '.'
echo ""

echo "=========================================="
echo "场景2: 第二人签署（增量转换：第1页和最后1页）"
echo "=========================================="

echo "上传PDF - 用户: SIGNER-002, 只转换第1页和第5页"
RESPONSE=$(curl -s -X POST "$API_BASE_URL/api/pdf/upload" \
  -F "file=@test-contract.pdf" \
  -F "businessId=$BUSINESS_ID" \
  -F "userId=SIGNER-002" \
  -F "pages=1,5")

echo "响应: $RESPONSE"
TASK_ID_2=$(echo $RESPONSE | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TASK_ID_2" ]; then
    echo "错误: 无法获取任务ID"
    exit 1
fi

echo "任务ID: $TASK_ID_2"
echo ""

echo "等待转换完成..."
sleep 3

echo "查询任务详情..."
curl -s "$API_BASE_URL/api/pdf/task/$TASK_ID_2" | jq '.'
echo ""

echo "查询SIGNER-002的图片（应该包含基类图片+第1,5页的特殊图片）..."
curl -s "$API_BASE_URL/api/pdf/images?businessId=$BUSINESS_ID&userId=SIGNER-002&startPage=1&pageSize=5" | jq '.'
echo ""

echo "=========================================="
echo "场景3: 第三人签署（增量转换：第1页和第5页）"
echo "=========================================="

echo "上传PDF - 用户: SIGNER-003, 只转换第1页和第5页"
RESPONSE=$(curl -s -X POST "$API_BASE_URL/api/pdf/upload" \
  -F "file=@test-contract.pdf" \
  -F "businessId=$BUSINESS_ID" \
  -F "userId=SIGNER-003" \
  -F "pages=1,5")

echo "响应: $RESPONSE"
TASK_ID_3=$(echo $RESPONSE | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TASK_ID_3" ]; then
    echo "错误: 无法获取任务ID"
    exit 1
fi

echo "任务ID: $TASK_ID_3"
echo ""

echo "等待转换完成..."
sleep 3

echo "查询任务详情..."
curl -s "$API_BASE_URL/api/pdf/task/$TASK_ID_3" | jq '.'
echo ""

echo "查询SIGNER-003的图片（应该包含基类图片+第1,5页的特殊图片）..."
curl -s "$API_BASE_URL/api/pdf/images?businessId=$BUSINESS_ID&userId=SIGNER-003&startPage=1&pageSize=5" | jq '.'
echo ""

echo "=========================================="
echo "场景4: 查询所有任务"
echo "=========================================="

echo "查询该业务的所有任务..."
curl -s "$API_BASE_URL/api/pdf/tasks?businessId=$BUSINESS_ID" | jq '.'
echo ""

echo "查询SIGNER-002的任务..."
curl -s "$API_BASE_URL/api/pdf/tasks?businessId=$BUSINESS_ID&userId=SIGNER-002" | jq '.'
echo ""

echo "=========================================="
echo "场景5: 错误场景测试"
echo "=========================================="

echo "尝试增量转换但没有基类（应该失败）..."
NEW_BUSINESS_ID="CONTRACT-NEW-$(date +%s)"
curl -s -X POST "$API_BASE_URL/api/pdf/upload" \
  -F "file=@test-contract.pdf" \
  -F "businessId=$NEW_BUSINESS_ID" \
  -F "userId=USER-001" \
  -F "pages=1,2" | jq '.'
echo ""

echo "=========================================="
echo "测试完成"
echo "=========================================="
echo "Business ID: $BUSINESS_ID"
echo "任务1 (全量): $TASK_ID_1"
echo "任务2 (增量): $TASK_ID_2"
echo "任务3 (增量): $TASK_ID_3"
echo ""
echo "可以使用以下命令查询图片:"
echo "  基类图片: curl '$API_BASE_URL/api/pdf/images?businessId=$BUSINESS_ID'"
echo "  SIGNER-002图片: curl '$API_BASE_URL/api/pdf/images?businessId=$BUSINESS_ID&userId=SIGNER-002'"
echo "  SIGNER-003图片: curl '$API_BASE_URL/api/pdf/images?businessId=$BUSINESS_ID&userId=SIGNER-003'"
