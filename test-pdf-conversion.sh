#!/bin/bash

# PDF转换功能测试脚本

BASE_URL="http://localhost:8080"
API_ENDPOINT="${BASE_URL}/api/pdf"

echo "=========================================="
echo "PDF转换功能测试"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 测试1: 获取支持的格式
echo -e "${YELLOW}测试1: 获取支持的文件格式${NC}"
echo "GET ${API_ENDPOINT}/formats"
curl -X GET "${API_ENDPOINT}/formats" \
  -H "Content-Type: application/json" | jq '.'
echo ""
echo ""

# 测试2: 创建测试文件
echo -e "${YELLOW}测试2: 创建测试文件${NC}"

# 创建测试文本文件
cat > test.txt << 'EOF'
这是一个PDF转换测试文件
Test PDF Conversion

支持的功能：
1. 文本文件转PDF
2. Office文档转PDF (doc, docx, xls, xlsx, ppt, pptx)
3. 图片转PDF (jpg, png, bmp, gif)
4. PDF页面转图片 (高清晰度)

转换过程：
- 自动检测文件格式
- 转换为PDF
- 异步生成每页图片
- 保存到本地存储
EOF

echo -e "${GREEN}✓ 测试文件创建成功: test.txt${NC}"
echo ""

# 测试3: 转换文本文件为PDF
echo -e "${YELLOW}测试3: 转换文本文件为PDF（包含图片生成）${NC}"
echo "POST ${API_ENDPOINT}/convert"

RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/convert" \
  -F "file=@test.txt" \
  -F 'request={"outputFilename":"test-output.pdf","convertToImages":true,"imageDpi":300,"imageFormat":"PNG"};type=application/json')

echo "$RESPONSE" | jq '.'
JOB_ID=$(echo "$RESPONSE" | jq -r '.jobId')

if [ "$JOB_ID" != "null" ] && [ -n "$JOB_ID" ]; then
    echo -e "${GREEN}✓ 转换任务已提交，JobID: ${JOB_ID}${NC}"
    echo ""
    
    # 测试4: 查询转换进度
    echo -e "${YELLOW}测试4: 查询转换进度${NC}"
    MAX_ATTEMPTS=30
    ATTEMPT=0
    
    while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
        echo "尝试 $((ATTEMPT + 1))/$MAX_ATTEMPTS: 查询进度..."
        
        PROGRESS=$(curl -s -X GET "${API_ENDPOINT}/progress/${JOB_ID}")
        echo "$PROGRESS" | jq '.'
        
        STATUS=$(echo "$PROGRESS" | jq -r '.status')
        PHASE=$(echo "$PROGRESS" | jq -r '.currentPhase')
        PERCENTAGE=$(echo "$PROGRESS" | jq -r '.progressPercentage')
        
        echo "状态: $STATUS, 阶段: $PHASE, 进度: $PERCENTAGE%"
        
        if [ "$STATUS" == "COMPLETED" ]; then
            echo -e "${GREEN}✓ 转换完成！${NC}"
            break
        elif [ "$STATUS" == "FAILED" ]; then
            ERROR=$(echo "$PROGRESS" | jq -r '.errorMessage')
            echo -e "${RED}✗ 转换失败: $ERROR${NC}"
            exit 1
        fi
        
        ATTEMPT=$((ATTEMPT + 1))
        sleep 2
    done
    
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        echo -e "${RED}✗ 转换超时${NC}"
        exit 1
    fi
    
    echo ""
    
    # 测试5: 获取转换结果
    echo -e "${YELLOW}测试5: 获取转换结果${NC}"
    echo "GET ${API_ENDPOINT}/result/${JOB_ID}"
    
    RESULT=$(curl -s -X GET "${API_ENDPOINT}/result/${JOB_ID}")
    echo "$RESULT" | jq '.'
    
    PDF_PATH=$(echo "$RESULT" | jq -r '.pdfFilePath')
    PDF_SIZE=$(echo "$RESULT" | jq -r '.pdfFileSize')
    PAGE_COUNT=$(echo "$RESULT" | jq -r '.pageCount')
    IMAGE_COUNT=$(echo "$RESULT" | jq -r '.imageFilePaths | length')
    PROCESSING_TIME=$(echo "$RESULT" | jq -r '.processingTimeMs')
    
    echo ""
    echo -e "${GREEN}========== 转换结果 ==========${NC}"
    echo "PDF文件路径: $PDF_PATH"
    echo "PDF文件大小: $PDF_SIZE bytes"
    echo "页面数量: $PAGE_COUNT"
    echo "生成图片数量: $IMAGE_COUNT"
    echo "处理时间: ${PROCESSING_TIME}ms"
    echo -e "${GREEN}=============================${NC}"
    echo ""
    
    # 验证文件是否存在
    if [ -f "$PDF_PATH" ]; then
        echo -e "${GREEN}✓ PDF文件已成功保存到本地${NC}"
        echo "文件位置: $PDF_PATH"
        ls -lh "$PDF_PATH"
        echo ""
        
        # 检查图片文件
        IMAGE_DIR=$(dirname "$PDF_PATH")/images
        if [ -d "$IMAGE_DIR" ]; then
            echo -e "${GREEN}✓ 图片文件已成功生成${NC}"
            echo "图片目录: $IMAGE_DIR"
            ls -lh "$IMAGE_DIR" | head -10
            echo ""
        fi
    else
        echo -e "${RED}✗ PDF文件不存在: $PDF_PATH${NC}"
    fi
else
    echo -e "${RED}✗ 转换任务提交失败${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}=========================================="
echo "所有测试完成！"
echo "==========================================${NC}"
