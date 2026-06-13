# 项目路线图

## 现状

RuleForge 当前已具备的能力：

- **RETE 规则引擎** — 基于 RETE 算法的高性能规则匹配与执行(Java 0.16ms / 2000 fact)
- **7 种规则类型** — DRL · DMN 1.3 · PMML 4.4 · 决策表 · 评分卡 · 决策树 · 决策流
- **可视化设计器** — React + bpmn-js 的 Web 规则编辑器
- **决策流编排** — 自建 BPMN 2.0 流程引擎(V5.21-V5.39,V5.20 之前是 Flowable 8,已全替)
- **陪跑测试** — A/B 对比执行,4 维度 × 4 级严重度自动对比
- **决策日志** — 完整记录输入、输出、执行明细、各阶段耗时
- **Rust 实验引擎** — `experiments/server-rust/`,alpha,验证 RETE 性能(V5.46:2.12ms / 1000 fact,Rust 17-26x 慢于 Java 端)
- **路线 B 收口** — 决策表走 DMN 1.3(Kie DMN 10.1.0),评分卡 + 决策树走 PMML 4.4(pmml4s 1.5.6 BSD-2-Clause),规则 + DSL 走 DRL 4 自研 ANTLR4(Apache 2.0 clean,无 Drools runtime)

## 版本管理

**版本号不预先钉死**。规则:`x.y.z` 在 Phase 实际完成、发版时由 CHANGELOG 决定(看实际改动密度 + 是否含 breaking change)。roadmap 只列 Phase,版本号在 release 时回填。

参考已发版的版本:

| 版本 | Phase | 主要内容 |
|------|-------|---------|
| 5.0.0 | 1-4 | 监控告警、数据源、版本发布、Agent 分析(打包发版) |
| 5.1.0 | 5 | 规则仿真 |
| 5.2.0 | 6 | 前端 UI 现代化 |
| 5.3.0 | 7 | AgentScope 集成 |
| 5.6.0 | 10 | 文档与 Demo |
| 5.7.0 | 11 | PMML/PKL 模型 |

Phase 8 / 9 / 12 完成后,版本号按改动量决定是 patch bump 还是 minor bump。

> Flyway 迁移脚本按实际完成的 Phase 编号,而不是 roadmap 上钉死的版本。例如 Phase 9 真做的时候如果项目当前是 5.7.0,就新建 `V5.8.0__xxx.sql` 或 `V5.7.1__xxx.sql`,看改动密度。

## 实施顺序

```
Phase 1-12 ✅ 已完成 → V5.25-V5.47 路线 B 收口 + 删老路径 ✅ 已完成 → V5.48+ 候选方向
```

```
Phase 9   数据源批量测试                 P1  ── 收尾中(后端 60%,剩 controller)
V5.40-V5.47 路线 B + 删老路径            P0  ── 已完成(冻结历史)
V5.48+     DRL grammar 扩展 + 编辑器     P0  ── 候选
V5.48+     Rust alpha index + Drools 7.31 实测  P2  ── 候选
```

## 路线图总览

| 方向 | 目标 | 优先级 | 状态 |
|------|------|:------:|:----:|
| 监控与告警 | 决策执行全链路可观测 | P1 | ✅ 已完成 |
| 上游数据源管理 | 统一管理外部数据接入 | P0 | ✅ 已完成 |
| 规则版本与发布管理 | 变更审批、灰度发布、回滚 | P0 | ✅ 已完成 |
| 下游 Agent 分析 | AI 分析决策结果，优化规则 | P2 | ✅ 已完成 |
| 规则仿真 | 批量回放历史流量，预知变更影响 | P0 | ✅ 已完成 |
| 前端 UI 现代化 | TypeScript + Vite + Ant Design 5 | P0 | ✅ 已完成 |
| AgentScope 集成 | Web 内置 AI 对话分析（LLM 工具调用） | P1 | ✅ 已完成 |
| 文档与 Demo | GitHub Pages + VitePress | P2 | ✅ 已完成 |
| PMML/PKL 模型 | Python 模型导入执行 | P2 | ✅ 已完成 |
| ClickHouse 分析 | 高性能分析数据库 | P1 | ✅ 已完成 |
| Rust 执行引擎 | RETE 端到端复刻 + BPMN 完整化 (V5.25-V5.27) | P3 | ✅ 已完成(alpha) |
| 数据源批量测试 | CSV/JSON 批量导入测试 | P1 | 📋 60% 完成(等接 controller) |
| **路线 B 收口** | **DMN 1.3 + PMML 4.4 + DRL 4 自研 ANTLR4 (V5.40-V5.42) + 删老 .xml/.ul (V5.43-V5.47)** | P0 | ✅ 已完成 |
| **RETE perf baseline** | **Java 0.16ms / 2000 fact · Rust 2.12ms / 1000 fact (V5.46)** | P0 | ✅ 已完成 |

