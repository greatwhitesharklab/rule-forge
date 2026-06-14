# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Maven-based Java 17 project, Spring Boot 4.0.6. Run from `server/`:

```bash
mvn compile                       # Compile all modules
mvn compile -pl ruleforge-core    # Compile single module with deps (-am)
mvn clean package -DskipTests     # Package without tests
```

Frontend is in `console-ui/`, check its package.json for npm commands.

Docs site is in `docs-site/` (VitePress):

```bash
cd docs-site
npm install          # Install VitePress
npm run dev          # Dev server (localhost:5173)
npm run build        # Production build
```

Docker Compose full stack:

```bash
docker compose up -d              # Start all 5 services
docker compose logs -f console-app # View logs
```

## Project Architecture

```
parent                  Maven parent POM, Spring Boot BOM, Java 17
ruleforge-core          RETE rule engine (parsing, execution, knowledge base)
ruleforge-console       Web console business logic (controllers, services, DB)
ruleforge-executor      Rule execution engine (test endpoints, knowledge package)
ruleforge-console-app   Deployable Spring Boot app — editor (port 8081)
ruleforge-executor-app  Deployable Spring Boot app — executor (port 8082)
frontend                React-based visual rule designer
```

Dependency chain:
```
core ← console ← console-app
core ← executor ← executor-app
```

## Module Details

### ruleforge-core (`com.ruleforge.*`)
Pure engine, no Spring Boot dependency:
- `model/` — rule models (rule, table, tree, scorecard, library)
- `model.rete/` — RETE algorithm implementation
- `runtime/` — knowledge session, execution, caching
- `parse/` — XML/DSL rule parsers (ANTLR4)
- `controller/` — KnowledgePackageReceiverServlet

### ruleforge-console (`com.ruleforge.console.*`)
Web editor backend:
- `controller/` — REST controllers (frame, common, package)
- `flow/` — Flowable 8 integration (delegates, controller, converter)
- `service/` — repository, permission, test services
- `storage/` — project storage (DB-backed)
- `mapper/` — MyBatis-Plus mappers
- `repository/` — model classes (RepositoryFile, VersionFile, ResourcePackage, etc.)
- `servlet/` — utilities (RequestContext, ErrorInfo, ScriptType)
- `config/` — MybatisPlusConfig, FlywayConfig

### ruleforge-executor (`com.ruleforge.executor.*`)
Rule execution:
- `controller/TestController` — `/test/do`, `/test/knowledge`
- `service/` — RuleForgeService, KnowledgePackageServiceImpl
- `service/impl/ExecResourceProvider` — fetches resources from console via HTTP

### ruleforge-console-app (`com.ruleforge.console.app.*`)
Deployable editor with datasource config, environment provider, and business-specific code (loan decision, shadow execution, decision logging).

### ruleforge-executor-app (`com.ruleforge.executor.app.*`)
Deployable executor with RestTemplate config for console communication.

## Key Technologies

- Java 17, Spring Boot 4.0.6, Spring Framework 7
- MyBatis-Plus 3.5.9, MySQL, Flyway
- ANTLR4, Jackson, fastjson2, HikariCP
- V5.21+ 自建 BPMN 2.0 决策流引擎(`lib/ruleforge-decision/`,V5.33-V5.39 完整化);V5.20 之前是 Flowable 8
- Frontend: TypeScript, React, Vite 8, Ant Design 5, bpmn-js for flow designer
- Frontend HTTP: centralized `src/api/client.ts` (formPost, jsonPost, jsonPut, httpGet, httpDelete, save, saveNewVersion)
- Frontend tests: Vitest unit tests, Playwright E2E tests

## Development Principles

### 最高优先级:agent 连续性 & 长时间自主

本项目的工程规则围绕一个最高优先级:**让 agent 能连续、长时间自主运行,不被不必要的中断打断**。下面所有规则(BDD/TDD、Sub-task 工作流、分支策略、模块边界)都服务于它;**任何规则与之冲突时,以连续性为准**。

- **默认不中断**:写测试、写实现、推进 sub-task、写文档都不 ask user,直接做
- **一个特性一个分支连续做完再一次 PR**,不中途拆(拆 = 合并→切 main→开分支,中断推进)
- **不定义中断情形**:agent 不预留"中断问用户"的口子 —— 遇到破坏性风险就谨慎操作(不删数据/不覆盖未读文件/commit 保进度),缺关键信息就用最合理的默认推进并标注假设。只有用户主动发话才停
- **方案选型前置**:涉及多种合理路径的选型(交互模型、技术栈、架构方案)在**需求开始时**就和用户确认,不作为中途中断的理由。读到代码发现新复杂度时,先用现有信息继续推进,不回头问选型
- **长时间运行保进度**:每个 sub-task 完成立即 commit(即使 context 被压缩,也能从 commit 继续,不丢进度)。auto-compact 由 TUI 自动处理,无需 agent 介入

### BDD/TDD

- Follow Behavior Driven Development practices for test writing:
  1. Write test files with Gherkin-style behavior annotations (Given/When/Then) in @DisplayName/@Nested
  2. Write test code directly after annotations — no need to confirm with user before writing test code
- Follow Test Driven Development: never write business/implementation code before writing tests

### Sub-task 工作流(plan mode 9 个 sub-task 或类似)

