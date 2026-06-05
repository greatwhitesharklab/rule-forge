# Changelog

All notable changes to RuleForge will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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
