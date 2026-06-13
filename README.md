<div align="center">

# RuleForge

**面向金融场景的智能决策引擎** — 确定性规则 + ML 模型推理,每个决策可审计、可解释、可追溯

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green.svg)](https://spring.io/projects/spring-boot)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6.svg)](https://www.typescriptlang.org/)
[![Rust](https://img.shields.io/badge/Rust-alpha-orange.svg)](experiments/server-rust/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

</div>

> [!NOTE]
> 金融级 Java 17 + Spring Boot 4 决策引擎,内嵌 **RETE 规则匹配** + **BPMN 2.0 决策流**双推理内核,  
> 支持**信贷审批 / 反欺诈 / 评分卡 / 决策流**四大场景,Docker Compose 一键起栈,Web Console + CLI + REST API 全渠道接入。  
> Rust 实验引擎(`experiments/server-rust/`)正在做热点路径性能验证,不进生产流量。

## 🎯 快速选择你的路径

| 我是… | 从这里开始 |
|---|---|
| **业务分析师 / 产品经理** | [快速开始](#-快速开始) 跑 demo → [规则类型](docs-site/guide/rule-types.md) 看 7 类规则 → [小微信贷审批教程](docs-site/tutorial/sme-loan-approval.md) 跟一遍 |
| **信贷业务方 / 风控决策方** | [小微信贷审批教程](docs-site/tutorial/sme-loan-approval.md) 端到端场景 → [反欺诈检测教程](docs-site/tutorial/anti-fraud-detection.md) → [架构概览 · 决策审计 / 灰度](docs-site/architecture/overview.md) |
| **运维 / DevOps** | [端口速查](#-端口速查) + [技术栈一览](#-技术栈一览) → [开发环境搭建](docs-site/development/setup.md) Docker Compose → [生产部署](docs-site/deployment/production.md) 加固 |
| **二次开发 / 架构师** | [架构概览](docs-site/architecture/overview.md) 双内核边界 → [Rust 引擎架构](experiments/server-rust/ARCHITECTURE.md) → [RETE 引擎详解](docs-site/architecture/rete-engine.md) 算法 → [AI 规则混合架构](docs-site/architecture/ai-rules-hybrid.md) |

## ✨ 核心特性

- **RETE + BPMN 2.0 双推理内核** — 规则匹配毫秒级,决策流覆盖完整 BPMN 2.0 子集(多池协作 / 异步消息 / 错误补偿 SAGA)
- **7 种规则类型** — DRL · DMN 1.3 · PMML 4.4 · 决策表 · 评分卡 · 决策树 · 决策流
- **金融场景开箱即用** — 信贷审批 / 反欺诈 / 评分卡 / 保险理赔,自带 XSD 校验、银企报文、决策审计
- **灰度发布 + 陪跑对比** — 流量切分、影子规则包、主/陪跑差异自动对比(4 维度 × 4 级严重度)
- **Web Console + CLI + DSL + REST API** — React + bpmn-js 设计器、Agent CLI 通道、外部系统 REST 集成
- **可观测性 / 决策日志** — Micrometer + Prometheus 指标,完整决策链路可追溯

## 🚀 快速开始

```bash
git clone https://github.com/greatwhitesharklab/rule-forge.git
cd rule-forge
docker compose up -d
curl localhost:8080/actuator/health    # → {"status":"UP"}
```

5 分钟跑起来。完整 Docker Compose 选项 + 端口 + 健康检查 + 示例数据加载
→ [开发环境搭建](docs-site/development/setup.md)。

## 📐 架构

Java 侧:Console App(8180) + Executor App(8280) + Model Service(8501) + MySQL

三库(app_db / ruleforge_db / flowable_db),Spring Boot 微服务。Rust 侧:

`experiments/server-rust/` 平行的 RETE 引擎 + BPMN 执行器,目前 alpha 阶段。

**完整架构图与执行流程** → [架构概览](docs-site/architecture/overview.md)。

## 📚 文档站导览

| 类别 | 入口 |
|---|---|
| 概念与上手 | [docs-site/guide/](docs-site/guide/) — 安装 / 快速开始 / 规则类型 / AI 规则编写 / 流程设计器 / 评分卡 / 测试 |
| 端到端教程 | [docs-site/tutorial/](docs-site/tutorial/) — [小微信贷审批](docs-site/tutorial/sme-loan-approval.md) · [反欺诈检测](docs-site/tutorial/anti-fraud-detection.md) |
| 用户手册 | [docs-site/development/code-structure.md](docs-site/development/code-structure.md) — 代码结构与模块边界 |
| 架构 | [docs-site/architecture/](docs-site/architecture/) — 概览 · [RETE 引擎](docs-site/architecture/rete-engine.md) · [决策流](docs-site/architecture/decision-flow.md) · [AI 规则混合](docs-site/architecture/ai-rules-hybrid.md) |
| 部署 / 运维 | [docs-site/deployment/](docs-site/deployment/) — [Docker Compose](docs-site/deployment/docker-compose.md) · [生产加固](docs-site/deployment/production.md) |
| API | [docs-site/api/](docs-site/api/) — [Console API](docs-site/api/console-api.md) · [Executor API](docs-site/api/executor-api.md) · [决策 API](docs-site/api/decision-api.md) · [Model Service API](docs-site/api/model-service-api.md) |
| 贡献 | [docs-site/development/contributing.md](docs-site/development/contributing.md) |

## 📌 项目状态

Java 引擎 5.0 系列稳定可生产;Rust 实验引擎 alpha 推进中;路线 B(IR 标准化

DMN / PMML / DRL)V5.40-V5.42 已完成,后续聚焦"删老 .xml / .ul 路径 + Rust性能基线收敛"。

**最近里程碑:V5.46.2**(2026-06) — README 重写为读者路径引导。

完整版本演进→ [CHANGELOG.md](CHANGELOG.md)。

## 🗺️ 路线图

Phase 1-12(数据源 / 灰度 / 陪跑 / 监控)已完成;Rust 引擎 GA → 规则市场

→ 多租户 是下一阶段方向。详细路线图 → [docs/roadmap.md](docs/roadmap.md)。

---

## 📦 展开更多

<details>
<summary>🦀 <b>Rust 实验引擎</b></summary>

- 仓库:[experiments/server-rust/](experiments/server-rust/) · 架构图:[ARCHITECTURE.md](experiments/server-rust/ARCHITECTURE.md)
- 状态:**alpha · 实验性 · 不进生产流量** — 用于验证 Rust 在 RETE / BPMN 场景下的性能 / 内存 / 并发上限
- 跟 Java 引擎平行实现,共用 DRL / PMML / DMN IR;升格 production 只需 `git mv experiments/server-rust ./server-rust` 一条命令
- 性能基线:Java 0.16ms / 2000 fact · Rust 2.12ms / 1000 fact(Rust 慢 17-26x,见 [完整 perf 报告](server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/README.md))
</details>

<details>
<summary>🛠️ <b>技术栈一览</b></summary>

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 4.0.6 · MyBatis-Plus · MySQL · ANTLR4 · RETE · 自建 BPMN 2.0 决策流引擎 |
| 后端(实验) | Rust 1.x · Tokio · Axum · sqlx |
| 前端 | TypeScript · React · Vite 8 · Ant Design 5 · bpmn-js |
| AI / ML | PKL Model Service(Python FastAPI)· Agent CLI |
| 测试 | JUnit 5 · Vitest · Playwright · cargo bench |
| 部署 | Docker · Docker Compose |

详细架构图与依赖链 → [架构概览](docs-site/architecture/overview.md)
</details>

<details>
<summary>📑 <b>规则类型一览</b></summary>

| 类型 | IR 标准 | 说明 |
|---|---|---|
| 向导式规则集 | RuleForge Native | 可视化条件-动作 |
| 脚本式规则集(UL/DSL) | DRL 4(自研 ANTLR4) | DSL 脚本 |
| 决策表 | DMN 1.3(Kie DMN) | 表格化条件匹配,7 种 hit policy |
| 评分卡 | PMML 4.4(pmml4s) | 加权评分,A/B 卡对比 |
| 决策树 | PMML 4.4(pmml4s) | 树形结构决策 |
| 决策流 | BPMN 2.0 | 流程编排多规则 |
| AI 规则 | V5.22 自研 agent 通道 | 自然语言创建规则 |

完整字段 / 编辑器说明 → [规则类型](docs-site/guide/rule-types.md)
</details>

<details>
<summary>🔌 <b>端口速查</b></summary>

| 服务 | 端口 | 健康检查 |
|---|---|---|
| Console App(编辑器) | 8180 | `/actuator/health` |
| Executor App(执行器) | 8280 | `/actuator/health` |
| Model Service(ML 推理) | 8501 | `/health` |
| MySQL | 3306 | `mysqladmin ping` |
| Console UI(docker nginx) | 80 | `wget /` |

完整启动顺序 + 端口冲突排查 → [开发环境搭建](docs-site/development/setup.md)
</details>

<details>
<summary>📐 <b>规则 IR 演进 (DRL / PMML / DMN)</b></summary>

V5.40-V5.42 路线 B 完成"规则 IR 标准化"三刀:
- **V5.40 决策表 → DMN 1.3**(Kie DMN 10.1.0) — 工业标准
- **V5.41 评分卡 + 决策树 → PMML 4.4**(pmml4s 1.5.6,BSD-2-Clause,非 jpmml AGPL-3.0)
- **V5.42 规则 + DSL → DRL 4**(自研 ANTLR4 grammar,Apache 2.0 clean,无 Drools runtime)

老 .xml / .ul 路径保留并行,V5.43+ 一次性删除。详细架构叙事 →
[AI 规则混合架构](docs-site/architecture/ai-rules-hybrid.md)
</details>

## 🤝 贡献

[贡献指南](docs-site/development/contributing.md) · [代码结构与模块边界](docs-site/development/code-structure.md) · [Apache-2.0](LICENSE)
