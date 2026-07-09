# 彻底删除 BPMN 决策流,改造为 V1 单一决策路径

## 总目标
删除老 BPMN 决策流(前端 flow-bpmn + 后端 ruleforge-decision 模块 + 陪跑/灰度全链路),让 V1 决策流成为唯一决策路径。项目从未上生产,无数据迁移负担。

## 改造前依赖链
```
core ← decision(BPMN引擎+共享层) ← {console-app, executor-app}
```
## 改造后依赖链
```
core ← datasource(数据源+变量定义) ← {console-app, executor-app}
core ← {console-app, executor-app}  (app 直接依赖 core,不再传递)
```

---

## 阶段 0:前端彻底删除(零后端依赖,可独立先合)

**删整目录**:`console-ui/src/flow-bpmn/`(14 文件,BPMN 编辑器,自包含叶子模块,零被复用)

**删入口和引用**(9 文件 ~20 处改动):
- `src/main.tsx:28,60` — 删 FlowEditorRoute import + Route
- `src/components/tree/component/treeDataUtils.tsx:167-171` — 删 .rl.xml 点击分支 + :23 CONTAINER_TYPES 删 flowLib
- `src/components/tree/component/TreeItem.tsx:228-236` — 删 isFlow 分支
- `src/frame/action.ts` — 删 4 处:FILE_TYPE_MAP 的 'rl.xml'(:448)、buildType 的 case 'rl.xml'(:476-478)、case "flowLib" 整块(:627-646 含"添加决策流"菜单)、case "flow"(:689-693)、通用菜单 flowLib 分支(:1004-1016)、文件夹类型判断(:830)
- `src/types/tree.ts:44,50` — TreeNodeType 删 'flowLib'、'flow'
- `src/Utils.ts:76,116` — 两张映射表删 flow / .rl.xml 条目
- `src/components/componentAction.ts:197-200` — 删老 case "flow"

**删测试**:`treeDataUtils.test.tsx:77`(flow 用例行)、`action.test.ts:160-161`(rl.xml 用例)、`FileTree.test.tsx:101,109`(flowLib 契约用例同步改)

**不删(防误删)**:`src/Styles.ts` 的 getFlowIcon 等(被 V1 复用)、`src/v1-flow/`(新主线)、`KnowledgeTreeDialog`/`QuickTestDialog`(成孤儿但属另一清理范围,本次保留)

**验证**:typecheck + 单测 + e2e 全页面扫描(确认 .rl.xml 入口消失、其他页面无回归)

---

## 阶段 1:新建 ruleforge-datasource 模块 + 迁移共享层

**新建** `server/lib/ruleforge-datasource/`(pom 继承 parent,依赖 core + 复制 decision 的 mybatis-plus/jdbc/HikariCP/fastjson2 等 ORM 依赖)

**迁移 28 个文件**(从 ruleforge-decision 搬到 ruleforge-datasource,包名 `com.ruleforge.decision.*` → `com.ruleforge.datasource.*`):
- entity(5):Datasource, DatasourceLog, DatasourceEntityMapping, DatasourceFieldMapping, RuleVariableDef
- mapper(5):对应 5 个 BaseMapper
- repository(2):DatasourceRepository, DatasourceRepositoryImpl
- service(4):IDatasourceService, IRuleVariableDefService + 2 impl
- connector(7):DataSourceConnector SPI + 6 实现(REST/JDBC/AI-Java/AdvanceAI/PKL + TokenManager)
- jcompiler(3):IJavaDataSource, ClassLoaderPool, JavaSourceCompiler(原 datasource 子包改名)
- util(1):ComparisonUtils(console 仿真对比在用,非陪跑专用)
- config(1):InsertBatchSqlInjector + 新建精简版 RuleForgeDatasourceAutoConfiguration(只扫 service.impl/connector/mapper,去掉 flow 包扫描)

**改引用**(console-app + executor-app 的消费方,import 改包前缀):
- console:`DatasourceController`、`AiJavaDataSourceService`(含 :56,58 字符串 "com.ruleforge.decision.datasource.IJavaDataSource" 改包名)、`ExternalRepositoryImpl`、`SimulationServiceImpl`
- executor:`DatasourceRoutingProvider`(lazy 层 app-local,留 app,只改 import)

**验证**:`mvn compile -pl lib/ruleforge-datasource -am` + 两个 app 编译通过

---

## 阶段 2:净删 BPMN 引擎 + 陪跑/灰度全链路(后端)

**改模块拓扑**(3 个 pom):
- `server/pom.xml:15` — 删 `<module>lib/ruleforge-decision</module>`,加 `<module>lib/ruleforge-datasource</module>`
- `console-app/pom.xml:54-62` — 删 ruleforge-decision 依赖,加 ruleforge-core + ruleforge-datasource 直接依赖
- `executor-app/pom.xml:68-74` — 同上

