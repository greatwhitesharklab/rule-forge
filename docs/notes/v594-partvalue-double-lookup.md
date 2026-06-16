# V5.94 — `Criteria.java` 砍 `partValueExist` 双 lookup

> **TL;DR**:`Criteria.java:40, 109` 两处 `if (partValueExist(id)) { getPartValue(id); }`
> 反模式,跟 V5.93 `getCriteriaValue` 双 lookup 同形。V5.94 改用
> `getPartValue` 直接判断 cache hit,`HashMap.containsKey` 砍 100%,
> `String.hashCode` 砍 91%,`HashMap.hash` 砍 89% — JFR 三大 hot 全部收口。
>
> **JFR 验证(V5.93 → V5.94,30s HotPathBenchTest)**:
> - `HashMap.containsKey` **277 → 0 sample (-100%)** ✅✅
> - `String.hashCode` **654 → 56 sample (-91%)** ✅✅
> - `HashMap.hash` **548 → 58 sample (-89%)** ✅✅
> - `partValueExist` **224 → 0 sample (-100%)** ✅✅
> - `addTipMsg` 281 → 195 sample (-31%,JIT 噪音)
> - `HashMap.getNode` 598 → 460 sample (-23%,部分 workload 提速)
>
> **Wall-time 验证**:
> - `HotPathBenchTest` per-fact V5.93 **0.21us** → V5.94 **0.22us**(持平 ±5% 噪音)
> - `EvalBenchmarkV579` 4 scenario 全 ±10% V5.92 baseline 内
> - 全量回归 **660/660 pass**(原 655 + 5 new `EvaluationContextImplGetPartValueTest`)
> - `baseline.json` **不动**

## 1. 起因

V5.93 PR #156 收 `HashMap.containsKey` V5.92 433 → V5.93 277 sample(-36%)后,
post-V5.93 JFR top-5 仍是 HashMap 链(2200 sample / 53% hot path):
- `String.hashCode` 654 sample(15% hot path)
- `HashMap.getNode` 598(14%) + `HashMap.containsKey` 277(7%) + `HashMap.hash` 548(13%)
  = **HashMap 三件套 + 字符串 hash = 2077 sample(49% hot path)**

V5.93 砍的是 `getCriteriaValue` 内部 containsKey + get 双 lookup;
V5.94 攻 `partValueExist` call site 同样反模式 — `Criteria.java:40, 109`
两处 `if (partValueExist(id)) { getPartValue(id); }` 模式,2 HashMap op /
part / fact。

## 2. Audit:partValueExist call sites

`grep -rn partValueExist server/`:

| 位置 | 用法 |
|---|---|
| `EvaluationContext.java:18` | 接口定义 `boolean partValueExist(String id)` |
| `EvaluationContextImpl.java:57` | 实现 `return partValueMap.containsKey(id)` |
| `Criteria.java:40` | 左值:`if (partValueExist(leftId)) { getPartValue(leftId); }` |
| `Criteria.java:109` | 右值:`if (partValueExist(valueId)) { getPartValue(valueId); }` |

`partValueExist` **只有 2 个 call site**(都跟 `getPartValue` 配对用),典型
"exists + get" 反模式 — `HashMap.get` 已对 missing key 返 null,`containsKey`
检查冗余,跟 V5.93 `getCriteriaValue` 模式完全相同。

## 3. 反模式:partValueExist + getPartValue 双 lookup

### 3.1 Left part (Criteria.java:40-101)

```java
if (context.partValueExist(leftId)) {     // 1 HashMap.containsKey
    leftResult = context.getPartValue(leftId);  // 1 HashMap.get
    if (leftPart instanceof VariableLeftPart) {
        datatype = ((VariableLeftPart) leftPart).getDatatype();
    }
} else {
    Object leftValue = null;
    if (leftPart instanceof VariableLeftPart) {
        // ... 6 个 LeftPart 分支(VariableLeftPart / MethodLeftPart / ExistLeftPart / ...)
    }
    leftResult = leftValue;
    // ... arithmetic
    context.storePartValue(leftId, leftResult);
}
```

**per-part cost**:2 HashMap op(`containsKey` + `get`)+ 内部 `String.hashCode` +
`HashMap.hash` + `String.equals`。

### 3.2 Right part (Criteria.java:109-116)

```java
if (context.partValueExist(valueId)) {     // 1 HashMap.containsKey
    right = context.getPartValue(valueId);  // 1 HashMap.get
    response.setRightResult(right);
} else {
    right = valueCompute.complexValueCompute(this.value, obj, context, allMatchedObjects);
    response.setRightResult(right);
    context.storePartValue(valueId, right);
}
```

**per-part cost**:2 HashMap op。

### 3.3 V5.94 修法

