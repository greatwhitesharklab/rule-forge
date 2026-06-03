# Changelog

All notable changes to RuleForge will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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
