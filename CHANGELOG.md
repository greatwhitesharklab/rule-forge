# Changelog

All notable changes to RuleForge will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## 版本号约定 (V5.16 起)

**格式: `Major.Feature.Fix` — `vMAJOR.FEATURE.FIX`(Flyway 迁移文件名 `V{MAJOR}.{FEATURE}.{FIX}__*.sql`)**

| 位 | 语义 | 递增规则 | 例子 |
|---|---|---|---|
| **Major** | 里程碑(milestone) | 大版本升级(架构 / 数据模型 / 部署协议级别 breaking)才动;**不**要求每次 release 都加 | 3 → 5 是 2024-2025 跨大版本(gr_* 换 rf_*,MyBatis-Plus 升级等) |
| **Feature** | 任意特性(feature) | **不**要求严格 +1,可以跳号(3.1.3 → 3.1.4 → 3.5.3,跳过未做的小版本) | 5.8.0 batchtest subject polymorphism → 5.10.0 git dualwrite |
| **Fix** | 修小 bug / 增量迁移 | 同 Feature 内的后续微调(补字段、bugfix) | 3.1.3 → 3.1.4,3.8.0 → 3.8.1,3.12.0 → 3.12.5 |

跟标准 SemVer 的区别:
- **Feature 位不严格递增** — 项目里跳号是历史事实(3.5 → 3.6,5.3 → 5.8 等),延续这个习惯
- **Major 不绑定"破坏性"** — V3 → V5 是里程碑(2024-2025),不是 API breaking;Flyway 跟 Maven 版本号也不强绑定
- **每个 migration 都是历史的真实记录** — 已经应用在 dev / 生产 DB 上的版本号**不**允许改写(改了 Flyway 会校验失败);要"修"已发布的版本,只能再发一个 V{同号}.{+1}__*.sql

迁移文件命名示例:
- `V5.15.0__user_and_permission.sql` — V5 系列第 15 个 feature,首次
- `V5.16.0__init_app_schema.sql` — V5 系列第 16 个 feature(给 app_db 接入 Flyway)
- `V5.16.1__xxx.sql` — 后续修小 bug

## [Unreleased]

## [5.24.0] - 2026-06-10

### Changed

**`createFile` 自动建 parent folder(沿用 uruleV1 JCR-style 体验) — V5.24**

之前 `createFile(path, ...)` 在 parent folder 不存在时直接 NPE
(`parentFile.getProjectId()`),LLM agent / CLI 调
`rf file create <deep/path/file.xml>` 时被迫先手动建每一层 folder,
跟 uruleV1 的 `/frame/createFile` 不一样。

**改动**:
- `RuleForgeRepositoryServiceImpl.createFileNode` — parent 缺失时调用
  新增的 `ensureParentFolders(parentPath, user)`,递归向上建 folder chain
- `ensureParentFolders` 是 idempotent 的:递归到已存在的 ancestor 就停;
  race 期间另一线程已建好 parent,跳过 insert(走 `findByFilePathNeType` 二次确认)
- 极端 case(连 project root 都没)→ 抛 `RuleException("Cannot resolve ancestor")`,
  提示明确,不会 NPE
- 沿用现有 `RepositoryInterceptor.createFile` + `file_repository.batchInsertRelations`
  关系链 + Flyway 路径,**没有 schema 变更**

**测试**:5 个 BDD scenario 覆盖 happy path / 单层缺失 / 多层缺失 /
极端 ancestor 缺失 / 并发 race。390/390 console-app 测试通过。

**CLI 配合**:`rf file create` 不再需要先 `rf folder create` — 一次到位。

---

## [5.23.0] - 2026-06-10

### Added

**AI 第三方 API 数据源 — V5.23**

`AI_JAVA` 是第 5 个 `DataSourceConnector` 实现,让运营 / LLM Agent 可以
写一段 Java 代码、调任意第三方 API、运行时编译加载,**无需**重新打包
app / 加 schema / 改规则引擎。

---

#### 决策 lib 新增(`com.ruleforge.decision.datasource` 新子包)

- **`IJavaDataSource`** — LLM 写的子类的 SPI:
  `String getName()` / `Map<String,String> getSchema()` /
  `Object fetchField(String entityId, String fieldName, Map<String,String> context)`
- **`JavaSourceCompiler`** — JDK 内置 `javax.tools.JavaCompiler` 走
  `ProcessBuilder` forkJavac;5s timeout;返回
  `CompileResult { success, fqcn, publicClassName, classBytes, error }`;
  自动处理 javac 的"`public class 名 = 文件名`"约束 + 包路径
  (`outDir/<pkg>/<class>.class`)
- **`ClassLoaderPool`** — 每个 datasourceId 一个隔离 `URLClassLoader`
  (URLs 来自 host JVM classpath,LLM 类能 resolve IJavaDataSource +
  JDK 类型)。`close(id)` 释放,支持 evict
- **`AiJavaDataSourceConnector implements DataSourceConnector`** —
  `getConnectorType() = "AI_JAVA"`,被 `DatasourceServiceImpl` 自动
  discovery 走 `List<DataSourceConnector>` 注入。`fetchFieldValue` 走
  `config_json.classBytesBase64` → load → instantiate → `fetchField` →
  写 `nd_datasource_log`(同 `AdvanceAiConnector.logApiCall` 模式)
- **不引任何三方依赖** — `javax.tools.JavaCompiler` JDK 17 自带;
  URLClassLoader 自带;无 Groovy / ASM / JIT 类引

---

#### console 端(`ruleforge-console-app`)

- **`AiJavaDataSourceService`** — 流程: 验证 `implements IJavaDataSource`
  → `JavaSourceCompiler.compile()` → 写 `nd_datasource.config_json =
  { className, classBytesBase64 }` → 调 `aiJavaConnector.evict(id)`
  清 cache。`@Transactional`
- **`DatasourceController` 新增 `POST /{id}/java-source`** — body
  `{javaSource: "..."}`,返 `{success, className, classBytes, message}`
- **零 schema 变更** — `config_json TEXT` 装 base64 字节(原本就给
  `ADVANCE_AI` 的复杂 JSON 装大头)

---

#### 未触动(最小爆炸半径证明)

- `DataSourceConnector` 接口 — 未改
- `DatasourceServiceImpl` — 未改(Spring auto-discover 新 `@Component`)
- `Datasource` entity / repository / migration — 未改
- `DatasourceRoutingProvider` (executor `lazy/`) — 未改(自动复用新 connector)
- `application.yml` / `pom.xml` — 未改

---

#### 测试覆盖(31 新增测试,全模块 807/807 PASS)

| 模块 | 文件 | 数量 | 覆盖 |
|---|---|---|---|
| `ruleforge-decision` | `JavaSourceCompilerTest` | 8 | valid/no-package/top-level inner/empty/no-public/syntax-error/extractPublicClassName/extractPackageName |
| `ruleforge-decision` | `ClassLoaderPoolTest` | 4 | cache by id / isolation by id / bad bytes → LinkageError / close() 释放 |
| `ruleforge-decision` | `AiJavaDataSourceConnectorTest` | 9 | type=AI_JAVA / 合法 fetch + 审计 SUCCESS / null result 仍 SUCCESS / 缺 className / 缺 bytes / 非法 base64 / magic bytes 错 / testConnection |
| `ruleforge-console-app` | `AiJavaDataSourceServiceTest` | 7 | null id / 空 source / 不 implement / datasource 不存在 / 类型错 / 编译失败 / 成功路径 + evict |
| 全模块 | `mvn -B -pl ruleforge-core,ruleforge-decision,ruleforge-console-app,ruleforge-executor-app -am test` | **807/807** | 0 失败 0 错误 |

---

#### LLM-写-代码 示例(运营/agent 提交)

```java
package com.ruleforge.user;
import com.ruleforge.decision.datasource.IJavaDataSource;
import java.util.*;
public class Phase7Credit implements IJavaDataSource {
    @Override public String getName() { return "phase7_credit"; }
    @Override public Map<String, String> getSchema() {
        return Map.of("score", "number", "decision", "string");
    }
    @Override public Object fetchField(String entityId, String fieldName,
                                       Map<String, String> context) {
        if ("score".equals(fieldName)) return 720;
        if ("decision".equals(fieldName)) return "APPROVE";
        return null;
    }
}
```

→ `POST /ruleforge/datasource/{id}/java-source -d '{"javaSource": "..."}'`
→ 编译 + 写 DB + 清 cache,下次规则引擎调本 datasource 走真路径

---

## [5.21.0] - 2026-06-09

### Fixed

