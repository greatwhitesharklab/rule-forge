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
ruleforge-parent        Maven parent POM, Spring Boot BOM, Java 17
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
- Flowable 8 BPM engine for decision flow execution
- Frontend: TypeScript, React, Vite 8, Ant Design 5, bpmn-js for flow designer
- Frontend HTTP: centralized `src/api/client.ts` (formPost, jsonPost, jsonPut, httpGet, httpDelete, save, saveNewVersion)
- Frontend tests: Vitest unit tests, Playwright E2E tests

## Development Principles

### BDD/TDD

- Follow Behavior Driven Development practices for test writing:
  1. Write test files with Gherkin-style behavior annotations (Given/When/Then) in @DisplayName/@Nested
  2. Write test code directly after annotations — no need to confirm with user before writing test code
- Follow Test Driven Development: never write business/implementation code before writing tests

### Versioning Convention (V5.16 起)

**格式: `Major.Feature.Fix` — 迁移文件 `V{MAJOR}.{FEATURE}.{FIX}__*.sql`,分支 `feature/{MAJOR}.{FEATURE}-{slug}`,CHANGELOG 标签 `v{MAJOR}.{FEATURE}.{FIX}`**

| 位 | 语义 | 递增规则 |
|---|---|---|
| **Major** | 里程碑(milestone) | 大版本升级(架构 / 数据模型 / 部署协议级别)才动;**不**要求每次 release 都加 |
| **Feature** | 任意特性(feature) | **不**要求严格 +1,可以跳号(3.1.3 → 3.1.4 → 3.5.3,跳过未做的小版本) |
| **Fix** | 修小 bug / 增量迁移 | 同 Feature 内的后续微调(补字段、bugfix) |

跟标准 SemVer 的区别:
- Feature 位不严格递增 — 项目里跳号是历史事实,延续这个习惯
- Major 不绑定"破坏性" — V3 → V5 是 2024-2025 里程碑(gr_* 换 rf_*,MyBatis-Plus 升级),不是 API breaking
- Flyway 跟 Maven 版本号不强绑定 — Maven version 是 1.0.0 / 4.0.6(Spring Boot),Flyway migration 用项目自己的节奏

**硬规则:已发布在 dev / 生产 DB 上的版本号不允许改写**(改了 Flyway checksum 校验失败);要"修"已发布的版本只能再发一个 `V{同号}.{+1}__*.sql`。

## Rule Types

向导式规则集, 脚本式规则集 (UL), 决策表, 脚本决策表, 决策树, 评分卡, 决策流

## Configuration

- Console-app: port 8081, dual datasource (app + ruleforge), `ruleforge.exec.url` → executor
- Executor-app: port 8082, `ruleforge.console.url` → console
