# 前端优化清单与改造方案

> 触发背景:2026-06-14 通过 Playwright MCP 对 console-ui 做端到端测试(登录 → 创建项目 → 进入规则编辑)时,
> 结合用户对 "React 项目根路径 `/` 不能访问、为什么需要多个 html" 的架构质疑,盘点出的前端优化项。
> 本文档先记录清单与方案,执行按优先级分批落地。

---

## 一、核心架构问题:MPA 多页结构 → 应改为 SPA

> 📋 **可执行落地计划见 [spa-migration-plan.md](./spa-migration-plan.md)**(6 阶段 / 4.5d / 每阶段独立 PR + 可回滚)

### 现状

`console-ui` 是 **3 页 MPA(Multi-Page Application)**,每个页面是独立的 React 入口:

| 入口 HTML | React 挂载点 | 入口脚本 | 作用 |
|---|---|---|---|
| `html/login.html` | `#root` | `src/login/index.tsx` | 登录页 |
| `html/frame.html` | `#container` | `src/frame/index.tsx` | 主框架壳(菜单 + 文件树 + 内容区) |
| `html/editor.html` | 动态注入容器 | `src/editor/bootstrap.tsx` | 规则编辑器(按 `?type=` 参数化) |

### 证据

1. **`vite.config.ts`** `build.rollupOptions.input` 注册 3 个独立入口:
   ```js
   input: { frame: r('./html/frame.html'), login: r('./html/login.html'), editor: r('./html/editor.html') }
   ```
   根目录 `console-ui/` 下**没有 `index.html`**,所以 dev server 对 `/` 返回 404,必须访问 `/html/login.html`。

2. **`html/frame.html`** 用**同步 XHR** 做鉴权,失败硬跳 login:
   ```js
   xhr.open('POST', '../api/frame/currentUser', false); // 同步,阻塞渲染
   if (!result.status) window.location.href = 'login.html?redirect=...';
   ```

3. **`html/editor.html`** 是一个"参数化"页面,用一坨 `switch-case` **按 `?type=` 注入不同 DOM 结构**(toolbarContainer/container/dialogContainer 等组合),而非 React 组件声明:
   ```js
   case 'ruleset': dom = '<div id="toolbarContainer"></div><div id="container">...'; 
   case 'scorecard': dom = '<div id="toolbarContainer"></div><div id="tableContainer">...';
   // 共 8+ 种编辑器类型,各自不同的容器组合 + body 样式
   ```

4. **页面间跳转全部是多页硬跳**:
   - 打开编辑器:`window.open('/html/editor.html?type=ruleset', '_blank')`(`frame/action.ts:473`)— 新标签页
   - 退出登录:`window.location.href = 'html/login.html'`(`TopBar.tsx:42`、`SidebarToolbar.tsx:154`)
   - 各页独立加载完整 React bundle,状态只靠 session cookie 共享

### 为什么"一个 index.html 就够了"——成立

用户的判断完全正确。这是技术债,不是架构必需。现代 React SPA 做法:

```
/                  → 重定向到 /login
/login             → 登录页
/app               → frame 主框架(layout: 顶栏 + 侧栏 + Outlet)
  /app/editor/:type → 编辑器(按 :type 路由到对应编辑器组件)
  /app/release      → 版本发布
  /app/simulation   → 规则仿真
  /app/monitoring   → 监控告警
  ... (左侧 10 个菜单各一个子路由)
```

收益:
- ✅ 根路径 `/` 可访问(不再 404)
- ✅ 单 bundle + React.lazy 代码分割,首屏按需加载
- ✅ 去掉手写同步 XHR 鉴权 → 改 `<RequireAuth>` 路由守卫
- ✅ editor 的 `switch-case DOM 注入` → 纯 JSX 组件(容器结构在组件里声明,TS 可校验)
- ✅ 跨页状态共享(项目切换、当前用户)走 React Context,不再依赖全局变量/cookie
- ✅ E2E 测试与路由统一(单页,playwright 不用跨标签页)

### iframe 的两类用法与取消方案

全局共 **7 处 iframe**,分两类,**全部可取消**:

**A 类 — 内容容器(4 处,MPA 副作用,取消它是 SPA 改造的核心收益)**

