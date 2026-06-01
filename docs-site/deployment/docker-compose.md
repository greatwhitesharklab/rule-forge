---
title: Docker Compose 部署
---

# Docker Compose 部署

## 一键启动

```bash
git clone https://github.com/FredGoo/rule-forge.git
cd rule-forge
docker compose up -d
```

## 服务说明

| 服务 | 端口 | 说明 |
|------|------|------|
| console-ui | 80 | React 前端（Nginx） |
| console-app | 8180 | 编辑器后端（Spring Boot） |
| executor-app | 8280 | 规则执行器 |
| model-service | 8501 | ML 模型推理（Python） |
| mysql | 3306 | 数据库 |

## 自定义配置

创建 `.env` 文件覆盖默认配置：

```env
# 数据库密码
MYSQL_ROOT_PASSWORD=your_secure_password

# 服务端口
CONSOLE_UI_PORT=80
MYSQL_PORT=3306
```

## 数据持久化

Docker Compose 使用 named volumes 持久化数据：

```bash
# 查看卷
docker volume ls | grep ruleforge

# 备份数据
docker run --rm -v ruleforge_mysql_data:/data -v $(pwd):/backup alpine tar czf /backup/mysql-backup.tar.gz /data
```

## 常用命令

```bash
# 查看日志
docker compose logs -f console-app

# 重启单个服务
docker compose restart console-app

# 重新构建
docker compose up -d --build

# 停止并删除容器
docker compose down

# 停止并删除容器+数据
docker compose down -v
```
