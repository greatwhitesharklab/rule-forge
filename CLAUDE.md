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
  1. Before writing test code, create an empty test file with Gherkin-style behavior annotations (Given/When/Then) and function names only
  2. Use `AskUserQuestion` to confirm with the user before proceeding to write actual test code
  3. Never write test code before completing the behavior annotations
- Follow Test Driven Development: never write business/implementation code before writing tests

## Rule Types

向导式规则集, 脚本式规则集 (UL), 决策表, 脚本决策表, 决策树, 评分卡, 决策流

## Configuration

- Console-app: port 8081, dual datasource (app + ruleforge), `ruleforge.exec.url` → executor
- Executor-app: port 8082, `ruleforge.console.url` → console
