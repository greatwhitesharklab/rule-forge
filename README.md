<div align="center">

# RuleForge

**面向金融场景的智能决策引擎**

确定性规则 + ML 模型推理 — 每个决策可审计、可解释、可追溯

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green.svg)](https://spring.io/projects/spring-boot)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6.svg)](https://www.typescriptlang.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

</div>

> **⚠️ 项目状态：活跃开发中**
> Phase 1-11 已完成，Phase 12+ 规划中。详见 [路线图](docs/roadmap.md) 和 [更新日志](CHANGELOG.md)。

---

## ✨ 为什么选择 RuleForge

| | |
|---|---|
| **AI + 规则混合** | 确定性规则引擎 + ML 模型推理，每个决策可审计可解释 |
| **金融场景优先** | 信贷审批、反欺诈、信用评分等开箱即用的业务模板 |
| **可视化全生命周期** | 编辑 → 版本管理 → 灰度发布 → 影子测试 → 监控告警 |
| **高性能 RETE** | 生产级规则匹配算法，毫秒级响应，支持热部署 |

## 💰 金融场景

| 场景 | 说明 |
|------|------|
| 🏦 **小微信贷审批** | 多维度风控规则 + 评分卡，自动化审批决策 |
| 🛡️ **反欺诈交易检测** | 实时规则匹配 + 模型推理，毫秒级风险识别 |
| 📊 **信用评分卡** | 可配置评分模型，支持 A/B 卡对比与灰度发布 |
| 🏥 **保险理赔** | 决策流编排 + 规则集联动，智能理赔审核 |

## 📐 架构

```
┌─────────────┐     ┌──────────────┐
│  Console UI  │────▶│ Console App  │ (编辑器, 端口 8180)
│  (React)     │     │  (Spring)    │
└─────────────┘     └──────┬───────┘
                           │
                 ┌─────────┴─────────┐
                 ▼                   ▼
         ┌──────────────┐   ┌──────────────┐
         │ Executor App │   │ Model Service│
         │ (执行, 8280) │   │ (ML,  8501) │
         └──────┬───────┘   └──────────────┘
                ▼
         ┌──────────────┐
         │    MySQL     │
         │ (规则存储)    │
         └──────────────┘
```

## 🧩 特性

### AI & 金融

- 🤖 **AI 助手** — 自然语言创建规则、智能分析决策日志
- 🧠 **ML 模型推理** — PKL 模型热加载，规则 + 模型混合决策
- 🏦 **金融业务模板** — 信贷审批、反欺诈等场景开箱即用
- 🎯 **灰度发布** — 用户比例、随机百分比、白名单策略，灰度结果写入日志
- 🔄 **陪跑对比** — 流量重放到影子规则包，自动对比主/陪跑差异（4 维度 × 4 级严重度）

### 规则引擎

- ⚡ **RETE 算法** — 高性能规则匹配与执行引擎
- 🧩 **多类型规则** — 向导式规则集、脚本式规则集、决策表、决策树、评分卡、决策流
- 🔄 **Flowable 8 BPM** — 基于 Flowable 8 的 BPMN 2.0 决策流引擎
- 🔥 **热部署** — 规则动态更新，无需重启服务

### 可视化 & 运维

- 🎨 **可视化设计器** — 基于 React + bpmn-js 的 Web 规则编辑器，所见即所得
- 🔌 **数据源管理** — REST API、JDBC、Advance AI 多类型数据源接入
- 📊 **监控与告警** — 决策执行全链路可观测，Micrometer + Prometheus 指标采集
- 🤖 **Agent 分析** — 决策日志聚合、规则覆盖率、偏差检测，CLI + Skills 供外部 Agent 调用

## 📦 模块结构

```
⚙️  ruleforge-core            规则引擎核心（RETE 算法、规则解析、知识库）
📦  ruleforge-decision        共享决策模块（数据源、灰度策略、陪跑配置）
🖥️  ruleforge-console         编辑器业务（REST API、项目管理、知识包管理）
🚀  ruleforge-executor        执行器业务（规则执行、知识包接收）
🌐  ruleforge-console-app     可部署的编辑器应用 → 端口 8180
⚡  ruleforge-executor-app    可部署的执行器应用 → 端口 8280
🎨  frontend                  React 可视化规则设计器
🖥️  cli                       RuleForge CLI（Agent 命令行接口）
```

依赖链：

```
core ← decision ← console ← console-app
core ← executor ← executor-app ← decision
```

## 🚀 快速开始

### 方式一：Docker Compose（推荐）

```bash
git clone https://github.com/FredGoo/rule-forge.git
cd rule-forge
docker compose up
```

启动后打开 http://localhost 即可访问编辑器界面。

### 方式二：手动构建

#### 1️⃣ 配置

```bash
cp .env.example .env
# 编辑 .env，填入数据库连接信息
```

#### 2️⃣ 编译

```bash
cd server
mvn compile
```

#### 3️⃣ 启动

```bash
# 1) 本地 mvn 编译 + docker build 镜像(增量,几秒)
./scripts/build-images.sh

# 2a) Docker 全栈启动(推荐)
./scripts/dev-up.sh             # 启动 — 保留 MySQL 数据
./scripts/dev-up.sh --clean     # 启动 — 清数据卷重新 init
./scripts/dev-up.sh --logs      # 启动 + tail 关键日志
./scripts/dev-up.sh --stop      # 只停不起

# 2b) Java 本地跑(其它容器化)
./scripts/dev-local.sh console  # 本地跑 console,MySQL/UI/Model 用 docker
./scripts/dev-local.sh executor # 本地跑 executor
./scripts/dev-local.sh all      # 本地跑 console + executor
./scripts/dev-local.sh --stop   # 停本地 Java 进程
```

- **Editor API** — http://localhost:8180
- **Executor API** — http://localhost:8280
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
| 🎨 前端 | TypeScript · React · Vite 8 · Ant Design 5 · bpmn-js |
| 🧠 AI/ML | PKL Model Service · Python · Agent 分析 |
| ✅ 测试 | JUnit 5 · Mockito · AssertJ · Vitest · Playwright |
| 🐳 部署 | Docker · Docker Compose |

## ✅ 测试

```bash
# 后端单元测试
cd server
mvn test

# 前端单元测试
cd console-ui
npm test

# 前端 E2E 测试（需要启动后端服务）
npx playwright test
```

## 📚 文档

| 文档 | 说明 |
|------|------|
| 📖 [在线文档](https://fredgoo.github.io/rule-forge/) | VitePress 文档站点 |
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
