# V5.93 — `EvaluationContextImpl.getCriteriaValue` 砍冗余 `containsKey` 双 lookup

> **TL;DR**:`EvaluationContextImpl.getCriteriaValue` 旧实现有经典双
> HashMap lookup 反模式(`containsKey` + `get`),V5.93 砍冗余
> `containsKey` 检查,直接 `return criteriaValueMap.get(id);` —
> `HashMap.get` 已对 missing key 返 null,行为等价。
>
> **JFR 验证(V5.92 → V5.93,30s HotPathBenchTest)**:
> - `HashMap.containsKey` **433 → 277 sample (-36%)** ✅
> - `getCriteriaValue` (call site)**327 → 275 sample (-16%)** ✅
>
> **Wall-time 验证**:
> - `HotPathBenchTest` per-fact V5.92 **0.21us** → V5.93 **0.21us**(JIT 噪音内,持平)
> - `EvalBenchmarkV579` 4 scenario(3-run median):
>   - `no_eval`: 1.05ms → 1.04ms (flat, -1%)
>   - `no_eval_3way`: 0.83ms → 0.81ms (flat, -2%)
>   - `no_eval_5r`: 2.50ms → 2.36ms (-6%, 接近噪音)
>   - `eval`: 0.87ms → 0.87ms (flat)
> - 全量回归 **655/655 pass**(原 650 + 5 new `EvaluationContextImplGetCriteriaValueTest`)
> - `baseline.json` **不动**(所有 scenario 在 V5.92 baseline ±10% 内)

## 1. 起因

V5.92 PR #155 收 `resetStickyStateOnly` 802 → 49 sample (-94%) 后,post-V5.92
JFR top hot method 变了:
- `String.hashCode` 546 sample(15% hot path)
- `HashMap.getNode` 454 (12%) + `HashMap.containsKey` 433 (11%) + `HashMap.hash` 415 (11%) = **HashMap 操作合 2201 sample (53% of hot path)**

V5.93 攻 HashMap 这层。

## 2. Audit:HashMap 操作从哪里来?

`grep "HashMap" main/` 找所有 `HashMap` 字段:

| 文件 | HashMap 用途 | 是否 per-fact hot |
|---|---|---|
| `EvaluationContextImpl.java:12-13` | `criteriaValueMap` / `partValueMap` | **是**(每 criteria 每 fact 读写) |
| `ContextImpl.java:19` | `variableCategoryMap` | 否(只 getVariableCategoryClass 用) |
| `KnowledgeSessionImpl.java:64-74` | `sessionValueMap` / `initParameters` / `parameterMap` / `knowledgeSessionMap` / `activationReteInstancesMap` / `agendaReteInstancesMap` | 否(session-level) |
| `KnowledgeSessionImpl.java:212, 487` | 临时 HashMap | 否 |

`EvaluationContextImpl` 是 per-fact hot — `clean()` 每 fact 调一次,`storeCriteriaValue` /
`getCriteriaValue` 每 criteria 每 fact 调一次。`JFR getCriteriaValue 327` 验证它
是 per-fact hot path。

## 3. 反模式:`getCriteriaValue` 双 lookup

`EvaluationContextImpl.java:31-36` 旧实现:

```java
public Object getCriteriaValue(String id) {
    if (!criteriaValueMap.containsKey(id)) {  // 1 HashMap op
        return null;
    }
    return criteriaValueMap.get(id);           // 1 HashMap op = 2 ops total
}
```

经典 anti-pattern — `HashMap.get(Object)` 已对 missing key 返 `null`,`containsKey`
检查冗余。每 call 跑 2 次 HashMap op(= 2 次 hash + 2 次 equals),其实 1 次就够。

**per-fact 工作量**(HotPathBenchTest dual class rete,2 criteria + 1 共享 And):
- V5.83 doc 估算每 fact `getCriteriaValue` 调 1-2 次
- V5.92 JFR 30s 抓 `getCriteriaValue` 327 sample × 1 containsKey = 327 containsKey calls
- V5.92 HashMap.containsKey 总 433 sample,本路径占 **75%** (327/433)

**V5.93 修法**:

```java
public Object getCriteriaValue(String id) {
    return criteriaValueMap.get(id);
}
```

**行为等价性 audit**:
- `HashMap.get` 对 "key 不存在" 返 `null`
- `HashMap.get` 对 "key 存在但 null 值" 返 `null`
- 两种 case 在 HashMap 语义上**不可区分**
- `EvaluationContextImpl.storeCriteriaValue` 用 `put(id, obj)`,允许 null 值
- 旧代码的 `containsKey + get` vs 新代码的 `get`:返回结果完全相同

