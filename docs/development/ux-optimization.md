# UX/UE 审计清单与优化方案

> **执行状态(2026-07-19):全部 7 批次完成,清单关闭。**
> - ✅ B1 止血(`17f4420`):V1 后缀 bug、Modal Escape(forceRender 根因)、对话框取消/回车
> - ✅ B2 死编辑器(`b75be67`):老 4 库编辑器删除(后端 /xml 端点 V5.43 已删),老库文件点击改只读源码
> - ✅ B3 面板布局(`1aa2ce8`):监控/Git 健康 Statistic/Card 化、AlertBell 双控 open 修复、导入项目入口可达
> - ✅ B4 错误态(`40c6b09`):EditorLoadState 统一空态/错误态(含 [object Response] 修复)、全局 zhCN
> - ✅ B5 文案(`e52af1a`):QuickStart 重写、V1 画布/ag-grid 中文化、ActivityBar 文字标签
> - ✅ B6 编辑器内嵌(`43a0c4a` + `d3754a5`):11 编辑器组件化 + EditorTabs 应用内标签页,
>   window.open 清零,FrameTab/ContentTabBar 拆除;真机冒烟通过(无新浏览器标签、标签保活)
> - ✅ B7 代码健康(`da08bd1` + `df53d5c`):console.log 清零、dev-local.sh 加固、antd/ag-grid 警告、树重复 key 防御
>
> 已知余项(2026-07-19 已处理,`18278f6` + `0039ad3`):V1 画布工具栏裁切(自适应换行修复)、
> demo 路由规则节点(no-op → 明确提示)、老类型版本列表"打开"(降级只读源码,顺带修"源码"死按钮)、
> 后端死 controller(ActionEditor/CrosstabEditor/generateFields,含两处反射执行面)已删、
> permission/client 双重提示已消。
> 唯一遗留:监控面板指标仍是占位值(实时 metrics 端点未接,需后端立项,不属于 UX 清单)。

> 2026-07-18 全栈走查:console-app(8180)+ vite dev + MySQL,admin 登录,
> Playwright 逐页截图 40+ 张(原始图在 `.ux-audit/`,走查笔记 `.ux-audit/notes.md`),
> 覆盖登录/主框架/文件树/右键菜单/对话框/各编辑器/V1 画布/10 个 ActivityBar 面板。
> 与 [frontend-optimization.md](./frontend-optimization.md)(架构债)互补:本文档只管用户体验。

## P0 — 功能阻断(修 bug,不是优化)

### U-0.1 4 库编辑器整体不可用(白屏)

- **变量库/常量库/动作库**:带有效 file 也白屏,后端 `GET /api/xml` 500;打开不存在的文件同样白屏,用户零反馈(`.ux-audit/60/61/63/73`)
- **参数库**:`/app/editor/parameter` 路由根本不在 `main.tsx`(TreeItem/Utils 的映射表还留着 `parameter` 项,点了就是空白页);V7.20 后端已删参数库死代码,前后端状态需对齐
- **待定**:V7 后文件树已不展示这 4 类文件(只有 V1 资产),这些编辑器是死是活要先拍板——要么修好并把入口接回,要么删路由/映射/编辑器代码
- **附**:所有编辑器无 file 参数直接访问 → 全白屏,无空态/无提示(`20~24`)

### U-0.2 V1 文件创建后树里不可见(后缀 bug)

- UI 创建 V1 库/规则集/决策表/评分卡生成 `lib1.V1Library`/`rs1.V1RuleSet` 裸类型名后缀,文件实际创建成功但**文件树按后缀归类,新文件不可见**
- `createNewFile` 的 `FILE_TYPE_MAP` 缺 4 个 V1 类型后缀映射(应为 `.v1lib.json` 等,跟 a4c4565 修的 v1flow.json 是同一个坑的剩余部分)
- 连带:V1 评分卡分类右键菜单文字为空(`.ux-audit/86-ctx-menu-v1scorecard.png`)

## P1 — 严重 UX 缺陷

### U-1.1 监控告警 / Git 健康面板布局塌缩

- 监控告警:内容区几乎空白,P95/成功率/告警三个指标沉到**页面左下角**;Git 健康:统计纯文本堆叠无卡片,"24h/轮询 30s" 散落左下角(`.ux-audit/40-panel-monitoring.png`、`40-panel-gitStatus.png`)
- 疑似面板未撑满 host 的 flex 布局 bug(V5.101 修过 monitoring 117px 问题,没修完)

### U-1.2 antd Modal 无法 Escape 关闭

- 创建项目/创建目录等对话框按 Escape 不关闭,残留 modal 遮挡后续所有操作(走查脚本踩中两次,`06/89/94` 均有残留 modal 入镜)
- 疑似 `keyboard` 属性被关或 onCancel 未接;所有 `CommonDialog`/`Dialog` 消费方受影响

