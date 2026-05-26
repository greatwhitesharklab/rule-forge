<div align="center">

# RuleForge

**基于 RETE 算法的高性能 Java 规则引擎**

🚀 多类型规则定义 · 🎨 可视化设计器 · 🔥 热部署 · ⚡ 高性能执行

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

</div>

> **⚠️ 项目状态：深度重构中**
> 本项目目前正在进行全面重构（Flowable 8 决策流迁移、测试覆盖补充等），**尚不可用**。需要等待重构全部完成后才能正常使用。

---

---

## ✨ 特性

- 🧩 **多类型规则定义** — 向导式规则集、脚本式规则集、决策表、决策树、评分卡、决策流
- 🎨 **可视化设计器** — 基于 React + bpmn-js 的 Web 规则编辑器，所见即所得
- ⚡ **RETE 算法** — 高性能规则匹配与执行引擎
- 🔄 **Flowable 8 BPM** — 基于 Flowable 8 的 BPMN 2.0 决策流引擎
- 🔥 **热部署** — 规则动态更新，无需重启服务
- ☕ **标准 Java** — 纯 Java 实现，Spring Boot 4.0，易于集成

## 📦 模块结构

```
⚙️  ruleforge-core            规则引擎核心（RETE 算法、规则解析、知识库）
🖥️  ruleforge-console         编辑器业务（REST API、项目管理、知识包管理）
🚀  ruleforge-executor        执行器业务（规则执行、知识包接收）
🌐  ruleforge-console-app     可部署的编辑器应用 → 端口 8081
⚡  ruleforge-executor-app    可部署的执行器应用 → 端口 8082
🎨  frontend                  React 可视化规则设计器
```

依赖链：

```
core ← console ← console-app
core ← executor ← executor-app
```

## 🚀 快速开始

### 📋 环境要求

| 依赖 | 版本 |
|------|------|
| ☕ JDK | 17+ |
| 📦 Maven | 3.8+ |
| 🐬 MySQL | 8.0+ |
| 💚 Node.js | 18+ |

### 1️⃣ 配置

```bash
cp .env.example .env
# 编辑 .env，填入数据库连接信息
```

### 2️⃣ 编译

```bash
cd backend
mvn compile
```

### 3️⃣ 启动

```bash
# 🎯 使用启动脚本（推荐）
./scripts/start-backend.sh    # Console + Executor
./scripts/start-frontend.sh   # Frontend

# 或单独启动
./scripts/start-console.sh    # 仅编辑器
./scripts/start-executor.sh   # 仅执行器
./scripts/start-all.sh        # 全部服务 🚀
```

| 服务 | 地址 |
|------|------|
| 🖥️ 编辑器 API | http://localhost:8081 |
| ⚡ 执行器 API | http://localhost:8082 |
| 🎨 前端设计器 | http://localhost:3000 |

## 📑 规则类型

| 类型 | 说明 |
|------|------|
| 📝 向导式规则集 | 可视化条件-动作规则 |
| 💻 脚本式规则集 (UL) | DSL 脚本语法定义规则 |
| 📊 决策表 | 表格化条件匹配 |
| 📋 脚本决策表 | 脚本驱动的决策表 |
| 🌳 决策树 | 树形结构决策 |
| 📈 评分卡 | 加权评分模型 |
| 🔄 决策流 | 流程编排多规则 |

## 🛠️ 技术栈

| 层 | 技术 |
|----|------|
| ☕ 后端 | Java 17 · Spring Boot 4.0.6 · MyBatis-Plus · MySQL · ANTLR4 · RETE · Flowable 8 |
| 🎨 前端 | React · bpmn-js |
| ✅ 测试 | JUnit 5 · Mockito · AssertJ · Vitest · Playwright |
| 🐳 部署 | Docker |

## ✅ 测试

```bash
# 后端单元测试
cd backend
mvn test

# 前端单元测试
cd frontend
npm test

# 前端 E2E 测试（需要启动后端服务）
npx playwright test
```

## 📄 License

[Apache-2.0](LICENSE)
