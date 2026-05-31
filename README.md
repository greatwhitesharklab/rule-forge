<div align="center">

# RuleForge

**基于 RETE 算法的高性能 Java 规则引擎**

🚀 多类型规则定义 · 🎨 可视化设计器 · 🔥 热部署 · ⚡ 高性能执行

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

</div>

> **⚠️ 项目状态：活跃开发中**
> 本项目已完成监控告警、上游数据源管理、规则版本与发布管理（含灰度发布、陪跑对比），正在进行下游 Agent 分析阶段。

---

## ✨ 特性

- 🧩 **多类型规则定义** — 向导式规则集、脚本式规则集、决策表、决策树、评分卡、决策流
- 🎨 **可视化设计器** — 基于 React + bpmn-js 的 Web 规则编辑器，所见即所得
- ⚡ **RETE 算法** — 高性能规则匹配与执行引擎
- 🔄 **Flowable 8 BPM** — 基于 Flowable 8 的 BPMN 2.0 决策流引擎
- 🔥 **热部署** — 规则动态更新，无需重启服务
- 🔌 **上游数据源管理** — REST API、JDBC、Advance AI 多类型数据源接入，可视化配置
- 📊 **监控与告警** — 决策执行全链路可观测，Micrometer + Prometheus 指标采集
- 🎯 **灰度发布** — 应用层灰度路由（用户比例、随机百分比、白名单），灰度结果写入日志
- 🔄 **陪跑对比** — 流量重放到影子规则包，自动对比主/陪跑差异（4 维度 × 4 级严重度）
- ☕ **标准 Java** — 纯 Java 实现，Spring Boot 4.0，易于集成

## 📦 模块结构

```
⚙️  ruleforge-core            规则引擎核心（RETE 算法、规则解析、知识库）
📦  ruleforge-decision        共享决策模块（数据源、灰度策略、陪跑配置）
🖥️  ruleforge-console         编辑器业务（REST API、项目管理、知识包管理）
🚀  ruleforge-executor        执行器业务（规则执行、知识包接收）
🌐  ruleforge-console-app     可部署的编辑器应用 → 端口 8180
⚡  ruleforge-executor-app    可部署的执行器应用 → 端口 8280
🎨  frontend                  React 可视化规则设计器
```

依赖链：

```
core ← decision ← console ← console-app
core ← executor ← executor-app ← decision
```

## 🚀 快速开始

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

- **Editor API** — http://localhost:8081
- **Executor API** — http://localhost:8082
- **Frontend** — http://localhost:3000

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

## 📚 文档

| 文档 | 说明 |
|------|------|
| 🏗️ [架构概览](docs/architecture/overview.md) | 模块结构、依赖链、执行流程 |
| ⚙️ [RETE 引擎](docs/architecture/rete-engine.md) | RETE 算法实现、会话生命周期 |
| 🌐 [Console API](docs/api/console-api.md) | 编辑器 REST API 参考 |
| ⚡ [Executor API](docs/api/executor-api.md) | 执行器 REST API 参考 |
| 🔧 [开发环境搭建](docs/development/setup.md) | 环境要求、编译、启动 |
| 🤝 [贡献指南](docs/development/contributing.md) | 编码规范、分支策略、开发流程 |
| 📑 [规则类型](docs/user-guide/rule-types.md) | 7 种规则类型说明 |
| 🧪 [规则测试](docs/user-guide/testing.md) | 单条/批量/快速测试 |
| 🗺️ [项目路线图](docs/roadmap.md) | 上游数据源、Agent 分析、版本管理、监控告警 |

## 📄 License

[Apache-2.0](LICENSE)
