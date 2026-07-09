# CLAUDE.md

本文件给 Claude Code(claude.ai/code)提供本仓库的工作指南。

## 构建命令

Maven Java 17 项目,Spring Boot 4.0.6,在 `server/` 运行:

```bash
mvn compile                            # 编译全部模块
mvn compile -pl lib/ruleforge-core -am # 编译单模块(含上游依赖)
mvn clean package -DskipTests          # 打包(跳过测试)
mvn test -pl lib/ruleforge-core        # 引擎核心全量测试(纯引擎,无 DB/端口)
```

前端在 `console-ui/`,npm 命令见其 `package.json`。

文档站 `docs-site/`(VitePress):

```bash
cd docs-site
npm install      # 装 VitePress
npm run dev      # 开发服务器(localhost:5173,局域网访问加 --host)
npm run build    # 生产构建
```

Docker Compose 全栈:

```bash
docker compose up -d               # 启动全部服务
docker compose logs -f console-app # 看 console 日志
```

## 项目架构

> **V7.21 架构重构** (2026-07):彻底删除老 BPMN 决策流(ruleforge-decision 模块),
> V1 决策流成为唯一决策路径。共享层(数据源/变量定义)拆成独立 ruleforge-datasource 模块。

```
parent                  Maven parent POM,Spring Boot BOM,Java 17
ruleforge-core          lib/ — RETE 规则引擎(解析、执行、知识库)+ V1 决策流(V1FlowRunner)
ruleforge-datasource    lib/ — 数据源管理 + 规则变量定义(通用基础设施层)
ruleforge-console-app   app/ — 可部署 Spring Boot app — 编辑器(含 com.ruleforge.console.* 业务)
ruleforge-executor-app  app/ — 可部署 Spring Boot app — 执行器(含 com.ruleforge.executor.* 业务)
```

依赖链:
```
core ← datasource ← console-app
core ← datasource ← executor-app
core ← console-app / executor-app  (app 直接依赖 core)
```

### 模块详解

- **ruleforge-core** (`com.ruleforge.*`):RETE 规则引擎核心 + V1 决策流。**引擎逻辑不依赖 Spring**(仅 `config/` 提供 Spring auto-config 做装配)。`model/`(规则/RETE 结构) + `runtime/`(知识会话/执行/缓存) + `parse/`(老 XML/DSL 解析,ANTLR4) + `ir/`(新标准 IR:DMN/DRL/PMML) + `v1/`(V1 决策流:AST/执行器/CEL/发布 bundle)。
- **ruleforge-datasource** (`com.ruleforge.datasource.*`):V7.21 从 ruleforge-decision 拆出的通用基础设施。数据源管理(entity/mapper/repository/service)+ 7 个连接器(REST/JDBC/AI-Java/AdvanceAI/PKL)+ Java 源码编译器(jcompiler)+ 规则变量定义 + Spring auto-config。
- **ruleforge-console** (`com.ruleforge.console.*`):Web 编辑器后端。`controller/`(REST) + `service/` + `storage/`(项目存储) + `mapper/`(MyBatis-Plus) + `repository/`(模型类) + `config/`(MybatisPlus/Flyway)。V1 决策流控制器在 `controller/v1/`、发布管线在 `app/v1/`。
- **ruleforge-executor** (`com.ruleforge.executor.*`):规则执行。`controller/TestController`(`/test/do`、`/test/knowledge`) + `service/`(RuleForgeService、KnowledgePackageServiceImpl) + `service/impl/ExecResourceProvider`(从 console HTTP 拉资源)。V1 生产执行在 `executor/v1/`(`POST /v1/exec`)。
- **ruleforge-console-app** (`com.ruleforge.console.app.*`):可部署编辑器,含数据源配置、环境 provider、V1 发布(V1PublishService)、决策分析。
- **ruleforge-executor-app** (`com.ruleforge.executor.app.*`):可部署执行器,RestTemplate 配置用于和 console 通信;数据源懒加载层(`com.ruleforge.decision.lazy`,app-local)。

## 技术栈