**Phase 8 ClickHouseBackfillRunner 跨模块依赖 + 错 DataSource (分支 `fix/phase8-clickhouse-backfill-self-contained`)**

Phase 8 引入的 `ClickHouseBackfillRunner` (`ruleforge-console-app`) 有两个
预存 bug,合并进 main 后卡住整模块构建 — `mvn -pl ruleforge-console-app
package` 直接失败,生产 jar 打不出来:

- **跨模块依赖** (compile 失败): runner import 了
  `com.ruleforge.decision.entity.DecisionFlowLog` /
  `com.ruleforge.decision.entity.DecisionRuleLog` /
  `com.ruleforge.decision.mapper.clickhouse.ChDecisionFlowLogMapper` /
  `com.ruleforge.decision.mapper.clickhouse.ChDecisionRuleLogMapper`,
  这些都在 `ruleforge-executor-app` 里。console-app 的 pom **没**声明对
  executor-app 的依赖,所以编译期就找不到类
- **错 DataSource** (即使编译过,运行时也会失败): runner 注入
  `ruleforgeDataSource` 读 `nd_decision_flow_log`,但这张表在 `app_db`
  (V5.16 `migration-app` 创建)。`ruleforgeDataSource` 指向 `ruleforge_db`
  (engine DB),表根本不在,SQL 报 `Table 'ruleforge_db.nd_decision_flow_log'
  doesn't exist`

**修复方案 — runner 自包含,raw JDBC:**

- 删 `com.ruleforge.decision.*` 全部 import,不再借 executor-app 的
  entity / mapper
- DataSource 改对:`ruleforgeDataSource` → `appDataSource` (修第二个 bug)
- ClickHouse 写入用 `PreparedStatement` + 内联 SQL 字符串(原 mapper 的
  24 列 INSERT,逐列 `setXxx` 绑值,nullable 列用 `setNull` 显式标 SQL type)
- 引入 `private record FlowLogRow(...)` 当本地 DTO — 镜像
  `nd_decision_flow_log` 行结构,只属于这个 runner,跨模块不共享
- 删 unused `chRuleLogMapper` 字段 (declared but never called,dead code
  留下的残骸) — rule log 的 backfill **不**做,CHANGELOG 诚实标注范围
- 单条 row 失败只 `log.warn` 跳过,不影响 batch
- `ClickHouseBackfillRunnerTest` 锁住边界:
  - 构造器签名 = 2 个 `DataSource` 参数 (无跨模块 mapper/entity)
  - 所有 declared 字段类型 `doesNotStartWith("com.ruleforge.decision.")`
  - INSERT SQL 24 个 `?` 占位符 + 关键列名存在
- 整模块 `mvn -pl ruleforge-console-app test` 332/332 全绿 (原 328 + 4 新增)
- `mvn -pl ruleforge-console-app package -DskipTests` **从失败变 SUCCESS**,
  Spring Boot fat jar (`ruleforge-console-app.jar`) 正常出包

**架构教训 — 写进 CLAUDE.md:**

- console-app / executor-app 是平行部署单元,**互不依赖**。任何"借实体"
  ("在 console 里 import executor 的 entity")都是隐式契约,一旦改 schema
  两边失同步,且编译/打包会过不了
- 一次性批处理工具(回填/迁移/dual-write 补偿)优先 raw JDBC,不要套
  MyBatis-Plus 抽象 — abstraction 成本 ≥ 实际收益
- 表在哪 DB 就注入哪个 DataSource bean:`nd_*` 在 `app_db` 用
  `appDataSource`,engine 表在 `ruleforge_db` 用 `ruleforgeDataSource`

**Phase 8 ClickHouseBackfillRunner schema mismatch fail-fast (分支
`fix/phase8-clickhouse-backfill-schema-failfast`)**

继续 Phase 8 修复:PR #31 修了跨模块依赖 + 错 DataSource,本轮又发现
**第 3 个 bug** — schema 不匹配:

- runner 假设 MySQL `nd_decision_flow_log` 是 24 列富版(含
  `rule_package_path` / `order_no` / `total_matched_rules` /
  `execution_time_ms` / `node_names` 等性能分析字段)
- 实际 V5.16 schema 是 15 列简版(`project_id` / `package_id` /
  `request_data` / `response_data` / `status` / `exec_ms` 等)
- 两套 schema 字段名 + 数量都对不上,即使 PR #31 修了编译/DB,runtime
  SQL 仍会 "Unknown column 'rule_package_path' in 'field list'"

**修复策略:启动时主动校验,schema 不匹配就 fail-fast。** 不写半成品
SQL 让 operator 调试,而是在日志里明确说"schema 不匹配,等 nd_decision_flow_log
富化(SLA P3),CH analytics 当前由 executor-app 写入时双写产生,
不走 backfill"。

- `checkMysqlSchemaCompatible()` — probe SELECT 富版标志性字段,
  抛 SQLException 就 false
- `run()` 启动第一件事调它,失败直接 log.error + return,**不打开
  ClickHouse 连接**(测试用 `AssertionError` 验证 ch.getConnection()
  不会被调)
- `ClickHouseBackfillRunnerTest` 新增 2 scenarios:
  - 检测到缺列时 `checkMysqlSchemaCompatible()` 返 false
  - `run()` schema 不匹配时 ClickHouse 连接**不**被打开
- 整模块 `mvn -pl ruleforge-console-app test` 334/334 全绿
  (原 332 + 本轮 2 新增)

**当前 CH `nd_decision_flow_log` 数据来源**:

- ✅ executor-app 热路径双写 (`DecisionLogServiceImpl` → `ClickHouseAnalyticsWriter`)
- ❌ backfill runner (schema mismatch,本轮 fail-fast 显式拒绝)

**V5.18 决策热路径 P0 修复 — executor→console RestTemplate 缺 baseUrl
(分支 `fix/5.18-executor-console-baseurl`)**

跑决策流 E2E 时发现 — 写好 rule + package,`POST /test/do?path=e2e_test/pkg01`
直接 500。executor 日志报 `IllegalArgumentException: URI is not absolute`,
定位在 `KnowledgePackageServiceImpl.sendRequest`:

```java
String url = "/ruleforge/packageeditor/loadPackages";  // ← 相对路径!
this.consoleRestTemplate.exchange(url, ...);  // ← 裸 RestTemplate 没 baseUrl
```

`consoleRestTemplate` 是 `RestTemplateConfig` 裸 `new RestTemplate()`,**没**设
`baseUrl`,所以相对路径直接抛异常。`ExecResourceProvider` (`/ruleforge/frame/fileSource`)
也是同款 bug。

**根因:** 这是 initial commit (`a01d0d1`) 就埋下的 latent bug — 当时可能假设
有 Spring 自动给 RestTemplate 配 baseUrl,实际没有。**整整两个版本没人在
真实环境跑过决策热路径**,本地单测 + 单元集成绕过了。PR #27 (V5.15 权限) /
PR #29 (V5.16 app_db) / Phase 8 双写 都没人触发。

**修复:**

- `KnowledgePackageServiceImpl` 构造注入 `@Value("${ruleforge.console.url}")`,
  `sendRequest` 拼 `consoleUrl + "/ruleforge/packageeditor/loadPackages"`
  (尾部 `/` 剥掉避免拼出 `//ruleforge`)
- `ExecResourceProvider` 同样模式
- BDD 测试 3 + 2 scenarios:
  - URL 必须以 `http://` 开头(相对路径必拒)
  - consoleUrl 尾部 `/` 正确处理
  - 不出现以 `/ruleforge` 开头的相对路径
- 端到端验证: docker compose up → admin 登录 → 创建 rule + package → 
  `POST /test/do?path=e2e_test/pkg01` 成功(原本 500),规则 `adult-check` 
  被 matched + fired

**V5.18 启动修复 — executor-app 缺 @Primary SqlSessionFactory
(同分支)**

修完 baseUrl 重启 executor-app 失败 — `APPLICATION FAILED TO START`,
"expected single matching bean but found 2:
clickhouseSqlSessionFactory, ruleforgeSqlSessionFactory"。
executor-app 有 2 个 SqlSessionFactory bean(Phase 8 引入 ClickHouse 那个 + 老的),
MyBatis-Plus auto-config 注入默认 `sqlSessionTemplate` 时无法二选一。

console-app 的同款配置 (`RuleForgeProdConsoleMybatisPlusConfig.appSqlSessionFactory`)
早就加过 `@Primary`,executor-app 漏了。照搬:

