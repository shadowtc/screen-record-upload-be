#!/bin/bash

# ============================================================
# MySQL 8.0 数据库初始化测试脚本
# 项目：minio-multipart-upload
# 版本：1.0.0
# 描述：测试数据库初始化脚本是否正确执行
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置变量
MYSQL_HOST=${MYSQL_HOST:-localhost}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_ROOT_USER=${MYSQL_ROOT_USER:-root}
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-root123}
DATABASE_NAME=${DATABASE_NAME:-minio_upload}

echo -e "${GREEN}开始测试 MySQL 8.0 数据库初始化...${NC}"

# 测试数据库连接
echo -e "${YELLOW}测试数据库连接...${NC}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" -e "SELECT 1;" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 数据库连接成功${NC}"
else
    echo -e "${RED}✗ 数据库连接失败${NC}"
    exit 1
fi

# 备份现有数据库（如果存在）
echo -e "${YELLOW}检查现有数据库...${NC}"
if mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" -e "USE $DATABASE_NAME;" > /dev/null 2>&1; then
    echo -e "${YELLOW}发现现有数据库，正在备份...${NC}"
    mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" "$DATABASE_NAME" > "backup_$(date +%Y%m%d_%H%M%S).sql"
    echo -e "${GREEN}✓ 数据库备份完成${NC}"
fi

# 执行快速初始化脚本
echo -e "${YELLOW}执行快速初始化脚本...${NC}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" < mysql8-init.sql
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 快速初始化脚本执行成功${NC}"
else
    echo -e "${RED}✗ 快速初始化脚本执行失败${NC}"
    exit 1
fi

# 验证表结构
echo -e "${YELLOW}验证表结构...${NC}"
TABLE_EXISTS=$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DATABASE_NAME' AND table_name = 'video_recordings';" -s -N)
if [ "$TABLE_EXISTS" = "1" ]; then
    echo -e "${GREEN}✓ video_recordings 表创建成功${NC}"
else
    echo -e "${RED}✗ video_recordings 表创建失败${NC}"
    exit 1
fi

# 验证索引
echo -e "${YELLOW}验证索引...${NC}"
INDEX_COUNT=$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = '$DATABASE_NAME' AND table_name = 'video_recordings';" -s -N)
if [ "$INDEX_COUNT" -ge "5" ]; then
    echo -e "${GREEN}✓ 索引创建成功 (共 $INDEX_COUNT 个索引)${NC}"
else
    echo -e "${RED}✗ 索引创建失败 (只有 $INDEX_COUNT 个索引)${NC}"
    exit 1
fi

# 测试数据插入
echo -e "${YELLOW}测试数据插入...${NC}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" "$DATABASE_NAME" -e "
INSERT INTO video_recordings (user_id, filename, size, object_key, status) 
VALUES ('test_user', 'test_video.mp4', 1024000, 'test/test_video.mp4', 'COMPLETED');
" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 数据插入测试成功${NC}"
else
    echo -e "${RED}✗ 数据插入测试失败${NC}"
    exit 1
fi

# 测试数据查询
echo -e "${YELLOW}测试数据查询...${NC}"
RECORD_COUNT=$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM $DATABASE_NAME.video_recordings WHERE user_id = 'test_user';" -s -N)
if [ "$RECORD_COUNT" = "1" ]; then
    echo -e "${GREEN}✓ 数据查询测试成功${NC}"
else
    echo -e "${RED}✗ 数据查询测试失败${NC}"
    exit 1
fi

# 测试唯一约束
echo -e "${YELLOW}测试唯一约束...${NC}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" "$DATABASE_NAME" -e "
INSERT INTO video_recordings (user_id, filename, size, object_key, status) 
VALUES ('test_user2', 'test_video2.mp4', 1024000, 'test/test_video.mp4', 'COMPLETED');
" > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo -e "${GREEN}✓ 唯一约束测试成功（重复插入被拒绝）${NC}"
else
    echo -e "${RED}✗ 唯一约束测试失败（重复插入未被拒绝）${NC}"
    exit 1
fi

# 清理测试数据
echo -e "${YELLOW}清理测试数据...${NC}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" "$DATABASE_NAME" -e "DELETE FROM video_recordings WHERE user_id IN ('test_user', 'test_user2');" > /dev/null 2>&1

# 显示数据库信息
echo -e "${YELLOW}数据库信息：${NC}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" -e "
SELECT 
    'Database' as Type,
    '$DATABASE_NAME' as Name,
    'Created' as Status
UNION ALL
SELECT 
    'Table' as Type,
    TABLE_NAME as Name,
    'Exists' as Status
FROM information_schema.tables 
WHERE table_schema = '$DATABASE_NAME'
UNION ALL
SELECT 
    'Index' as Type,
    INDEX_NAME as Name,
    CONCAT('Count: ', COUNT(*)) as Status
FROM information_schema.statistics 
WHERE table_schema = '$DATABASE_NAME' AND table_name = 'video_recordings'
GROUP BY INDEX_NAME;
"

echo -e "${GREEN}✓ 所有测试通过！数据库初始化脚本工作正常。${NC}"
echo -e "${YELLOW}提示：您现在可以使用 Spring Boot 应用连接到数据库了。${NC}"