---

## ✅ 已完成里程碑(冻结历史,V5.28-V5.47)

> **纪律**:以下里程碑都已发版并入主,代码与变更记录锁定。读者只看"将来
> 做什么"时,这块可整段跳过;追溯历史 / 理解架构决策时再回看。
>
> 详细 commit / 决策 / 数据见 [CHANGELOG.md](../CHANGELOG.md) 对应段。

### V5.28-V5.36:Java 端 BPMN 2.0 完整化

- 8 个 PR 增量落地:ParallelGateway JOIN / Multi-Instance (Sequential + Parallel)/
  Compensation SAGA / IntermediateEvent (Message / Signal / Timer) / Error /
  Escalation 触发与捕获
- FlowEngine 自研 — 全替 Flowable 8 starter(原 5.20 之前用 Flowable 8)
- 22 字段 god-object `FlowContext` 拆 4 角色:`FlowIdentity` + `BusinessVars` +
  `ReteSession` + `SuspendRegistry`(V5.39)
- `MessageBus` SPI 多实现:`InMemoryMessageBus` + Provider/Registry(V5.38)
- Multi-pool + Choreography 协作(V5.37)

### V5.40-V5.42:路线 B 三刀(IR 标准化)

| 版本 | 主题 | 标准 | 库 |
|---|---|---|---|
| **V5.40** | 决策表 → DMN 1.3 | OMG DMN 1.3 | Kie DMN 10.1.0(Apache-2.0) |
| **V5.41** | 评分卡 + 决策树 → PMML 4.4 | OMG PMML 4.4 | pmml4s 1.5.6(BSD-2-Clause,非 jpmml AGPL-3.0) |
| **V5.42** | 规则 + DSL → DRL 4 自研 | Drools DRL 子集(95% 业务覆盖) | 自研 ANTLR4 grammar(Apache-2.0 clean) |

- 决策叙事:规则引擎不查数据,只对 facts 跑规则 — LazyGeneralEntity 反模式已废弃
- 工程叙事:Apache 2.0 clean,无 Drools / Flowable runtime,依赖体积 + 风险双降
- grammar 缺失:`accumulate reverse` / `import` / `function` / `declare` 不在 V5.42 grammar 范围(V5.48+ 候选)

### V5.43-V5.44:删老解析路径 + 路线 B 后清理

- V5.43 — 删老 .xml rule 链(2 library deserializer 等)+ 删老 .ul DSL 链(`DSLRuleSet` interface
  + `KnowledgeBuilder.dslRuleSetBuilder` 字段)— 走 0 rule fallback
- V5.43.2 — 删 `SpringBeanParser` / `NamedJunctionParser` / `RuleSetResourceBuilder` /
  `RuleSetDeserializer` 4 class,防 refactor 救回
- V5.44 — 4 library deserializer(Variable/Action/Constant/Parameter)改 DRL 顶层 `import` 段;
  整 `com.ruleforge.dsl.*` 搬到独立 jar `lib/ruleforge-dsl`
- V5.45 — DSL chain runtime 真删:`KnowledgeBuilder` 删 `dslRuleSetBuilder` 字段
- **V5.47** — 删 `com.ruleforge.parse.RuleSetParser`(老 .xml 资源级根解析,71 行)+ 删
  整个 `lib/ruleforge-dsl` module(17 main + 1 test + pom)— 2 app 入口不 import
  `com.ruleforge.dsl.*`,classloader 跑 `Class.forName` 全部 CNFE