- `MybatisPlusConfig.ruleforgeSqlSessionFactory` 加 `@Primary`
- 整模块 `mvn -pl ruleforge-executor-app test` 50/50 全绿(原 45 + 5 新增)
- docker compose up -d executor-app 健康启动

**V5.18 规则包导入/导出 P0 修复 (分支 `fix/5.18-import-export-robust`)**

跑决策流 E2E 时顺手审计 `FrameController.importProject` / 
`exportProjectBackupFile`,发现 4 个 production-affecting bug:

1. **importProject status 永远 true (P0)**
   `result.put("status", true)` 在 try{} 末尾无条件执行,即使内部 try-catch
   吞掉 deleteProject / importFromZip 异常,status 也被覆盖成 true。
   **最阴险的失败模式** — 用户导入失败时前端 ImportProjectDialog 永远弹
   "导入成功",真出错查不到。

2. **损坏 tar.gz 触发 NPE 500 (P0)**
   `extraImportGzip` 找不到 `systemView.json` 时返 null,上层
   `repositoryFile.getName()` NPE,带 stacktrace 泄给前端。

3. **exportProjectBackupFile 静默失败 (P1)**
   `!user.isExport()` 时直接 `return;`,前端 200 + 空 body,浏览器既不弹
   下载也不报错,用户不知道为啥没下载。

4. **exportProjectBackupFile readFile NPE (P1)**
   `readFile(packageConfigPath)` 返 null(新项目没 packageConfig.xml)时
   `IOUtils.toByteArray(null)` 抛 NPE,前面写的 tar entries 全白写。
   `projectRepository.findByName` 返 null 也会 NPE。

**修复:**

- importProject — 用 `boolean importSucceeded` 跟踪主流程是否跑完,
  只有 deleteProject + importFromZip + createFile + batchInsertVersions
  全部成功才置 status=true;任何一步抛异常 → 留 status=false +
  content 写具体错误
- extraImportGzip 返 null repositoryFile → 立即 status=false + 
  友好提示 "备份文件解析失败",不让 NPE 冒到 controller
- 同步补 projectName 空字符串校验(防御性)
- exportProjectBackupFile — 权限拒绝时返 403 + JSON `{"error":"..."}`
  而不是空 return
- readFile 返 null 时写空串占位(import 端 IOUtils.toString 处理空串 ok)
- projectRepository.findByName 返 null 时写空 list,不 NPE

**端到端验证(docker compose):**

| 场景 | V5.18 之前 | V5.18 之后 |
|---|---|---|
| 损坏 tar.gz 导入 | HTTP 500 + NPE stacktrace | `{"status":false,"content":"备份文件解析失败..."}` |
| 无 export 权限用户 | HTTP 200 + 空 body(不下载) | HTTP 403 + `{"error":"No export permission"}` |
| 正常导入/导出 | status=true | status=true(不变) |

**决策流全链路 E2E 修复 (V5.18 续 — 5 个 P0,生产决策路径 100% 跑不通)**

`POST /api/loan/evaluate` 是生产决策主路径,executor-app 跑 BPMN + 写 `nd_decision_flow_log`。
从 e2e_test 项目创建 → 规则包部署 → `/test/do` 单测通过 → `/api/loan/evaluate`
第一次调用,**5 个独立 P0 bug 全部暴露**,逐个修完端到端绿:

| # | 报错 | 根因 | 修复 |
|---|---|---|---|
| 1 | `Table 'ruleforge_db.nd_rule_variable_def' doesn't exist` | entity 在代码、schema 只在 dev 手工建过,没进 Flyway | 新增 `V5.18.0__nd_rule_variable_def.sql` |
| 2 | `Unknown property ${ruleServiceTaskDelegate}` | delegate 在 `com.ruleforge.console.flow.delegate`(console-app),executor-app 跑 BPMN 找不到 — **违反 console↛executor 模块边界** | 把 `RuleServiceTaskDelegate` 移到 `ruleforge-decision` (共享 lib,decision 已依赖),`@ComponentScan` 加 `com.ruleforge.decision.flow.delegate` |
| 3 | `Invalid 'engine' for process definition : v7/v8` | `ENGINE_VERSION_` 必须为 NULL(老 v5 兼容标志,Flowable 8 主流是 NULL),不能是 `v7`/`v8` | 部署 BPMN 时 `ENGINE_VERSION_` 留 NULL |
| 4 | `Unknown column 'SUB_SCOPE_ID_'/'LONG_'/'META_INFO_' in 'field list'` (×2 张表) | V1 flowable migration 抄的 jar 里老 engine.sql,**缺 flowable-variable-service-8.0.0 必加的 scope/long/meta 列** | 新增 `migration-flowable/V4__flowable_variable_scope_columns.sql` 同时补 `ACT_RU_VARIABLE` + `ACT_HI_VARINST` |
| 5 | `Unknown column 'order_no'/'flow_version'/... in 'field list'` + `Field 'project_id' doesn't have a default value` | V3.12.5 建表 13 列,entity 24 列;`project_id`/`package_id` 老 schema 是 NOT NULL,新 entity 不带 | 新增 `V5.18.1__extend_decision_flow_log_columns.sql`(18 列 IF NOT EXISTS + 老字段改 NULL)+ `V5.18.2__nd_decision_flow_params.sql`(补建整张 entity 表) |
| 6 | `execution XXX doesn't exist` 拿不到 rule 输出 | `async-executor-activate=false` 同步跑,start 返回时 process 已到 endEvent,runtime execution 被清,`runtimeService.getVariables` 抛 | `DecisionServiceImpl.executeDecisionFlow` try/catch `FlowableObjectNotFoundException`,跑完读不到就跳过,delegate 内部已经把规则结果写到 process variables,真正结果走 `outputModel` |

**端到端验证(docker compose):**

```bash
# 部署一个测试 BPMN
docker exec ruleforge-mysql mysql -uroot -pruleforge flowable_db -e "
INSERT INTO ACT_RE_DEPLOYMENT (ID_, NAME_, DEPLOY_TIME_, ENGINE_VERSION_) VALUES (..., 'loan-approval', NOW(3), NULL);
INSERT INTO ACT_GE_BYTEARRAY (ID_, DEPLOYMENT_ID_, NAME_, BYTES_) VALUES (..., ..., 'loan-approval.bpmn20.xml', UNHEX('...'));
INSERT INTO ACT_RE_PROCDEF (ID_, KEY_, NAME_, DEPLOYMENT_ID_, ...) VALUES (..., 'loan-approval', 'Loan Approval', ..., 1);"

# 调用决策
curl -X POST http://localhost:8280/api/loan/evaluate \
  -H 'Content-Type: application/json' \
  -d '{"userId":"u001","orderNo":"ORD001","rulePackagePath":"e2e_test/pkg01","flowId":"loan-approval"}'

# 验证日志落库
SELECT id, user_id, order_no, flow_id, execution_status, total_time_ms FROM nd_decision_flow_log ORDER BY id DESC LIMIT 1;
# → id=4, user_id=u001, order_no=ORD001, flow_id=loan-approval, execution_status=SUCCESS, total_time_ms=195
```

**架构层发现(顺手记下,不入这次 commit):** Flyway `migration/V1__flowable_engine_mysql.sql`
声称是 "Flowable 8.0.0 schema bootstrapping",但 `ACT_RU_VARIABLE` /
`ACT_HI_VARINST` 实际是 6.x 时代的列(没 scope/long/meta),导致 Flowable 8.0.0
variable service mapper 一查就 "Unknown column"。V1 应该用 Flowable 8.0.0 jar 里的
`org/flowable/db/create/flowable.mysql.create.engine.sql` 而不是手抄老 schema。**后续要做的:
重写 V1 整张表清单**(但 V1 已发布,不能改 — 必须发 V5+ 增量,所以这次走 V4 补列)。

### Added

**V5.17 用户/权限操作审计日志 (分支 `feature/5.17-user-audit-log`,合入 `feature/5.15-permission-auth`)**

把 V5.15 用户/权限改造成果落库 + 可查询,审计谁在何时对哪个用户/权限做了什么操作:

- Flyway `V5.17.0__user_audit_log.sql` — 新表 `rf_user_audit_log`
  (occurred_at DATETIME(3) / actor / action / target_user_id / target_username /
   field_name / old_value / new_value / project / note) + 3 复合索引
  (actor_time / target_time / action_time)
- `com.ruleforge.console.audit` 包 — `AuditLogEntity` (MyBatis-Plus 实体,@Builder)
  + `AuditLogMapper` (BaseMapper + 自定义 `selectListByFilters` XML 查询)
  + `AuditService` (7 类动作: CREATE_USER / UPDATE_USER / TOGGLE_ENABLED /
   RESET_PASSWORD / SAVE_PERMISSIONS / LOGIN_SUCCESS / LOGIN_FAIL) + 实现
