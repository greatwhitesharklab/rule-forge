# AGENTS.md

本文件给 AI 编码 agent 提供本仓库的工作指南。仓库另有 `CLAUDE.md`(内容基本同源),修改本文件时请同步检查。

## 项目概览

RuleForge 是面向金融场景(信贷审批 / 反欺诈 / 评分卡 / 决策流)的智能决策引擎:确定性规则 + ML 模型推理,每个决策可审计、可解释、可追溯。核心是自研 RETE 规则匹配引擎 + V1 决策流编排(极简 6 节点 + CEL 条件,线性 + 排他网关)。

支持的规则类型:向导式规则集、脚本式规则集(UL/DSL,自研 ANTLR4 DRL 4 grammar)、决策表(DMN 1.3 / Kie DMN)、评分卡 + 决策树(PMML 4.4 / pmml4s)、决策流(V1 自研)、AI 规则。老 `.xml` / `.ul` 路径作为向后兼容与新标准 IR(DMN/DRL/PMML)并行保留,是有意的迁移策略,不是待删债。

## 仓库布局(monorepo)

```
server/              Java 后端 Maven 多模块(主代码库,版本 1.21.0)
  parent/            Maven parent POM,Spring Boot BOM,Java 17
  lib/ruleforge-core        RETE 规则引擎 + V1 决策流(com.ruleforge.*)
  lib/ruleforge-datasource  数据源管理 + 规则变量定义(com.ruleforge.datasource.*)
  app/ruleforge-console-app  可部署编辑器后端,端口 8180(com.ruleforge.console.*)
  app/ruleforge-executor-app 可部署执行器,端口 8280(com.ruleforge.executor.*)
console-ui/          React + Vite 前端(Web 编辑器 / 决策流画布)
cli/                 Agent CLI(双实现:TypeScript commander `ruleforge` + Python typer `rf`)
model-service/       Python FastAPI ML 推理服务(PKL 模型),端口 8501
experiments/server-rust/  Rust 实验引擎(cargo workspace,6 crates),alpha,不进生产流量
docs-site/           VitePress 文档站
docs/                架构审计、技术债、路线图等内部文档
docker/              init-sql、staging 配置、entrypoint
scripts/             build-images.sh / dev-up.sh / dev-local.sh / dev-lan.sh 等运维脚本
data-generator/      演示数据生成器(Python,几乎空壳)
demo/                小微信贷审批、反欺诈检测示例
docker-compose.yml   根目录全栈编排(mysql / console-app / executor-app / clickhouse /
                     model-service / console-ui / postgres-rust / rust-flow)
```

注意:`server/lib/ruleforge-decision/` 只剩 `target/` 残留,V7.21 起该模块已从 `server/pom.xml` 删除,老 BPMN 决策流彻底移除,V1 决策流是唯一决策路径。不要在该目录下新建代码。

### Java 模块依赖链

```
core ← datasource ← console-app
core ← datasource ← executor-app
```

`ruleforge-console-app` 和 `ruleforge-executor-app` 是平行的可部署 Spring Boot app,**互不依赖**。禁止在 A app 里 import B app 的类(IDE 能编但 `mvn -pl <app> package` 必失败);跨 app 通信只能走 HTTP(console → executor 单向,RestTemplate/HttpClient)。

### ruleforge-core 内部分层

`model/`(规则/RETE 结构)+ `runtime/`(知识会话/执行/缓存)+ `parse/`(老 XML/DSL 解析,ANTLR4)+ `ir/`(新标准 IR:DMN/DRL/PMML)+ `v1/`(V1 决策流 AST/执行器/CEL/发布 bundle)。引擎逻辑不依赖 Spring(仅 `config/` 提供 auto-config 装配)。

## 技术栈

