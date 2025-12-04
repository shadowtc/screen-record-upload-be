#!/bin/bash

# 关键字过滤功能测试脚本

BASE_URL="http://localhost:8080"
API_PREFIX="${BASE_URL}/api/keyword-filter"

echo "=================================="
echo "关键字过滤功能测试"
echo "=================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 1. 测试获取统计信息
echo -e "${BLUE}1. 获取关键字统计信息${NC}"
curl -s -X GET "${API_PREFIX}/stats" | jq .
echo -e "\n"

# 2. 测试刷新关键字库
echo -e "${BLUE}2. 刷新关键字库${NC}"
curl -s -X POST "${API_PREFIX}/refresh" | jq .
echo -e "\n"

# 3. 测试包含关键字的文本
echo -e "${BLUE}3. 测试包含关键字的文本（应返回true）${NC}"
curl -s -X POST "${API_PREFIX}/contains" \
  -H "Content-Type: application/json" \
  -d '{"text": "这是一个包含敏感词1的测试文本"}' | jq .
echo -e "\n"

# 4. 测试不包含关键字的文本
echo -e "${BLUE}4. 测试不包含关键字的文本（应返回false）${NC}"
curl -s -X POST "${API_PREFIX}/contains" \
  -H "Content-Type: application/json" \
  -d '{"text": "这是一个正常的文本，没有任何问题"}' | jq .
echo -e "\n"

# 5. 测试查找所有匹配的关键字
echo -e "${BLUE}5. 查找文本中所有匹配的关键字${NC}"
curl -s -X POST "${API_PREFIX}/find-all" \
  -H "Content-Type: application/json" \
  -d '{"text": "这段文本包含敏感词1和测试关键字，还有赌博和色情等违禁内容"}' | jq .
echo -e "\n"

# 6. 测试替换关键字
echo -e "${BLUE}6. 替换文本中的关键字${NC}"
curl -s -X POST "${API_PREFIX}/replace" \
  -H "Content-Type: application/json" \
  -d '{"text": "这段文本包含敏感词1和测试关键字", "replacement": "*"}' | jq .
echo -e "\n"

# 7. 高并发性能测试（可选）
echo -e "${BLUE}7. 高并发性能测试（100个并发请求）${NC}"
echo "测试开始时间: $(date +%H:%M:%S)"

# 创建临时测试文件
TEST_DATA='{"text": "这是一个包含敏感词1的测试文本，用于高并发测试。包含多个关键字：测试关键字、赌博、色情等违禁内容。"}'

# 使用GNU parallel或xargs进行并发测试
if command -v parallel &> /dev/null; then
    seq 1 100 | parallel -j 50 "curl -s -X POST '${API_PREFIX}/contains' -H 'Content-Type: application/json' -d '${TEST_DATA}' > /dev/null"
else
    # 如果没有parallel，使用xargs
    seq 1 100 | xargs -P 50 -I {} curl -s -X POST "${API_PREFIX}/contains" -H "Content-Type: application/json" -d "${TEST_DATA}" > /dev/null
fi

echo "测试结束时间: $(date +%H:%M:%S)"
echo -e "${GREEN}✓ 100个并发请求完成${NC}"
echo -e "\n"

# 8. 再次获取统计信息
echo -e "${BLUE}8. 再次获取统计信息${NC}"
curl -s -X GET "${API_PREFIX}/stats" | jq .
echo -e "\n"

echo "=================================="
echo -e "${GREEN}测试完成！${NC}"
echo "=================================="
