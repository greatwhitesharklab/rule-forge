---
title: 开发环境搭建
---

# 开发环境搭建

## 前置要求

- **Java 17+** — 推荐使用 Eclipse Temurin
- **Maven 3.8+** — Java 构建工具
- **Node.js 18+** — 前端构建
- **MySQL 8.0+** — 数据库
- **Git** — 版本控制

## 快速搭建

### 1. Fork 并克隆

```bash
git clone https://github.com/YOUR_USERNAME/rule-forge.git
cd rule-forge
```

### 2. 配置数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS app_db DEFAULT CHARSET utf8mb4;"
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ruleforge_db DEFAULT CHARSET utf8mb4;"
```

### 3. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 填入你的数据库密码
```

### 4. 编译

```bash
cd server
mvn compile
```

### 5. 启动开发环境

```bash
# 后端
./scripts/build-images.sh    # 本地 mvn 编译 + docker build
./scripts/dev-up.sh         # Docker 全栈启动
# 或本地跑 Java(其它容器化):
./scripts/dev-local.sh console

# 前端（新终端）
cd console-ui
npm install
npm run dev
```

## IDE 配置

### IntelliJ IDEA

1. 安装 Lombok 插件
2. 导入为 Maven 项目
3. 设置 JDK 17

### VS Code

推荐安装以下扩展：
- Extension Pack for Java
- Spring Boot Extension Pack
- Vue - Official（用于 VitePress 开发）
