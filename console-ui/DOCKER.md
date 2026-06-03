# RuleForge Console Docker 构建与部署指南

## 概述

本项目使用 Docker 多阶段构建，生成精简的生产级镜像。

## 核心特性

✅ **多阶段构建** - 最终镜像精简（仅包含静态文件 + nginx）
✅ **版本追踪** - 支持嵌入构建版本、commit 和时间
✅ **健康检查** - 内置容器健康检查机制
✅ **生产优化** - Gzip 压缩、缓存策略、安全响应头

## 快速开始

### 1. 构建镜像

```bash
# 基础构建
docker build -t ruleforge-console-ui:latest .

# 带版本信息构建（推荐）
docker build \
  --build-arg BUILD_VERSION=v1.0.0 \
  --build-arg BUILD_COMMIT=$(git rev-parse --short HEAD) \
  --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
  -t ruleforge-console-ui:v1.0.0 \
  .
```

### 2. 运行容器

```bash
# 基础运行
docker run -d -p 8080:80 ruleforge-console-ui:latest

# 完整示例
docker run -d \
  --name ruleforge-console-ui \
  -p 8080:80 \
  --restart unless-stopped \
  ruleforge-console-ui:latest
```

### 3. 验证部署

```bash
# 检查容器状态
docker ps | grep ruleforge-console-ui

# 查看容器日志
docker logs ruleforge-console-ui

# 检查版本信息
curl http://localhost:8080/VERSION.txt

# 健康检查
curl http://localhost:8080/health
```

## 环境变量说明

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `CONSOLE_PORT` | 前端服务暴露的端口 | `8080` |
| `BUILD_VERSION` | 构建版本号（用于镜像标签） | `dev` |
| `BUILD_COMMIT` | Git commit hash | `unknown` |
| `BUILD_DATE` | 构建时间 | `unknown` |

## Docker Compose 集成

### 基础配置

```yaml
services:
  ruleforge-console-ui:
    image: ruleforge-console-ui:latest
    container_name: ruleforge-console
    ports:
      - "8080:80"
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost/health"]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 5s
```

### 使用 docker-compose.yml

```bash
# 启动服务
docker-compose up -d

# 自定义端口
CONSOLE_PORT=3000 docker-compose up -d

# 查看日志
docker-compose logs -f ruleforge-console

# 停止服务
docker-compose down
```

## GitHub Actions CI/CD

项目包含 GitHub Actions workflow，支持：
- 手动触发构建和部署
- 可选版本号（默认使用时间戳）
- 仅构建模式（build_only）
- 自动健康检查和验证
- 镜像版本管理（保留最近5个版本）

### 使用方法

1. 进入 GitHub Actions 页面
2. 选择 "Build and Deploy Docker Image" workflow
3. 点击 "Run workflow"
4. 可选配置：
   - **version**: 版本号（如 v1.0.0）
   - **build_only**: 仅构建不部署

## 镜像信息

### 查看构建信息

```bash
# 查看版本文件
docker exec ruleforge-console-ui cat /usr/share/nginx/html/VERSION.txt

# 或通过 HTTP 访问
curl http://localhost:8080/VERSION.txt
```

### 镜像大小优化

- 使用 Alpine Linux 基础镜像
- 多阶段构建（构建依赖不包含在最终镜像中）
- 预期最终镜像大小：~50-80MB

## 故障排查

### 1. 容器启动失败

```bash
# 查看容器日志
docker logs ruleforge-console-ui

# 检查容器状态
docker ps -a | grep ruleforge-console-ui
```

### 2. 健康检查失败

```bash
# 手动测试健康检查
docker exec ruleforge-console-ui wget --quiet --tries=1 --spider http://localhost/health

# 检查 Nginx 配置
docker exec ruleforge-console-ui nginx -t
```

### 3. 无法访问服务

```bash
# 检查端口绑定
docker port ruleforge-console-ui

# 检查防火墙设置
curl -v http://localhost:8080/health
```

## 最佳实践

1. **版本管理**: 始终为镜像打标签，避免只用 `latest`
2. **健康检查**: 启用健康检查以便容器编排工具监控
3. **日志管理**: 使用 `docker logs` 或集中式日志收集
4. **资源限制**: 生产环境建议设置 CPU 和内存限制
5. **安全加固**:
   - 不在镜像中包含敏感信息
   - 定期更新基础镜像
   - 使用非 root 用户运行（可选）

## 全栈启动加速（commit `b4f821b` 起）

整个 RuleForge 全栈(`docker compose up -d` → 5 服务全 healthy)在 Linux + 预构建镜像下约 **18 秒**。本项目的关键加速项:

### JVM 启动加速
后端 Dockerfile 通过 `JAVA_OPTS` 环境变量传入(`docker-compose.yml` 已配):
- `-XX:TieredStopAtLevel=1` — 跳过 C2 JIT,首次请求走解释执行,JIT 后台异步。冷启省 1-3s
- `-XX:+ExitOnOutOfMemoryError` — OOM 立即退出,容器早 fail 早重启

### MySQL 启动加速
`docker-compose.yml` 的 `mysql.command`:
- `--skip-name-resolve` — 关 client DNS 反解,容器连 MySQL 每次握手省 ~5-50ms
- `--innodb-buffer-pool-size=512M` — 显式指定,免去启动期探测 host RAM
- `--skip-log-bin` — dev 不需要 binlog,关掉省 init + 写入开销
- `--performance-schema=OFF` — 关 perf schema,启动期省 ~500ms

### Spring Boot 启动加速
`application.yml`:
- `spring.main.banner-mode: off` — 关 banner 解析打印
- `spring.jmx.enabled: false` — 关 JMX 注册,各省 ~100-300ms

### 容器编排
- 所有服务加 `init: true` — tini 收信号,容器 PID 1 干净,关停更快
- `console-ui` 用 `depends_on: console-app: { condition: service_healthy }` — 等 Spring Boot 起来才起 nginx,避免首屏 502

### 验证
```bash
$ time docker compose up -d
...
real    0m18.3s
$ docker ps --filter name=ruleforge- --format 'table {{.Names}}\t{{.Status}}'
ruleforge-console-ui      Up 6 seconds (healthy)
ruleforge-console         Up 12 seconds (healthy)
ruleforge-executor        Up 12 seconds (healthy)
ruleforge-mysql           Up 17 seconds (healthy)
ruleforge-model-service   Up 17 seconds (healthy)
```

## 本地开发工作流

`scripts/` 目录提供 3 个 wrapper,免去手工 mvn / docker / vite 切换:

```bash
# 改 Java 代码后的标准 dev 流程
./scripts/build-images.sh            # 本地 mvn 增量编译 + docker build(只重 build 改过的 module)
./scripts/dev-up.sh                 # 启动/重启容器(默认保留数据卷)
./scripts/dev-up.sh --logs          # 启动后 tail 关键日志

# 本地跑 Java + Docker 跑依赖(更快的代码-重启循环)
./scripts/dev-local.sh console       # mvn spring-boot:run 起 console,其它容器化
./scripts/dev-local.sh all          # console + executor 都本地
./scripts/dev-local.sh --stop       # 停掉本地 Java
```

## 总结

通过这套方案，你可以：
- ✅ 快速构建生产级 Docker 镜像
- ✅ 通过 docker-compose 本地快速部署
- ✅ 轻松集成到 CI/CD 流程
- ✅ 获得完整的版本追踪和健康监控
- ✅ 端到端 5 服务冷启 < 20s(配合 `build-images.sh` 增量)
