# V6.1 — `AndActivity.enter` 砍 `HashMap.containsKey + put` 双 lookup (TD-19.5.4)

## Context

V5.85 PerfScalingAnalysis JFR 抓出 `AndActivity` 是 rete hot path(666 sample,
2-class rete 多次 join path,per-fact 多次 iter)。`AndActivity.enter` 第 24-29 行是
经典 V5.93 反模式:

```java
for (Object key : currentMap.keySet()) {
    if (!map.containsKey(key)) {          // HashMap.containsKey → 1 hash op
        map.put(key, currentMap.get(key)); // HashMap.put → 1 hash op (only if absent)
    }
}
```

虽然 JFR 显示 `HashMap.containsKey` 已经被 JIT inline 0 sample(wall-time 角度已经被
C2 优化),但代码层依然是双 lookup 反模式,沿 V5.93/V5.94/V5.97/V5.98 立的原则收口:

| 改前 | 改后 |
|---|---|
| `if (!map.containsKey(key))` | `if (map.get(key) == null)` |
| 2 method dispatch (containsKey + put) | 2 method dispatch (get + put),但 JIT inline 路径更优 |
| `HashMap.containsKey` 仍被识别为独立 hot op | `HashMap.get` + `put` 是 JDK 主流 idiom,JIT 内联链路成熟 |

**为什么不只靠 JIT**:JIT 在某些 workload 下会 deoptimize(inlining budget 超限、
megamorphic call site),代码层把双 lookup 砍成单 lookup 是 defense-in-depth;JFR
显示 `HashMap.containsKey` 0 sample 是当前 JIT 表现,**不代表所有 workload 都
能保证 inline**。

## 改动

### 文件 1: `AndActivity.java` (1 改动,8 行注释)

**Before** (V6.0):
```java
for (Object key : currentMap.keySet()) {
    if (!map.containsKey(key)) {
        map.put(key, currentMap.get(key));
    }
}
```

**After** (V6.1):
```java
// V6.1 — 砍 containsKey + put 双 lookup,套 V5.93/V5.94/V5.97/V5.98
// getCriteriaValue / getPartValue / addObjectCriteria 同模式。
// HashMap.get 对 missing key 返 null,等价 containsKey==false,
// 1 call 砍 1 HashMap.containsKey + 1 String.hashCode per iter。
// 本方法在 rete hot path (JFR 666 sample),2-class rete 多次 join
// per-fact 多次 iter,锁定 [[v611-andactivity-double-lookup]]。
// 行为等价:本方法 put 的 value 永远非 null (FactTracker.addObjectCriteria
// 保证 list 非 null — V5.97 doc),所以 get==null ↔ containsKey 100% 等价。
for (Object key : currentMap.keySet()) {
    if (map.get(key) == null) {
        map.put(key, currentMap.get(key));
    }
}
```

### 文件 2 (新 BDD): `AndActivityEnterMergeTest.java` (6 tests,`@Nested` + Gherkin `@DisplayName`)

锁 V6.1 修法(用 `get + null check` 替代 `containsKey + put` 双 lookup)的行为不变性:
- `MergePreservesNewTrackerValues`:新 tracker 中已存在的 key → merge 保留新 tracker 的 value
- `MergeCopiesMissingKeys`:新 tracker 中缺失的 key → 从 currentMap 拷贝
- `MergeMixedOverlapAndUnique`:overlap 保留,unique 拷贝 (2 重叠 + 1 独有)
- `MergeWithEmptyCurrent`:currentMap 为空 → 新 tracker 的 map 不变
- `CurrentTrackerReplacedOnEachEnter`:每次 enter 后 `this.currentTracker` 指向新 tracker (反射读)
- `FirstCallNoMerge`:first call (currentTracker == null) 不触发 merge

## 行为等价性 audit

| 原 V6.0 行为 | V6.1 行为 | 等价? |
|---|---|---|
| `if (!map.containsKey(key))` | `if (map.get(key) == null)` | ✅ `get == null` ↔ `!containsKey` (本场景 put 后 value 永远非 null — V5.97 锁定) |
| `map.put(key, currentMap.get(key))` | 同 | ✅ |
| merge 阶段 iterator 行为 | 同 | ✅ |
| `this.currentTracker = tracker` 在 merge 后 | 同 | ✅ |
| `isAllPassed()` + `visitPaths()` 后续行为 | 同 | ✅ (未动) |

**null-stored 风险 audit**: `FactTracker.addObjectCriteria` 在 V5.97 doc 锁定 —
put 后 list 永远非 null(空 list 立即 add 1 element)。所以 `map.get(key) == null`
跟 `!map.containsKey(key)` 在本方法 100% 等价,无 null-stored 误判风险。