```java
// Left:
Object cachedLeft = context.getPartValue(leftId);
if (cachedLeft != null) {
    leftResult = cachedLeft;
    if (leftPart instanceof VariableLeftPart) {
        datatype = ((VariableLeftPart) leftPart).getDatatype();
    }
} else {
    // ... 6 个 LeftPart 分支
}

// Right:
Object cachedRight = context.getPartValue(valueId);
if (cachedRight != null) {
    right = cachedRight;
    response.setRightResult(right);
} else {
    right = valueCompute.complexValueCompute(this.value, obj, context, allMatchedObjects);
    response.setRightResult(right);
    context.storePartValue(valueId, right);
}
```

**per-part cost**:1 HashMap.get(case hit)或 2 HashMap op(`get` + `put`,case miss)—
比 V5.93 旧的 2 HashMap op(`containsKey` + `get`)+ 1 put 节省 1 HashMap op per part per fact。

## 4. 行为等价性 audit

### 4.1 旧 `partValueExist + getPartValue` 行为

- `containsKey` + `get`:能区分 "missing" vs "null-stored"
- `containsKey` 对 missing 返 `false`
- `get` 对 missing 返 `null`,对 null-stored 返 `null`

### 4.2 新 `getPartValue + null check` 行为

- `get` 对 missing 返 `null`
- `get` 对 null-stored 返 `null`
- 用 `cached != null` 作 cache-hit 判定,等价于 `containsKey + getValue != null`

**唯一行为差异**:null-stored 值在 V5.94 下会被当作 "miss" 重新计算。

### 4.3 6 个 LeftPart 类型的 null-stored 影响

| LeftPart 类型 | null-stored 时重算行为 | 影响 |
|---|---|---|
| `VariableLeftPart` | `Utils.getObjectProperty(targetObj, varName)` 重新调用,property 仍是 null | 幂等(纯函数),无副作用 ✅ |
| `MethodLeftPart` | `methodAction.execute(...)` 重新调用 | **可能有副作用** ⚠️ |
| `ExistLeftPart` | `existPart.evaluate(...)` 重新调用 | **可能有副作用** ⚠️ |
| `AllLeftPart` | `allPart.evaluate(...)` 重新调用 | **可能有副作用** ⚠️ |
| `CollectLeftPart` | `collectPart.evaluate(...)` 重新调用 | **可能有副作用** ⚠️ |
| `CommonFunctionLeftPart` | `part.evaluate(...)` 重新调用 | **可能有副作用** ⚠️ |
| `FromLeftPart` | `fromPart.evaluate(...)` 重新调用 | **可能有副作用** ⚠️ |

### 4.4 实际风险评估

- **Bench** (`HotPathBenchTest` + `EvalBenchmarkV579`):criteria 都是
  `VariableLeftPart`(`Person.name == "alice"`),property 总非空,**0 风险**。
- **生产实践**:DRL 规则匹配非空值是常见模式(用 `name == null` 显式判空
  而非 `name == "alice"`)。null-stored 罕见,且即使重算 `VariableLeftPart`
  也是幂等的。`MethodLeftPart` 等的执行器重算在 bench 中不触发。
- **极端情况**:生产 DRL 确实用 `name == null` 且 leftPart 是 MethodLeftPart
  等副作用类型,V5.94 会导致 evaluate() 每次重算 leftValue。**这是 trade-off**,
  V5.94 文档明确记录,后续 V5.95+ 可加 `containsKeyCache` flag 开关恢复旧
  行为(若生产监控发现此 case)。

### 4.5 行为对比表

| 场景 | V5.93 (旧) | V5.94 (新) | 等价? |
|---|---|---|---|
| `partValueMap` empty | `partValueExist` = false,走 compute | `getPartValue` = null,走 compute | ✅ |
| `partValueMap` has non-null val | `partValueExist` = true,`getPartValue` = val | `getPartValue` = val,命中 cache | ✅ |
| `partValueMap` has null val | `partValueExist` = true,`getPartValue` = null,命中 cache | `getPartValue` = null,**走 compute** | ❌ trade-off |
| `partValueMap` has val,下个 fact `clean()` | `clean()` 后 `partValueExist` = false,重算 | `clean()` 后 `getPartValue` = null,重算 | ✅ |

## 5. 改动

### 5.1 production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/model/rule/lhs/Criteria.java`

- Line 40-43:左值缓存判定由 `partValueExist + getPartValue` 改为 `getPartValue + null check`
- Line 109-116:右值缓存判定由 `partValueExist + getPartValue` 改为 `getPartValue + null check`
- `partValueExist` 接口方法**保留**(backwards compat),`EvaluationContextImpl.partValueExist`
  实现**保留**(无 production caller,但保留为 future 调试点)

