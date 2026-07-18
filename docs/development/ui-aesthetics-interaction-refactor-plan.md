# console-ui 审美统一 + 交互重构计划

已确认方向：主色统一为 **indigo #5469d4**（现有 --rf-* token 体系）；编辑器**重构为应用内 tab**。
工作目录 `console-ui/`。每阶段验证 `npm run typecheck` + `npm test`(475) + `npm run build`，独立 commit。
无后端环境，e2e 不跑；视觉项靠 `npm run dev` 人工冒烟（frame 首页、登录页、一个编辑器、AI 面板）。

## 阶段 A：主题统一（视觉核心，收益最大）

1. **挂载主题**：`main.tsx` 根部包 `AntdProvider`（现有死代码 `src/theme/AntdProvider.tsx`，内部已含 zhCN locale）。
2. **重写 `src/theme/antd-theme.ts`** 对齐 indigo token 体系：
   - `colorPrimary` 系：`#5469d4` / hover `#4253b8` / active `#3a47a8` / bg `#eef0fe`（对照 `tailwind-base.css:10-13`）
   - 中性色对齐 slate 灰阶：`colorText #1a1f36`、`colorTextSecondary #3c4257`、`colorTextTertiary #697386`、`colorBorder #d8dee5`、`colorBorderSecondary #e3e8ee`、`colorBgLayout #f6f9fc`（对照 :23-37）
   - 圆角 `borderRadius 6` 保持；语义色沿用现有 a11y 调校值（success #237b00 / warning #874d00 / danger #ff4d4f）
   - 删掉过时注释（`antd-theme.ts:4` 声称匹配 #1677ff）；**注意**：#5469d4 白底对比度约 4.5:1 边缘，文字按钮/链接场景需在浏览器里过一眼，不达标则主色微调加深（如 #4a5dc0），token 文件同步改
3. **message 接入主题**：`utils/modal.tsx` 的 `bindMessageApi` 目前零调用（message 走静态默认主题）。在 `AntdProvider` 内用 antd `App` 组件包裹并调用 `bindMessageApi`，让命令式 message/dialog 也吃主题。
4. **修错误提示 HTML 泄漏**：`api/client.ts:69,76` 拼的 `<span style='color: red'>` 会被 antd message 当纯文本显示——改为纯文本消息（去掉 span 拼接）；`utils/modal.tsx:46` 正则猜 error/info 级别的逻辑保留（范围外不动）。
5. **修 token 文件内部自相矛盾**：`tailwind-base.css:332,352` 的 `rgba(9,88,217,…)`（antd 蓝）改为 indigo rgba(84,105,212,…)。
6. **修 `.rf` 类名冲突**：`tailwind-base.css:234` 的 `.rf` 工具类（链接着色）污染 iconfont 基类 `.rf`（`iconfont.css:7`）。把工具类改名 `.rf-link`，grep 其消费方同步改（预计极少）。
7. **删死样式**：`src/editor/context.standalone.css`（245 行零引用）。

## 阶段 B：视觉飞地治理（硬编码 hex → token）

1. **agent 面板**（曝光最高）：`agent/index.tsx:67-120` 内联手写 tab bar 改用 PageShell/antd Tabs 模式（对齐其他面板）；`DraftsView.tsx`(81 处 hex)、`RuleHealthView.tsx`(49)、`ChatPanel.tsx`、`ConfigPanel.tsx` 的硬编码 `#1677ff/#e8e8e8/#f0f0f0` 等 → `var(--rf-*)` 或 CSS 类。
2. **release/analysis 面板**：`release/index.tsx`(50 处 hex/72 处内联)、`analysis/index.tsx`(63/64) 同样收敛。**原则：只换色值与可抽取的重复布局类，不改组件结构与交互**。
3. **v1-flow 节点色集中**：`FlowNodes.tsx:8`、`NodePropertyDrawer.tsx:51` 的 `#1677ff/#722ed1/#eb2f96` 等抽到 `v1-flow/nodeColors.ts` 常量模块（画布节点色属语义色，不强套 --rf-primary）。
4. **TopBar 图标对齐**：`TopBar.tsx:126/130` iconfont 与 antd icons 混用处统一尺寸（去掉内联 fontSize 硬凑，加 CSS 类规范 16px 基线）。图标体系不整体迁移（iconfont 48 类 vs antd icons，成本大于收益），只修同菜单混排的对齐。

## 阶段 C：交互 bug 修复（小而关键）

