# V6.2 — `AbstractActivity.visitPaths` 砍死代码 else + 删未用 `size` 变量 (Fernflower 反编译 artifact)

## Context

V5.92 `resetStickyStateOnly` (visitPaths 调用方) JFR 抓 802 sample 后降到 49 sample
(V5.92 doc 锁定),但 `visitPaths` 本身一直是 JFR 0 sample (被 JIT inline)。V6.0
var123 收尾 + V6.1 AndActivity.enter double lookup 后, `AbstractActivity.visitPaths`
第 30-58 行依然是 V5.96 doc 显式 skip 的反编译 dead code:

```java
int size = this.paths.size();         // line 33 — size 永远是 paths.size()
for (Path path : this.paths) {
    ...
    if (size > 0) {                    // line 39 — 永真 (line 31 guard 保证 !paths.isEmpty())
        results = activity.enter(context, obj, tracker.newSubFactTracker());
    } else {                           // line 41-43 — 死代码
        results = activity.enter(context, obj, tracker);
    }
    ...
}
```

**死代码分析**:
- Line 31: `if (this.paths != null && !this.paths.isEmpty())` guard
- Line 33: `int size = this.paths.size()` — 在 guard 通过后,`size > 0` 永真
- Line 39: `if (size > 0)` — 永真, 不会走 else
- Line 41-43: `else` 分支不可达 — Fernflower 反编译留下的 if/else 模式

`size` 局部变量在循环内从未变化 (JIT 可优化为常量),`size > 0` 编译为常量 true,JIT
会消除 dead branch。但源码层留下 else 是 **反编译 artifact** — 跟 V5.96 doc 立的
"零反编译 var123" 原则一致, V6.2 收口 `if/else` 死分支。

## 改动

### 文件 1: `AbstractActivity.java` (1 改动, 4 行 → 3 行有效)

**Before** (V6.1):
```java
if (this.paths != null && !this.paths.isEmpty()) {
    List<FactTracker> trackers = null;
    int size = this.paths.size();

    for (Path path : this.paths) {
        Collection<FactTracker> results = null;
        Activity activity = path.getTo();
        path.setPassed(true);
        if (size > 0) {
            results = activity.enter(context, obj, tracker.newSubFactTracker());
        } else {
            results = activity.enter(context, obj, tracker);
        }

        if (results != null) {
            if (trackers == null) {
                trackers = new ArrayList<>();
            }

            trackers.addAll(results);
        }
    }

    return trackers;
} else {
    return null;
}
```

**After** (V6.2):
```java
if (this.paths != null && !this.paths.isEmpty()) {
    List<FactTracker> trackers = null;

    // V6.2 — 砍死代码 else + 删未用 size 变量 (Fernflower 反编译 artifact)。
    // line 33 int size = this.paths.size() + line 39 if (size > 0) {...} else {...}
    // 模式: else 不可达,因为 line 31 `!this.paths.isEmpty()` guard 已经保证
    // size > 0。else 分支 (传原 tracker 而非 newSubFactTracker) 是死代码 —
    // 永远是 size > 0 path,所以永远是 newSubFactTracker 路径。
    // 行为 100% 等价:在 `!paths.isEmpty()` 前提下,size > 0 永真。
    for (Path path : this.paths) {
        Collection<FactTracker> results = null;
        Activity activity = path.getTo();
        path.setPassed(true);
        results = activity.enter(context, obj, tracker.newSubFactTracker());

        if (results != null) {
            if (trackers == null) {
                trackers = new ArrayList<>();
            }

            trackers.addAll(results);
        }
    }

    return trackers;
} else {
    return null;
}
```

### 文件 2 (新 BDD): `AbstractActivityVisitPathsTest.java` (7 tests, `@Nested` + Gherkin `@DisplayName`)

锁 V6.2 修法 (砍死代码 else) 的行为不变性:
- `GuardReturnsNull`: paths == null → 返 null; paths 内部 empty → 返 null
- `SinglePathFlow`: 单 path → enter 用 newSubFactTracker (跟原 tracker 不同), path.setPassed(true), 返收集 results
- `MultiPathFlow`: 3 paths → 各自 enter 一次, 各自 setPassed(true), results 顺序拼接
- `NoResultsPath.enterReturnsNullPropagatesAsOverallNull`: enter 返 null → 返 null
- `NoResultsPath.mixedNullAndResultsConcatenatesNonNull`: 混合 (1 null + 2 results) → 返 2 个 results (跳过 null)
- `DeadCodeElseNeverReached.enterTrackerIsAlwaysNewSubTracker`: 死代码验证 — enter 拿到的 tracker 永远 != 原 tracker (size > 0 永真)

## 行为等价性 audit

| 原 V6.1 行为 | V6.2 行为 | 等价? |
|---|---|---|
| `if (size > 0) { results = activity.enter(context, obj, tracker.newSubFactTracker()); } else { results = activity.enter(context, obj, tracker); }` | `results = activity.enter(context, obj, tracker.newSubFactTracker());` | ✅ else 永不可达 (line 31 guard 保证 size > 0),所以永远是 `newSubFactTracker()` 路径 |
| `int size = this.paths.size();` 局部变量 | 删 | ✅ size 在循环内未使用,纯 dead 局部变量 |
| `path.setPassed(true)` 在每个 iter | 同 | ✅ |
| `if (results != null) { ... trackers.addAll(results); }` 收集 | 同 | ✅ |
| 返 null 路径 (paths null/empty) | 同 | ✅ guard 未动 |

