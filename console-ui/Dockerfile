# Stage 1: 构建阶段
FROM node:18-alpine AS builder

WORKDIR /app

# 复制依赖文件
COPY package*.json ./

# 安装所有依赖（包括 devDependencies，用于构建）
RUN npm install

# 复制源代码
COPY . .

# 构建生产版本
RUN npm run build

# Stage 2: 运行阶段
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 创建版本信息文件
ARG BUILD_VERSION=unknown
ARG BUILD_COMMIT=unknown
ARG BUILD_DATE=unknown

RUN echo "Version: ${BUILD_VERSION}" > /usr/share/nginx/html/VERSION.txt && \
    echo "Commit: ${BUILD_COMMIT}" >> /usr/share/nginx/html/VERSION.txt && \
    echo "Build Date: ${BUILD_DATE}" >> /usr/share/nginx/html/VERSION.txt

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1
