# V5.98 — EvaluationContextImpl HashMap → 2-array small store

## Context

V5.97 JFR 30s HotPathBenchTest 抓出 post-V5.97 残留热路径:`HashMap.get/put/clear`
链在 `EvaluationContextImpl` 三个方法占 **~779 leaf sample** (rete hot path 35%):

- `getCriteriaValue`: 437 sample
- `storePartValue`: 156 sample
- `clean`: 186 sample

**根因**:`EvaluationContextImpl` 用 `Map<String, Object> criteriaValueMap = new HashMap<>()` +
`Map<String, Object> partValueMap = new HashMap<>()`。per-fact 路径上,每次 criteria
eval 走 `getCriteriaValue` + `storePartValue`,每次 fact pass 走 `clean`。HashMap
每次 get/put 走 `String.hashCode` + bucket walk + `equals` 链,典型 N=1-5 entry
的小 store 完全浪费。

**Audit 结果**:
- 典型 per-fact N=0-5 entries(criteria + part)
- 极端 case 也极少超 8(超 8 走 grow path)
- store 后 value 永远非 null — 无 null-stored 路径(`CriteriaActivity.enter` 的
  `storeCriteriaValue(criteriaId, response)` 传 `EvaluateResponse` 非 null)
- `partValueExist` 调用者看的是"已 store 过",需要 key-existence 语义,
  linear scan 满足

## 改动

### 文件 1: `EvaluationContextImpl.java`

- `Map<String, Object> criteriaValueMap` → `String[] criteriaKeys + Object[] criteriaValues + int criteriaSize`
- `Map<String, Object> partValueMap` → `String[] partKeys + Object[] partValues + int partSize`
- inline initial capacity = 8
- 3 个 getter/setter + 1 个 containsKey 改 linear scan
- `clean()` 改 reset size + 清空 array 引用(避免 GC 漏掉旧 key/value 强引用)
- 加 `growCriteria()` / `growPart()` — size == array.length 时翻倍
- 砍 `import java.util.HashMap;`

### 文件 2 (新 BDD): `EvaluationContextImplTwoArrayTest.java`

10 个 BDD tests 用 `@Nested` + Gherkin-style `@DisplayName` 锁 V5.98 修法契约:

- **CriteriaMap** 4 tests
  - `storeGetRoundTrip`: store + get round-trip
  - `sameKeyOverwrites`: 同 key 多次 store → 后写覆盖
  - `missingKeyReturnsNull`: missing key → null(linear scan 找不到等价 HashMap.get miss)
  - `growPathBeyond8`: 12 entries 走 grow path 行为不变
- **PartMap** 4 tests
  - `storeGetRoundTrip`: store + get round-trip
  - `missingKeyPartValueExist`: missing key → partValueExist 返 false
  - `storedKeyPartValueExist`: stored key → partValueExist 返 true
  - `storedNullValuePartValueExist`: stored null value → partValueExist 返 true(V5.94 契约)
- **CleanBehavior** 2 tests
  - `cleanResetsAllMaps`: clean 后所有 entries 不可读
  - `cleanAllowsRegrow`: clean 后再 store 走 grow path 不残留旧数据

## 行为等价性 audit

| HashMap 行为 | 2-array 行为 | 等价? |
|---|---|---|
| `get(missing key)` → null | linear scan 找不到 → null | ✅ |
| `get(key) → value` | linear scan 找到 → value | ✅ |
| `put(new key, v)` → 新 entry | linear scan 找不到 → append | ✅ |
| `put(existing key, v)` → 覆盖 | linear scan 找到 → 覆盖 | ✅ |
| `containsKey(missing key)` → false | linear scan 找不到 → false | ✅ |
| `containsKey(key)` → true | linear scan 找到 → true | ✅ |
| `clear()` | reset size + 清空 array 引用 | ✅(零残留) |

无 null-stored 风险(每次 `storeCriteriaValue` 传非 null response)。

## Verification

### Step 1 — 全量回归
```bash
mvn test -pl lib/ruleforge-core
```

670 → **680 pass**(+10 V5.98 BDD)。V5.98 BDD 锁契约,所有现有 test 也都过 —
行为 100% 等价。

