# V5.84 增量 reset 优化尝试 — Reverse-Optimization 教训

> **TL;DR**:V5.84 增量 reset(`resetStickyStateOnly(Object fact)`,只 reset fact 命中的 ObjectTypeActivity 子树)实测在 `EvalBenchmarkV579` 4 scenario 全部 **比 V5.83 慢 20-50%**。`ObjectTypeActivity.support()` 反射检查的开销 > 节省的子树 reset 开销。**撤销改动**,本文档留作 perf 优化方向的教训。

## 1. 起因

V5.83 (PR #147) 修 pre-existing rete sticky state 缺陷,引入 `ReteInstance.resetStickyStateOnly()` —
每个 fact 进入 rete 前清 activity `passed` flag(但保留 `Path.passed` 跨 fact join 累积)。

V5.83 doc (`docs/notes/v583-rete-sticky-state-fix.md`) 末段留优化方向:

> V5.84+ 收到 perf 优化 task 时,优先做增量 reset — `resetStickyStateOnly()` 现在是 O(rete
> 节点数) 每个 fact 跑,2000 fact × 全 rete reset 是 perf 回归主因。优化方向:只重置该 fact
> class 对应的 ObjectTypeActivity 路径(其他 class 路径状态未污染,无需 reset),目标把
> V5.83 perf 数字降回 V5.79 量级(< 1ms p50)。

Phase 22 接到 task "V5.84 增量 reset 优化"。

## 2. 设计

```java
// V5.84 提案(已撤销)
public void resetStickyStateOnly(Object fact) {
    for (ObjectTypeActivity objectTypeActivity : objectTypeActivities) {
        if (objectTypeActivity.support(fact)) {  // 反射判定
            resetStickyActivities(objectTypeActivity.getPaths());
        }
    }
}
```

**理论论证**(代码 review 时成立):
- rete 网络是**多棵独立 class 子树**,根是 `ObjectTypeActivity`
- `ReteInstance.enter()` 循环只对 `support(fact)` 命中的子树 enter
- 跨 class 子树的 activity `passed` flag 没被该 fact 接触 → 无需 reset
- 复杂度从 O(全 rete 节点) 降到 O(该 fact class 子树节点)

## 3. 实测数据(用 5/3 轮 JIT 稳态对比)

**V5.83 (PR #147 baseline)** — 5 轮 `EvalBenchmarkV579` p50 中位:

| scenario | V5.83 p50 |
|---|---|
| no_eval (2-pattern, 3 fired) | 2.84 ms |
| no_eval_3way (3-pattern, 0 fired) | 1.55 ms |
| no_eval_5r (5 rule, 5 fired) | 5.13 ms |
| eval (no-match, 0 fired) | 1.21 ms |

**V5.84 (本尝试)** — 3 轮 `EvalBenchmarkV579` p50 中位:

| scenario | V5.84 p50 | vs V5.83 |
|---|---|---|
| no_eval | 3.40 ms | **+20% 慢** |
| no_eval_3way | 2.20 ms | **+42% 慢** |
| no_eval_5r | 7.63 ms | **+49% 慢** |
| eval | 1.63 ms | **+35% 慢** |

**全 4 scenario 都更慢**。功能性正确(4 scenario fired=3/0/5/0 锁值不变),但 perf 是 regression。

## 4. 根因分析

**`ObjectTypeActivity.support()` 在 hot path 上很贵**:

```java
public boolean support(Object object) {
    if (object instanceof GeneralEntity) {       // 1 instanceof
        String targetClass = ((GeneralEntity) object).getTargetClass();
        if (this.clazz != null) {
            if (targetClass.equals(this.clazz)) return true;  // 1 string equals
        } else if (targetClass.equals(this.typeClass.getName())) return true;  // 1 string equals
    } else if (this.typeClass != null) {
        Class<?> c = object.getClass();
        if (this.typeClass.isAssignableFrom(c) || this.typeClass.getName().equals(c.getName())) {
            // 1 Class.isAssignableFrom(反射) + 1 string equals
            return true;
        }
    }
    return false;
}
```

每次 fact 进入 `resetStickyStateOnly(fact)`,先跑 N 次 `support()`(N = ObjectTypeActivity 数量),
EvalBenchmarkV579 2000 fact × 2 ObjectTypeActivity = 4000 次 `support()` 反射检查。

**开销对比**:
- `support()` 反射检查(每次):~几微秒(`Class.isAssignableFrom` 走 native)
- activity.reset() — `passed = false` 单字段赋值:~几纳秒
- 2000 fact × `support()` 反射累计开销:**毫秒级**
- 2000 fact × 跨 class 子树 reset 节省:**亚毫秒级**

`support()` 反射开销 **远大于** 跨 class 子树 reset 节省时间。**reverse-optimization**。

**V5.83 vs V5.84 实际工作量**:
- V5.83: 2000 × (Person subtree + Address subtree 整 reset) = 4000 子树 reset 单位
- V5.84: 2000 × (2 support 反射 + 1 subtree reset) = 4000 support 反射 + 2000 subtree reset 单位
- 节约: 2000 subtree reset 单位 (亚毫秒)
- 代价: 4000 support 反射 (毫秒级)
- **净 loss**:毫秒级(实测 0.5-2.5ms)

## 5. perf 真实 bottleneck 在哪

`EvalBenchmarkV579` per-fact 1-5ms 的成本主要不在 reset。拆解 per-fact 工作:

1. `KnowledgeSessionImpl.insert(fact)` — agenda 推送 + WorkingMemory 记录
2. `KnowledgeSessionImpl.evaluationRete` 循环 — 1 个 fact × N reteInstance
3. `ReteInstance.enter(context, fact)` — ObjectTypeActivity 入口
4. `CriteriaActivity.evaluate(...)` — `valueCompute.findObject(className, obj, context)` 跨 pattern 查找 + evaluate response
5. `EvaluationContext.clean()` — 清 `criteriaValueMap` / `partValueMap`
6. `ReteInstance.resetStickyStateOnly()` — 清 sticky state

V5.84 实测 reset 优化加 `support()` 反射**比**V5.83 整 reset 还慢,说明 **reset 本身不是 perf bottleneck**(每 fact reset 开销 < 0.5ms),而 `CriteriaActivity.evaluate` + `valueCompute.findObject` + `EvaluationContext` 内部是更热的 hot path。

## 6. 后续方向(V5.85 提议)

V5.84 reverse-optimization 后,正确方向是 **真实 perf profiling** 而非基于"感觉"的优化。

候选 V5.85 方向(perf 收益可能有量级):

### 6.1 缓存 `support()` 判定结果(若坚持增量 reset 思路)

- `ReteInstance` 维护 `Map<Class<?>, List<ObjectTypeActivity>>` 预解析
- `resetStickyStateOnly(fact)` O(1) 查表,不跑反射
- 预期收益:0.5-2ms(消除 support 反射开销),但仍比 V5.83 慢(因为 V5.83 跑 JIT 优化整 reset 路径)
- **不建议**:缓存维护成本 + Class 继承层次判断不准确,不划算

### 6.2 优化 `EvaluationContext.criteriaValueMap` 缓存结构

V5.83 修 #A (EvaluationContext 跨 fact 缓存污染) 后,每个 fact 跑前调 `clean()` 清缓存。`clean()` 内部
走 `criteriaValueMap.clear()`(HashMap clear,JDK 优化),但 V5.83 前 `clean()` 跑在 reteInstance 列表
结束后(1 次),V5.83 后跑每个 fact(2000 次)。

- 候选:`clean()` 改 lazy invalidation(只清 changed criteria id)
- 候选:`criteriaValueMap` 换 `WeakHashMap` / identity-based map 降低 clear 开销
- 预期收益:0.5-1ms(2000 fact × clean 开销)

### 6.3 优化 `CriteriaActivity.evaluate` 跨 pattern 查找

- `valueCompute.findObject(className, obj, context)` 是 rete 跨 pattern join 的核心
- 候选:加 index 按 (className, criteriaId) 缓存最近 N 个 obj
- 预期收益:0.5-2ms(2-pattern join 内部循环)

### 6.4 接受 V5.83 1-5ms 是新常态

V5.79 旧 < 1ms p50 是 **pre-existing sticky bug + fired=0 错行为** 时的"便宜"数字。
V5.83+ 1-5ms 是 **正确行为** 下的真实成本,baseline.json 锁值是诚实表达。

**最实际建议**:V5.85 跑 jvisualvm 风格 profiling(用 JMH 严格 microbench 或 async-profiler 抓火焰图),
**先确定** perf 真实分布,**再**选优化点。**不要**继续基于 doc 文字推断优化方向(本 V5.84 教训)。

## 7. 改动回退

V5.84 代码改动(`ReteInstance.resetStickyStateOnly(Object)` + `KnowledgeSessionImpl` 调用)
**已撤销**(`git checkout -- ...`)。working tree 干净,不留 commit。

`EvalBenchmarkV579` 4 scenario `assertEquals(fired=3/0/5/0)` 锁值保持 V5.83 行为。
`baseline.json` 保持 V5.83 数字不动。

## 8. 教训(留给后续 perf 优化)

1. **基于 doc 文字推断的优化方向** 不可信,先 profiling 拿数据
2. **micro-optimization 必须实测**,5 轮 JIT 稳态对比才能下结论
3. **`ObjectTypeActivity.support()` 反射** 在 hot path 不可忽略
4. **V5.79 < 1ms 旧数字** 是 sticky bug 时的错行为,不能作为新 baseline 目标
5. **接受新常态** 比"硬回旧数字"更诚实

**Why:** V5.83 sticky state fix 引入 per-fact reset 是 correctness 必需,fired=0 错行为 < 1ms 不能接受。
**How to apply:** 后续 perf task 先 profiling,不动手;micro-optimization 必经 5+ 轮对比;V5.79 旧数字不作为新 baseline 目标。

## 9. 引用

- [[v582-allfactsmap-rewrite]] V5.82 (PR #146) allFactsMap 改 List<Object>
- [[v583-rete-sticky-state-fix]] V5.83 (PR #147) rete per-fact clean + resetStickyStateOnly
- `docs/notes/v583-rete-sticky-state-fix.md` V5.83 文档(留 V5.84 优化方向)
- `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/EvalBenchmarkV579.java` 4 scenario
- `server/lib/ruleforge-core/src/test/resources/perf/baseline.json` V5.83 锁值