### U-1.3 编辑器全部新开浏览器标签,主框架成空壳

- 文件点击 `window.open('/app/editor/...', '_blank')`,主框架内容区永远停在 QuickStart;`FrameTab`/`ContentTabBar` 标签系统已是摆设(代码还在,UI 不出现)
- 结果:编辑 3 个文件 = 3 个浏览器标签,项目上下文(树)不可见,来回切换成本高
- 方向:SPA 内嵌编辑器(内容区渲染编辑器组件 + 应用内标签),或至少"同一项目同一编辑器类型复用标签"。**这是本次清单里最大的交互重构,需单独设计**

## P2 — 明显体验问题

### U-2.1 AlertBell 点击无响应

- 顶栏铃铛点击后无下拉/面板(`.ux-audit/13`),B-1/B-6 修了轮询但交互是死的

### U-2.2 "导入项目"入口不可达

- 菜单挂在"项目列表"根节点右键,但渲染跳过根节点 → UI 里找不到导入入口(ImportProjectDialog 做好了却进不去)

### U-2.3 对话框可用性细节

- 创建项目/目录对话框:**只有"保存"没有"取消"**(只能点右上角 X);输入框无 placeholder、无自动聚焦、回车不提交

### U-2.4 错误反馈缺失/裸露

- DRL 无 file:"加载失败: [object Response]"(错误对象没格式化)
- 编辑器 404/500 一律白屏(同 U-0.1 附)
- 方向:编辑器统一错误态组件(加载失败 + 原因 + 返回)

## P3 — 文案/一致性

- **U-3.1 QuickStart 卡片过时**:决策流写"BPMN流程编排"(V7.21 已移除)、决策树/脚本式决策集卡片对应编辑器已删;卡片纯展示不可点击,与左侧树的 V1 资产模型(V1决策流/V1库/…)完全对不上 → 按 V1 资产重写卡片并可点击跳转
- **U-3.2 中英文混杂**:ag-grid 空态 "No Rows To Show"、V1 画布节点名 Start/RuleSet/Gateway、仿真面板日期 mm/dd/yyyy(浏览器原生)、V1 画布左下帮助文本引用内部版本号 "V7.1-2b"
- **U-3.3 ActivityBar 纯图标无文字**:9+1 个图标全靠 tooltip(♥ = Git 健康、📖 = 智能分析,认知成本高)
- **U-3.4 双搜索框**:顶栏"搜索文件..."与树面板"搜索文件/项目"并存,职责重叠

## P4 — 代码健康(非阻塞)

- **U-4.1** `frame/reducer.ts` 生产代码带 ~10 条中文 debug `console.log`(CREATE_NEW_FILE 路径刷屏);另 4 处零散 console.log
- **U-4.2** console 警告:antd Table rowKey index 弃用、Drawer `width` 弃用、ag-grid error #239(ValidationModule)
- **U-4.3** test01 树数据异常:5 个分类下各挂同名 `V1决策流` 文件夹(fullPath 相同)→ antd Tree key 冲突警告(数据问题,但暴露树组件对重复 key 无防御)
- **U-4.4** `.env` 的 `APP_DB_URL` 含未引用 `&`,`source .env` 被 bash 截断(dev-local.sh 隐患);dev-local.sh 会 `docker compose up -d mysql` 与已在跑的容器抢 3306

## 建议执行批次

| 批次 | 内容 | 性质 | 预估 |
|---|---|---|---|
| **B1 止血** | U-0.2(V1 后缀 bug)+ U-1.2(Modal Escape)+ U-2.3(对话框取消/回车) | 纯 bug,小改 | 0.5d |
| **B2 死编辑器拍板** | U-0.1:确认 4 库编辑器死活 → 修好或删除(含 parameter 路由/映射清理) | 需决策 + bug | 0.5-1d |
| **B3 面板布局** | U-1.1(监控/Git 健康 flex 塌缩)+ U-2.1(AlertBell)+ U-2.2(导入入口) | bug + 小 UX | 0.5d |
| **B4 错误态统一** | U-2.4 + U-0.1 附:编辑器统一加载/错误/空态组件 | UX 组件 | 0.5d |
| **B5 文案一致性** | U-3.1(QuickStart 重写)+ U-3.2(中文化)+ U-3.3/U-3.4 | 文案 | 0.5d |
| **B6 编辑器内嵌(大)** | U-1.3:SPA 应用内标签编辑,废弃 window.open | 架构交互 | 1.5-2d |
| **B7 代码健康** | U-4.1-U-4.4 | 清理 | 0.5d |

每批独立 commit(main 直提),B1-B5 可快速连做,B6 建议单独评估设计后再动。
