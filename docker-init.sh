#!/bin/bash

# ============================================================
# MySQL 8.0 Docker 初始化脚本
# 项目：minio-multipart-upload
# 版本：1.0.0
# 描述：用于Docker容器中的MySQL数据库初始化
# ============================================================

set -e

# 等待MySQL服务启动
echo "等待MySQL服务启动..."
while ! mysqladmin ping -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" --silent; do
    echo "MySQL尚未启动，等待中..."
    sleep 2
done

echo "MySQL服务已启动，开始初始化数据库..."

# 执行初始化SQL
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" < /docker-entrypoint-initdb.d/mysql8-init.sql

echo "数据库初始化完成！"

# 创建应用用户（如果配置了）
if [ ! -z "$MYSQL_APP_USER" ] && [ ! -z "$MYSQL_APP_PASSWORD" ]; then
    echo "创建应用用户..."
    mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASSWORD" << EOF
CREATE USER IF NOT EXISTS '$MYSQL_APP_USER'@'%' IDENTIFIED BY '$MYSQL_APP_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`minio_upload\`.* TO '$MYSQL_APP_USER'@'%';
FLUSH PRIVILEGES;
EOF
    echo "应用用户创建完成！"
fi

echo "所有初始化任务完成！"