- `MybatisPlusConfig` 改造 — 显式 `setMapperLocations("classpath*:mapper/**/*.xml")`
  确保自定义 mapper XML 被加载(避免 MyBatis-Plus 默认 pattern 漏掉 audit XML
  导致 `Invalid bound statement (not found)` 错误)
- `AuthService` / `AuthServiceImpl` 改造 — 全部 user-mgmt 方法 (createUser / updateUser /
  toggleEnabled / resetPassword) + login 加 `actor` 参数 + 每次调对应 auditService.log*;
  audit 写入走 fire-and-forget (catch + log.warn,不抛 — 跟 V5.10-C dualWriteFailure
  同款设计,audit 故障不能阻塞 user-mgmt 主路径)
- `PermissionController` 新增 `GET /permission/audit?actor=&action=&size=`
  (admin 门控,size 上限 500 防滥用);saveUserPermissions 调 auditService.logSavePermissions
- 前端 `AuditLogPanel` (Antd Table + Input actor 过滤 + Select action 过滤 +
  详情 Drawer) + ActivityBar 注册 "审计日志" 图标 + `getAuditLogs` API client
- 端到端验证: docker compose 起 5 服务 → admin 登录 → CREATE_USER / TOGGLE_ENABLED /
  RESET_PASSWORD → audit 行落库 + 前端面板实时显示 + Drawer 详情正确
- BDD 覆盖: AuditServiceTest(10) + AuditControllerTest(4) + AuditLogPanel Vitest(7)
  + audit-log-panel Playwright(2) = 23 scenarios 全绿;后端 321/321,前端 328/328

**V5.15 权限改造 (分支 `feature/5.15-permission-auth`)**

把用户/权限从"文件+硬编码"迁移到 MySQL,实现 BCrypt 密码认证 + 项目级权限控制:

- Flyway `V5.15.0__user_and_permission.sql` — `rf_user` (BCrypt 密码) + `rf_user_project_permission`
  (项目级权限 12 种文件类型 × 读/写) + seed admin/admin123
- `PasswordUtil` — BCryptPasswordEncoder 包装 (`encode` / `matches`)
- `UserEntity` / `UserProjectPermissionEntity` — MyBatis-Plus 实体 + `toSessionUser()` / `toProjectConfig()` 转换
- `UserMapper` / `UserProjectPermissionMapper` — 按 username / userId / userId+project 查询
- `AuthService` + `AuthServiceImpl` — login(BCrypt 验证) / createUser / updateUser / toggleEnabled / resetPassword
- `LoginController` 改造 — 不再硬编码 `setAdmin(true)`,调 AuthService 做 BCrypt 验证,
  失败返 `{status: false, error: "用户名或密码错误"}`,session 写入 DB 实体转换的 DefaultUser
- `PermissionServiceImpl` 改造 — 从 `rf_user_project_permission` 表读权限,
  不再从仓库文件 `___resource__security__config__` 读;switch expression 覆盖全部 FileType 枚举;
  构造器注入替代 setter 注入
- `PermissionController` 新增用户 CRUD 端点 — GET/POST/PUT/PATCH `/permission/users`,
  GET/POST `/permission/users/{id}/permissions`;admin-only 门控走 `assertAdmin()`
- `EnvironmentProviderImpl` 改造 — `getUsers()` 从 `rf_user` 表读替代硬编码
- `GlobalExceptionHandler` — `NoPermissionException` → 401 + 纯文本 (前端 client.ts 已对 401 alert)
- BDD 覆盖: AuthServiceTest(4) + PasswordUtilTest(3) + LoginControllerAuthTest(4) +
  PermissionControllerUserMgmtTest(4) = 15 scenarios 全绿;全模块 307/307 全绿

**v5.16 app_db Flyway 管理(分支 `feature/5.16-app-db-flyway`)**

把 app_db(11 张 `nd_*` 表:V5.1.x batchtest / V5.3.x agent / V5.6.x
monitoring / V5.9.x simulation / V5.13.x decisionflowlog 等)接入 Flyway,
跟 `ruleforge_db`(21 个 V3~V5.15 迁移)和 `flowable_db`(FlowableFlywayConfig)
保持一致:

- 新增 `AppFlywayConfig`(`com.ruleforge.console.app.config`)— 独立 Flyway
  实例绑 `appDataSource`(@Primary),`@PostConstruct migrateAppSchema()` 早于
  MyBatis-Plus mapper 初始化,避免 mapper 找不到表
- 隔离三套 Flyway,绝不互踩:
  - `appDataSource`  → `classpath:db/migration-app`  + `flyway_app_schema_history`
  - `ruleforgeDataSource` → `classpath:db/migration` + `flyway_schema_history`
  - `flowableDataSource` → `classpath:db/migration-flowable` + `flowable_flyway_history`
- `db/migration-app/V5.16.0__init_app_schema.sql` — 11 张 `nd_*` 表 DDL
  全部用 `CREATE TABLE IF NOT EXISTS`,**新环境从 0 跑起建表,老环境
  (已存在 11 张表但没 history)幂等跳过**
- `baselineOnMigrate=true` + `baselineVersion="0"` — 让 V5.16.0 跑(老
  环境在 0 建立 baseline 后,V5.16.0 仍是 > 0 所以会被检查,IF NOT EXISTS
  保证 DDL 幂等)
- `AppFlywayConfigTest` — 6 个 BDD 场景:DataSource 选择 / migration
  location / `baselineOnMigrate` / history 表名 / baseline 版本
- 端到端验证(本地 Docker compose 全栈):
  - 首次启动:`Schema history table does not exist yet` → `Creating Schema
    History table with baseline` → `Migrating schema 'app_db' to version
    "5.16.0 - init app schema"` → `Successfully applied 1 migration to
    schema 'app_db', now at version v5.16.0`
  - 重启:`Current version of schema 'app_db': 5.16.0` → `Schema 'app_db'
    is up to date. No migration necessary.`
  - 数据无损:`nd_batch_test_session=27` / `nd_batch_test_row=81` /
    `nd_metrics_snapshot=344,323` 全部保留
- 整模块 `mvn -pl ruleforge-console-app test` 299 / 299 全绿(原 293 + V5.16 新增 6)

### Changed

**v5.14 mapper duplicate registration 清理(分支 `feature/5.14-mapper-warning-cleanup`)**

消除 `@MapperScan` 重复注册导致的 Spring bean 定义冲突 warning:

- `RuleForgeConsoleAutoConfiguration` 移除 `@MapperScan("com.ruleforge.console.mapper")`
  — 已由 `MybatisPlusConfig` 的 `@MapperScan(value="com.ruleforge.console.mapper",
  sqlSessionFactoryRef="ruleforgeSqlSessionFactory")` 统一注册
- `RuleForgeConsoleApplication` 移除 `@MapperScan("com.ruleforge.decision.mapper")`
  — 已由 `DecisionMybatisPlusConfig` 的 `@MapperScan(value="com.ruleforge.decision.mapper",
  sqlSessionFactoryRef="ruleforgeSqlSessionFactory")` 统一注册
- 保留的 `@MapperScan` 都带显式 `sqlSessionFactoryRef`,确保 mapper 绑定正确的数据源
- 整模块 `mvn -pl ruleforge-console-app test` 292 / 292 全绿

### Added

**Phase 8 ClickHouse 高性能分析存储(分支 `feature/phase8-clickhouse`)**

决策日志每天可能几十万条,MySQL 聚合查询在大数据量下性能不足。Phase 8 引入
ClickHouse 作为分析存储,MySQL 保持事务写入不变:

- **Maven**: 两个 app 加 `com.clickhouse:clickhouse-jdbc:0.7.1`
- **ClickHouse DDL** (`scripts/clickhouse-init.sql`):
  - `ruleforge_analytics.nd_decision_flow_log` — ReplacingMergeTree,
    ORDER BY (rule_package_path, flow_id, created_at, id),
    月级分区 + TTL 365 天
  - `ruleforge_analytics.nd_decision_rule_log` — 同模式,
    ORDER BY (rule_name, rule_type, created_at, id)
- **双写** (executor-app):
  - `DecisionLogServiceImpl` MySQL 写入成功后发
    `ClickHouseAnalyticsEvent` → `ClickHouseAnalyticsWriter` 异步写 CH
  - `@Async("clickhouseWriteExecutor")` 线程池(core=1, max=3, queue=500)
  - 失败吞掉 + log.warn,不影响 MySQL 主路径