- Java 17、Spring Boot 4.0.6、Spring Framework 7
- MyBatis-Plus 3.5.9、MySQL、Flyway
- ANTLR4、Jackson、fastjson2、HikariCP
- V1 决策流:极简 6 节点(Start/RuleSet/DecisionTable/ScoreCard/Decision/Gateway)+ CEL 条件,线性 + 排他网关图遍历(`V1FlowRunner`,在 ruleforge-core)
- 前端:TypeScript、React、Vite 8、Ant Design 5、react-flow(V1 决策流画布)
- 前端 HTTP:集中式 `src/api/client.ts`(formPost/jsonPost/jsonPut/httpGet/httpDelete/save/saveNewVersion)
- 前端测试:Vitest 单测、Playwright E2E

## 规则类型

向导式规则集、脚本式规则集(UL)、决策表、脚本决策表、决策树、评分卡、决策流

## 开发原则

**原则**:让 agent 连续自主运行,不被不必要中断打断。破坏性操作谨慎,缺信息用合理默认推进并标注。

- **默认不中断**:写测试、写实现、推进 sub-task、写文档都不 ask user,直接做
- **commit 即 save**:每个 sub-task 完成立即 commit,即使 context 被压缩也能从 commit 继续,不丢进度
- **方案选型前置**:涉及多种合理路径的选型(交互模型、技术栈、架构方案)在**需求开始时**就和用户确认,不作为中途中断的理由。读到代码发现新复杂度时,先用现有信息继续推进,不回头问选型

### BDD/TDD

- 遵循 BDD 写测试:① 测试文件用 Gherkin 行为注解(Given/When/Then)写在 `@DisplayName`/`@Nested` ② 注解后直接写测试代码,**无需**先问用户
- 遵循 TDD:绝不先写业务/实现代码再写测试

### 代码优雅(硬约束,与 BDD/TDD、模块边界同级)

优雅实现是硬规则,不是"先跑通再说"的牺牲品。与"最高优先级:agent 连续性"冲突时,取舍顺序:**破坏性风险 > 连续性 > 优雅** —— 优雅不得成为中断推进的借口,但**新代码不得引入红线违反,触碰老代码时顺手修**(童子军法则)。

红线:

- **分层单向**:`model/`(结构)禁止 import `runtime/`(执行)/`parse/`(解析);`runtime/` 禁止 import `parse/`。新代码引入反向 import 必须先重构落地,不留"后面再说"
  > 现状:`model/rete` 的 node/activity 融合是历史遗留(RETE 节点自带执行行为),已被 584 测试覆盖、性能达标,grandfathered;新代码不得加深该耦合,可逆的 import 逐步清理,node/activity 彻底拆分留作未来大版本
- **不造轮子**:类路径已有的库(Jackson / Spring / ANTLR)优先;手写 JSON 反序列化 / DI lookup / 表达式求值,仅在有明确技术理由时允许,并在类注释写明为什么
- **拒绝 god class**:单类 > 400 行 或 职责字段 > 7 = 警戒线;编排类(如 KnowledgeSession)把状态/agenda/event/response 拆给协作者,主类只编排
- **Utils 不是垃圾桶**:只放无状态、无依赖的纯函数;业务逻辑、静态容器(ApplicationContext holder)不准塞
- **核心引擎逻辑不渗 Spring**:`ruleforge-core` 引擎逻辑(非 `config/` 装配)不依赖 Spring ApplicationContext;要 bean 走构造注入或显式注册表,不用 `ApplicationContextAware` 静态 lookup
- **动核心前先 grep**:碰核心文件前,先确认有无违反上述任一条;修 bug/加 feature 时,顺手把触及文件的红线违反修掉

### Sub-task 工作流

- **BDD/TDD 硬规则**:先写测试 → 写实现 → 跑全量回归 → commit。sub-task 完成直接做下一个,不 ask user。
- **分支 + PR 流程**:
  - 一个特性 (V5.x.y) 开一个分支 `feature/V5.x.y-<slug>`, N 个 sub-task 在分支内 commit
  - 完成后 `git push -u origin` → `gh pr create` → `gh pr merge --squash --delete-branch`(走 GitHub PR API,不本地 merge)
  - `git checkout main && git pull` 回主干,继续下一个特性