### V5.46 / V5.46.1 / V5.46.2:RETE 性能基线

- V5.46 — Java + Rust 横向 perf bench:`EvalBenchmark` 锁基线
  - Java RETE:2000 fact + 3 rule = **0.16ms p50 / 0.27ms p50 with eval**(per-fact 0.08-0.13 μs)
  - Rust rf-rule:1000 fact + 3 rule = **2.34ms baseline**
- V5.46.1 — 修 Rust `EvaluationContext` 跨 fact cache bug,1-shot BDD 验证 + bench regression
  lock,Rust 真值 **2.12ms / 1000 fact**
- V5.46.2 — 根 README 表格化 + perf 报告落地(`server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/README.md`)
- **结论**:Java 0.1ms 量级生产无忧;Rust 17-26x 慢(Arc<dyn Activity> 动态分发 / 缺 alpha
  index / per-fact HashMap clean 重建),**Rust 升格 production 0 收益,继续留 alpha**

### V5.47:文档整改

- 根 README 重写为读者 path 引导 + 折叠细节(154 行,333 → 154)
- 4 个 `<details>` 折叠块:Rust 实验引擎 / 技术栈一览 / 规则类型一览 / 端口速查 / 规则 IR 演进
- "🎯 快速选择你的路径" 4 行表:BA / 信贷方 / 运维 / 二次开发 各自起点
- CHANGELOG 补 V5.40-V5.47 8 段(此前漏更)+ 修 V5.40/V5.41/V5.42 错位(在 [5.0.0] 段下面)
- Maven `<revision>` 5.0.0 → 5.47.0,跟项目 V 号对齐
- 删 `lib/ruleforge-dsl` 整个 module + 老 .xml `RuleSetParser` 解析路径

---

## Phase 1: 监控与告警 ✅ 已完成 (5.0.0)

### 已实现

- **执行耗时 Metrics** — Micrometer + Prometheus，P50/P95/P99 延迟、各阶段耗时分解
- **成功率监控** — 按规则包、决策流统计成功/失败率
- **异常告警** — 失败率超阈值、执行超时主动告警
- **决策趋势看板** — 决策结果分布、通过率趋势

---

## Phase 2: 上游数据源管理 ✅ 已完成 (5.0.0)

### 已实现

- **数据源注册中心** — REST API / JDBC / Advance AI / PKL 模型四种连接器，JSON 配置
- **变量映射配置** — 实体级映射 + 字段级映射（规则变量名 → 外部字段名）
- **连接管理** — HikariCP 连接池、超时、缓存策略
- **数据缓存** — 120h TTL，数据库缓存 + 审计日志
- **数据源测试** — 配置阶段测试连通性
- **路由集成** — DatasourceRoutingProvider 替代硬编码，零侵入核心引擎
- **前端界面** — DatasourcePanel（数据源 CRUD + 映射配置 + 字段映射查看）
- **监控与日志** — nd_datasource_log 全链路记录

### 模块结构

```
数据源管理模块
├── DatasourceRegistry        数据源注册与元数据（nd_datasource）
├── DataSourceConnectors      各类型连接器（REST/JDBC/AdvanceAI）
├── VariableMapping           数据字段 → 规则变量映射（entity_mapping + field_mapping）
├── DatasourceCache           数据缓存层（DB 缓存 + TTL）
└── DatasourceRoutingProvider  零侵入路由集成
```

---

## Phase 3: 规则版本与发布管理 ✅ 已完成 (5.0.0)

### 已实现

- **变更审批工作流** — auto/manual 两种模式，gr_approval_task 表
- **环境隔离** — dev / staging / prod
- **灰度发布** — WHITELIST / PERCENT_USER / PERCENT_RANDOM 三种策略
- **结构化 Diff API** — side-by-side 可视化 diff
- **一键回滚** — 部署历史回滚按钮
- **陪跑流量重放** — ShadowExecutionService + ShadowComparisonService（4 维度 × 4 级严重度）
- **Flyway** — V3.1.0 ~ V3.14.0