| 位置 | 用途 |
|---|---|
| `components/frametab/component/FrameTab.tsx`(2 处) | 内容标签页用 iframe 加载 `editor.html?type=xxx` |
| `components/frametab/component/IFrame.tsx`(1 处) | iframe 封装组件 |
| `package/components/ReteDiagramDialog.tsx`(1 处) | RETE 图对话框 |

→ SPA 改造后用 React 组件 / react-router 嵌套路由替代,主框架直接渲染编辑器组件,不再开 iframe。

**B 类 — 隐藏 hack(3 处,老式文件上传/下载技巧)**

| 位置 | 用途 | 现代替代 |
|---|---|---|
| `frame/components/ImportProjectDialog.tsx` | hiddenFrame 表单上传项目 | `FormData + fetch` |
| `variable/components/ImportXmlDialog.tsx` | 隐藏 iframe 接收 XML 导入 form | `FormData + fetch` |
| `editor/crosstab/ExcelImportDialog.ts` | `createElement('iframe')` Excel 导入 | `FormData + fetch` |

**附带收益**:消除 `feedback_iframe_layout` memory 记录的一连串坑(iframe 高度链、ref 时序、event 数据覆盖、React 重复 import)。

### 工作量与风险

- **工作量:中等偏大**(预计 2-3 天纯前端)
- **风险点**:
  1. editor.html 的 DOM 注入逻辑被 `bootstrap.tsx` 依赖(8+ 种编辑器各自的容器 id),改组件化时要逐一确认容器挂载顺序
  2. `window.open(_blank)` 新标签页语义 → SPA 内路由,需确认"多编辑器并排打开"的产品预期
  3. 鉴权从同步 XHR 改异步守卫,首屏可能有短暂的"未登录闪烁",需要 loading 态
  4. E2E 测试(`console-ui/e2e/`)大量基于 `editor.html?type=` 路径,需同步改造

- **建议:分阶段迁移**(见第三节执行计划),不一次性全改。

---

## 二、其他优化项

### B-0 [阻断 BUG] dev 环境所有规则编辑器打开后空白 ⚠️ 最高优先级

- **现象**:E2E 测试中点击任意规则文件(决策集/决策表/评分卡/决策流…),内容区 iframe 加载到的 URL 为:
  ```
  http://localhost:5173/html/html/editor.html?type=ruleset?file=/e2e-test/rs-demo.rs.xml
  ```
  iframe `readyState=complete` 但 `title=""`、`getElementById('container')=null` —— **编辑器完全空白**,无法编辑任何规则。
- **根源**(两个独立错误叠加):
  1. **路径双重 `html/`**:`frame/action.ts` 定义 `data.editorPath = "/html/editor.html?type=ruleset"`;打开处(`components/tree/component/TreeItem.tsx:151`、`reference/ReferenceDialog.tsx:311`、`frame/components/VersionListDialog.tsx:123`)统一用 `'.' + editorPath + ...` 拼接,得到 `./html/editor.html?...`。iframe 嵌在 `/html/frame.html` 内,相对路径 `./html/editor.html` 相对 `/html/` 目录解析为 `/html/html/editor.html`(双 html)。
  2. **查询参数双 `?`**:editorPath 已含 `?type=ruleset`,但 file 参数仍用 `"?file="` 拼接(非 `"&file="`),产生 `?type=ruleset?file=...`。`editor.html` 里 `window._editorType = new URLSearchParams(...).get('type')` 取到 `"ruleset?file=/e2e-test/rs-demo.rs.xml"`,switch-case 匹配不到任何分支 → DOM 不注入 → 空白。
- **影响范围**:`action.ts` 中所有 14 种 editorPath(ruleset/decisiontable/scriptdecisiontable/decisiontree/flowbpmn/scorecard/complexscorecard/crosstab/ul/package/action/parameter/constant/variable)走同一拼接逻辑,**全部受影响**。dev 环境核心编辑功能完全不可用。
- **修复方向**(两个都要改):
  1. editorPath 统一为相对 `frame.html` 同级的路径,即 `editor.html?type=xxx`(去掉前导 `/html/`),或拼接处不再加 `.` 前缀。
  2. file 参数拼接用 `URLSearchParams` 或判断 editorPath 是否已含 `?` 决定用 `&`。
- **优先级**:**P0(阻断)**。SPA 改造后编辑器变路由组件,此 bug 自然消失;但作为独立 bug 需在 SPA 落地前先修,否则任何中间状态都不可用。

