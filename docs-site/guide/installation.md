---
title: 安装部署
---

# 安装部署

## 环境要求

| 组件 | 最低版本 | 说明 |
|------|---------|------|
| Java | 17+ | OpenJDK 或 Oracle JDK |
| Maven | 3.8+ | Java 构建工具 |
| Node.js | 18+ | 前端构建 |
| MySQL | 8.0+ | 规则存储 |
| Python | 3.11+ | Model Service（可选） |

## 方式一：Docker Compose（推荐）

参见 [快速开始](/guide/getting-started)。

## 方式二：源码编译

### 1. 克隆代码

```bash
git clone https://github.com/FredGoo/rule-forge.git
cd rule-forge
```

### 2. 配置数据库

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS app_db DEFAULT CHARSET utf8mb4;"
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ruleforge_db DEFAULT CHARSET utf8mb4;"

# 配置环境变量
cp .env.example .env
# 编辑 .env，填入数据库连接信息
```

### 3. 编译后端

```bash
cd server
mvn clean package -DskipTests
```

### 4. 启动服务

```bash
# 1) 本地 mvn 编译 + docker build 镜像
./scripts/build-images.sh

# 2) Docker 全栈启动
./scripts/dev-up.sh             # 保留 MySQL 数据
./scripts/dev-up.sh --clean     # 清数据卷重新 init
./scripts/dev-up.sh --logs      # 启动 + tail 关键日志
./scripts/dev-up.sh --stop      # 只停不起

# 或手动启动
cd server
mvn spring-boot:run -pl ruleforge-console-app &  # 编辑器（端口 8180）
mvn spring-boot:run -pl ruleforge-executor-app & # 执行器（端口 8280）

cd ../console-ui
npm install && npm run dev                      # 前端（端口 3000）
```

### 5. 启动 Model Service（可选）

```bash
cd model-service
pip install uv
uv sync
uv run uvicorn app.main:app --host 0.0.0.0 --port 8501
```

## 配置说明

主要环境变量（在 `.env` 文件中配置）：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `CONSOLE_PORT` | 编辑器端口 | 8180 |
| `EXECUTOR_PORT` | 执行器端口 | 8280 |
| `APP_DB_URL` | 应用数据库连接 | - |
| `RF_DB_URL` | 规则数据库连接 | - |
| `EXECUTOR_URL` | 执行器地址 | http://localhost:8280 |
| `AGENT_ENABLED` | 启用 AI 助手 | false |
