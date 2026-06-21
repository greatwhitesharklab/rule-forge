# V6.10 — 架构耦合审计 (TD-1 / TD-2 containment 复核 + post-V6.1 scout)

> **2026-06-21** — Audit 报告
> **范围**: V6.1 TD-2 收口 commit `3a3ce5b` → `HEAD` (含 V6.9.x cleanup 系列)
> **结果**: ✅ 0 violations across 5 audit axes — TD-1 / TD-2 边界稳定
> **行动**: 仅 audit doc commit, **无 production code 改动**

---

## 1. 验收标准复核 (V6.0 / V6.1 closure verification)

### TD-1: `ruleforge-core` 引擎去 Spring (V6.0 PR #182)

```bash
grep -rn "import org.springframework" lib/ruleforge-core/src/main/java/com/ruleforge | grep -v "/config/"
# → 0 hits ✓
```

### TD-2: model → runtime 分层倒置 (V6.1 PR #183-#185)

```bash
grep -rn "import com.ruleforge.runtime" lib/ruleforge-core/src/main/java/com/ruleforge/model
# → 0 hits
# 唯一 string match 在 BaseReteNode.java:16 JavaDoc 注释,解释已消除 ✓
```

### Rich Domain Model 模式: model.evaluate() 依赖倒置

V6.1 设计决策: model 类保留 `evaluate()` 方法,只依赖 `com.ruleforge.engine.*` 接口
不依赖 runtime 具体实现。Fowler Rich Domain Model 模式。

---

## 2. Fresh scout — post-V6.1 新增耦合

### Audit window

```
git log 3a3ce5b..HEAD --oneline
46c6a50 refactor(core): V6.9.30-36 batch — micro-cleanup + 1 bug fix (6 sub-tasks / 1 PR) (#218)
```

仅 1 commit (V6.9.30-36 batch) 在 audit window。10 main java 文件被改 + 1 new test。

### 5 Audit axes — 全部 0 violations

| Axis | 检查 | 结果 |
|---|---|---|
| **TD-1** | 新加文件 / diff 加行有 `import org.springframework` (非 config/) | 0 ✓ |
| **TD-2** | `git diff 3a3ce5b HEAD -- model/**` 加行有 `import com.ruleforge.runtime` | 0 ✓ |
| **engine 包** | `engine/*` 里有 `import com.ruleforge.runtime/parse/model` | 0 (new) ✓ |
| **runtime → parse** | `runtime/*` 里有 `import com.ruleforge.parse` | 0 ✓ |
| **model.evaluate() → runtime impl** | 列出 model 全部 `evaluate` 方法, 用 runtime 具体类 (非 engine 接口) | 0 (new) ✓ |

### 详细发现

**TD-1 (Spring leakage)**: 0 new hits across 10 changed files. PASS.

**TD-2 (model→runtime reverse)**: 0 new imports in `model/`. The only `com.ruleforge.runtime` string match
is in `BaseReteNode.java:16` Javadoc (comment explaining what was removed), pre-existed. PASS.

**model.evaluate() → runtime impl**: No new `evaluate()` methods added post-3a3ce5b. All 10 existing
model `evaluate()` methods (`AllLeftPart`, `Criteria`, `PropertyCriteria`, `CriteriaUnit`,
`ExistLeftPart`, `CollectLeftPart`, `MultiCondition`, `NamedCriteria`, `FromLeftPart`,
`CommonFunctionLeftPart`) were last modified in V6.1 PRs #184/#185 — grandfathered (depend only on
engine interfaces). PASS.

**engine reverse imports**: The 13 engine files with reverse imports (e.g. `ReteInstanceFactory.java`
→ `runtime.rete.ReteInstance`, `KnowledgeSessionFactory.java` → `runtime.KnowledgeSessionImpl`,
`ValueCompute.java` → `model.rule.*`) were all last modified in V6.1 commit `b1ec52a` (TD-2 completion),
which is **at** the 3a3ce5b baseline. The single post-3a3ce5b commit (46c6a50) did not touch any
engine/* file. Net new reverse imports = 0. PASS.

**runtime → parse reverse imports**: 0 hits. The 46c6a50 commit did not introduce any new
runtime→parse coupling (its runtime changes are raw-type `<>` diamond conversions in
`SessionParameterManager`/`AbstractRuleBox` and a stack-pop order bug fix in `ElCompute`). PASS.

---

## 3. 结论

V6.1 架构边界 (TD-1, TD-2, Rich Domain Model) 在 V6.9.x cleanup 系列期间**完全保持**:

- 8 PR (V6.9.14-36) 期间 **0 个新架构耦合引入**
- V6.9.30-36 batch 11 文件改动全是 raw-type erasure / helper extract / dead-path bug fix /
  try-with-resources / BDD test — **100% 符合 V6.1 边界**

**Containment rule (CLAUDE.md "代码优雅" 原则) 持续有效**, 新代码无加深反向 import。

**Recommendation**: 后续如发现 architecture-level 改动, 应**先 review 是否违反 V6.1 边界**,
再决定是否需要新 TD-3 (例如: parse 包是否需要同样地依赖倒置到中性包, 或 engine 包是否应进一步
下沉到独立 module, 等)。

---

## 4. 引用

- `docs/engine-tech-debt.md` — V6.0/V6.1 TD-1/TD-2 closure
- `CLAUDE.md` — "代码优雅" 硬约束 (分层单向 / 不造轮子 / 拒绝 god class / Utils 不是垃圾桶 /
  核心引擎不渗 Spring / 动核心前先 grep)
- [[v69-pipeline]] — V6.9.x candidate closure tracking
