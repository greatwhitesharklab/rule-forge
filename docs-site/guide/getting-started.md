---
title: 快速开始
---

# 快速开始

## Docker Compose 一键启动（推荐）

最简单的方式是使用 Docker Compose 一键启动所有服务：

::: code-group
```bash
# 克隆项目
git clone https://github.com/FredGoo/rule-forge.git
cd rule-forge

# 启动所有服务
docker compose up -d
```
:::

启动后访问：
- **Console UI**: http://localhost — 可视化规则编辑器
- **Console API**: http://localhost:8180 — 编辑器后端
- **Executor API**: http://localhost:8280 — 规则执行器
- **Model Service**: http://localhost:8501 — ML 模型推理服务

## 验证服务状态

```bash
# 检查所有服务状态
docker compose ps

# 查看日志
docker compose logs -f console-app
```

## 下一步

- [安装部署](/guide/installation) — 详细部署配置说明
- [第一个规则](/guide/quick-start) — 创建你的第一条规则
- [小微信贷审批教程](/tutorial/sme-loan-approval) — 完整金融场景实战