- **BDD/TDD 硬规则不破**:每个 sub-task 仍按"先写 BDD → 写实现 → 跑全量回归 → mark complete"循环
- **不打断主流程**:sub-task 完成 → 内部 mark complete → 直接做下一个,**不**等用户确认
- **大分支 + 多次 commit + 一次性 PR**:
  - 一个特性(V5.x.y)开**一个**分支 `feature/V5.x.y-<slug>`
  - 内部 N 个 sub-task 各自独立 commit(commit message 标 `V5.x.y.N <title>`)
  - **所有** sub-task 跑完 → `git push -u origin` → `gh pr create` → `gh pr merge --squash --delete-branch`(**自**合并,走 GitHub PR API,**不**本地 merge)
  - 合并后 `git checkout main && git pull` 回到主干,继续下一个特性
- **无例外**:任何改动(含热修复、文档/规则、CLAUDE.md 本身)都是一个特性一个分支一次 PR,**禁止中途拆独立 PR**。中途拆 = 合并当前 → 切回 main → 开新分支,会中断 agent 全权委托的连续推进节奏,这是首要避免的。热修复 / 文档改动各自**就是一个特性**,各自走分支 PR,没有特殊通道;跨周跨月的大特性也在同一个分支上连续做完再一次 PR,不拆成系列

### Versioning Convention (V5.16 起)

**格式: `Major.Feature.Fix` — 迁移文件 `V{MAJOR}.{FEATURE}.{FIX}__*.sql`,分支 `feature/{MAJOR}.{FEATURE}-{slug}`,CHANGELOG 标签 `v{MAJOR}.{FEATURE}.{FIX}`**

| 位 | 语义 | 递增规则 |
|---|---|---|
| **Major** | 里程碑(milestone) | 大版本升级(架构 / 数据模型 / 部署协议级别)才动;**不**要求每次 release 都加 |
| **Feature** | 任意特性(feature) | **不**要求严格 +1,可以跳号(3.1.3 → 3.1.4 → 3.5.3,跳过未做的小版本) |
| **Fix** | 修小 bug / 增量迁移 | 同 Feature 内的后续微调(补字段、bugfix) |

跟标准 SemVer 的区别:Feature 位可跳号(历史习惯)、Major 不绑定"破坏性"(V3→V5 是里程碑,非 API breaking)、Flyway 跟 Maven 版本号不强绑定。

**硬规则:已发布在 dev / 生产 DB 上的版本号不允许改写**(改了 Flyway checksum 校验失败);要"修"已发布的版本只能再发一个 `V{同号}.{+1}__*.sql`。

### Module Boundaries — 禁止"借实体"

**核心规则:`ruleforge-console-app` 和 `ruleforge-executor-app` 是平行的可部署 Spring Boot app,互不依赖。** 共享的只有 `ruleforge-core` / `ruleforge-decision` / `ruleforge-console` / `ruleforge-executor` 库模块。

**禁止的反模式**:
- ❌ console-app 里 `import com.ruleforge.decision.entity.*`(这些 entity 在 executor-app 里)
- ❌ console-app 里 `import com.ruleforge.decision.mapper.clickhouse.*`(同理)
- ❌ 任何"在 A app 里 import B app 的类"的操作 — 看起来能编(因为 IDE 把整 monorepo 都 index 了),**实际上 pom.xml 没声明依赖,`mvn -pl <app> package` 一定失败**

**判断当前表/Entity 该归谁**:
| 表/Entity | 所属模块 | 注入的 DataSource |
|---|---|---|
| `rf_*` (V5.15 起的权限/用户/审计) | `ruleforge-console-app` 专属 | `ruleforgeDataSource` (`ruleforge_db`) |
| `nd_*` (V5.1~V5.13 的批测/agent/监控/决策日志) | `ruleforge-console-app` 专属 | `appDataSource` (`app_db`) |
| `act_*` / `flw_*` (Flowable 引擎) | `ruleforge-console-app` + executor-app 共用 | `flowable` (`flowable_db`) |

**"我的表/Entity 在哪个 module / DataSource 怎么选" 速查**:
- `com.ruleforge.console.app.entity.*`  → console-app / 看 entity 注释指明 DataSource
- `com.ruleforge.decision.entity.*`    → executor-app / `ruleforgeDataSource` 在 executor 侧
- `com.ruleforge.console.*` (storage / flow / batchtest / migration / observability) → console 模块,可在 console-app 直接用

**一次性批处理工具(回填/迁移/dual-write 补偿)准则**:优先 raw JDBC + `PreparedStatement`(不套 @Mapper/Entity,跑一次就完事)、不借其他 app entity(用本地 `record` DTO)、`BATCH_SIZE`+lastId 分页、单行失败 catch+log.warn 跳过、测试锁构造器签名(DataSource 类型 + 无跨模块 import)。

**反面教材**:`ClickHouseBackfillRunner`(Phase 8)违反三条 → main 合并后 `mvn package` 失败,生产 jar 打不出。修法见 CHANGELOG `fix/phase8-clickhouse-backfill-self-contained`。

## Rule Types

向导式规则集, 脚本式规则集 (UL), 决策表, 脚本决策表, 决策树, 评分卡, 决策流

## Configuration

- Console-app: port 8081, dual datasource (app + ruleforge), `ruleforge.exec.url` → executor
- Executor-app: port 8082, `ruleforge.console.url` → console
