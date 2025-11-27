#!/bin/bash

# 测试多签名注解预览功能
# 验证在同一页中多个签名是否都能正确显示

API_BASE_URL="http://localhost:8080"

echo "=========================================="
echo "测试多签名注解预览功能"
echo "=========================================="
echo ""

# 测试数据
BUSINESS_ID="20251127181501"
TENANT_ID="1"

echo "1. 发送多签名注解预览请求..."
echo "   - businessId: $BUSINESS_ID"
echo "   - tenantId: $TENANT_ID"
echo "   - 第1页包含2个签名："
echo "     * 受试者签字: [118, 116, 185, 166]"
echo "     * 研究者签字: [595, 108, 661, 158]"
echo ""

RESPONSE=$(curl -s -X POST "${API_BASE_URL}/api/pdf/preview-annotations" \
  -H "Content-Type: application/json" \
  -d '{
    "businessId": "'"${BUSINESS_ID}"'",
    "tenantId": "'"${TENANT_ID}"'",
    "totalAnnotations": 2,
    "pageAnnotations": {
        "1": [
            {
                "id": "annotation_1",
                "index": 1,
                "contents": "受试者签字",
                "markValue": "subjectSignature",
                "pageNumber": "1",
                "pdf": [118, 116, 185, 166],
                "scale": 1.2
            },
            {
                "id": "annotation_2",
                "index": 2,
                "contents": "研究者签字",
                "markValue": "investigatorSignature",
                "pageNumber": "1",
                "pdf": [595, 108, 661, 158],
                "scale": 1.2
            }
        ]
    }
}')

echo "响应结果："
echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
echo ""

# 检查响应状态
STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
if [ "$STATUS" == "SUCCESS" ]; then
    echo "✅ 请求成功！"
    echo ""
    echo "2. 提取图片URL..."
    
    # 提取第一页的图片URL
    IMAGE_URL=$(echo "$RESPONSE" | grep -o '"imageUrl":"[^"]*"' | head -1 | cut -d'"' -f4)
    
    if [ -n "$IMAGE_URL" ]; then
        echo "   图片URL: $IMAGE_URL"
        echo ""
        echo "3. 下载预览图片..."
        
        OUTPUT_FILE="page_1_multi_signature_preview.png"
        curl -s -o "$OUTPUT_FILE" "$IMAGE_URL"
        
        if [ -f "$OUTPUT_FILE" ]; then
            FILE_SIZE=$(stat -f%z "$OUTPUT_FILE" 2>/dev/null || stat -c%s "$OUTPUT_FILE" 2>/dev/null)
            echo "   ✅ 图片已保存: $OUTPUT_FILE (${FILE_SIZE} bytes)"
            echo ""
            echo "请打开 $OUTPUT_FILE 查看预览效果"
            echo "应该能看到两个签名："
            echo "  1. 受试者签字（左侧位置）"
            echo "  2. 研究者签字（右侧位置）"
        else
            echo "   ❌ 图片下载失败"
        fi
    else
        echo "   ❌ 未找到图片URL"
    fi
else
    echo "❌ 请求失败: $STATUS"
    echo ""
    MESSAGE=$(echo "$RESPONSE" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$MESSAGE" ]; then
        echo "错误信息: $MESSAGE"
    fi
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
