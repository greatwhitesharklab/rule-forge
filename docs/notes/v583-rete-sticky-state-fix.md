# V5.83 — Rete sticky state 缺陷修:per-fact clean + resetStickyStateOnly

## 背景

V5.82 修 `allFactsMap` className-keyed 覆盖 bug 后,`EvalBenchmarkV579` 仍 fired=0。
V5.82 调查([[v582-allfactsmap-rewrite]] TD-19.5.4)发现**第 3 个** pre-existing rete
实现 bug — rete sticky state 缺陷:1-pattern 100 noise + 1 alice(noise-first 顺序)
都不 fire。V5.83 修这个。

## Root cause

V5.82 调查 + V5.83 trace 锁定 2 个独立的 sticky state 问题:

### 问题 #A — EvaluationContext 跨 fact 缓存污染

`KnowledgeSessionImpl.evaluationRete` 原代码只在 reteInstance 列表跑完才
`evaluationContext.clean()`,导致 `criteriaValueMap` / `partValueMap` 跨 fact
复用 — 首 fact 评估的 `EvaluateResponse`(leftResult/rightResult/result)被
后续 fact 错用。

**Trace 数据**(V5.83):100 noise + 1 alice(noise-first):
- noise-0:Person(name=noise-0)→ criteria 评估 → 缓存 `result=false`
- noise-1..99:走 cached response(都是 noise-0 跟 alice 比)→ 全 false
- alice:Person(name=alice)→ 走 cached response(还是 noise-0 跟 alice 比)→ **false**

`alice` 根本没被 evaluate,直接拿了 noise-0 的缓存。

**修法**:`evaluationRete` 每个 fact 进入 rete 前 `this.evaluationContext.clean()`。

### 问题 #B — Activity `passed` flag 跨 fact 粘滞

`CriteriaActivity.enter` line 24-26:
```java
if (this.passed) return null;
else if (this.joinNodeIsPassed()) return null;
```

`this.passed` 在 fact 1 评估失败后通过 `passAndNode()` 把下游 `AndActivity.passed`
设成 true。fact 2 进入时,`joinNodeIsPassed()` 返回 true(因为 AndActivity.passed=true)
→ short-circuit → 永远不 evaluate。

**Trace 数据**(V5.83, 4 fact noise-first: bob, Side, alice, Main):
- bob:Person → `result=false` → `passAndNode()` → AndActivity.passed=true
- Side:Address → `joinNodeIsPassed=true` → return null(永远不 evaluate)
- alice:Person → `joinNodeIsPassed=true` → return null(永远不 evaluate!)
- Main:Address → `joinNodeIsPassed=true` → return null(永远不 evaluate!)

**修法**:加 `ReteInstance.resetStickyStateOnly()` — 重置活动节点的 `passed` flag +
`currentTracker`,但**保留 Path.passed 标记**(Path.passed 是 2-pattern join
跨 fact 累积 join 状态的关键)。`evaluationRete` 每个 fact 进入 rete 前
`reteInstance.resetStickyStateOnly()`。

## V5.83 修法(TD-19.5.4)

**`ReteInstance.java`**:
- 新增 `resetStickyStateOnly()` 方法:递归调用 `activity.reset()`,但**不**重置
  `Path.passed`(vs 原 `reset()` 会 `path.setPassed(false)` 破坏 join 累积)
- 实现:遍历 ObjectTypeActivity 路径,递归对每个 AbstractActivity 调 `reset()`

**`KnowledgeSessionImpl.evaluationRete`**:
- 每个 fact 进入 rete 前:
  1. `this.evaluationContext.clean()` — 清 EvaluationContext 缓存
  2. `reteInstance.resetStickyStateOnly()` — 清 rete sticky state(保留 Path.passed)

**`CriteriaActivity`**:`this.passed` 仍然按原语义工作(已通过 resetStickyStateOnly 在
新 fact 进入前清掉)。

## EvalBenchmarkV579 4 scenario 收紧 (V5.83)

