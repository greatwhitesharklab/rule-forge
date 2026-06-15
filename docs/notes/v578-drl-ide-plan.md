# V5.78 DRL IDE Editor — 调研 + 选型

Phase 14 收口(roadmap Phase 14 "完整 DRL 编辑器(console-ui)重写")。
TD-14.0 调研,2026-06-15。

## 现状盘点

### console-ui 端 DRL 编辑器

- **编辑器库**:CodeMirror 5(`codemirror: ^5.33.0`),非 Monaco
- **入口**:`console-ui/src/editor/drleditor/index.tsx` + `DrlCodeMirror.ts` + `drlStreamLanguage.ts`
- **语法高亮**:手写 `StreamLanguage`,keyword / string / comment / number / ident
- **autocomplete**:无(虽然 `@codemirror/autocomplete` 在 package.json 但未用)
- **outline**:无(侧栏是后端 load 时静态 render `payload.imports + payload.ruleNames`)
- **诊断**:无(`index.tsx:17` 注释 "live syntax-error reporting ... 暂未序列化到响应" — V5.46+ deferred)
- **LSP 脚手架**:零

### 后端 antlr4 grammar

- **grammar**:`DrlLexer.g4`(186 行)+ `DrlParser.g4`(474 行,~40 rules)
- **Visitor**:`DrlAstVisitor` extends `DrlParserBaseVisitor<Void>`,15 visit 方法
- **Semantic info**:`DatatypeResolver.TypeInfo`(name + fields + isFact),**无 source-position symbol table**(`Symbol`/`Scope`/`Binding`/`Reference`/`Definition` 都不存在)
- **现成端点**:`POST /loadDrl` in `CommonController.java:270`,helper `parseDrlSummary(String):319` — 可复用,**但响应里无 error positions**(需要加)
- **线程安全**:所有 call sites 每请求 `new DrlLexer + new DrlParser`,无 ThreadLocal / synchronized — **LSP server 并发请求无需重架构**

## 选型 — 3 方案

| 方案 | 内容 | 周 | 主要风险 | 主要收益 |
|---|---|---|---|---|
| **A — Full LSP** | lsp4j v1.0.0 + monaco-languageclient v10.7.0 + WebSocket | 8-11 | lsp4j v1.0 first SemVer edge bugs;symbol table 仍缺 | VS Code 同款 hover/go-to-def/find-refs/format-on-type |
| **B — Custom JSON-RPC** | Spring WS endpoint `parse`/`validate`/`complete`;Monaco providers 直连 | 4-6 | 自定义协议,新 LSP-shaped feature 需新端点;无跨文件 refs | live diagnostics + keyword/snippet completion + 简单 hover,~80% IDE 体验 |
| **C — Frontend-only** | Monaco Monarch tokenizer + 客户端 `CompletionItemProvider`;后端只 save 时 re-parse | 1-2 | Tokenizer grammar 双份(Monarch + ANTLR)drift;无 live diagnostics | 实时高亮 + tab 关键字补全 |

**选 Option B**。理由:

- 4-6 周,budget 8-12 周内富余
- 不依赖 lsp4j v1.0 first-SemVer 风险
- 覆盖 80% IDE 体验
- 留 Option A 升级路径(后续需要跨文件 refs / signature help / format-on-type 时再做)

## 关键技术细节

- **lsp4j**: `org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0` (2026-02-10,supports LSP 3.18.0)
- **monaco-languageclient**: `TypeFox/monaco-languageclient` v10.7.0 (canonical,未 archive)
- **WebSocket transport**: `org.eclipse.lsp4j:org.eclipse.lsp4j.websocket.jakarta`(Spring Boot 4 default)
- **ANTLR4 线程安全**: 显式 non-thread-safe;**canonical pattern** = 每请求 `new DrlLexer(CharStream) + new DrlParser(CommonTokenStream)` — 现有代码已遵守,无需改

## Sub-task 拆解

| TD | 范围 |
|---|---|
| 14.0 | 调研 + 选型(本文件) |
| 14.1 | 选 B:Java 端 `DrlLspController` (Spring WS) + 复用 `DrlAstVisitor` 提供 parse / validate / complete / hover |
| 14.2 | console-ui 升 CodeMirror 5 → Monaco(`@monaco-editor/react` + `monaco-editor`)+ 调 WS endpoint |
| 14.3 | 端点: parse-with-errors(返回 offset/line/column) / complete(关键字 + snippet + 已声明 type/fact field) / hover(builtin 关键字说明 + TypeInfo field) |
| 14.4 | Playwright E2E autocomplete / go-to-def(本地) / diagnostics / docs + 整体 PR |

## 风险

- **风险 1**:lsp4j v1.0 edge bugs → 选 B 规避
- **风险 2**:Monaco 包体(`monaco-editor` ~5MB minified)→ Vite tree-shake 配 `monaco-editor-webpack-plugin`,只装 DRL 用到的 language contribution
- **风险 3**:CodeMirror 5 删 → 需扫 console-ui 其它地方是否依赖(已知 drleditor 是唯一入口,但要 grep 确认)
- **风险 4**:Live diagnostics 性能 → 客户端 debounce 300ms 调 parse,Java 端每请求 new parser 开销 < 5ms(实测 0.5KB DRL)

## 备查

- https://github.com/eclipse-jdtls/eclipse.jdt.ls — JDT Language Server (lsp4j reference)
- https://github.com/eclipse-lsp4j/lsp4j/tree/main/examples — minimal client/server examples
- https://github.com/TypeFox/monaco-languageclient/tree/main/packages/examples — Monaco-editor + remote LSP demos