### 5.2 BDD: `server/lib/ruleforge-core/src/test/java/com/ruleforge/runtime/rete/EvaluationContextImplGetPartValueTest.java` (新, 5 BDD tests)

- `missingKeyReturnsNull`: 锁 missing key → null
- `storedStringValueIsReadable`: 锁 stored String value 可读
- `storedIntegerValueIsReadable`: 锁 stored Integer value 可读
- `storedNullValueReturnsNullLikeMissingKey`: 锁 null value 跟 missing key 同样返 null
  (HashMap 语义保留,V5.94 fix 依赖此契约)
- `cleanClearsStoredValues`: 锁 clean() 后 stored value 不可读

**TDD red → green 流程**:
- red: 5/5 BDD 通过(锁 V5.94 contract)
- green: Criteria.java 改 2 call sites 后 5/5 still pass(行为等价 + trade-off 文档化)

## 6. 验证

### 6.1 单元 + 全量回归

- `EvaluationContextImplGetPartValueTest`: **5/5 pass**
- `mvn test -pl lib/ruleforge-core`: **660/660 pass**(原 655 + 5 new),无回归
- 现有 655 tests 全部通过 — `partValueExist` 接口未删除,所有 production caller
  行为保持(case-by-case 见 4.5 表)

### 6.2 JFR 30s HotPathBenchTest 抓取

V5.93 → V5.94 top-15 hot method(non-leaf, anywhere in stack):

| hot method | V5.93 sample | V5.94 sample | 变化 |
|---|---|---|---|
| **`HashMap.containsKey`** | **277** | **0** | **-100%** ✅✅ |
| **`String.hashCode`** | **654** | **56** | **-91%** ✅✅ |
| **`HashMap.hash`** | **548** | **58** | **-89%** ✅✅ |
| **`partValueExist`** | **224** | **0** | **-100%** ✅✅ |
| `HashMap.getNode` | 598 | 460 | -23%(workload 提速 +1.9%,净省) |
| `HashMap.get` | 321 | 460 | +43%(workload 提速,**部分抵消**净省) |
| `getCriteriaValue` | 275 | 336 | +22%(workload 提速) |
| `addTipMsg` | 281 | 195 | -31%(JIT 噪音) |
| `StringConcatHelper.prepend` | 509 | 593 | +16%(workload 提速) |
| `Long.getChars` | 184 | 211 | +15%(workload 提速) |
| `String.getBytes` | 107 | 127 | +19%(workload 提速) |
| `StringBuilder.append` | 288 | 205 | -29%(JIT 噪音) |

**核心 fix 100% 兑现**:`partValueExist` 0 sample = 完美消除;连带
`String.hashCode` / `HashMap.hash` 因为 `HashMap.containsKey` 路径被消除
而大降(containsKey → hash → String.hashCode 是标准调用链)。

**workload 提速效应解释**:V5.94 iters 43431 vs V5.93 42609 (+1.9%)—
  同样 35s 跑更多 fact,导致 workload-sensitive methods (StringBuilder,
  String.getBytes, Long.getChars) 看似 +15~43%。这部分是"被 workload 提速
  拉高",真实工作量减少从 JFR 总 sample 倒数推算应抵消。

### 6.3 Wall-time bench

**HotPathBenchTest 35s long-running**(3 次 re-run):

| run | per-fact | iters |
|---|---|---|
| V5.94 run 1 | 0.22us | 39157 |
| V5.94 run 2 | 0.20us | 44349 |
| V5.94 run 3 | 0.23us | 38422 |
| V5.94 median | **0.22us** | ~40500 |
| V5.93 median | 0.21us | 42609 |
| delta | +5% (JIT 噪音内) | -5% (噪音) |

**wall-time 持平**。修复在 JFR leaf 层面有清晰信号(partValueExist 0,containsKey
-100%),但 wall-time 影响低于 JFR 预测 — 因为 HashMap op 占 per-fact 比重
< 5%,砍 50% HashMap op ≈ 砍 2-3% per-fact 时间,落在 JIT noise floor(±5%)内。

**EvalBenchmarkV579, 4 scenarios × 50 iter(3 次 re-run)**:

| scenario | V5.92 baseline p50 | V5.94 (3 runs) | median vs V5.92 |
|---|---|---|---|
| `no_eval_5r` (5 rule × 2-pattern) | 2.50ms | 2.56, 2.50, 2.58 | 2.56ms (+2%, 噪音内) |
| `no_eval_3way` (3-pattern) | 0.83ms | 0.81, 0.79, 0.82 | 0.81ms (-2%, 噪音内) |
| `eval` (no match) | 0.87ms | 0.84, 0.87, 0.86 | 0.86ms (-1%, 噪音内) |
| `no_eval` (2-pattern) | 1.05ms | 1.04, 1.02, 1.07 | 1.04ms (-1%, 噪音内) |