### 模块结构

```
版本与发布模块
├── ApprovalTaskEntity     审批任务实体
├── ApprovalController     审批 REST 端点
├── DeploymentController   部署管理 REST 端点
├── GrayStrategyService    灰度策略服务
├── ShadowExecutionService 陪跑异步执行服务
├── ShadowComparisonService 陪跑结果自动对比
├── ReleasePanel           前端版本发布面板
└── DiffViewer             前端可视化 diff 组件
```

---

## Phase 4: 下游 Agent 分析 ✅ 已完成 (5.0.0)

### 设计理念

RuleForge 不内置 LLM，提供分析 API + CLI + Skills，让外部 Agent（Claude Code、Cursor 等）调用。CLI 是最通用的 Agent 接口。

### 已实现

- **DecisionAnalysisMapper** — 聚合 SQL：时间序列、包汇总、拒绝码分布、偏差基线
- **RuleCoverageMapper** — 规则触发频率排名、全量曾触发规则名
- **AnalysisServiceImpl** — ECharts 格式转换、热/冷/死规则分类、sigma 阈值偏差检测
- **AnalysisController** — REST API 7 个端点（/analysis/*）
- **ExportController** — 规则内容导出 4 个端点（/export/*）
- **前端分析仪表盘** — 三 Tab（决策趋势、规则覆盖、偏差检测）+ ECharts
- **ruleforge CLI** — Node.js 命令行工具（analysis + export 命令组）
- **Claude Code Skills** — 6 个 skill

### 模块结构

```
Agent 分析模块
├── DecisionAnalysisMapper   聚合 SQL
├── RuleCoverageMapper       规则触发频率、覆盖率
├── AnalysisServiceImpl      分析逻辑
├── AnalysisController       REST API（/analysis/*）
├── ExportController         规则内容导出（/export/*）
├── Analysis Dashboard       前端分析仪表盘
├── ruleforge CLI            命令行工具
└── .claude/skills/          Claude Code Skills（6 个）
```

---

## Phase 5: 规则仿真（主动模拟） ✅ 已完成 (5.1.0)

### 已实现

- **SimulationServiceImpl** — 异步批量仿真（LOADING → RUNNING → COMPARING → COMPLETED）
- **4 维度对比** — 状态匹配、结果匹配、输出字段、规则执行（ComparisonUtils 共享工具）
- **SimulationController** — 5 个 REST 端点（启动/进度/结果/历史/统计）
- **SimulationPanel** — 前端仿真面板（配置表单 + 进度条 + 结果表 + 统计）
- **CLI** — `ruleforge simulation run/list/results/stats` 命令

---

## Phase 6: 前端 UI 现代化 ✅ 已完成 (5.2.0)

### 问题

Bootstrap 3.4.1 已过时，UI 影响产品形象和用户体验。Webpack 5 构建慢（~25s）。

### 已实现

- **Webpack → Vite 8** — 构建时间 ~25s → ~1s
- **JavaScript → TypeScript** — 全量迁移（strict: false），~120 源文件已转换
- **Ant Design 5** — 安装 + 主题配置（#1677ff 匹配设计令牌）+ AntdProvider
- **集中式 HTTP 客户端** — `src/api/client.ts`，统一 ~150 个 fetch/ajaxSave/XHR 调用
- **已迁移模块**: login, reference, datasource, client, constant, parameter, variable,
  action, permission, resource, release, simulation, monitoring, analysis, package,
  components/ (Grid, Tree, Dialog, Splitter, Menu, Widgets), frame/ (root shell),
  editor/*, scorecard/, flow-bpmn/
- **Playwright E2E 测试** — Phase 5-7 覆盖

---

## Phase 7: 内置 Agent（AI 助手） ✅ 已完成 (5.3.0)

### 已实现

- **LlmClient** — OpenAI 兼容 Chat Completions API 客户端，支持 SSE 流式 + tool_calls 增量解析
- **AgentConfigService** — DB 存储配置（nd_agent_config），30s 缓存，运行时修改无需重启
- **VendorPresets** — 10 个预配置 LLM 厂商（OpenAI, DeepSeek, 通义千问, 智谱, Moonshot, 百川, MiniMax, SiliconFlow, Ollama, 自定义）
- **ToolRegistry** — 11 个注册工具（分析趋势、规则覆盖率、异常检测、规则导出、监控指标等）
- **ToolExecutor** — 工具调用直接映射到 IAnalysisService + RuleForgeRepositoryServiceImpl（零网络开销）
- **AgentService** — Agentic 循环（LLM → tool_calls → 执行工具 → 再调 LLM → 直到文本响应），最多 10 轮
- **AgentController** — REST + SSE 端点（/agent/chat, sessions, config, vendors, status）
- **Flyway V5.3.0** — nd_agent_config, nd_agent_chat_session, nd_agent_chat_message 三张表
- **前端 AgentPanel** — 聊天界面（消息流 + 流式显示 + 工具状态）+ 配置面板（厂商选择 + API Key + 连接测试）

---

## Phase 8: 高性能分析数据库（ClickHouse） ✅ 已完成 (5.4.0)

### 问题

MySQL 聚合查询在大数据量下性能不足，决策日志每天可能几十万条。

### 方案

引入 **ClickHouse** 作为分析存储，MySQL 保持事务存储。

### 架构（双写 + 查询路由）

```
决策日志写入
    ├── MySQL（事务，实时查询）
    └── ClickHouse（异步批量写入，分析查询）

分析 API 查询路由
    ├── 小数据量 → MySQL（现有 Mapper）
    └── 大数据量/历史分析 → ClickHouse（新增 Mapper）
```

### 新增模块

```
分析存储模块
├── AnalyticsDataSourceConfig       ClickHouse 数据源配置
├── DecisionLogAnalyticsMapper      ClickHouse 聚合查询
├── DecisionLogClickHouseWriter     异步批量写入
└── Flyway V5.4.0__clickhouse_sync.sql
```

---

## Phase 9: 数据源批量测试 📋 规划中 (5.5.0)

### 问题

数据源配置后只能单条测试，无法验证批量调用性能和正确性。

### 方案

扩展数据源测试功能，支持 CSV/JSON 批量导入 → 批量调用 → 结果对比。

### 新增

- `DatasourceBatchTestController` — 批量测试 REST API
- `DatasourceBatchTestServiceImpl` — 读取测试数据 → 批量调用 → 记录结果
- 前端：`DatasourceBatchTestPanel` — 上传 CSV → 配置映射 → 运行 → 查看结果
- CLI：`ruleforge datasource batch-test --source myapi --input test_data.csv`

---

## Phase 10: 文档与 Demo 网站 ✅ 已完成 (5.6.0)

### 已实现

- **VitePress 文档站** — docs-site/，GitHub Pages 部署，zh-CN，本地搜索
- **Landing Page** — 6 个 Feature 卡片（AI+规则、7种规则、可视化、金融场景、RETE、AI分析）
- **使用指南** — 快速开始、安装部署、规则类型、评分卡、决策流、测试（7 页）
- **场景教程** — 小微信贷审批 + 反欺诈交易检测（各 7 步）
- **API 文档** — Console/Executor/Model Service/Decision API（4 页）
- **架构文档** — 概览、RETE 引擎、决策流、AI+规则混合（4 页）
- **开发文档** — 环境搭建、代码结构、贡献指南（3 页）
- **部署文档** — Docker Compose + 生产环境（2 页）
- **金融 Demo** — 小微信贷审批（评分卡+规则集+决策表+决策流）+ 反欺诈交易检测（ML模型+规则混合）
- **全栈 Docker Compose** — 一行命令启动 5 个服务
- **GitHub Actions** — 推送到 main 自动部署文档站

---

## Phase 11: PKL 模型支持 ✅ 已完成 (5.7.0)

### 问题

风控模型用 Python 训练，导出 PKL 文件，规则引擎无法直接使用模型预测结果。

### 已实现

- **Python model-service** (FastAPI + uv) — PKL 模型上传、字段自动检测、激活/停用、预测
- **PklModelConnector** — Java DataSourceConnector 实现，通过 REST 调用 model-service，提取预测字段值
- **前端 PKL 配置** — DatasourcePanel 新增 PKL 类型，模型服务地址 + 模型 ID + 模型列表加载
- **字段映射自动填充** — "获取模型字段"按钮从 model-service 拉取输入/输出字段，自动创建映射
- **模型生命周期** — 上传 → 自动检测字段 → 激活 → 预测 → 停用 → 删除

### 架构

```
规则执行 → DatasourceRoutingProvider → PklModelConnector
                                          ↓
                                     Python model-service (FastAPI)
                                          ↓
                                     pickle.load() → model.predict()
                                          ↓
                                     返回预测结果 JSON
```

### 模块结构

```
PKL 模型模块
├── model-service/                Python 微服务（FastAPI + uv）
│   ├── app/models/registry.py    模型注册表（内存 + 文件系统持久化）
│   ├── app/models/loader.py      PKL 加载 + sklearn 字段自动检测
│   ├── app/routes/predict.py     POST /predict
│   ├── app/routes/manage.py      CRUD /models + activate/deactivate
│   └── app/routes/health.py      GET /health
├── PklModelConnector.java        Java DataSourceConnector 实现
├── DatasourcePanel (PKL)         前端配置表单 + 模型字段自动填充
└── saveFieldMappings action       批量字段映射保存
```

---

## Phase 12: Rust 高性能执行引擎 ✅ 已完成 (V5.25-V5.27)

### 方案演进

最初 roadmap 上写"通过 JNI 调用" — 后来改为 **`experiments/server-rust/crates/`**
下从零复刻,跟 Java `ruleforge-core` 平行的 Rust 端,作为 side-by-side
实现(不是 JNI,而是独立的 `rf-http` HTTP service,通过 console-app
proxy 跟 Java 端共享 BPMN 定义和 knowledge package JSON)。

V5.25 P0-P6 完成 RETE 引擎端到端复刻(ObjectType / Criteria / And /
Or / Terminal + 20 op assertor + Agenda salience 排序 + activation_
group / agenda_group + 5 rule type adapter),114 个测试 pass。

V5.26 加 HTTP 入口(IntermediateEvent message/signal/timer catch +
`/flow/event` 投递 + pg 持久化 suspend/resume)。

V5.27 production 化收口:`ReteRuleEngine` 切生产 — 1 flag 替代
MockRuleEngine;BoundaryEvent / SubProcess executor 补齐 BPMN 2.0
剩余节点;docker-compose 接 `rust-flow` 服务,挂 `console_data` volume
共享 Java 端导出的知识包。

### 范围(故意不做)

- ❌ JNI 桥接 — 走 HTTP service 路径,跟 Java 解耦
- ❌ Spring bean lookup — `MethodLeftPart` 改 trait-based 函数指针
- ❌ Spring EL 表达式 — 用 `simpleeval` crate 子集替代
- ❌ 跟 Java 100% API 兼容 — Rust 端只跑规则 + 流程,BPMN 编辑器仍
  在 Java 端

### 评估(回顾)

跟当初 roadmap 上的"建议暂缓,先做性能压测"判断对一下:
- ✅ 用 Rust 重写确实能避开 Java GC 对延迟的影响
- ✅ HTTP service 路径比 JNI 桥接更简单,部署更灵活(独立容器)
- ⚠️ 还没做 head-to-head 压测 — V5.28 候选之一是 Rust 路径的
  e2e 性能 benchmark,跟 Java 端对比 P50/P95/P99

### 下一步(V5.28 候选)

- Knowledge hot reload(file watcher 接 `load_dir`)
- SubProcess outputMapping(目前全量回拷 sub-flow vars)
- BoundaryEvent 真正的 attachedToRef 路由(目前 boundary 是 sibling
  node 走 dispatcher)
- Java ↔ Rust 行为 parity 验证(跑 Java 导出的真实 knowledge package
  对比输出)
- ComplexGateway + ParallelGateway fork/join
- Rust 路径 Playwright e2e 测试(目前 Playwright 只覆盖 Java console)

---

## 🛣️ 当前 Phase 方向(V5.48+ 候选)

> **纪律**:roadmap 只列 Phase 方向,版本号在 release 时回填(参考上文
> "版本管理"段)。以下 Phase 编号是 `x.y` 占位,具体发版号跟 PR 走。

### Phase 13:DRL 4 grammar 扩展 — P0

- 背景:V5.42 grammar 覆盖 95% 业务,缺 `accumulate reverse` / `import` / `function` /
  `declare` 4 段
- 工作量:ANTLR4 grammar 扩 + AST visitor 扩 + DrlDeserializer 扩,4-6 周
- 风险:实测业务样本,确认这 4 段是 "想要" 还是 "真必要"(部分语法可以走替代品)
- 决策:开始前先盘点业务代码,看哪段真用过

### Phase 14:完整 DRL 编辑器(console-ui)重写 — P0

- 背景:V5.42 起 console-ui 只 source format badge(语法高亮 + 大纲),无 IDE 级
  autocomplete / 类型检查 / 重命名重构
- 工作量:VS Code 同款 LSP client 集成到 monaco-editor,8-12 周
- 收益:DRL 编写体验追平 IDE,降低 BA 学习成本
- 风险:VS Code LSP server 实现成本高,可能做 "half-LSP" — 内部用 bpmn-js
  风格定制

### Phase 15:Rust alpha index 优化 — P2

- 背景:V5.46.1 测得 Rust 2.12ms / 1000 fact,比 Java 0.16ms / 2000 fact 慢 17-26x
- 工作量:加 alpha index(节点级哈希索引),从 2.12ms → 0.3ms 量级,2-3 周
- 风险:**Java 端 0.16ms 已经 0.1ms 量级生产无忧**,即使 Rust 优化到 0.3ms 仍慢
  ~2x。**升格 production 决策:不做**(V5.46 已定调)
- 候选:alpha 继续做,Rust 端跑长尾测试场景(fact 10w+)验证上限

### Phase 16:Drools 7.31 真 baseline 实测 — P2

- 背景:V5.46 perf 报告里 Drools 7.31 数字 0.5-2ms 是社区估的,没实测
- 工作量:搭 Drools 7.31 容器,跑同样 EvalBenchmark,1-2 周
- 收益:Java 端 0.16ms 跟 Drools 7.31 真值的差距清晰化(可能要宣传 "5x 快" 或
  "持平")
- 风险:Drools 7.31 license 是 Apache-2.0,临时引入跑测试 OK,长期不并入

### Phase 17:删老路径后 perf 回归(DRL-only) — P1

- 背景:V5.43-V5.47 删老 .xml / .ul 路径,DRL-only 走生产路径,需回归测试
  确认 perf 跟功能不退化
- 工作量:扩展 `EvalBenchmark` 加 DRL-only 场景,1 周
- 收益:锁 V5.47 后的 perf baseline(0.16ms / 2000 fact),为 Phase 15 / 16 提供
  起点

### Phase 18:数据源批量测试(controller 接入) — P1

- 继承自 Phase 9,后端 60% 完成,等接 `DatasourceBatchTestController` + 前端
  `DatasourceBatchTestPanel`
- 工作量:2-3 周
- 风险:此 PR 早开工早收口(已挂在 P1 半年)

### 路线图总览(V5.48+ 候选)

| 方向 | 候选 Phase | 优先级 | 状态 |
|---|---|:---:|:---:|
| DRL grammar 扩展 | Phase 13 | P0 | 📋 候选(先盘点业务) |
| 完整 DRL 编辑器 | Phase 14 | P0 | 📋 候选(8-12 周) |
| Rust alpha index 优化 | Phase 15 | P2 | 📋 候选(2-3 周,继续 alpha) |
| Drools 7.31 真 baseline | Phase 16 | P2 | 📋 候选(1-2 周) |
| DRL-only perf 回归 | Phase 17 | P1 | 📋 候选(1 周) |
| 数据源批量测试收口 | Phase 18 | P1 | 📋 收口(2-3 周) |
| Rust 升格 production | — | — | ❌ **不做**(V5.46 已定调 0 收益) |
| Java alpha index 优化 | — | — | ❌ **不做**(0.1ms 节省不抵 1 周改 + 回归) |
