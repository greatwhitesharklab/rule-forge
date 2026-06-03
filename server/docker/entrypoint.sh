#!/bin/bash

# RuleForge Console Docker启动脚本
# 完全通过环境变量配置

set -e

# 从环境变量读取配置（提供默认值）
JAR_FILE=${JAR_FILE:-/app/app.jar}
SERVER_PORT=${SERVER_PORT:-8080}
JAVA_OPTS=${JAVA_OPTS:-"-Xmx1024m -Xms512m -XX:+UseG1GC -XX:+UseContainerSupport"}

# 打印启动信息
echo "=============================================="
echo "RuleForge Console Docker Container"
echo "=============================================="
echo "JAR File: ${JAR_FILE}"
echo "Server Port: ${SERVER_PORT}"
echo "Java Options: ${JAVA_OPTS}"
echo "=============================================="

# 检查jar文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    echo "Please check JAR_FILE environment variable"
    exit 1
fi

# 设置JVM参数
JVM_OPTS="$JAVA_OPTS \
    -Dserver.port=${SERVER_PORT} \
    -Dlogging.file.path=/app/logs \
    -Djava.security.egd=file:/dev/./urandom"

# 添加内存限制（如果设置了容器内存限制）
if [ -n "$JAVA_MAX_MEM_RATIO" ]; then
    JVM_OPTS="$JVM_OPTS -XX:MaxRAMPercentage=$JAVA_MAX_MEM_RATIO"
fi

echo "JVM Options: $JVM_OPTS"
echo "=============================================="
echo "Starting application..."
echo ""

# 启动应用
exec java $JVM_OPTS -jar "$JAR_FILE"