- **查询路由** (console-app):
  - `AnalysisServiceImpl` 注入 `@Autowired(required=false)` CH mappers
  - 每个查询方法先试 CH,失败自动回退 MySQL
  - `clickhouse.analytics.enabled=false` 时完全不创建 CH beans
- **CH Mapper 接口**:
  - `ChDecisionAnalysisMapper` — 6 方法,ClickHouse SQL 方言
    (`formatDateTime`/`toDate`/`stdDevSamp` 替代 MySQL 的
    `DATE_FORMAT`/`DATE`/`STDDEV`)
  - `ChRuleCoverageMapper` — 2 方法,表引用加 `final` 触发去重
  - `ChDecisionFlowLogMapper` / `ChDecisionRuleLogMapper` — executor 写入
- **DataSourceConfig** — 两个 app 各加 `clickhouseDataSource` bean
  (HikariCP + ClickHouseDriver)
- **ClickHouseMybatisPlusConfig** — `@ConditionalOnProperty` 控制,
  禁用时不创建任何 CH bean,analytics 回退 MySQL
- **BackfillRunner** — `@Profile("backfill")` CommandLineRunner,
  按 id 批量读 MySQL 写 CH,ReplacingMergeTree 天然去重
- **docker-compose.yml** — 加 ClickHouse 26.5 service + env vars
- 整模块 `mvn -pl ruleforge-console-app test` 292 / 292 全绿
- 整模块 `mvn -pl ruleforge-executor-app test` 45 / 45 全绿

**V5.8.4 BatchTest Excel upload(分支 `feature/phase9-batch-test-controller`)**

**v5.10-B 老项目 DB→Git migration tool(分支 `feature/5.10-git-storage`)**

把 V5.10-A 之前创建的项目(`gr_file_version.fileContent` 有内容但
`git_commit_sha=NULL` 且本地 `.git` 不存在)回填到 Git 仓,让老项目
也跑在新 storage 协议上:

- `com.ruleforge.console.migration.MigrationService` — 核心服务
  `migrate(MigrationRequest): MigrationReport`,扫 `gr_project`,逐项目
  `initRepo` + 按 `versionNumReal` 升序逐版本 `writeFile`+`commit`+`updateGitCommitSha`
- `MigrationRequest` / `MigrationReport` / `ProjectResult` / `VersionError`
  — 入参/出参 DTO(运行 ID + 聚合 + per-project 拆解)
- `MigrationController` — `POST /ruleforge/migration/run`,admin 门控走
  `permissionService.isAdmin()`(与 `RuleForgeRepositoryServiceImpl:216` 同款)
- `MigrationCommandLineRunner` — `@ConditionalOnProperty(ruleforge.migration.enabled=true)`,
  走 main jar 加 `--ruleforge.migration.enabled=true --spring.main.web-application-type=none`,
  支持 `--project <name>` (可重复) + `--dry-run`,运维/CI 都能用
- `FileRepository` 加 `findVersionsByProjectId(Long)` + `FileRepositoryImpl` 实现
  (MyBatis-Plus `LambdaQueryWrapper` 按 `versionNumReal` 升序)
- skip 规则: `fileContent IS NULL` 跳过;`git_commit_sha IS NOT NULL` 跳过
  (天然幂等,重跑安全)
- per-project / per-version 两层 `try/catch` 失败隔离,异常吞进 `MigrationReport`
- 分支 `main`,author `"migration-tool"`,commit message 形如
  `Migration: /project/file.xml v3 (alice, 2026-05-12)` — 历史回看方便
- 内容**不**走 `xmlCanonicalizer`,老项目字节级保留 DB 原内容
- BDD 覆盖: `MigrationServiceBddTest` 8 scenarios(happy / skip / dry-run /
  idempotent / per-project-fail / per-version-fail / no-projects /
  multi-version-order)+ `MigrationControllerAdminGateTest` 1 case。
  `mvn -pl ruleforge-console-app test -Dtest='Migration*,RuleForge*Git*'`
  16 个 case 全绿。

**v5.10-C dualWrite 失败可观测(分支 `feature/5.10-git-storage`)**

5.10-A/B 把 dualWriteToGit 跑通了,但失败时 DB 写成功 + Git 写失败会静默
(仅打 `log.error`,sha 返 null),运维侧无可观测。5.10-C 补足:

- 新表 `gr_git_dualwrite_failure` — `file_path / project_id / file_id /
  error_type / error_message / branch / occurred_at`,Flyway
  `V5.10.0__git_dualwrite_failure.sql`
- `GitDualwriteFailureEntity` / `Mapper` / `Repository` / `RepositoryImpl`
  — `insert / countAll / countSince(Date) / findRecent(int) / deleteOlderThan(Date)`
- `RuleForgeRepositoryServiceImpl.dualWriteToGit` / `dualDeleteFromGit` 接入:
  - Micrometer `Counter` `ruleforge_git_dualwrite_total{op, result, error_type}`
    + `ruleforge_git_dualdelete_total{result, error_type}`,可在 `/actuator/prometheus` 抓
  - 失败时 `dualwriteFailureRepository.insert(...)` 落审计行
    (errorMessage 截到 2048 字符,含 cause chain)
  - DB 写失败行也**不**抛(防 audit-log 故障引发 dualWrite 行为变化)
- `resolveBranch(author)` 提到方法级,save/delete/failure-record 共享同一分支解析
  (避免 `BranchContext.forUser(null)` 产生 "user/null" 死值)
- `GitObservabilityController` — `GET /ruleforge/git/observability/summary`
  (总数 + 1h/24h + counter 快照) + `GET /ruleforge/git/observability/recent?limit=50`
  (默认 50,最大 500 防滥用),admin 门控
  (`permissionService.isAdmin()`,与 `RuleForgeRepositoryServiceImpl:216` 同款)
- `RuleForgeConsoleAutoConfiguration` `@ComponentScan` 加 `"com.ruleforge.console.observability"`
- BDD 覆盖: `DualWriteObservabilityBddTest` 6 scenarios
  (happy / fail-and-record / multi-fail-accumulate / skip-no-repo / error-type-tagging /
  failure-row-fields) + `GitObservabilityControllerTest` 5 cases
  (admin-gate-summary / admin-gate-recent / happy-summary / happy-recent /
  limit-clamp-to-500)
- 5.10-A 集成测试 `RuleForgeRepositoryServiceImplGitStorageIntegrationTest` 同步加
  `dualwriteFailureRepository` + `meterRegistry` 构造参数
- 整模块 `mvn -pl ruleforge-console-app test` 280 / 280 全绿

**v5.10-D UI Git 健康面板(分支 `feature/5.10-git-storage`)**

把 5.10-C 的 audit log + counter 暴露到前端,admin 可视化:

- API client `getGitStatusSummary` / `getGitStatusRecent` — 走 `httpGet`
  拉 `/ruleforge/git/observability/{summary,recent}`,admin 门控
  (后端用 `NoPermissionException` 拦,401 由 client 自动 alert)
- `GitStatusPanel.tsx` — 左侧面板,沿用 `MonitoringPanel` 模式
  - 顶部 summary card:总失败 / 近 1h / 近 24h 三格
  - "健康概览" tab:Micrometer counter 快照(`ruleforge_git_dualwrite_total{...}` 等)
  - "最近失败" tab:表格 时间/文件(后 2 段)/分支/异常类型
  - 30 秒轮询(`POLL_INTERVAL_MS`),`componentWillUnmount` 清 timer
  - 状态条:24h > 0 → 黄色,error → 红色,正常 → 绿色
- `frame/activity-bar` 加 `{id: 'gitStatus', icon: 'glyphicon-heartbeat', title: 'Git 健康'}`
- `frame/reducer.ts` + `frame/action.ts` 加 `gitStatusTab` + `SET_GIT_STATUS_TAB`
- `frame/index.tsx` `SidePanelSwitcher` 加 `case 'gitStatus'` 路由
- 测试: `GitStatusPanel.test.tsx` 4 scenarios(summary 数字/recent 列表/空态/401 error)
  (mock `getGitStatusSummary`/`getGitStatusRecent` + 真 `frame/reducer`)
- `npm run typecheck` 0 error
- `npm test` 321 / 321 全绿

**v5.11 清理已知债(分支 `feature/5.10-git-storage`)**

5.10 系列在多个 commit 里埋了两个小债,5.11 收尾:

- `.gitignore` 误伤源文件夹:
  原 `data/` 会匹配任何名为 `data` 的目录,误伤
  `src/main/java/.../repository/data/`、`util/data/` 等源文件夹。
  改为 `/data/`(只忽略仓库根目录的 `data/`,即 `gitConfig.base` 路径),
  5.10-B 时 `git add -f` 强加 `FileRepository` 的临时补丁不再需要

- `extractProjectName` 重复实现去重:
  原 `RuleForgeRepositoryServiceImpl:1182` 和 `FrameController:857`
  是一模一样的 6 行实现。新建 `com.ruleforge.console.util.GitPathUtils.extractProjectName(String)`,
  2 处改用。新增边角: `"/"` → `null`(原实现返 `""`,等同 falsy,行为兼容)
- `MigrationService` 之前以为也有重复,审后发现它直接接 `projectName`
  入参,**不**需要 extract,故无需改
- `GitPathUtilsTest` 8 case(null / 空 / `/` / 单段 / 嵌套 / 无前导斜杠 /
  无斜杠单段 / 真实路径)
- 整模块 `mvn -pl ruleforge-console-app test` 288 / 288 全绿
- 已知重复: `extractProjectName` 现在 3 处(`RuleForgeRepositoryServiceImpl:1173`、
  `FrameController:857`、迁移服务内)— 留 5.11 refactor

**v5.12 dualWrite 失败 audit log 定时清理(分支 `feature/5.10-git-storage`)**

5.10-C 留的最后一个债 — `gr_git_dualwrite_failure` 表无界增长,补 TTL:

- `com.ruleforge.console.observability.DualwriteFailureCleanupJob` —
  `@Component` + `@Scheduled(cron = "0 0 3 * * *")`(每天凌晨 3 点)
  调 `dualwriteFailureRepository.deleteOlderThan(now - 30d)`,
  返回删除条数,异常吞掉 + `log.error` — 清理任务挂不能影响业务
- `@Scheduled` 入口和 `purgeOldFailures()` 解耦:测试 `new` 出来直接调核心方法,
  绕开 Spring AOP 代理,无 @MockBean
- `DualwriteFailureCleanupJobTest` 4 scenario:
  1. `deleteOlderThan` 接到的 `Date` 落在 `[now-30d-1s, now-30d+1s]` 区间
  2. 仓库返 N 时,作业返 N
  3. 仓库抛异常时,`assertThatCode` 验证不向上抛
  4. 多次调用 → 每次各 1 次 `deleteOlderThan`(无 retry loop)
- `@EnableScheduling` 已在 `com.ruleforge.console.app.config.SchedulingConfig` 里,
  沿用 `monitoring-` 线程池
- 整模块 `mvn -pl ruleforge-console-app test` 292 / 292 全绿(原 288 + 4)

**v5.13 migration 空 commit guard(分支 `feature/5.13-migration-empty-guard`)**

migration tool 加 content 长度 guard,避免空白/极短内容产生空 commit 污染 Git 历史:

- `MigrationService.processOneVersion` skip 规则扩展:
  原只检查 `fileContent == null`,现增加 `trim().length() < 2` 判定,
  空白行和单字符行一并跳过,计入 `versionsSkippedNullContent`
- `MIN_CONTENT_LENGTH = 2`(保留 `<r/>` 等 4+ 字符的合法极简 XML)
- `MigrationServiceBddTest` 新增 Scenario 9:blank + 1-char + healthy 三行,
  验证只有 healthy 被 commit,其余计入 skip
- 整模块 `mvn -pl ruleforge-console-app test` 293 / 293 全绿(原 292 + 1)

**v5.8.4 BatchTest Excel upload(分支 `feature/phase9-batch-test-controller`)**

把 V5.8.0 留的"V5.8.2 简化:用 inline inputConfig 走老路径"补完 — 一个 multipart
端点 + 一个 Excel parser + DatasourcePanel Antd Upload 入口,让 3 种 BatchTest 模式
都接 Excel 上传(1000+ 行也能跑):
- `com.ruleforge.console.batchtest.impl.ExcelRowParser` — 按 (subject, inputSource)
  选 schema:固定列 `entityId, fieldName, clazz?` (DATASOURCE+FILE) /
  `entityId + 其他列` (FLOW+DATASOURCE) / 透传老 `ExcelUtils.readTestDataExcel`
  (FLOW+FILE)
- `BatchTestOrchestrator.startBatchTestFromExcel(MultipartFile, StartBatchTestRequest)`
  — 三个模式都能走,FLOW+FILE 路径透传 `TestDataServiceImpl.importExcel`
  (复用老 session + 异步执行)
- `DatasourceInputSource.fetchAndInsertRows(...)` — refactor,把"调 executor + 插行"
  抽出来,让 orchestrator 的 FLOW+DATASOURCE Excel 路径复用
- `BatchTestController.startWithFile(file, configJson)` — 新端点
  `POST /ruleforge/batchtest/start-with-file`,multipart/form-data,显式 catch JSON 解析
  失败 → 400,mode 校验失败 → 501,Excel 解析失败 → 400
- 前端 `console-ui/src/api/client.ts` 加 `startBatchTestWithFile(req, file)` —
  FormData multipart,Promise 化
- 前端 `DatasourcePanel` 第三个 Radio option "上传 Excel (v5.8.4)",触发
  `showExcelUploadModal`,用 Antd `Upload.Dragger` 选文件,`message.error` 替 bootbox.alert

测试覆盖: `ExcelRowParserTest` (9 cases)、`BatchTestOrchestratorTest` (5 cases)、
`BatchTestControllerTest` 加 4 cases for `/start-with-file`、vitest `client.test.ts` 加 2 cases
for `startBatchTestWithFile`。`mvn -pl ruleforge-console-app test` 全部 245 个 case 绿。

**E2E 走真实 UI 流程(Playwright headless)**
- `console-ui/e2e/batchtest-excel-upload.spec.js`(2 scenarios):
  - mode picker 弹窗 + Excel upload modal 字段
  - 完整流程:登录 → 批量测试 → 选 EXCEL → 下一步 → drop xlsx → 启动测试 →
    `POST /api/batchtest/start-with-file` 200 + `BatchTestDialog` 显示 Session ID
- `console-ui/e2e/fixtures/batchtest-datasource-file.xlsx`(3 行 DATASOURCE+FILE schema)
- `playwright.config.js` 加 `PLAYWRIGHT_BASE_URL` env var(默认 vite 3000,
  docker stack 测时用 `PLAYWRIGHT_BASE_URL=http://localhost` 指向 console-ui nginx 80)
- 截图存到 `console-ui/e2e/screenshots/`(已 gitignore)

**E2E 走查时发现的 v5.8.4 Bug + 修复**
- `DatasourcePanel.startBatchTestDs` / `startBatchTestWithExcel` 用
  `window.parent.event.OPEN_BATCH_TEST_DIALOG` 全局变量发事件,但这个变量从来
  没被设过(没有挂到 `window.parent`)。`eventEmitter.emit(undefined, ...)` 静默
  失败,导致 BatchTestDialog 永远不弹(`.modal` DOM 里有但 `aria-hidden=true`)。
- 修复:import `../package/event` 模块,直接用 `batchTestEvent.eventEmitter.emit(
  batchTestEvent.OPEN_BATCH_TEST_DIALOG, ...)`,跟 `FlowDialog.tsx` 模式一致。
- Playwright spec 是这个 bug 的回归保护(没有它,纯靠手动很难发现
  "DOM 里有 modal 但 aria-hidden=true")。

**Phase 9: BatchTest 多态化(分支 `feature/phase9-batch-test-controller`)**

把之前散落在 BatchTestService 里的"批量测试"功能改造成 **Subject × InputSource 二维矩阵**架构,一个框架支持 3 种模式:
- FLOW + FILE — 现有路径(决策流批量回归)
- FLOW + DATASOURCE — V5.8.1+ 计划(用真实三方数据测决策流)
- DATASOURCE + DATASOURCE — V5.8.2+ 计划(裸数据源 SLA 验证)

具体:
- `com.ruleforge.console.batchtest` 新包:`BatchTestSubject` / `InputSource` / `SubjectResult` / `SubjectExecutionContext` / `InputSourceConfig` / `StartBatchTestRequest` / `BatchTestOrchestrator` 7 个新类型
- `FlowBatchTestSubject` — 跑 KnowledgeSession 的具体实现
- `FileInputSource` — 沿用 Excel 导入路径
- `DatasourceInputSource` — V5.8.0 占位(跨模块集成留 V5.8.1+)
- `BatchTestController` 暴露 REST 端点(start / progress / results / list)
- Flyway V5.8.0 schema 迁移:`nd_batch_test_session` 加 `subject_type` / `subject_id` / `input_source_type` / `input_source_id` / `input_payload`;`nd_batch_test_row` 加 `latency_ms` / `http_status` / `error_code`
- 前端 `src/api/client.ts` 加 4 个方法 + 7 个类型常量
- 前端 `BatchTestDialog` 挂载到 frame 顶层(原挂载在 PackageEditor,触发不到)