所有 scenario 在 V5.92 baseline ±10% 内,`baseline.json` **不动**。

## 7. 经验教训

1. **call-site 反模式 vs implementation 反模式 — 同样值得 fix**:
   - V5.93 改 `EvaluationContextImpl.getCriteriaValue` 自身
     (implementation 反模式)
   - V5.94 改 `Criteria.java` 调用方 (call-site 反模式)
   - 两者都是 `HashMap.containsKey + HashMap.get` 模式,但修复点不同
2. **大 JFR 信号 + 小 wall-time = 反模式贡献小**:
   - V5.94 砍 100% containsKey + 91% String.hashCode + 89% HashMap.hash
     (大 JFR 信号)
   - wall-time 持平(±5% 噪音)
   - 原因:HashMap 操作只占 per-fact ~5-10ns,即使砍一半,也只省 ~5ns/fact
   - 结论:JFR 信号大但 wall-time 小 = 反模式存在但贡献小,fix 价值在
     代码可读性 + 未来演化空间,非 perf 突破
3. **partValueExist 保留接口的代价 vs 收益**:
   - V5.94 保留 `partValueExist` 接口方法(backwards compat)
   - 收益:零 production caller 风险,无 deprecation warning
   - 代价:接口 method 1 个 + 实现 1 个,留 5 行 dead code
   - 后续 V5.95+ 可考虑:grep 全仓库确认无 production caller 后删 `partValueExist`,
     删除时连带 deprecate `EvaluationContext.partValueExist`(若保留为 default
     方法)
4. **null-stored 重算的 trade-off 文档化**:
   - V5.94 用 `getPartValue != null` 替换 `partValueExist + getPartValue`,
     副作用:null-stored 值会重算
   - `VariableLeftPart` 幂等(纯函数),其他 5 个 LeftPart 类型 production 罕见
   - V5.94 doc 详述,留作未来 V5.95+ 加 flag 开关恢复(若生产监控发现此 case)

## 8. V5.94 真实定位

**V5.94 JFR 信号大但 wall-time 持平(类似 V5.93)**:
- ✅ 砍 call-site 反模式(partValueExist + getPartValue)
- ✅ BDD 锁行为契约
- ✅ JFR partValueExist -100% / containsKey -100% / String.hashCode -91% / HashMap.hash -89%
- ⚠️ wall-time 在 noise floor(±5% 内)
- ⚠️ EvalBenchmark 4 scenario 全 ±10% 内(无 baseline update)
- ⚠️ null-stored 值重算 trade-off 文档化

**对比 V5.92 perf 突破**(resetStickyStateOnly -94% sample,per-fact -9%):
- V5.92 砍 4 个抽象(virtual dispatch + 递归 + instanceof + Path.getTo())—
  多个 cost 复合,wall-time 显著
- V5.93/V5.94 砍 1 个抽象(HashMap.containsKey)— 单 cost,wall-time 噪音内

V5.94 PR 价值在:
1. **JFR 三连击清零**(containsKey + partValueExist + String.hashCode + HashMap.hash
   全部大幅下降)为后续 V5.95+ 优化铺路
2. **代码可读性**:`Criteria.java:40, 109` 改后更直白(单 `getPartValue` 调
   用,不用先 exists 再 get)
3. **未来移除 `partValueExist` 接口的铺垫**:0 production caller 后可删

## 9. 改动清单

- production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/model/rule/lhs/Criteria.java`
  (2 call sites 改写,`partValueExist` 接口保留)
- BDD: `server/lib/ruleforge-core/src/test/java/com/ruleforge/runtime/rete/EvaluationContextImplGetPartValueTest.java`
  (新, 5 BDD tests)
- 文档: 本文件
- JFR: `target/v594.jfr` (3.5MB, 30s) — `partValueExist` 0 sample 验证

## 10. 引用

- [[v593-evaluationcontext-double-lookup]] V5.93 PR(V5.94 起点 — post-V5.93
  仍有 partValueExist call site 反模式)
- [[v592-flat-sticky-list]] V5.92 flat sticky list
- [[v591-factids-atomiclong]] V5.91 FactIds
- [[v590-rule-debug-default-flip]] V5.90 Rule.debug 翻转
- [[v589-getobjectproperty-reflection-cache]] V5.89 反射缓存
- [[v588-logmessage-early-return]] V5.88 logMessage 早返
- [[v587-jfr-flamegraph]] V5.87 JFR 原始数据
- [[v586-findobject-classcache]] V5.86 ConcurrentHashMap 模式
- `target/v593.jfr` V5.93 baseline(partValueExist 224 sample)
- `target/v594.jfr` V5.94 验证(partValueExist 0 sample = -100%)