**删整个 ruleforge-decision 模块**(`server/lib/ruleforge-decision/` 整个目录,含 109 main + 49 test 文件):
- BPMN 引擎 flow/* 子包(61 文件 7150 LOC):engine/executor/parser/ir/state/bus
- 陪跑/灰度专用(20 文件):entity{DecisionFlowState,ShadowConfig,ShadowComparison,GrayStrategy} + mapper 4 + service 5 + exception 3 + dto 3 + config/RuleForgeDecisionAutoConfiguration

**删 executor-app 的 BPMN/陪跑/灰度 app-local 类**:
- `DecisionServiceImpl` + `IDecisionService`(主决策走老引擎,整删——V1 已有自己的 V1ExecutionController)
- `FlowDecisionController`(USER_TASK 回调端点)、`FlowDefinitionInvalidateController`(flow 缓存失效)
- `ShadowExecutionServiceImpl` + `IShadowExecutionService`、`ShadowComparisonServiceImpl` + `IShadowComparisonService`、`ShadowComparisonController`
- `ShadowDecisionLogServiceImpl` + `IShadowDecisionLogService`
- `DecisionLogServiceImpl` + `IDecisionLogService`(BPMN 决策日志,V1 不落库,整删)
- `DecisionController`(/decision/evaluate 端点,绑 DecisionServiceImpl)、`SimulationLogController`(BPMN 日志查询)
- entity/mapper:DecisionFlowLog 系列 5 个、ShadowFlowLog 系列 5 个、clickhouse mapper、DecisionRequest/Response DTO、OutputModel/BaseModel
- `ClickHouseAnalyticsWriter` + `ClickHouseAnalyticsEvent`(BPMN 日志写 ClickHouse)
- `AsyncDataSourcePendingException`(仅 DecisionServiceImpl 用,V1 不做异步数据源)
- lazy 层保留(DatasourceRoutingProvider/DataSourceProvider 等留,只改 import)

**删 console-app 的 BPMN/陪跑/灰度类**:
- `flow/controller/BpmnFlowController` + `flow/converter/FlowXmlConverter`(console 侧 BPMN)
- `controller/GrayStrategyController`、`controller/ShadowConfigController`、`controller/ShadowComparisonProxyController`
- `controller/TestController` 的 doTest flow 分支(或整类,看是否还有非 flow 用途)
- `service/impl/TestServiceImpl` 的 flow 路径

**改装配注解**:
- `RuleForgeConsoleApplication.java:8-12,34-42` — @Import 删 RuleForgeDecisionAutoConfiguration + 陪跑/灰度 ServiceImpl;加 RuleForgeDatasourceAutoConfiguration
- `RuleForgeConsoleAutoConfiguration.java:47-49` — @ComponentScan 删 3 个 decision 包,加 datasource 包
- `RuleForgeExecutorApplication.java:9` — @ComponentScan 删 "com.ruleforge.decision"(app-local decision 类已删完)
- `RuleForgeProdConsoleMybatisPlusConfig.java:37`、`DecisionMybatisPlusConfig.java`、executor `MybatisPlusConfig.java:36,87` — MapperScan/字符串改 com.ruleforge.datasource.mapper(或删灰度 mapper 扫描)

**删 Flyway 迁移**(console-app resources/db/migration-app/):rfa_decision_flow_state、rfa_decision_shadow_*、shadow_comparison 相关 SQL。**注意**:这些若已在 dev DB 执行过,删除会让 Flyway checksum 失败——需写 forward 迁移(DROP TABLE)而非直接删文件。dev 环境可重建库,生产保留迁移文件不删。

**验证**:`mvn clean compile`(全模块)+ 两个 app `mvn package -DskipTests` + 启动 console/executor + Playwright 跑 V1 决策流完整流程

---

## 阶段 3:文档与清理收尾

- `CLAUDE.md` — 更新模块边界章节(5 模块 → core/datasource/console/executor 4 模块,删 decision 段、表归属速查删 flow/shadow/gray 行)
- `docs/roadmap.md` — 记录 V7.21 "彻底删除 BPMN,V1 单一决策路径"里程碑
- `CHANGELOG.md` — 补 Unreleased 段
- `docs-site/architecture/decision-flow.md` — 重写(当前还讲 Flowable 8,严重脱节;改为 V1 决策流架构)
- `README.md` — 技术栈表删 BPMN 2.0 自建引擎行、改"双推理内核"表述
- 清理 npm 依赖:bpmn-js/diagram-js/min-dash/tiny-svg(确认 package.json/lockfile)
- `console-ui/src/components/dialog/` 的 KnowledgeTreeDialog/QuickTestDialog(阶段 0 后成孤儿,顺手删)

---

## 执行顺序与风险控制

**严格按阶段 0 → 1 → 2 → 3**,每阶段独立分支 + commit + 验证通过再进下一阶段:
1. 阶段 0(前端)零后端依赖,风险最低,先合
2. 阶段 1(新模块)只加不改,迁移共享层,两 app 编译通过即可——此时 decision 模块还在,新旧并存
3. 阶段 2(删 BPMN)是高风险核心,改模块拓扑 + 删大量类 + 改装配——必须全量编译 + 启动验证 + V1 流程跑通
4. 阶段 3(文档)收尾,纯文档无风险

**最大风险点**:阶段 2 的装配注解改错会导致 Spring 启动失败(Bean 找不到/Mapper 不注册)。缓解:每改一处装配就 `mvn package` + 启动验证,不攒着一起测。

**不做的事**(明确排除):
- 不补 V1 的决策日志/监控/灰度/陪跑(你确认从未上生产 + V1 设计上砍掉这些;若以后需要是新 feature,不在本次"删除"范围)
- 不迁移历史 .rl.xml 资产(无生产数据)
- 不动 V1 的执行/发布代码(它已就绪,本次只让它成为唯一路径)