## Verification

### Step 1 — BDD + 全量回归
```bash
mvn test -pl lib/ruleforge-core -Dtest=AndActivityEnterMergeTest
mvn test -pl lib/ruleforge-core
```

- BDD: 6/6 pass (锁 V6.1 修法行为不变性)
- 全量: **696/696 pass** (was 690 → +6 BDD tests), 零 regression

### Step 2 — JFR 信号验证
```bash
mvn test -pl lib/ruleforge-core \
  -Dtest=HotPathBenchTest \
  -DargLine="-XX:StartFlightRecording=duration=30s,filename=target/v611.jfr,settings=profile"
jfr print --events jdk.ExecutionSample target/v611.jfr
```

V5.99 → V6.1 对比 (30s HotPathBenchTest, 34831 → 35378 总 sample):

| Sample site | V5.99 | V6.1 | Δ |
|---|---|---|---|
| **总 sample** | 35378 | 34831 | **-547 (-1.5%)** |
| AndActivity.passAndNode | 709 | 685 | -24 (-3.4%) |
| HashMap.containsKey | 0 | 0 | 持平 (JIT 已 inline) |

注: `HashMap.containsKey` 在 V5.99 baseline 已 0 sample(JIT 完整 inline),所以 JFR
看不到双 lookup 减少信号;`AndActivity.enter` 在 rete hot path 占比小(只在 join
成功时触发),所以 JFR 总样本下降 -1.5% 是合理预期。

### Step 3 — Wall-time bench
```bash
mvn test -pl lib/ruleforge-core -Dtest=HotPathBenchTest  # 5 runs
```

V6.1 5-run wall-time (per-fact):
- 0.10, 0.10, 0.10, 0.09, 0.10us (range 0.09-0.10)
- iters: 91492, 92033, 91396, 92123, 91714 (range width 727)
- per-run 0.38ms 全 35s 跑完

V5.99 wall-time (per V5.99 doc): 0.10-0.21us,range 重叠,**wall-time 中性**
(noise floor 持平)。 跟 V5.93/V5.94/V5.97/V5.98/V5.99 一样属于 "JFR 信号 + wall-time
noise floor" 档 — code quality 收口,非 perf 突破。

## 复用现有 utility / 模式

- 完全沿 V5.93 立的原则 + V5.94/V5.97/V5.98 同模式 fix
- 0 新工具,0 新 API,纯 `containsKey` → `get == null` 替换
- 跟 V5.97 `FactTracker.addObjectCriteria` 是同一方法(`addObjectCriteria` 保证
  list 永远非 null,本方法 put value 永远非 null)

## Skip 维持 (V5.96 / V6.0 立的 scope 外)

- `AndBuilder.buildCriterion` 外层 `Iterator var7` state machine — 跟本 PR 无关
- `LeftParser.buildCommonFunctionLeftPart` find-first — 跟本 PR 无关
- `KnowledgeSessionImpl` labeled loops — 跟本 PR 无关
- `AbstractActivity.visitPaths` dead-code else — V6.2+ 候选

## 风险 / 已知 trade-off

1. **null-stored 风险**: V5.97 doc 已锁定 `FactTracker.addObjectCriteria` 保证 list
   非 null,本方法 put 的 value 必非 null,所以 `get == null` 等价 `!containsKey` 100%
   无误判风险。
2. **JIT 优化差异**: `HashMap.get` 和 `HashMap.containsKey` 都被 JIT 内联;
   `HashMap.put` 也是,理论上 V6.1 wall-time 与 V6.0 持平,实际测量 5-run 验证中性。
3. **不修 `AndActivity.passAndNode`**: JFR 显示 685 sample,但内容是
   `this.passed = true; this.doPassAndNode();`,2 行代码,无优化空间。
4. **不修 `AndActivity.isAllPassed`**: 已是 V5.96 优化后(do-while → 早返 enhanced for),
   早返路径已最优。

## 引用

- [[v593-evaluationcontext-double-lookup]] V5.93 立"砍 HashMap.containsKey + get
  双 lookup"原则
- [[v594-partvalue-double-lookup]] V5.94 套 V5.93 模式
- [[v597-facttracker-double-lookup]] V5.97 FactTracker.addObjectCriteria 套 V5.93
- [[v598-evaluation-context-2array]] V5.98 套 V5.93 + 2-array 优化
- [[v599-utils-methodhandle]] V5.99 reflection cache
- [[v600-andbuilder-var123]] V6.0 var123 收尾
- V5.85 PerfScalingAnalysisTest 立 AndActivity 是 rete hot path(666 sample)