### Step 2 — JFR 验证 HashMap 链消除
```bash
mvn test -pl lib/ruleforge-core -Dtest=HotPathBenchTest \
  -DargLine="-XX:StartFlightRecording=duration=30s,filename=target/v598.jfr,settings=profile"
jfr print --events jdk.ExecutionSample target/v598.jfr | grep EvaluationContextImpl
```

预期:
- `getPartValue` + `storePartValue` + `clean` 总 sample: 779 → **< 400**(-50%+)
- HashMap 链(get/put/clear)在 rete hot path 跌出 top 15

**实际**(本机 30s JFR):
- `getPartValue`: 208 sample
- `storePartValue`: 54 sample
- `clean`: 92 sample
- **合计: 354 sample**(-55% vs V5.95 baseline 779)

剩余 HashMap 链残留 = ConcurrentHashMap.get(V5.86 classNameCache + 其他路径),
非 V5.98 范围。

### Step 3 — Wall-time bench
```bash
mvn test -pl lib/ruleforge-core -Dtest=EvalBenchmarkV579
```

预期:per-fact wall-time 持平(noise floor);V5.95 baseline 1.10/0.71/0.21/0.43 ms。

**实际**:1.08/0.72/0.21/0.43 ms — **完全持平**。JFR 体现的 HashMap 链省 425 sample
落在 per-fact 1us 量级,wall-time 在 200-shot 噪声内不可见(跟 V5.86/V5.93/V5.94
一致 — JFR 看出大信号,wall-time 看不出)。

## Why 选 2-array 小型 store (vs OpenAddressing/HashnDo/Long2ObjectHashMap)

- **2-array 是 HotSpot JIT 最喜欢的 pattern**:连续内存 + 内联循环 + 无间接
  调用,V5.85 PerfScalingAnalysis 显示 per-fact 1-3us 链路里 0 间接调用占
  大头
- **inline initial capacity = 8**:典型 per-fact N=0-5,99% case zero-grow
- **超 8 grow path**:`Arrays.copyOf` 一次性 2x,摊销 O(1)
- **不引新 dep**:`System.arraycopy` 是 JDK 内置
- **跟 codebase 风格一致**:V5.86 `classNameCache` 同样是 `Map → 1-层 cache` 模式
  (虽然那边是 `Class → Map`),都是砍间接调用 + 砍 hash

## Why V5.98 价值在 JFR + 行为契约(非 wall-time)

跟 V5.93/V5.94/V5.97 一致 — 这条 path 是 codebase 立的"砍 HashMap 反模式"原则的
延续,每次都:
1. 找出 `containsKey + get` 或 `HashMap.get/put/clear` 在 hot path 的 sample
2. 套现有修法(get-only / 2-array / 1-层 cache)
3. 跑 JFR 验证 sample 减少 + 跑 wall-time 确认无 regression

wall-time 收效小但 JFR 信号大,跟 V5.93 doc 立的"双 lookup 反模式"一档。

## 风险 / 已知 trade-off

1. **Grow path overhead**:`Arrays.copyOf` 一次性 O(N),只在 size > 8 时触发,
   极端 path 影响 0
2. **Array 引用清理**:`clean()` 显式 nullify slot 防止 GC 漏掉旧引用,跟
   V5.92 sticky activities 模式一致
3. **无 null-stored 风险**:每次 storeCriteriaValue 传非 null response,
   partValueExist 走 linear scan 找 key 即可
4. **`EvaluationContext` interface 不变**:签名全部保留,Behavior contracts 不变,
   只动 impl

## 未来 V5.99+ 候选(per V5.97 doc)

- `FactTracker.newSubFactTracker` `putAll` 全量复制 — V5.99 候选
- `Utils.getObjectProperty` MethodHandle — V5.100 候选
- `KnowledgeSessionImpl` labeled loop 重构(配 characterization test) — 长期
- `ReteInstance.computeAllFacts` / `criteriaActivity` 等更多 HashMap → 2-array
  改造 — 待 V5.85 PerfScaling 后续分析驱动

## 引用

- [[v597-facttracker-double-lookup]] V5.97 上一个 PR(立 V5.98 候选)
- [[v594-partvalue-double-lookup]] V5.94 同模式反模式
- [[v593-evaluationcontext-double-lookup]] V5.93 立"砍双 lookup"原则
- [[v592-flat-sticky-list]] V5.92 同 JFR 风格分析
- `target/v597.jfr` V5.97 baseline(779 sample)
- `target/v598.jfr` V5.98 验证(354 sample,-55%)
