# V6.11 — 跨模块 architecture boundary audit

> **2026-06-21** — Audit 报告
> **范围**: 5 Maven 模块 (core / decision / console-app / executor-app) 的 architecture boundary
> **结果**: ✅ **Hard rules 0 violations**, 10 doc/code drift 已分类
> **行动**: 1 doc PR 修正 2 处 CLAUDE.md doc drift

---

## 1. 模块图 (实际, 跟 CLAUDE.md 描述有差异)

**Pom 实际声明** (5 模块):

```
parent                     Maven parent POM, Spring Boot BOM, Java 17
lib/ruleforge-core         com.ruleforge.* 引擎
lib/ruleforge-decision     com.ruleforge.decision.* BPMN 引擎 + 共享 entity
app/ruleforge-console-app  com.ruleforge.console.* + .console.app.* 部署的 editor
app/ruleforge-executor-app com.ruleforge.executor.* + .executor.app.* 部署的 executor
```

**依赖图** (mvn dependency:tree verify):

```
ruleforge-core ← ruleforge-decision ← ruleforge-console-app
                                  ← ruleforge-executor-app
```

- `lib/ruleforge-decision` → `lib/ruleforge-core` ✓ 单向
- `app/ruleforge-console-app` → `lib/ruleforge-decision` + `lib/ruleforge-core` (per pom)
- `app/ruleforge-executor-app` → `lib/ruleforge-decision` + `lib/ruleforge-core` (per pom)
- **No lib → app 反向**; **No app ↔ app** (仅 HTTP)

---

## 2. 硬规则验证 (PASS, 0 violations)

| 规则 | 检查 | 结果 |
|---|---|---|
| **No app-to-app Java import** | `grep "import com.ruleforge.executor" app/ruleforge-console-app/src` | **0 hits** ✓ |
| | `grep "import com.ruleforge.console" app/ruleforge-executor-app/src` | **0 hits** ✓ |
| **No lib → app import** | lib/*/src 不 import com.ruleforge.console.* / com.ruleforge.executor.* | **0 hits** ✓ |
| **No lib → lib reverse** | ruleforge-core 不依赖 ruleforge-decision | **PASS** ✓ |
| **HTTP-only boundary** | console → executor 走 `execRestTemplate` (RestTemplateConfig.java) / `java.net.http.HttpClient` (ExecutorDatasourceClient.java) | **PASS** ✓ |

---

## 3. Doc/code drift (medium severity, 10 处)

### 3.1 CLAUDE.md 描述 4 个 lib 模块,实际只有 2 个 (中等)

**CLAUDE.md L36-50 写**:
```
parent                  Maven parent POM, Spring Boot BOM, Java 17
ruleforge-core          RETE 规则引擎
ruleforge-decision      自建 BPMN 2.0 决策流引擎
ruleforge-console       Web 控制台业务   ← 不存在
ruleforge-executor      规则执行引擎     ← 不存在
ruleforge-console-app   可部署 Spring Boot app — 编辑器
ruleforge-executor-app  可部署 Spring Boot app — 执行器
```

**Pom.xml 实际**:
```xml
<modules>
    <module>parent</module>
    <module>lib/ruleforge-core</module>
    <module>lib/ruleforge-decision</module>
    <module>app/ruleforge-console-app</module>
    <module>app/ruleforge-executor-app</module>
</modules>
```

**真相**: `ruleforge-console` / `ruleforge-executor` 不是独立 Maven 模块,它们的源码
(`com.ruleforge.console.*` / `com.ruleforge.executor.*`) 直接住在 app 模块内
(`app/ruleforge-console-app/src/main/java/com/ruleforge/console/` 等)。

### 3.2 CLAUDE.md 标 `com.ruleforge.decision.entity.*` 为 executor-app 专属 (中等, 9 处误禁)

**CLAUDE.md L130 写**:
> ❌ console-app 里 `import com.ruleforge.decision.entity.*` (这些 entity 在 executor-app 里)

**真相**: 9 个 console-app 文件 import `com.ruleforge.decision.entity.*`:
- `DatasourceController.java:6-8` (Datasource, DatasourceEntityMapping, DatasourceFieldMapping)
- `ShadowConfigController.java:3` (ShadowConfig)
- `TestController.java:31` (DecisionFlowState)
- `GrayStrategyController.java:3` (GrayStrategy)
- `ExternalRepositoryImpl.java:10` (RuleVariableDef)
- `AiJavaDataSourceService.java:8` (Datasource)
- `TestServiceImpl.java:18` (DecisionFlowState)

**真相**: 这些 entity 在 **`lib/ruleforge-decision` (共享 lib)**,不是 executor-app 专属。
console-app pom 正确声明了对 `ruleforge-decision` 的依赖,代码 import 是合法的。
`decision` entity 用的是 `flowable_db` (跟 act_*/flw_* 共用),console-app 也要操作。

### 3.3 决策表 (CLAUDE.md L142) 同 L130 误标 (medium, 跟 3.2 同一根因)

---

## 4. 软发现 (info, 不需修)

| 类型 | 文件 | 说明 |
|---|---|---|
| http_call_between_apps | `RestTemplateConfig.java`, `ExternalProcessServiceImpl.java`, `SimulationServiceImpl.java`, `ExecutorDatasourceClient.java`, `BpmnFlowController.java`, `ShadowComparisonProxyController.java` | console → executor 单向 HTTP, 文档化的边界机制, 非 violation |
| introspection_intra_console | `VariableController.java`, `RefactorServiceImpl.java`, `EnvironmentUtils.java` | Class.forName / getBeansOfType 都在 console-app 自家 Spring context 内, 非 cross-module bypass |
| all_decision_entities_used | `lib/ruleforge-decision/src/main/java/com/ruleforge/decision/entity/` | 9 个 entity 全部非零引用, 无 orphan |

---

## 5. 修正建议 (1 doc PR)

1. **CLAUDE.md 项目架构块** (L36-50): 改成 2 lib + 2 app 的实际结构
2. **CLAUDE.md "禁止的反模式"** (L129-132): 删 `com.ruleforge.decision.entity.*` 那条 (3.2 真相);
   改为 "no app-to-app Java import, only HTTP boundary" + 加 clarification
3. **CLAUDE.md "表/Entity 归属速查"** (L142): `com.ruleforge.decision.entity.*` 改 "共享 lib/ruleforge-decision",
   跟 `act_*`/`flw_*` 流程表归类一致 (都是 shared `flowable_db`)

---

## 6. 结论

**Architecture boundary 健康**:
- 硬规则 100% 满足 (no app-to-app import, no lib→app import, lib 单向)
- HTTP boundary 单向 (console → executor) 走 RestTemplate / HttpClient, 文档化机制
- **唯一问题是 CLAUDE.md 文档/代码 drift** (10 处), 1 doc PR 修完即收口

**Recommendation**: 1 doc-only PR (V6.11.3) 收所有修正。

---

## 7. 引用

- `docs/engine-tech-debt.md` — V6.0/V6.1 TD-1/TD-2 closure
- `docs/architecture-audit-v6.10.md` — engine 内部 audit (上轮, 0 violations)
- `CLAUDE.md` — 项目级架构文档 (本轮修正对象)
- [[v69-pipeline]] — V6.9.x candidate closure