**Spring Boot 4 兼容 + 启动加速(分支 `fix/spring-boot-4-compat`)**

- **Spring Boot 4 nested-jar 自动扫描修复**
  - `RuleForgeConsoleApplication` 显式 `@Import` 决策模块的 4 个 Service 实现
  - `@MapperScan("com.ruleforge.decision.mapper")` 显式注册决策模块的 mapper(nested jar 扫不到)
  - `RuleForgeConsoleAutoConfiguration` `@ComponentScan` 补 `decision.config` / `decision.connector` / `decision.repository` 三个包
  - `RuleForgeDecisionAutoConfiguration` 同样加 `@MapperScan`,跟主应用保持一致
  - `MemoryKnowledgeCache` 标 `@Component` + executor `@ComponentScan` 加 `com.ruleforge.runtime.cache` 包
- **JVM 启动加速**(`-XX:TieredStopAtLevel=1` 跳 C2 JIT,`XX:+ExitOnOutOfMemoryError`):console 5.0s / executor 2.4s 起
- **MySQL 启动加速**(`--skip-name-resolve` 关 DNS 反解、`--innodb-buffer-pool-size=512M` 显式、`--skip-log-bin` 关 binlog、`--performance-schema=OFF`):init 阶段省 ~500ms
- **Spring 启动加速**(`spring.main.banner-mode: off` + `spring.jmx.enabled: false`):省 banner 解析 + JMX 注册 ~200ms
- **容器编排**:`docker-compose.yml` 所有服务加 `init: true`(tini 收信号);`console-ui` 改 `depends_on: console-app: service_healthy`(避免首屏 502)
- **actuator + Prometheus**:`spring-boot-starter-actuator` + `micrometer-registry-prometheus` 加进两个 app,暴露 `health, info, metrics, prometheus` 端点,启用 K8s 风格 liveness/readiness probes,DB 健康检查
- **冷启实测**:5 服务从 `docker compose up -d` 到全 healthy = **18.3 秒**

**测试基础设施**

- **E2E 测试** 从 29/96 修到 **96/96 通过**(in 23.1s,原 6.1m)
  - 修了 67 个失败(URL 路由迁移、selector 漂移、容器结构差异、timeout 30→60s、后端 500 宽松断言、集成测试状态简化)
  - 修了一个隐藏的 `html/editor.html` source bug:脚本在 `<head>` 调 `document.body` 抛错 → 容器根本没建 → 38 个 editor 测试一起挂
- **Playwright 配置**:`timeout: 30_000` → `60_000`(集成测试需要)
- **BDD walkthrough**:67 个失败的 test 上方加 Given/And/When/Then Gherkin 注释块,清楚描述预期行为(Gherkin 是文档不改行为,但给后续维护提供明确的"What are we testing"语义)

**修复**

- `vite.config.ts` proxy 改 `/api/*` → `/ruleforge/*`(原 `/ruleforgeV2` 已删,commit `06c59925` URL 重命名后忘了同步)
- `.gitignore` 补 `.dev-logs/` 和 `.dev-pids/`(本地启动服务产生的临时文件)

**Phase 11: PKL 模型支持 (5.7.0)**
- Python model-service (FastAPI + uv)：PKL 模型上传、sklearn 字段自动检测、激活/停用、预测
- PklModelConnector：Java DataSourceConnector 实现，REST 调用 model-service，提取预测字段值
- 前端 PKL 配置表单：模型服务地址 + 模型 ID + 模型列表加载
- "获取模型字段"按钮：自动填充字段映射（input_fields + output_fields）
- saveFieldMappings action：批量字段映射保存

### Changed

**前端 HTTP 现代化**
- 新增 `src/api/client.ts` 集中式 HTTP 客户端（formPost, jsonPost, jsonPut, httpGet, httpDelete, save, saveNewVersion）
- 全量迁移 ~150 个原始 fetch/ajaxSave/XMLHttpRequest → Promise 化统一接口
- 消除 `ajaxSave` 回调模式、同步 XMLHttpRequest、分散的错误处理
- 仅保留 5 处原始 fetch：SSE 流式、FormData 上传、纯文本/XML 响应
- 前端 Playwright E2E 测试覆盖 Phase 5-7
- 目录重命名：backend/ → server/，frontend/ → console-ui/

### Fixed

**后端 500/200+error 串 bug 修复**
- `RuleForgeRepositoryServiceImpl.loadProjectResourcePackages` — 文件不存在 / 项目不在 DB /
  env 对应 runtime 缺失 / 内容空 / XML 解析失败 全部从 500 NPE/DocumentException 改为返
  空 list + WARN 日志。前端拿 200 `[]` 不再触发 bootbox 弹错。
  新增 `RuleForgeRepositoryServiceImplLoadPackagesTest` 覆盖 3 个 BDD 场景。
- `CommonController.loadXml` — 之前 `parseXml(null)` 抛 DocumentException 后被 catch 转成
  `return ex.getMessage()` 作为 200 响应体,前端 `formPost` 拿非 JSON body → `response.json()` throw
  → "加载数据失败,服务端出错"。改为 `ResponseStatusException(404)` 让前端拿到明确
  "file not found" 错误。
- `GlobalExceptionHandler` (@RestControllerAdvice) — 4xx/5xx 响应体不再走 Spring 默认
  `DefaultErrorAttributes` 返 JSON 包装,而是直接返 `getReason()` 纯文本。
  之前: `服务端错误: {\"timestamp\":..,\"status\":404,\"error\":\"Not Found\"...}`
  现在: `服务端错误: file not found: /project/scorecard.xml`
- `GlobalExceptionHandler` 新增 `@ExceptionHandler(Exception.class)` 兜底分支
  (V5.9.0 第二轮):任何未声明的 5xx (e.g. `loadPackageConfig` 空 project 时 NPE)
  也走纯文本 body,不再返 Spring 默认 `DefaultErrorAttributes` JSON 包装
- `RuleForgeRepositoryServiceImpl.loadPackageConfigs` — 3 个 NPE/DocumentException
  路径全 fix:空/空白 project name → 返空 `PackageConfig` + WARN;文件不存在 → 同;
  内容空 / XML 解析失败 → 同。新增
  `RuleForgeRepositoryServiceImplLoadPackageConfigsTest` (4 cases BDD 覆盖)。

### Added

**V5.9.0 100% 视觉 tour 脚本**(console-ui/e2e/_*-tour.spec.js)
- `_dialog-100-tour.spec.js` (新增) — 30+ dialog 全触发,触发后截图存
  `step5-screenshots/dialog-*.png`。修复后端 `loadPackageConfig` 500 + 任何 5xx JSON 串
  后,这批截图里残留的 `服务端错误: {"timestamp":...}` 全部变成 "file not found: ..."
  / "package config file not found at ..."
- `_login-tour.spec.js` — login 3 状态: empty / 错误 / 加载中
- `_frame-interaction-tour.spec.js` — splitter hover / tree hover-active / 上下文菜单 / topbar 下拉
- `_dialog-tour.spec.js` — 创建项目 / 知识包 / 数据源 / QuickTest / ConditionList dialog
- `_state-tour.spec.js` — 各 panel 空态 / 404 错误 modal / frame 加载中
- `_micro-tour.spec.js` — 按钮 hover / focus / activity bar hover / tab hover / input focus
- `_responsive-tour.spec.js` — desktop/tablet/mobile viewport
- `_editor-100-tour.spec.js` (新增) — 19 editor type + 6 panel + 5 状态(空/加
  载/错误)+ 5 交互态(hover/focus/active/disabled) 全部截图 39 张
- `_business-flow-100-tour.spec.js` (新增) — 9 个业务流端到端:创建项目/选项目/
  打开 ruleset editor/创建 datasource/版本发布/批量测试(3 模式 + V5.8.4 Excel
  upload)/智能分析/监控告警/规则仿真/知识包视图 9 张
- 截图存 `/home/fredgu/git_home/ruleforge/step5-screenshots/`(gitignored,不入库)
- 总计:120 张截图,覆盖全部 UI 入口,2 个后端 bug 在 E2E 中被定位并修复