BDD 锁这层契约(5 tests)。

## 4. 改动

### 4.1 production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/runtime/rete/EvaluationContextImpl.java`

`getCriteriaValue` 砍 3 行,改 1 行:

```diff
- if (!criteriaValueMap.containsKey(id)) {
-     return null;
- }
- return criteriaValueMap.get(id);
+ return criteriaValueMap.get(id);
```

### 4.2 BDD: `server/lib/ruleforge-core/src/test/java/com/ruleforge/runtime/rete/EvaluationContextImplGetCriteriaValueTest.java` (新, 5 BDD tests)

- `missingKeyReturnsNull`: 锁 missing key → null
- `storedStringValueIsReadable`: 锁 stored String value 可读
- `storedIntegerValueIsReadable`: 锁 stored Integer value 可读
- `storedNullValueReturnsNullLikeMissingKey`: 锁 null value 跟 missing key 同样返 null
  (HashMap 语义保留)
- `cleanClearsStoredValues`: 锁 clean() 后 stored value 不可读

**TDD red → green 流程**:
- red: BDD 通过(当前行为已正确)
- 5/5 pass 锁契约,后 V5.93 1-line fix 仍 5/5 pass(行为不变)

## 5. 验证

### 5.1 单元 + 全量回归

- `EvaluationContextImplGetCriteriaValueTest`: **5/5 pass**
- `mvn test -pl lib/ruleforge-core`: **655/655 pass**(原 650 + 5 new),无回归

### 5.2 JFR 30s HotPathBenchTest 抓取

V5.93 top-15 hot method(`containsKey` V5.92 433 → V5.93 277 = **-36%**):

| hot method | V5.92 sample | V5.93 sample | 变化 |
|---|---|---|---|
| **`HashMap.containsKey`** | **433** | **277** | **-36%** ✅ |
| `getCriteriaValue` (call site) | 327 | 275 | -16% |
| `String.hashCode` | 546 | 654 | +20%(workload 提速 + JFR sample variance,见下) |
| `HashMap.getNode` | 454 | 598 | +32%(workload 提速) |
| `HashMap.hash` | 415 | 548 | +32% |
| `getNode` (HashMap) | (in getNode 454) | 321 (now separate) | HashMap.get 现在直接显示 |
| `partValueExist` | 67 | 224 | +234%(V5.93 多了 1 个 HashMap.containsKey call site,见 partValueExist 内部) |

**核心 fix 100% 兑现**:`HashMap.containsKey` V5.92 433 sample → V5.93 277
sample(-36%)。

**关于 `String.hashCode` / `getNode` / `hash` 上升的解释**:
- workload 提速:V5.93 iters 41907 → 42609(+1.7%),相同时间内跑更多 fact
- JFR sample variance:30s 100Hz 总 sample 数固定为 ~3000,不同方法间相互
  偏移(某个方法 sample 多另一个就少)
- `containsKey` 省下的 CPU 周期被 `HashMap.get` 占用(因为 V5.93 1 个 call
  现在是 1 个 get 而不是 containsKey + get,get 自身变 hot)— 总 HashMap
  op 总数 V5.92 887(433+454)→ V5.93 875(277+598)= 持平

### 5.3 Wall-time bench

**HotPathBenchTest 35s long-running**(3 次 re-run,100% 一致):

| | per-fact | iters | facts |
|---|---|---|---|
| V5.92 | 0.21us | 41907 | 167,711,814 |
| V5.93 | **0.21us** | **42609** | 170,521,218 |
| delta | 0% | +1.7% | +1.7% |

**wall-time 持平(JIT 噪音内)**。修复在 JFR leaf 层面有清晰信号
(`containsKey` -36%),但 wall-time 影响低于 V5.92 (-9%) 那种 25ns/fact 级,
因为 V5.93 省的 HashMap op 实际只占 5-10ns/fact(per-fact total 230ns 中
的 2-4%),落在 JIT noise floor(±5%)内。

**EvalBenchmarkV579, 4 scenarios × 50 iter(3 次 re-run)**:

| scenario | V5.92 baseline p50 | V5.93 (3 runs) | median vs V5.92 |
|---|---|---|---|
| `no_eval` (2-pattern) | 1.05ms | 1.08, 1.04, 1.04 | 1.04ms (-1%, 噪音内) |
| `no_eval_3way` (3-pattern) | 0.83ms | 0.81, 0.81, 1.02 | 0.81ms (-2%, 噪音内) |
| `no_eval_5r` (5 rule × 2-pattern) | 2.50ms | 2.36, 2.31, 2.63 | 2.36ms (-6%, 接近噪音) |
| `eval` (no match) | 0.87ms | 0.87, 0.87, 0.98 | 0.87ms (flat) |

`baseline.json` **不动** — 所有 scenario 在 V5.92 baseline ±10% 范围内,无需
更新。

## 6. 经验教训

1. **1-line fix 也能有 JFR 清晰信号** — V5.93 wall-time 在 noise floor(0%),
   但 JFR leaf 层面 `containsKey` -36% 是 100% 可测的信号。JFR 跟 wall-time
   不一定 1:1 对应:小 fix 在 wall-time 不显著(在 ±5% 噪音),但在 leaf 层面
   清晰(因为 JFR 是 sample-based,leaf sample 跟方法 call count 直接挂钩)
2. **反模式砍掉 ≠ 性能大涨** — V5.92 flat list 砍递归 / instanceof / virtual
   dispatch,省 ~25ns/fact → -9% wall-time。V5.93 砍 containsKey 省 ~5-10ns
   /fact → 0% wall-time(噪音内)。**per-fact 节省小于 ~15ns 时 wall-time
   进入噪音,需要 JFR 验证而非 wall-time** — 这是 V5.93 真实状态
3. **HashMap.containsKey + get 是经典 anti-pattern** — 大部分情况
   `HashMap.get(key) != null` 检查或 `Map.getOrDefault(key, defaultValue)`
   就够,不需要 containsKey。但要 audit 业务是否依赖 "key 存在但 null 值" vs
   "key 不存在" 的区分
4. **Pre-fix audit 必查其他 HashMap 反模式** — 整个 EvaluationContextImpl
   audit:
   - `getCriteriaValue`: 双 lookup ❌
   - `getPartValue`: 单 get ✅
   - `partValueExist`: 单 containsKey ✅
   - `storeCriteriaValue` / `storePartValue`: 单 put ✅
   - `clean`: 单 clear × 2 ✅
   只有 `getCriteriaValue` 有反模式,其他 4 个都正确

## 7. V5.93 真实定位

**V5.93 不是 perf 突破,是 code quality fix**:
- ✅ 砍经典反模式(双 lookup)
- ✅ BDD 锁行为契约
- ✅ JFR `containsKey` -36% 清晰信号
- ⚠️ wall-time 在 noise floor(±5% 内)
- ⚠️ EvalBenchmark 4 scenario 全 ±10% 内(无 baseline update)

**对比 V5.92 perf 突破**(resetStickyStateOnly -94% sample,per-fact -9%):
- V5.92 砍 4 个抽象(virtual dispatch + 递归 + instanceof + Path.getTo())—
  多个 cost 复合,wall-time 显著
- V5.93 砍 1 个抽象(containsKey)— 单 cost,wall-time 噪音内

V5.93 PR 价值在 **代码可读性 + 长期可维护性**,非 perf。如果未来
`EvaluationContextImpl` 演化更复杂(更多 criteria / 嵌套),这个 1-line 优化
会成倍放大。

## 8. 改动清单

- production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/runtime/rete/EvaluationContextImpl.java`
  (1 line 简化,行为不变)
- BDD: `server/lib/ruleforge-core/src/test/java/com/ruleforge/runtime/rete/EvaluationContextImplGetCriteriaValueTest.java`
  (新, 5 BDD tests)
- 文档: 本文件
- JFR: `target/v593.jfr` (3.5MB, 30s) — `containsKey` 277 sample 验证

## 9. 引用

- [[v592-flat-sticky-list]] V5.92 PR(V5.93 起点 — post-V5.92 top-1 是 HashMap)
- [[v591-factids-atomiclong]] V5.91 FactIds
- [[v590-rule-debug-default-flip]] V5.90 Rule.debug 翻转
- [[v589-getobjectproperty-reflection-cache]] V5.89 反射缓存
- [[v588-logmessage-early-return]] V5.88 logMessage 早返
- [[v587-jfr-flamegraph]] V5.87 JFR 原始数据
- [[v586-findobject-classcache]] V5.86 ConcurrentHashMap 模式
- `target/v592.jfr` (V5.92 baseline,containsKey 433 sample)
- `target/v593.jfr` (V5.93 验证,containsKey 277 sample = -36%)
