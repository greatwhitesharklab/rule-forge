# 端到端测试报告 — 2026-06-14

## 测试方式
通过 Playwright MCP 交互式操作浏览器(非录制脚本),模拟真实用户从登录到各功能模块的完整流程。

## 测试环境
- 前端:`http://localhost:5173`(vite dev,console-ui)
- console-app:`8180`,executor-app:`8280`(本地 java 进程,LAN 启动)
- DB:dev `192.168.3.36`
- 账号:`admin / admin123`
- 测试项目:新建 `e2e-test`

## 功能覆盖矩阵

| 功能 | 结果 | 说明 |
|---|---|---|
| 登录(`/frame/login`) | ✅ 通过 | admin/admin123,跳转 frame.html |
| 根路径 `/` 访问 | ❌ **404** | MPA 无根 index.html → B-2 |
| 创建项目 | ✅ 通过 | `e2e-test` 创建成功,文件树正常 |
| 创建规则文件 | ✅ 通过 | rs-demo.rs.xml 创建成功,文件树展示 |
| 打开规则编辑器 | ❌ **空白** | iframe 路径错误 → **B-0(阻断)** |
| 数据源 panel | ✅ 通过 | 表格加载(空,新项目无数据源) |
| 版本发布 panel | ✅ 通过 | 6 子功能 UI 完整 |
| 监控告警 panel | ✅ 通过 | 无报错 |
| 规则仿真 panel | ✅ 通过 | 4 tab + 配置区 + 启动仿真 |
| 智能分析 panel | ✅ 通过 | 无报错 |
| Git 健康 panel | ❌ **API 404** | observability 端点缺失 → B-5 |
| 用户管理 panel | ✅ 通过 | 用户表格正常 |
| 审计日志 panel | ✅ 通过 | 审计表格正常(V5.17) |
| 系统设置 panel | ✅ 通过 | 无报错 |
| 待审通知铃铛 | ❌ **轮询 404** | list_drafts 持续轮询 → B-1/B-6 |

## 发现的问题(详见 [frontend-optimization.md](./frontend-optimization.md))

**阻断级(必修)**:
- **B-0**:dev 环境所有规则编辑器打开后空白(iframe 路径双 `html/` + 查询参数双 `?`)。影响 14 种编辑器类型。**核心编辑功能不可用**。

**功能级 bug**:
- **B-1/B-6**:`/api/agent/tools/list_drafts` 404 + 高频轮询(几分钟 39 次)
- **B-5**:`/api/ruleforge/git/observability/{summary,recent}` 404,Git 健康 panel 不可用

**架构问题**:
- MPA 3 页结构(login/frame/editor)→ 应改 SPA(详见优化文档第一节)
- 7 处 iframe → 可全部取消(详见优化文档)
- 根路径 `/` 404

## 结论
- 登录、项目/文件 CRUD、各管理面板(数据源/发布/监控/仿真/分析/用户/审计/设置)功能正常。
- **规则编辑器(B-0)是阻断性 bug**,导致规则编辑/仿真执行/发布等核心决策流程在 dev 环境无法走通。
- 控制台错误集中于 B-0/B-1 两个模式,代码质量整体可控。
- 优化方向已记录于 `frontend-optimization.md`,按 P0(修 B-0)→ 架构(SPA/iframe)顺序执行。