### B-1 [BUG] `/api/agent/tools/list_drafts` 持续 404

- **现象**:E2E 测试中 console 累积 **7 个相同 404 错误**,全部是 `GET /api/agent/tools/list_drafts`。
- **来源**:顶栏"待审通知"铃铛(🔔)组件在轮询一个后端不存在的端点。
- **关联**:V5.22 AI 规则编写助手(`rf_draft` 表相关),端点可能未实现或路径变更后前端没同步。
- **影响**:无用网络请求 + 控制台噪音;若铃铛是产品功能,则该功能实际不可用。
- **方案**:后端确认端点是否存在 → 要么实现,要么前端去掉轮询/隐藏铃铛。
- **优先级**:高(明显 bug,且影响"待审通知"功能判断)

### B-2 [UX] 根路径 `/` 404

- **现象**:`curl http://localhost:5173/` → 404;用户必须知道 `/html/login.html`。
- **根因**:MPA 副作用(无根 index.html)。
- **方案**:随 MPA→SPA 一并解决(SPA 下 `/` 重定向 /login)。过渡期可在 `console-ui/` 加 `index.html` 做 meta refresh 跳转。
- **优先级**:中(随架构改造解决)

### B-3 [代码质量] Bootstrap 3 + Ant Design 双样式栈混用

- **现象**:`package.json` 同时依赖 `bootstrap@3.4.1`、`bootstrapvalidator`、`font-awesome@4.6.1`(老 jQuery 时代栈)和 `antd@6.4.3`、`tailwindcss`。
- **影响**:样式系统分裂,主题难统一,bundle 偏大。
- **方案**:editor 模块仍依赖 Bootstrap 3 的 DOM 结构(表格/工具栏),完整剥离工作量大;可分两步:① 新功能(登录/主框架/管理面板)全用 AntD+Tailwind,不再引入 Bootstrap;② 老编辑器逐步迁移。
- **优先级**:低(长期债,不阻塞)

### B-4 [代码质量] 全局变量传配置

- **现象**:三个 html 都靠 `window._server = "../api"`、`window._editorType`、`window.__currentUser` 全局变量传递运行时配置。
- **方案**:改用 `import.meta.env.VITE_API_BASE`(Vite 标准)+ React Context。随 SPA 改造一并处理。
- **优先级**:低(随架构改造解决)

---

### B-5 [BUG] Git 健康模块 observability API 404

- **现象**:点「Git 健康」panel,console 报 2 个 404:
  - `/api/ruleforge/git/observability/summary`
  - `/api/ruleforge/git/observability/recent?limit=50`
- **影响**:Git 健康 panel 无法加载健康摘要 / 最近提交记录,功能实际不可用。
- **根源**:后端缺 observability controller,或路径变更后前端没同步(同 B-1 模式:前端调了后端未实现的端点)。
- **优先级**:中(功能模块不可用,但不阻断主流程)。

### B-6 [BUG] Agent list_drafts 轮询频率过高

- **现象**:B-1 的轮询在几分钟内累积 **39 次** 404(平均 ~10s 一次),持续刷控制台 + 网络请求。
- **修复**:除了补端点(B-1),即使端点存在也应降低轮询频率(如 60s)+ 失败后退避(连续失败停止轮询)。
- **优先级**:中。

## 三、推荐执行顺序(分阶段)

| 阶段 | 内容 | 优先级 | 风险 | 预估 |
|---|---|---|---|---|
| **P0** | 修 B-1:agent list_drafts 404(后端补端点 or 前端摘轮询) | 高 | 低 | 0.5d |
| **P1** | 引入 react-router-dom,新建根 `index.html`,合并 login + frame 为 SPA 路由;`/` 重定向解决 B-2 | 高 | 中 | 1d |
| **P2** | editor.html 参数化页面 → SPA 子路由,B-4 全局变量改 import.meta.env | 高 | 高(8+ 编辑器) | 1.5d |
| **P3** | 同步改造 `console-ui/e2e/` 测试到新路由 | 中 | 低 | 0.5d |
| **P4** | B-3 样式栈长期收敛(新功能不再引入 Bootstrap) | 低 | 低 | 持续 |

**硬约束**:每阶段独立分支 + PR(遵循 CLAUDE.md 版本约定,如 `feature/V5.x-spa-migration`),全量 E2E 回归通过再合。