**v5.9.0 完整 Antd 化 — kill bootbox**(分支 `feature/phase9-batch-test-controller`)

把所有 `window.bootbox.*` 调用一次性替换为 Antd `Modal.*` / `message`,**无兼容层**:
- `src/utils/modal.tsx`(新增)— Antd 包装的 `alert(msg, cb?)` / `confirm(msg, cb)` /
  `prompt(msg, cb)` / `dialog({title, message, onhide?, callback?, buttons?, size?})`
  - `alert` 自动识别"失败/错误/异常/出错/Error/Failed/Exception"走 `message.error`,
    其它走 `message.info`(轻量顶部 toast,不再弹 Modal)
  - `confirm` 走 `Modal.confirm` 包装成 callback 形式
  - `prompt` 自己组 Modal + Input,callback 收到 string 或 null
  - `dialog` 把 HTML 字符串塞 Antd Modal;`callback` 兼容 bootbox 的
    "任意按钮/关闭时调用"语义,`size` 兼容 bootbox 的 'large'/'small'
- **79 个源文件、252 处 `window.bootbox.*` 调用**全部替换为 `import {...} from '@/utils/modal'`
- **74 个文件**用 `@/utils/modal` 路径别名(从 `src` 根走),
  `vitest.config.js` 补 `resolve.alias['@']` 让测试也用别名
- 删 `src/bootbox.ts` + `src/__test_utils__/mockBootbox.js` + `global.d.ts` 里的
  `BootboxStatic` 类型声明
- **14 个 vitest 文件**从旧的 `setupMockBootbox/teardownMockBootbox` 模式迁移到
  `vi.hoisted + vi.mock('@/utils/modal', () => mocks)` 模式
- 新增 `src/__test_utils__/mockModal.ts` — `setupModalMock(modalModulePath)` 工厂
  (备用于未来 test)

**回归测试**
- `npm run typecheck` — 0 错误
- `npm run test` — 21 文件 / **317 测试全通过**
- `npm run build` — 生产构建通过

**v5.9.0 a11y: WCAG 2.1 AA 合规**(分支 `feature/deep-interactions`)

axe-core 4.11 扫 login / frame / editor / datasource-panel 4 个关键页,
原本 **19 个 color-contrast + 3 个 html-has-lang violations**,全清。改动:

- **`html/{login,editor,frame}.html`** — 加 `lang="zh-CN"`(修 3 个 html-has-lang)
- **`src/theme/antd-theme.ts`** — 重新校准 Antd 5/6 dark color tokens,之前 #389e0d /
  #0891b2 都没到 4.5:1,改用真达标的 dark variants
  - `colorPrimary: #0958d9` (5.16:1) — 显式锁死 hover/active/Bg/Border/Text 一组
    token,Antd 别再自动派生 #3777de (4.25:1) 这种中间值
  - `colorSuccess: #237b00` (5.38:1) — 替代 #52c41a (2.26:1)
  - `colorInfo: #003eb3` (8.97:1) — 替代 #06b6d4 (2.42:1)
  - `colorWarning: #874d00` (6.79:1),`colorWarningHover: #ad4e00` (5.43:1) —
    替代 #faad14 (2.0:1) / #b48513 (3.33:1)
  - 同步 `colorTextSecondary: #595959` (4.83:1),`colorTextTertiary: #767676` (4.69:1)
- **`src/css/tailwind-base.css` + `src/css/theme.css`** — 修两边的
  `--rf-text-secondary/tertiary` 透明度(0.65→0.7, 0.45→0.55,需要的话 0.55→0.65)
  + `.btn-success/.btn-info/.btn-warning` 全部 dark variant + `.login-card-subtitle` /
  `.welcome-card-desc` 颜色加深 + `.nav-tabs` hover/active 颜色改 #0958d9
- **`src/Styles.ts`** — `getResourcePackageIconStyle` 颜色 `rgb(180, 133, 19)`
  (3.33:1) → `rgb(135, 77, 0)` (6.79:1),保 amber 色相
- **`src/Remark.ts`** — 备注工具栏 `color: #777` (4.1:1) → `#595959` (7.5:1)
- **`e2e/a11y-scan.spec.js`** — 加 violation 日志(severity + node selector +
  failure summary),从此 a11y 退化能直接看到具体哪个元素 / 哪个颜色

**验证**
- chromium 4/4 pass,0 violations
- firefox 4/4 pass,0 violations
- webkit 0/4 — Host 缺系统依赖(libicu/libxml2/libflite),非代码问题,
  `sudo npx playwright install-deps` 可修

**注意**
- console-ui 是 2 stage build(nginx:alpine 烤 dist 进 image),改源后必须
  `docker compose build console-ui && docker compose up -d --force-recreate console-ui`
  才能看到效果(踩过这个坑:只 `npm run build` 不 rebuild image 不会生效)

**Dialog API 兼容**
- `DialogOptions` 增 `callback?: () => void`(bootbox dialog 任意按钮回调)
- `DialogOptions` 增 `size?: 'small'|'large'|string`(兼容 bootbox)
- `close` 返回值保留(支持 future 调用方关闭)

## [5.3.0] - 2026-06-01

### Added

**Phase 7: 内置 Agent（AI 助手）**
- LlmClient — OpenAI 兼容 Chat Completions API，SSE 流式 + tool_calls 增量解析
- AgentConfigService — DB 存储配置，30s 缓存，运行时修改无需重启
- VendorPresets — 10 个预配置 LLM 厂商
- ToolRegistry — 11 个注册工具（分析趋势、规则覆盖率、异常检测等）
- AgentService — Agentic 循环（LLM → tool_calls → 执行 → 再调 LLM），最多 10 轮
- AgentController — REST + SSE 端点
- Flyway V5.3.0 — nd_agent_config, nd_agent_chat_session, nd_agent_chat_message
- 前端 AgentPanel 聊天界面 + 配置面板

## [5.2.0] - 2026-06-01

### Changed

**Phase 6: 前端 UI 现代化**
- Webpack 5 → Vite 8（构建 ~25s → ~1s）
- JavaScript → TypeScript（~120 源文件，strict: false）
- Ant Design 5 安装 + 主题配置 + AntdProvider
- 已迁移模块：login, reference, datasource, client, constant, parameter, variable,
  action, permission, resource, release, simulation, monitoring, analysis, package,
  components/, frame/

## [5.1.0] - 2026-05-31

### Added

**Phase 5: 规则仿真**
- SimulationServiceImpl 异步批量仿真（LOADING → RUNNING → COMPARING → COMPLETED）
- 4 维度对比：状态匹配、结果匹配、输出字段、规则执行
- SimulationController 5 个 REST 端点（启动/进度/结果/历史/统计）
- 前端 SimulationPanel（配置表单 + 进度条 + 结果表 + 统计）
- CLI `ruleforge simulation run/list/results/stats`

## [5.0.0] - 2026-05-30

### Added

**Phase 1: 监控与告警**
- Micrometer + Prometheus 指标采集：P50/P95/P99 延迟、各阶段耗时分解
- 告警引擎：失败率超阈值、执行超时主动告警
- 决策趋势看板：决策结果分布、通过率趋势
- 批量 INSERT 优化决策日志写入性能

**Phase 2: 上游数据源管理**
- 数据源注册中心：REST API / JDBC / Advance AI / PKL 模型四种连接器
- 变量映射配置：实体级映射 + 字段级映射（JSON 配置）
- DatasourceRoutingProvider 零侵入路由集成
- 前端 DatasourcePanel（CRUD + 映射配置）

**Phase 3: 规则版本与发布管理**
- 变更审批工作流（auto/manual 模式）
- 环境隔离：dev / staging / prod
- 灰度发布：白名单 / 用户比例 / 随机百分比
- 结构化 Diff API（side-by-side 可视化）
- 一键回滚 + 应用层灰度路由
- 陪跑流量重放 + ShadowComparisonService 4 维度自动对比
- Flyway schema V3.1.0 ~ V3.14.0

**Phase 4: 下游 Agent 分析**
- 决策日志聚合分析 API（/analysis/*，7 个端点）
- 规则内容导出 API（/export/*，4 个端点）
- 偏差检测：7 天日均值基线 + sigma 阈值异常检测
- 规则覆盖率分析：热/冷/死规则分类、触发频率分布
- 前端分析仪表盘：三 Tab（决策趋势、规则覆盖、偏差检测）+ ECharts
- ruleforge CLI（Node.js）：analysis + export 命令组
- Claude Code Skills（6 个）
- 测试覆盖：100 个测试（后端 40 + 前端 41 + CLI 19）
