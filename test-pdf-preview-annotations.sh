#!/bin/bash

# PDF注解预览API测试脚本
# 功能：测试在基类PDF图片上渲染注解

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# API配置
BASE_URL="http://localhost:8080"
API_ENDPOINT="/api/pdf/preview-with-annotations"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}PDF注解预览API测试${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查参数
if [ -z "$1" ] || [ -z "$2" ]; then
    echo -e "${YELLOW}使用方法: $0 <businessId> <tenantId>${NC}"
    echo -e "${YELLOW}示例: $0 project-123 tenant-789${NC}"
    exit 1
fi

BUSINESS_ID=$1
TENANT_ID=$2

echo -e "${YELLOW}业务ID: ${BUSINESS_ID}${NC}"
echo -e "${YELLOW}租户ID: ${TENANT_ID}${NC}"
echo ""

# 构造请求JSON
REQUEST_JSON=$(cat <<EOF
{
  "businessId": "${BUSINESS_ID}",
  "tenantId": "${TENANT_ID}",
  "exportTime": "2025-11-27T06:37:55.634Z",
  "totalAnnotations": 2,
  "pageAnnotations": {
    "1": [
      {
        "id": "annotation_1",
        "index": 1,
        "contents": "受试者签字",
        "markValue": "subjectSignature",
        "pageNumber": "1",
        "pdf": [214, 166, 280, 216],
        "scale": 1.2,
        "normalized": {
          "x": "13496",
          "y": "7426",
          "width": "20.96",
          "height": "10.48",
          "basePoint": "LU"
        }
      }
    ],
    "3": [
      {
        "id": "annotation_2",
        "index": 2,
        "contents": "研究者签字",
        "markValue": "investigatorSignature",
        "pageNumber": "3",
        "pdf": [138, -1530, 204, -1481],
        "scale": 1.2,
        "normalized": {
          "x": "8717",
          "y": "-68167",
          "width": "20.96",
          "height": "10.48",
          "basePoint": "LU"
        }
      }
    ]
  }
}
EOF
)

echo -e "${GREEN}步骤1: 发送预览请求${NC}"
echo -e "${YELLOW}请求URL: ${BASE_URL}${API_ENDPOINT}${NC}"
echo ""

# 发送请求
RESPONSE=$(curl -s -X POST "${BASE_URL}${API_ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d "${REQUEST_JSON}")

# 保存响应到文件
RESPONSE_FILE="preview_response_$(date +%Y%m%d_%H%M%S).json"
echo "${RESPONSE}" | jq '.' > "${RESPONSE_FILE}"

echo -e "${GREEN}响应已保存到: ${RESPONSE_FILE}${NC}"
echo ""

# 解析响应
STATUS=$(echo "${RESPONSE}" | jq -r '.status')
MESSAGE=$(echo "${RESPONSE}" | jq -r '.message')
TOTAL_PAGES=$(echo "${RESPONSE}" | jq -r '.totalPages')
RENDERED_PAGES=$(echo "${RESPONSE}" | jq -r '.renderedPages')

echo -e "${GREEN}步骤2: 解析响应${NC}"
echo -e "状态: ${STATUS}"
echo -e "消息: ${MESSAGE}"
echo -e "总页数: ${TOTAL_PAGES}"
echo -e "渲染页数: ${RENDERED_PAGES}"
echo ""

# 检查状态
if [ "${STATUS}" = "SUCCESS" ]; then
    echo -e "${GREEN}✓ 预览成功!${NC}"
    echo ""
    
    echo -e "${GREEN}步骤3: 显示图片信息${NC}"
    echo ""
    
    # 提取图片信息
    IMAGES=$(echo "${RESPONSE}" | jq -c '.images[]')
    
    echo -e "${YELLOW}页码 | 是否渲染 | 图片URL${NC}"
    echo -e "${YELLOW}-----|----------|----------${NC}"
    
    while IFS= read -r image; do
        PAGE_NUM=$(echo "${image}" | jq -r '.pageNumber')
        IS_RENDERED=$(echo "${image}" | jq -r '.isRendered')
        IMAGE_URL=$(echo "${image}" | jq -r '.imageUrl')
        
        if [ "${IS_RENDERED}" = "true" ]; then
            RENDER_STATUS="${GREEN}✓ 已渲染${NC}"
        else
            RENDER_STATUS="原始图片"
        fi
        
        echo -e "第${PAGE_NUM}页 | ${RENDER_STATUS} | ${IMAGE_URL:0:60}..."
    done <<< "${IMAGES}"
    
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}测试完成!${NC}"
    echo -e "${GREEN}========================================${NC}"
    
elif [ "${STATUS}" = "NOT_FOUND" ]; then
    echo -e "${RED}✗ 基类任务未找到${NC}"
    echo -e "${YELLOW}请确保：${NC}"
    echo -e "  1. businessId和tenantId正确"
    echo -e "  2. 已经上传过PDF并完成转换（isBase=1）"
    echo ""
    
elif [ "${STATUS}" = "ERROR" ]; then
    echo -e "${RED}✗ 预览失败${NC}"
    echo -e "${RED}错误信息: ${MESSAGE}${NC}"
    echo ""
    
else
    echo -e "${RED}✗ 未知状态: ${STATUS}${NC}"
    echo ""
fi

echo -e "${YELLOW}详细响应请查看: ${RESPONSE_FILE}${NC}"
echo ""

# 提供下一步建议
echo -e "${GREEN}下一步操作建议:${NC}"
echo -e "  1. 查看响应文件: ${YELLOW}cat ${RESPONSE_FILE} | jq '.'${NC}"
echo -e "  2. 在浏览器中打开图片URL查看渲染效果"
echo -e "  3. 如果基类任务未找到，先上传PDF：${YELLOW}./test-pdf-upload.sh${NC}"
echo ""