- 后端:Java 17、Spring Boot 4.0.6(Spring Framework 7)、MyBatis-Plus、MySQL 8、Flyway、ANTLR4、Jackson、fastjson2、HikariCP;分析存储 ClickHouse(决策日志双写)
- 前端:TypeScript、React 18、Vite 8、antd 6、@xyflow/react(react-flow)、Monaco/CodeMirror 编辑器、ag-grid、echarts、Tailwind
- CLI:TypeScript(commander,经 `npx tsx` 运行,bin 名 `ruleforge`)和 Python(typer + httpx + rich,script 名 `rf`,Python ≥ 3.12)两套并存
- Model Service:Python ≥ 3.11、FastAPI、uvicorn、scikit-learn/pandas/numpy、uv 管理依赖
- Rust 实验引擎:Rust 1.85+、Tokio、Axum、sqlx(Postgres)、rhai、criterion
- 测试:JUnit 5(Java)、Vitest(前端/CLI)、Playwright(E2E)、pytest(model-service)、cargo test/bench(Rust)
- 部署:Docker + Docker Compose;数据库迁移 Flyway

## 构建与测试命令

### Java(`server/` 目录下)

```bash
mvn compile                            # 编译全部模块
mvn compile -pl lib/ruleforge-core -am # 编译单模块(含上游依赖)
mvn clean package -DskipTests          # 打包(跳过测试)
mvn test -pl lib/ruleforge-core        # 引擎核心全量测试(纯引擎,无 DB/端口依赖)
```

app 模块测试依赖 DB/端口,全量 `mvn test` 需要 Docker 起的 MySQL 等环境。

### 前端(`console-ui/`)

```bash
npm run dev          # Vite dev server(默认 3000)
npm run build        # 生产构建
npm run typecheck    # tsc --noEmit
npm test             # Vitest 单测
npm run test:e2e     # Playwright E2E(需运行中的全栈;PLAYWRIGHT_BASE_URL 指目标,默认 http://localhost:3000,docker 全栈用 http://localhost)
npm run test:e2e:smoke / test:e2e:tour   # 冒烟 / 业务流巡览子集
```

前端 HTTP 统一走集中式封装 `console-ui/src/api/client.ts`(formPost/jsonPost/jsonPut/httpGet/httpDelete/save/saveNewVersion),不要在组件里裸 fetch。

### CLI(`cli/`)

```bash
npm test / npm run typecheck   # TypeScript 实现
uv run rf --help               # Python 实现(typer)
```

### Model Service(`model-service/`)

```bash
uv sync --extra dev && uv run pytest
```

### Rust(`experiments/server-rust/`)

```bash
cargo test
cargo bench
```

### 文档站(`docs-site/`)

```bash
npm install && npm run dev   # localhost:5173
npm run build
```

## 运行与部署

日常 dev 工作流(Docker 全栈):

```bash
./scripts/build-images.sh    # 本地 mvn 增量编译 + docker build(可加 console/executor/--no-tests/--clean)
./scripts/dev-up.sh          # 启动/重启容器(--rebuild/--clean 清数据卷,慎用/--logs/--stop)
```

不用 Docker 跑 Java 时用 `./scripts/dev-local.sh console|executor|all`(MySQL 等仍走 Docker,日志在 `.dev-logs/`,PID 在 `.dev-pids/`)。

端口速查:Console App 8180(`/actuator/health`)、Executor App 8280、Model Service 8501(`/health`)、MySQL 3306、ClickHouse 8123、Console UI(docker nginx)80、Vite dev 3000、Rust flow 8281、Rust Postgres 5433。

数据库:三个 MySQL 库(`ruleforge_app_db` / `ruleforge_db` / flowable 遗留库,见 `docker/init-sql/`)+ ClickHouse `ruleforge_analytics`。Flyway 迁移文件在 `server/app/ruleforge-console-app/src/main/resources/db/migration/`(ruleforge_db)和 `db/migration-app/`(app_db)。

## 开发约定

### 代码红线(硬约束)