1. **Loading 卡死**：`frame/actions/fileOps.ts` 10 处 `.catch(function () {})` 静默吞错（:39,60,73,92,124,138,151,160,178,188）——统一补 `HIDE_LOADING` emit + `alert` 错误提示（对齐同文件其他操作的处理模式）。
2. **window.confirm 统一**：`datasource/index.tsx:70`、`monitoring/components/AlertRulePanel.tsx:84`、`agent/components/DraftsView.tsx` 的原生 confirm/alert → `utils/modal.tsx` 的 `confirm()`/`alert()`（视觉与行为一致）。
3. **Ctrl+S 保存**：给有保存按钮的编辑器（variable/constant/action/resource/permission/client + v1-flow 编辑器）加全局 keydown 钩子，触发现有保存 handler；有 dirty 状态的只在 dirty 时响应。做成 `src/utils/useSaveShortcut.ts` 复用 hook。
4. **键盘可达性（低成本部分）**：`ActivityBar.tsx:42-47` 图标 div → `<button>` + `aria-label`；`TopBar.tsx` 用户下拉补 Esc 关闭。不做全站 aria 改造。
5. **settings 死入口**：`ActivityBar.tsx` 隐藏 settings 图标 + 删 `frame/index.tsx:54-55` 的 PlaceholderPanel 分支（PlaceholderPanel 组件保留，monitoring 等占位仍在用就保留，否则一并删——实施时确认消费方）。

## 阶段 D：编辑器应用内 tab 重构（最大件）

现状：所有编辑器 `window.open('/app/editor/xxx?file=…','_blank')` 开新窗口；`ContentTabBar`/`FrameTab` 是死 UI（`FrameTab.tsx:143-149` 只渲染 QuickStart）。

设计（keep-alive 方案）：
1. **tab 状态进 frame store**：`openTabs: {fullPath, editorType, label}[]` + `activeTab`，actions：`openTab/closeTab/activateTab`（openTab 幂等——已打开则激活）。reducer 放 `frame/reducer.ts`。
2. **编辑器宿主改造**：各 `EditorRoute.tsx`（variable/constant/action/resource/client/permission + drl/dmn/pmml 只读 + v1-flow 系列）目前从 `useSearchParams` 取 file——抽出 `file` 可经 props 传入的形式（props 优先、searchParams 兜底），store-per-editor 模式不变（每个 tab 实例自己的 store，天然隔离）。
3. **EditorTabHost**：挂在 `frame/index.tsx` 的 content-area，对所有 openTabs 渲染对应编辑器，非激活 tab `display:none` **保活**（切 tab 不丢未保存内容——这是选 keep-alive 而非路由切换的核心理由）。无 tab 时显示现有 QuickStart（FrameTab 的现状职责）。
4. **ContentTabBar 复活**：接 Redux tab 状态渲染 tab 条（label + 关闭 ×），点击激活、关闭后激活邻近 tab、全部关闭回 QuickStart。删掉它与 FrameTab 之间的 ref 传话（`frame/index.tsx:89-100` 的 contentTabBarRef/frameTabRef）。
5. **替换 window.open 调用点**（7 处）：`TreeItem.tsx:207,214`、`actions/treeNode.ts:85`、`TopBar.tsx:124`、`VersionListDialog.tsx:90`、`ReferenceDialog.tsx:310`、`FlowDesigner.tsx:527` → dispatch `openTab`。tree/dialog 在 frame 内可拿到 frame store（connect 或 frame event 总线，实施时按各文件现状选最小改动路径）。
6. **保留 standalone 路由**：`/app/editor/*` 深链仍可用（窗口外链接、书签），但应用内一律走 tab。
7. **明确不做**：关 tab 不做未保存确认（现状关浏览器标签同样无提示，对齐）；tab 不持久化到 localStorage；不做 tab 拖拽排序。

## 不做清单（控制范围）

- 不做 dark mode（token 化完成后是后续低成本项）
- 不做 Tailwind 原子类推广 / 内联样式全量清理（只治理阶段 B 列出的飞地）
- 不做登录页重设计（深色品牌页与主应用不同是可接受的，不割裂到需要改）
- 不做图标体系迁移、不做全站 aria、不做 settings 面板实现

## 风险与验证

- 阶段 A 改全局主题，所有 antd 组件观感变化——人工冒烟清单：frame 首页、登录页、一个编辑器、AI 面板、发布面板
- 阶段 D 动编辑器打开链路，单测覆盖有限（FileTree 相关测试会兜底一部分）——提交前逐编辑器手动开/关/切 tab 验证
- 每阶段独立 commit，可单独 revert