| Scenario | 期望 (V5.46) | V5.79/80/81/82 | V5.83 |
|---|---|---|---|
| `no_eval` 3 rule + 1000 P/A | fired=3 | fired=0 (#1+#2+#3) | **fired=3** ✓ |
| `no_eval_3way` 3-pattern 链 | fired=0(无 cross) | fired=0 | **fired=0** ✓ |
| `no_eval_5r` 5 rule | fired=5 | fired=0 | **fired=5** ✓ |
| `eval` 字段过滤全 no-match | fired=0 | fired=0 | **fired=0** ✓ |

5 条 rule 全 fire 是因为 2 条跨 special(Mario+First, Duncan+Second)也都 match
— bench 设计如此,不是 bug。

## Perf 数字变化 (V5.79 → V5.83)

- `no_eval`: 0.20ms → 3.0ms p50 (15x ↑,per-fact clean + 2-pattern join 工作)
- `no_eval_3way`: 0.10ms → 1.3ms p50
- `no_eval_5r`: 0.56ms → 5.2ms p50
- `eval`: 0.25ms → 1.2ms p50

`baseline.json` 同步更新 V5.83 数字。性能回归来源:`evaluationContext.clean()` +
`resetStickyStateOnly()` 每个 fact 都跑,O(n) 开销,2000 fact → 显著慢。后续
优化方向:增量 reset(只重置该 fact 涉及的 ObjectTypeActivity 路径,不全 rete
reset)— 留 V5.84+。

## Test count

- V5.82: 620 非 perf + 10 perf = 630 total
- V5.83: 620 非 perf + 10 perf(AllFactsMap 3 + TwoPattern 2 + SingleRule 3 +
  DrlRebuild 1 + EvalBenchmark 4)= 630 total,0 failure

## Files changed

| File | +/- | 用途 |
|---|---|---|
| `runtime/rete/ReteInstance.java` | +27/-0 | 新增 resetStickyStateOnly() 方法 |
| `runtime/KnowledgeSessionImpl.java` | +9/-0 | evaluationRete 每 fact clean + resetStickyStateOnly |
| `rete/perf/EvalBenchmarkV579.java` | +12/-12 | 收紧 4 scenario 期望值 + 注释更新 |
| `rete/perf/TwoPatternJoinFiresTest.java` | +2/-2 | 测试 twoPatternWithNoise 改用 noise-first 顺序 |
| `rete/perf/SingleRuleFiresBDD.java` | +0/-0 | 已有 1+1 fact test,V5.83 不变 |
| `test/resources/perf/baseline.json` | +12/-12 | V5.83 数字同步 |
| `docs/notes/v583-rete-sticky-state-fix.md` | +新增 | 本文件 |

## 后续 V5.84+ (可选优化)

`resetStickyStateOnly()` 现在是 O(rete 节点数) 每个 fact 跑 — 2000 fact × 全 rete
reset 是 perf 回归主因。优化方向:
- 增量 reset:只重置该 fact class 对应的 ObjectTypeActivity 路径(其他 class 路径
  状态未污染,无需 reset)
- `evaluationContext.clean()` 改成增量失效(只清该 fact class 涉及的 criteriaId
  缓存)
- 目标:把 V5.83 perf 数字降回 V5.79 量级(< 1ms p50)

不是 V5.83 必修,留 V5.84+ 跟其他 rete 优化一起做。

## 关键教训

1. **不要 mock 无状态 utility 类**(V5.81 教训)→ 写 BDD 时改用真实实例
2. **trace 数据要分阶段打**(V5.83 教训)→ 三层 trace(ObjectTypeActivity / 
   CriteriaActivity / AndActivity)才能定位 "joinNodePassed 跨 fact 粘滞" 是
   哪个 flag 没清
3. **EvaluationContext 缓存语义需要 per-fact 失效** → 业务缓存不能跨 fact 复用
   是 rete 类系统通用教训
4. **sticky state 设计**: 任何标记"已处理"的 flag 都要明确"per-fact"还是"per-rete",
   不区分清楚就会跨 fact 污染
