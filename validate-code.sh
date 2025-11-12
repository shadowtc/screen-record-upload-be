#!/bin/bash

# 代码验证脚本
# 检查Java代码语法和基本问题

echo "=== MinIO分片上传代码验证 ==="
echo ""

# 检查Java文件语法
echo "1. 检查Java文件语法..."
find src/main/java -name "*.java" -exec echo "检查文件: {}" \; -exec javac -cp "$(find . -name "*.jar" | tr '\n' ':')" {} \; 2>&1 | grep -E "(error|Error|ERROR)" || echo "✅ 所有Java文件语法正确"

echo ""

# 检查关键方法是否存在
echo "2. 检查关键方法是否存在..."
SERVICE_FILE="src/main/java/com/example/minioupload/service/MultipartUploadService.java"
REPO_FILE="src/main/java/com/example/minioupload/repository/VideoRecordingRepository.java"

if grep -q "validateParts" "$SERVICE_FILE"; then
    echo "✅ validateParts方法存在"
else
    echo "❌ validateParts方法缺失"
fi

if grep -q "existsByObjectKey" "$REPO_FILE"; then
    echo "✅ existsByObjectKey方法存在"
else
    echo "❌ existsByObjectKey方法缺失"
fi

if grep -q "MIN_CHUNK_SIZE" "$SERVICE_FILE"; then
    echo "✅ 分片大小验证存在"
else
    echo "❌ 分片大小验证缺失"
fi

echo ""

# 检查错误处理
echo "3. 检查错误处理..."
if grep -q "try.*catch" "$SERVICE_FILE"; then
    echo "✅ 包含try-catch错误处理"
else
    echo "❌ 缺少try-catch错误处理"
fi

if grep -q "IllegalArgumentException" "$SERVICE_FILE"; then
    echo "✅ 包含参数验证"
else
    echo "❌ 缺少参数验证"
fi

echo ""

# 检查日志记录
echo "4. 检查日志记录..."
if grep -q "log.error" "$SERVICE_FILE"; then
    echo "✅ 包含错误日志记录"
else
    echo "❌ 缺少错误日志记录"
fi

if grep -q "log.warn" "$SERVICE_FILE"; then
    echo "✅ 包含警告日志记录"
else
    echo "❌ 缺少警告日志记录"
fi

echo ""

# 检查测试文件
echo "5. 检查测试文件..."
TEST_FILE="src/test/java/com/example/minioupload/service/MultipartUploadServiceValidationTest.java"
if [ -f "$TEST_FILE" ]; then
    echo "✅ 验证测试文件存在"
    if grep -q "testValidateParts" "$TEST_FILE"; then
        echo "✅ 包含validateParts测试"
    else
        echo "❌ 缺少validateParts测试"
    fi
else
    echo "❌ 验证测试文件缺失"
fi

echo ""

# 检查配置文件
echo "6. 检查配置文件..."
if [ -f "application-test.yml" ]; then
    echo "✅ 测试配置文件存在"
else
    echo "❌ 测试配置文件缺失"
fi

echo ""

# 检查文档
echo "7. 检查文档..."
if [ -f "MINIO_MULTIPART_UPLOAD_FIXES.md" ]; then
    echo "✅ 修复文档存在"
else
    echo "❌ 修复文档缺失"
fi

echo ""

# 统计代码行数
echo "8. 代码统计..."
echo "Java文件数量: $(find src/main/java -name "*.java" | wc -l)"
echo "Java代码行数: $(find src/main/java -name "*.java" -exec wc -l {} \; | tail -1 | awk '{print $1}')"
echo "测试文件数量: $(find src/test/java -name "*.java" | wc -l)"

echo ""
echo "=== 验证完成 ==="