- **灵活分组**: 同 concern 的 sub-task 可共分支; 不同 concern 可独立分支独立 PR。按实际情况决定。版本号 V6.x.y 三位原则保持。

### 版本约定 (V5.16 起)

**格式:`Major.Feature.Fix`** — 迁移文件 `V{MAJOR}.{FEATURE}.{FIX}__*.sql`,分支 `feature/{MAJOR}.{FEATURE}-{slug}`,CHANGELOG 标签 `v{MAJOR}.{FEATURE}.{FIX}`。

| 位 | 语义 | 递增规则 |
|---|---|---|
| **Major** | 里程碑 | 大版本升级(架构/数据模型/部署协议级别)才动;不要求每次 release 都加 |
| **Feature** | 特性 | 不要求严格 +1,可跳号(3.1.3 → 3.1.4 → 3.5.3) |
| **Fix** | 修小 bug/增量迁移 | 同 Feature 内的后续微调 |

跟标准 SemVer 的区别:Feature 位可跳号(历史习惯)、Major 不绑定"破坏性"(V3→V5 是里程碑,非 API breaking)、Flyway 跟 Maven 版本号不强绑定。

**硬规则:已发布在 dev/生产 DB 上的版本号不允许改写**(改了 Flyway checksum 校验失败);要"修"已发布的版本只能再发一个 `V{同号}.{+1}__*.sql`。

### 模块边界 — 禁止"借实体"

**核心规则:`ruleforge-console-app` 和 `ruleforge-executor-app` 是平行的可部署 Spring Boot app,互不依赖。** 共享的只有 `ruleforge-core` / `ruleforge-datasource` 两个 lib 模块(跟 Maven 模块 pom 一致)。

**禁止的反模式**(只有 1 条,因为共享 lib 是合理设计):
- ❌ **任何"在 A app 里 import B app 的类"** — 看起来能编(IDE 把整 monorepo index 了),**实际上 pom.xml 没声明依赖,`mvn -pl <app> package` 一定失败**。跨 app 只能走 HTTP(RestTemplate / HttpClient, console → executor 单向)。

**表/Entity 归属速查**(V7.21 更新:ruleforge-decision 已删,共享层迁 datasource):

| 表/Entity | 所属模块 | DataSource |
|---|---|---|
| `rf_*` (V5.15 起权限/用户/审计) | console-app 专属 | `ruleforgeDataSource` (`ruleforge_db`) |
| `nd_*` (V5.1~V5.13 批测/agent/监控) | console-app 专属 | `appDataSource` (`app_db`) |
| `rfa_datasource*` / `rfa_rule_variable_def` | 共享 `lib/ruleforge-datasource`(`com.ruleforge.datasource.entity.*`) | `appDataSource` (`app_db`) |
| `rfa_decision_flow_log` (决策分析) | console-app(AnalysisController/DecisionAnalysisMapper) | `appDataSource` (`app_db`) |
| `rf_v1_publish` (V1 发布 bundle) | console-app(`com.ruleforge.console.app.v1`) | `ruleforgeDataSource` (`ruleforge_db`) |
| `com.ruleforge.console.app.entity.*` | console-app | 看 entity 注释指明 DataSource |
| `com.ruleforge.console.*` (storage/batchtest/migration/observability) | console-app 内置业务包 | 可在 console-app 直接用 |
| `com.ruleforge.executor.*` (controller/service/...) | executor-app 内置业务包 | 可在 executor-app 直接用 |
| `com.ruleforge.decision.lazy.*` | executor-app 内置(数据源懒加载层,V7.21 残留包名) | executor-app 直接用 |

## 配置

- Console-app:端口 **8180**,双数据源(app + ruleforge),`ruleforge.exec.url` → executor
- Executor-app:端口 **8280**,`ruleforge.console.url` → console
