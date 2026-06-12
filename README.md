<div align="center">

# RuleForge

**面向金融场景的智能决策引擎**

确定性规则 + ML 模型推理 — 每个决策可审计、可解释、可追溯

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green.svg)](https://spring.io/projects/spring-boot)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6.svg)](https://www.typescriptlang.org/)
[![Rust](https://img.shields.io/badge/Rust-alpha-orange.svg)](experiments/server-rust/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

</div>

> **⚠️ 项目状态：活跃开发中**
> Phase 1-12 + V5.28-V5.40 已完成 (Rust 端 RETE 引擎 + Java 端 BPMN 2.0 完整化 + 多池协作 + 异步消息 + SPI 化 + 接口分离 + **决策表 → DMN 1.3 切格式(V5.40 路线 B 第一刀,Kie DMN 10.1.0,DmnTableDeserializer/Serializer + DmnResourceDispatcher + XmlToDmnTableConverter 一次性迁移工具,老 .xml 路径保留并行 — V5.41/V5.42 再删**))。V5.41+ 规划中。详见 [路线图](docs/roadmap.md) 和 [更新日志](CHANGELOG.md)。

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

```mermaid
graph TB
    subgraph Client["浏览器"]
        UI["Console UI<br/>React + Vite + Ant Design 5<br/>(nginx :80 / vite dev :3000)"]
    end

    subgraph Backend["后端服务"]
        ConsoleApp["Console App<br/>Spring Boot 4.0.6 :8180<br/>编辑器后端 + REST API<br/>(@/actuator/health)"]
        ExecutorApp["Executor App<br/>Spring Boot 4.0.6 :8280<br/>规则执行 + 知识包接收<br/>(@/actuator/health)"]
        ModelService["Model Service<br/>Python FastAPI :8501<br/>PKL 模型推理"]
    end

    DB[("MySQL 8.0<br/>app_db / ruleforge_db / flowable_db<br/>HikariCP × 3 连接池")]

    UI -->|REST| ConsoleApp
    UI -.->|REST<br/>规则执行| ExecutorApp
    ConsoleApp -->|读写规则 / 项目 / 日志| DB
    ExecutorApp -->|加载知识包 / 写决策日志| DB
    ExecutorApp -->|PKL 推理| ModelService
    ConsoleApp -.->|可选:模型预测| ModelService

    classDef client fill:#e3f2fd,stroke:#1976d2,color:#0d47a1
    classDef backend fill:#fff3e0,stroke:#f57c00,color:#e65100
    classDef db fill:#f3e5f5,stroke:#7b1fa2,color:#4a148c
    class UI client
    class ConsoleApp,ExecutorApp,ModelService backend
    class DB db
```

**端口速查**

| 服务 | 端口 | 健康检查 |
|------|------|----------|
| Console UI (vite dev) | 3000 | — |
| Console UI (docker nginx) | 80 | wget / |
| Console App | 8180 | /actuator/health |
| Executor App | 8280 | /actuator/health |
| Model Service | 8501 | /health |
| MySQL | 3306 | mysqladmin ping |

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
- **V5.33-V5.36 (Java 端 BPMN 2.0 完整化)** — ParallelGateway 真 JOIN + Multi-Instance parallel + Error/Escalation/Terminate/Cancel/Compensation/Message/Signal EndEvent 严格化 + Compensation SAGA + IntermediateEvent (Message/Signal/Timer/Link/Conditional) + UEL 表达式 + Polling worker
- **V5.37 (多池协作)** — BPMN §12 Collaboration + Pool + Lane 完整支持,跨池 Message Flow 走 MessageBus transport
- **V5.37 (对话协议)** — BPMN §11 Choreography IR + parser(纯协议层补完,executor 留给未来)
- **V5.38 (异步消息)** — MessageBus SPI(InMemoryMessageBus) + FlowResumer 桥接 + Send Task / Receive Task 单 pool 异步回调节点(channel 命名空间 `message:<name>`,跟跨池 `pool:<from>_to_<to>:<name>` 隔离)
- **V5.39 (SPI 化 + 角色化上下文 + 接口分离)** — `MessageBusProvider` + `MessageBusRegistry` 多实现优先级;`FlowContext` 20 字段 god-object 一次性拆 `FlowIdentity` + `BusinessVars` + `ReteSession` + `SuspendRegistry` 4 角色;`FlowEngine implements StatelessDecisionExecutor, StatefulDecisionFlow` — 纯函数式求值 vs 长生命周期在类型系统层面显式分
- **V5.40 (路线 B 第一刀:决策表 → DMN 1.3)** — Kie DMN 10.1.0 依赖(`kie-dmn-core` / `kie-dmn-api` / `kie-dmn-feel`)+ `DmnTableDeserializer` (DMN → DecisionTable) + `DmnTableSerializer` (DecisionTable → DMN 1.3 XML) + `DmnResourceDispatcher` (.dmn 单点转换) + `XmlToDmnTableConverter` (一次性 .xml → .dmn 迁移工具);`DecisionTable` model 加 4 字段(`hitPolicy` / `aggregation` / `dialect` / `variableName`)+ 3 个新 enum(`HitPolicy` 7 种 / `Aggregation` 5 种 / `TableDialect` RULEFORGE_NATIVE|DMN);`KnowledgeBuilder.buildKnowledgeBase()` 入口加 `.dmn` 路径分流(7 行改动,老 .xml 路径完全保留并行 0 破坏);console-ui 加 `decisionTableDialect.ts` utility module 镜像后端 enum

### 可视化 & 运维

- 🎨 **可视化设计器** — 基于 React + bpmn-js 的 Web 规则编辑器，所见即所得
- 🔌 **数据源管理** — REST API、JDBC、Advance AI 多类型数据源接入
- 📊 **监控与告警** — 决策执行全链路可观测，Micrometer + Prometheus 指标采集
- 🤖 **Agent 分析** — 决策日志聚合、规则覆盖率、偏差检测，CLI + Skills 供外部 Agent 调用

## 📦 模块结构

```
⚙️  ruleforge-core            规则引擎核心（RETE 算法、规则解析、知识库）
📦  ruleforge-decision        共享决策模块（数据源、灰度策略、陪跑配置）
🌐  ruleforge-console-app     可部署的编辑器应用 → 端口 8180
⚡  ruleforge-executor-app    可部署的执行器应用 → 端口 8280
🎨  console-ui                React + Vite + Ant Design 5 可视化设计器
🖥️  cli                       RuleForge CLI（Agent 命令行接口）
🐍  model-service             Python FastAPI 微服务（PKL 模型推理）
🦀  experiments/server-rust   Rust 规则引擎 + BPMN 执行器（**alpha / 实验性**）
```

> 历史说明:原 `ruleforge-console` / `ruleforge-executor` 子模块已合入 `console-app` / `executor-app`(commits `5f01ebe5` / `f963fd5`);原 `frontend/` 目录已重命名为 `console-ui/`(commit `06c59925`)。

依赖链：

```mermaid
graph LR
    core["ruleforge-core<br/>RETE / 解析 / 知识库"]
    decision["ruleforge-decision<br/>数据源 / 灰度 / 陪跑"]
    console["ruleforge-console-app<br/>编辑器应用 :8180"]
    executor["ruleforge-executor-app<br/>执行器应用 :8280"]
    rustEx["experiments/server-rust<br/>🦀 Rust 引擎 + BPMN 执行器<br/><b>alpha · 实验性</b>"]

    core --> decision
    decision --> console
    core --> executor
    decision --> executor
    core -. Rust RE-implementation .-> rustEx

    classDef module fill:#e8f5e9,stroke:#388e3c,color:#1b5e20
    classDef experimental fill:#fff3e0,stroke:#e65100,color:#bf360c,stroke-dasharray: 5 5
    class core,decision,console,executor module
    class rustEx experimental
```

## 🦀 实验性 Rust 引擎(仅 `experiments/server-rust/`)

> **状态:alpha · 实验性 · 不进生产流量**。Rust 端是 BPMN 2.0 执行器的平行 Rust 实现,
> 目的是:
> 1. 验证 Rust 在规则引擎 + BPMN 编排场景下的性能 / 内存 / 并发上限;
> 2. 给 Java 端落 Java 长期未跟的 BPMN 2.0 子集(Link / Conditional / 完整的 Compensation SAGA)
>    提供"先在 Rust 走通契约"的可参考实现;
> 3. 给生产流量做"逃生通道"——一旦 Java 执行器在某个 corner case 出问题,Rust 端可作为
>    备选,但需要单独的性能 + 正确性压测才能上生产。

### 当前覆盖(V5.25-V5.32)

- ✅ RETE 引擎 + 知识包加载 + Mock rule engine
- ✅ BPMN 2.0 节点:Start / End / ServiceTask / ScriptTask / UserTask / ExclusiveGateway / ParallelGateway
- ✅ IntermediateEvent(message / signal / timer / **conditional** / **linkThrow** / **linkCatch**)
- ✅ BoundaryEvent(error / timer) + multi-outgoing fan-out
- ✅ SubProcess(并行 join + outputMapping)
- ✅ Multi-Instance task wrapper(parallel + sequential)
- ✅ Error / Escalation / Terminate EndEvent
- ✅ Compensation SAGA(scope / throw / handler / per-scope LIFO)
- ✅ Postgres state store + atomic compound writes + advisory lock + recovery sweep
- ✅ HTTP:`/evaluate` · `/flow/decision` · `/flow/event` · `/flow/start-by-message` · `/flow/load` · `/health`

### Java 端还没 mirror、待回填的 Rust 首发语义

- `linkThrowEvent` / `linkCatchEvent`(`BpmnXmlParser.java` 当前不识别)
- `conditionalIntermediateCatchEvent` v0 外部 signal trigger 模式
- 多 handler / 跨 scope compensation
- Postgres advisory-lock 驱动的 single-key CAS
- 复合原子化写(`put_suspended` / `mark_terminal_with_vars`)

### 怎么跑(本地)

```bash
cd experiments/server-rust

# 1) 单元 + 集成测试(纯 cargo,无外部依赖)
cargo test --workspace

# 2) 启 HTTP front(默认 :8281,跟 Java executor 的 8280 平行)
PG_URL=postgres://ruleforge:ruleforge@localhost:5432/ruleforge_rust \
KNOWLEDGE_DIR=./fixtures/knowledge \
cargo run -p rf-http
```

> 升格 production 时只需 `git mv experiments/server-rust ./server-rust` 一条命令;详见
> [`experiments/README.md`](experiments/README.md) 的 "升格 production" 段落。

## 🚀 快速开始

### 方式一：Docker Compose（推荐）

```bash
git clone https://github.com/greatwhitesharklab/rule-forge.git
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
| ☕ 后端 | Java 17 · Spring Boot 4.0.6 · MyBatis-Plus · MySQL · ANTLR4 · RETE · 自建 BPMN 2.0 决策流引擎(V5.21+ 替代 Flowable) |
| 🦀 后端(实验) | Rust 1.x · Tokio · Axum · sqlx · `experiments/server-rust/`(alpha,**不**进生产流量) |
| 🎨 前端 | TypeScript · React · Vite 8 · Ant Design 5 · bpmn-js |
| 🧠 AI/ML | PKL Model Service · Python · Agent 分析 |
| ✅ 测试 | JUnit 5 · Mockito · AssertJ · Vitest · Playwright · cargo test |
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
| 📖 [在线文档](https://greatwhitesharklab.github.io/rule-forge/) | VitePress 文档站点 |
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
