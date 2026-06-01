# ================================
# 第三阶段：运行环境
# ================================
FROM eclipse-temurin:17-jre AS runtime

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 创建应用用户
RUN groupadd -r ruleforge && useradd -r -g ruleforge ruleforge

# 设置工作目录
WORKDIR /app

# 设置环境变量
ENV JAVA_OPTS="-Xmx2048m -Xms512m -XX:+UseG1GC -XX:+UseContainerSupport"
ENV SERVER_PORT=8080
ENV JAR_FILE=/app/app.jar

# 复制启动脚本
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

# 创建日志目录
RUN mkdir -p /app/logs && chown -R ruleforge:ruleforge /app

# 从构建阶段复制jar文件
COPY ruleforge-prod-console/target/ruleforge-prod-console.jar /app/app.jar

# 切换到应用用户
USER ruleforge

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

# 设置入口点
ENTRYPOINT ["/app/entrypoint.sh"]