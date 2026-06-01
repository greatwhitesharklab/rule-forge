# ================================
# RuleForge Model Service - Python FastAPI
# ================================

FROM python:3.11-slim AS builder

WORKDIR /app

# 安装 uv（快速 Python 包管理器）
RUN pip install --no-cache-dir uv

# 复制依赖文件
COPY pyproject.toml .
COPY uv.lock .

# 安装依赖
RUN uv sync --frozen --no-dev

# ================================
# 运行环境
# ================================
FROM python:3.11-slim AS runtime

WORKDIR /app

# 从构建阶段复制虚拟环境
COPY --from=builder /app/.venv /app/.venv

# 复制应用代码
COPY app/ app/

# 环境变量
ENV PATH="/app/.venv/bin:$PATH" \
    PORT=8501 \
    LOG_LEVEL=INFO

EXPOSE 8501

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8501/health')" || exit 1

# 启动
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8501"]
