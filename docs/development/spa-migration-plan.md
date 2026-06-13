# SPA 迁移实施计划(MPA → 单页 React)

> 这是 [frontend-optimization.md](./frontend-optimization.md) 第一节"核心架构问题"的可执行落地计划。
> 因涉及正在运行的 dev 环境 + 8+ 编辑器 + E2E 测试,需分阶段、每阶段可回滚、完整回归后再合。
> **不在会话中贸然一把梭** —— 每阶段独立分支 + PR + 全量 E2E。

## 当前状态盘点(实施前必读)

- 3 个独立 html 入口:`html/login.html`、`html/frame.html`、`html/editor.html`
- `vite.config.ts` `rollupOptions.input` 注册 3 入口
- frame 渲染逻辑在 `src/frame/index.tsx:109-149` 的 `DOMContentLoaded` 回调内(createRoot + Redux Provider + 副作用)
- 全局依赖:`window.__currentUser`(5 处)、`window.location.href` 跳转(4 处)、同步 XHR 鉴权(`frame.html:14-28`)
- editor 是参数化页面(`editor.html?type=xxx`),按 type 注入不同 DOM(`html/editor.html` 的 switch-case)
- FrameTab 用 iframe 加载 editor.html(`FrameTab.tsx:186`)
- E2E 测试(`console-ui/e2e/`)大量基于 `editor.html?type=` 路径

## 阶段划分

### 阶段 0:基础设施(0.5d,低风险)
- [ ] `npm install react-router-dom`
- [ ] 新建 `src/router/types.ts`(路由参数类型,如 `EditorType`)
- [ ] 新建 `src/router/RequireAuth.tsx`(鉴权守卫组件,异步检查 currentUser)
- [ ] 单测守卫组件(登录/未登录分支)
- **不改任何现有 html/组件**,只加新文件。回归:全量 vitest 通过。

### 阶段 1:根 SPA 入口 + login 路由化(0.5d,低风险)
- [ ] 新建 `src/main.tsx`:`BrowserRouter` + `Routes`,根 `/` → `<Navigate to="/login"/>`
- [ ] `src/login/index.tsx`:把 `createRoot(#root).render(<LoginPage/>)` 拆成 `export default LoginPage` + 保留独立挂载(兼容 login.html)
- [ ] 路由 `/login` → `<LoginPage/>`
- [ ] login 成功后 `navigate('/app')` 替代 `window.location.href='frame.html'`(保留旧跳转作 fallback)
- [ ] 根 `index.html` 的 script 改指向 `src/main.tsx`(取代 meta refresh)
- **保留** `html/login.html` 可独立访问。回归:浏览器 `/` → SPA → `/login` 能登录。

### 阶段 2:frame 路由化(1d,中风险 ⚠️ 核心)
- [ ] `src/frame/index.tsx`:把 `DOMContentLoaded` 回调里的 JSX 提取成 `export default function FrameApp()`,store 创建移到组件内 `useMemo`,副作用移到 `useEffect`
- [ ] `window.__currentUser` 改读 `RequireAuth` 守卫注入的 Context
- [ ] `frame.html` 的同步 XHR 鉴权删掉,改 `RequireAuth` 包裹 `/app` 路由
- [ ] 退出登录 `navigate('/login')` 替代 `location.href='login.html'`
- [ ] FrameTab 的 iframe 加载 editor.html **暂保留**(阶段 3 再改)
- 回归:`/app` 能渲染 frame,10 个 panel 切换正常,文件树 + 编辑器打开正常(B-0 已修)。

### 阶段 3:editor 子路由化(1.5d,高风险 ⚠️⚠️ 最大风险)
- [ ] `html/editor.html` 的 switch-case DOM 注入 → 每种 type 一个 React 组件(`src/editor/<type>/Editor.tsx`)
- [ ] 路由 `/app/editor/:type` → 对应编辑器组件
- [ ] FrameTab 的 iframe → 直接 render `<Outlet/>` 或 tab 状态管理的组件
- [ ] editor 内部依赖 `#toolbarContainer`/`#container`/`#dialogContainer` 的代码改用 React ref/portal
- [ ] **逐个 type 迁移**(ruleset → decisiontable → scorecard → flowbpmn → ...),每个 type 独立 PR + E2E
- 回归:每种编辑器创建/编辑/保存/仿真全流程。

### 阶段 4:E2E 测试迁移(0.5d)
- [ ] `console-ui/e2e/*.spec.ts` 从 `editor.html?type=` 改 SPA 路由
- [ ] FrameTab 多标签页语义确认(SPA 内 tab 状态 vs 原 iframe 多实例)

### 阶段 5:iframe 清理 + 收尾(0.5d)
- [ ] 取消 A 类 iframe(FrameTab/IFrame/ReteDiagram,阶段 3 已完成)
- [ ] B 类隐藏 iframe → `FormData + fetch`:
  - `ImportProjectDialog.tsx`(hiddenFrame 上传项目)
  - `ImportXmlDialog.tsx`(隐藏 iframe XML 导入)
  - `ExcelImportDialog.ts`(createElement iframe Excel 导入)
- [ ] 删除 `html/login.html`、`html/frame.html`(editor.html 视阶段 3 决定)
- [ ] `vite.config.ts` `rollupOptions.input` 改单入口

## 风险与回滚

- 每阶段独立分支 `feature/V5.54-spa-stageN-<slug>`,出问题直接弃分支
- editor 子路由化(阶段 3)是最大风险点,必须**逐 type 迁移**,不批量改
- 全程保留旧 html 作为 fallback,直到阶段 5 才删
- 每阶段合入前跑全量 vitest + 关键 E2E(editor 创建/保存/仿真)

## 工作量与排期

| 阶段 | 工作量 | 风险 | 可否合并即用 |
|---|---|---|---|
| 0 基础设施 | 0.5d | 低 | ✅ 独立可用 |
| 1 login 路由 | 0.5d | 低 | ✅ |
| 2 frame 路由 | 1d | 中 | ✅ |
| 3 editor 子路由 | 1.5d | 高 | ⚠️ 逐 type |
| 4 E2E 迁移 | 0.5d | 低 | ✅ |
| 5 iframe 清理 | 0.5d | 低 | ✅ |
| **合计** | **4.5d** | — | 分 6 个 PR |

## 为什么不一次做完

- frame 是复杂 Redux app(`createStore` + `Provider` + `connect` + 多 panel switch + FrameTab iframe),DOM 副作用多
- editor 8+ 种类型各有独立 DOM 结构 + body 样式,批量改易回归
- E2E 测试 ~20 个 spec 基于 MPA 路径,需同步迁移
- 正在运行的 dev 环境,破坏后影响调试

分阶段 = 每步可验证、可回滚,符合 CLAUDE.md 的 sub-task + PR 工作流。