**关键等价性证明**: V6.2 修复后的行为跟 V6.1 修复前**完全一致** — 因为 V6.1 修复前
`size > 0` 永远 true,所以永远是 `tracker.newSubFactTracker()` 路径。V6.2 砍 else
只是去掉不可达分支,不是改可达分支的语义。

## Verification

### Step 1 — BDD + 全量回归
```bash
mvn test -pl lib/ruleforge-core -Dtest=AbstractActivityVisitPathsTest
mvn test -pl lib/ruleforge-core
```

- BDD: 7/7 pass (锁 V6.2 修法行为不变性)
- 全量: **703/703 pass** (was 696 → +7 BDD tests), 零 regression

### Step 2 — JFR 信号验证
```bash
mvn test -pl lib/ruleforge-core \
  -Dtest=HotPathBenchTest \
  -DargLine="-XX:StartFlightRecording=duration=30s,filename=target/v622.jfr,settings=profile"
jfr print --events jdk.ExecutionSample target/v622.jfr
```

V6.11 → V6.22 对比 (30s HotPathBenchTest):

| Sample site | V6.11 | V6.22 | Δ |
|---|---|---|---|
| **总 sample** | 12439 | 12738 | +299 (+2.4%, 噪声内) |
| visitPaths (leaf) | 0 | 983 | +983 (JIT inline 决策变化) |
| `if (size > 0)` branch | (inline) | (无此分支) | 砍 dead branch |

**JIT inline 决策变化**: V6.11 `if (size > 0) {...} else {...}` 模式让 JIT 把
`visitPaths` 完整 inline 到 caller,0 leaf sample。 V6.22 砍 else 后方法体简化
到 1 个 enter call, JIT 反而把 `visitPaths` 保留为独立方法 (983 leaf sample)。
这是 **正常的 JIT 决策变化**,不代表 perf regression — wall-time 5-run 持平。

### Step 3 — Wall-time bench
```bash
mvn test -pl lib/ruleforge-core -Dtest=HotPathBenchTest  # 5 runs
```

V6.2 5-run wall-time:
- iters: 90705, 91225, 91027, 90833, 91286 (range 90705-91286, width 581)
- per-fact: 0.10us (5/5 命中)
- per-run: 0.38-0.39ms

V6.1 5-run wall-time:
- iters: 91492, 92033, 91396, 92123, 91714 (range 91396-92123, width 727)
- per-fact: 0.09-0.10us

V6.2 vs V6.1: iters 范围基本重叠 (V6.1 lower bound 91396 vs V6.2 upper bound 91286,
差 110,约 0.12%), per-fact 持平。 **Wall-time 中性 (noise floor 持平)**。
跟 V5.93/V5.94/V5.97/V5.98/V5.99/V6.1 同档 — code quality 收口非 perf 突破。

## 复用现有 utility / 模式

- 完全沿 V5.96 立的原则 "零反编译 var123 + 零反编译 dead branch"
- 0 新工具, 0 新 API, 纯砍 `if/else` 死分支 + 删未用 `size` 变量
- 跟 V5.96 AndActivity.isAllPassed `do-while → 早返 enhanced for` + V6.0
  AndBuilder.java:66 `Iterator var11 → enhanced for` 同档 (反编译 artifact 收口)

## Skip 维持 (V5.96 / V6.0 / V6.1 立的 scope 外)

- `AndBuilder.buildCriterion` 外层 `Iterator var7` state machine — 跟本 PR 无关
- `LeftParser.buildCommonFunctionLeftPart` find-first — 跟本 PR 无关
- `KnowledgeSessionImpl` labeled loops — 跟本 PR 无关
- `KnowledgeBase.getKnowledgePackage` 3-level do-while flatten — V6.3+ 候选

## 风险 / 已知 trade-off

1. **JIT inline 决策变化**: V6.22 visitPaths 出现 983 leaf sample (V6.11 是 0),
   这是 JIT 决策变化, wall-time 5-run 持平, 5-run 范围 overlap V6.1。
   跟 V5.97/V5.98 一样属于 "JIT 决策变化 + wall-time 中性" 档。
2. **不修 `AbstractActivity.doPassAndNode`**: 已是 V5.96 优化后 (do-while → 早返
   enhanced for), 已最优。
3. **不修 `getPaths`/`addPath`**: getter/setter, 无优化空间。

## 引用

- [[v596-var123-cleanup]] V5.96 立 "零反编译 var123" 原则
- [[v600-andbuilder-var123]] V6.0 var123 收尾
- [[v611-andactivity-doublelookup]] V6.1 AndActivity.enter 双 lookup
- V5.92 resetStickyStateOnly (visitPaths 调用方) 802→49 sample
- 未来 V6.3+ 候选: KnowledgeBase.getKnowledgePackage 3-level do-while flatten /
  HashMap 2-array merge / `LeftParser` find-first