- **分层单向**:`model/` 禁止 import `runtime/`、`parse/`;`runtime/` 禁止 import `parse/`。`model/rete` 的 node/activity 融合是历史遗留(grandfathered),新代码不得加深该耦合
- **不造轮子**:类路径已有 Jackson / Spring / ANTLR 就优先用;手写 JSON 反序列化 / DI lookup / 表达式求值需有明确技术理由并写进类注释
- **拒绝 god class**:单类 > 400 行或职责字段 > 7 是警戒线;编排类把状态/agenda/event/response 拆给协作者
- **Utils 不是垃圾桶**:只放无状态、无依赖的纯函数;业务逻辑、静态容器(ApplicationContext holder)不准塞
- **核心引擎不渗 Spring**:ruleforge-core 引擎逻辑(非 `config/` 装配)不依赖 ApplicationContext,要 bean 走构造注入或显式注册表
- **动核心前先 grep**:修 bug/加 feature 时顺手修触及文件的红线违反(童子军法则)

### 测试策略(BDD/TDD)

- 先写测试 → 写实现 → 跑全量回归 → commit;绝不先写实现再补测试
- Java 测试用 Gherkin 行为注解(Given/When/Then 写在 `@DisplayName`/`@Nested`)
- 引擎核心测试(`ruleforge-core`)纯内存跑,无 DB/端口依赖,应作为改引擎代码的回归基线
- 前端:Vitest 单测 + Playwright E2E(E2E 需全栈环境,60s timeout,chromium/firefox/webkit 三浏览器)

### 版本与迁移

- 版本格式 `Major.Feature.Fix`(Major 是里程碑、Feature 可跳号,非严格 SemVer)
- Flyway 迁移文件 `V{MAJOR}.{FEATURE}.{FIX}__*.sql`,**已发布到 dev/生产 DB 的版本号不允许改写**(checksum 校验会失败),只能再发一个 `V{同号}.{+1}__*.sql`
- 单人开发(2026-07 起):直接在 `main` 上提交,不再走特性分支 + GitHub PR;需要隔离的大改动可临时开分支
- 每个 sub-task 完成立即 commit

### 表/Entity 归属速查

| 表/Entity | 所属模块 | DataSource |
|---|---|---|
| `rf_*`(权限/用户/审计) | console-app | `ruleforgeDataSource`(ruleforge_db) |
| `nd_*`(批测/agent/监控) | console-app | `appDataSource`(app_db) |
| `rfa_datasource*` / `rfa_rule_variable_def` | 共享 lib/ruleforge-datasource | `appDataSource`(app_db) |
| `rfa_decision_flow_log` | console-app(决策分析) | `appDataSource`(app_db) |
| `rf_v1_publish`(V1 发布 bundle) | console-app | `ruleforgeDataSource`(ruleforge_db) |

## 安全注意事项

- 根目录 `.env` 含本地开发用数据库口令等,**不要提交、不要打印到日志或回复中**;模板见 `.env.example`
- docker-compose 里的默认口令(`ruleforge` / `changeme`)仅供本地 dev;生产部署按 `docs-site/deployment/production.md` 加固
- MySQL 容器 dev 配置关了 binlog / perf schema、`innodb-flush-log-at-trx-commit=2`,这些是 dev 加速取舍,生产要去掉
- 评分卡/决策树用 pmml4s(BSD-2-Clause),刻意避开 jpmml 的 AGPL-3.0;引入新依赖时注意许可证
- 引擎暴露 Java 源码编译器(jcompiler)和脚本执行(DSL/rhai)能力,涉及远程代码执行面,改相关代码时保持输入校验与隔离,不要扩大可执行面

## 更多文档

- 架构:`docs-site/architecture/`(概览 / RETE 引擎 / 决策流 / AI 规则混合)
- API:`docs-site/api/`(Console / Executor / Decision / Model Service API)
- 部署:`docs-site/deployment/`(Docker Compose / 生产加固)
- 代码结构与模块边界:`docs-site/development/code-structure.md`
- 路线图:`docs/roadmap.md`;Rust 引擎架构:`experiments/server-rust/ARCHITECTURE.md`
