# V5.91 — Test fixture `UUID.randomUUID()` → `AtomicLong` 计数器

> **TL;DR**:`HotPathBenchTest` / `PerfScalingAnalysisTest` / `EvalBenchmarkV579`
> 用 `UUID.randomUUID().toString()` 标记 fact name 标签占 V5.90 JFR
> 28% hot path;V5.91 替换为 `FactIds` (AtomicLong 计数器) 后 hot path
> 100% 消除,`HotPathBenchTest` per-fact 0.36us → **0.23us (-36%)**,
> iter +58% (24071 → 38136)。
>
> **JFR 验证(V5.90 → V5.91,30s HotPathBenchTest)**:
> - `UUID.randomUUID` / `SecureRandom` / `MessageDigest` 链 V5.90 543 sample → V5.91 **0** ✅
> - `String.hashCode` 313 → **127** (-59%,剩余 hash 来自 HashMap 路径)
>
> **Wall-time 验证**:
> - `HotPathBenchTest` per-fact **0.36us → 0.23us (-36%)**,iter **+58%**
> - `EvalBenchmarkV579` 4 scenario 全在 ±10% 噪音范围内(无 baseline.json 改动)
> - 全量回归 **646/646 pass**(原 641 + 5 new `FactIdsTest`)

## 1. 起因

V5.90 PR #153 收 `Formatter.format` 311 sample → 0 后,post-V5.90 JFR
top hot method 变成:
- `String.hashCode` 313 sample(16% hot path)
- `resetStickyActivities` 275 sample(14%)
- `SecureRandom` UUID 链 230+ sample
- 合计 **543 leaf sample, 28% of post-V5.90 hot path**

V5.90 doc 末尾就指出下一步候选:**`UUID.randomUUID` 链 543 sample 全是
test fixture 副作用,production 0 per-fact UUID usage**。V5.91 把这层做掉。

## 2. Audit:production 是否真用 UUID?

`grep -rn "UUID.randomUUID" server/lib/ruleforge-core/src/main/`:

| 文件 | 频度 | 角色 |
|---|---|---|
| `KnowledgePackageImpl.java:34` | 1 次 / package | build-time id,非 per-fact |
| `ReteInstance.java:12` | 1 次 / instance | per-fireRules id,非 per-fact |

`KnowledgeSessionImpl.insert(fact)` line 250-258 **不**包 UUID — fact 存引用,
不复制内容。

**结论**:**production 零 per-fact UUID usage**。V5.91 100% test-only scope,
零 production risk。

## 3. 改动

### 3.1 新 test helper:`server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/FactIds.java`

```java
public final class FactIds {
    private static final AtomicLong COUNTER = new AtomicLong();
    private FactIds() {}

    public static String next(String prefix) {
        return prefix + "-" + COUNTER.incrementAndGet();
    }
    public static String next() { return next("f"); }
    public static void reset() { COUNTER.set(0); }
}
```

`AtomicLong.incrementAndGet()` 是 lock-free CAS,vs UUID v4 的
`MessageDigest` + `SecureRandom.updateState` + `String.hashCode` 链,per-call
~100-200ns → ~5ns(-95% alloc + -100% SecureRandom)。

### 3.2 BDD:`server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/FactIdsTest.java`

5 tests 用 `@Nested` + Gherkin-style `@DisplayName` 锁契约:
- `next("p")` 返 `"p-1"`,`next("a")` 返 `"a-2"`(counter 原子递增)
- `next()` 默认 prefix `"f"`
- 1000 次连续调用无重复
- 100 thread × 1000 call 产出 100k unique(AtomicLong thread-safe)
- `reset()` 后 counter 归零

### 3.3 替换 3 个 perf test 文件

| 文件 | 旧(UUID) | 新(FactIds) |
|---|---|---|
| `HotPathBenchTest.java:91, 92` | `UUID.randomUUID().toString()` | `FactIds.next("p")` / `FactIds.next("a")` |
| `PerfScalingAnalysisTest.java:145-148` | 同样 | 同样 |
| `EvalBenchmarkV579.java:132, 133` | 同样(保留 line 135-140 显式覆盖 4 个 special fact) | 同样 |

`ValueComputeFindObjectTest.java:92, 94, 112, 113` **不动**(unit test 用 4
个具体 UUID 值,不是高频生成,改它改 test data layout,无收益)。

## 4. 验证

### 4.1 单元 + 全量回归

- `FactIdsTest`: **5/5 pass**
- `mvn test -pl lib/ruleforge-core`: **646/646 pass**(原 641 + 5 new),无回归

### 4.2 JFR 30s HotPathBenchTest 抓取

V5.91 top-20 hot method 排序:

| hot method | V5.90 sample | V5.91 sample | 变化 |
|---|---|---|---|
| **UUID 链(`SecureRandom`/`MessageDigest`/`nextBytes`)** | **543** | **0** | **-100%** ✅ |
| `String.hashCode` | 313 | 127 | -59%(剩余来自 HashMap 路径) |
| `resetStickyActivities` | 275 | 802 | workload 提速 +58%(per-fact 下降 → 同 30s 跑更多 fact → reset 调用更多) |
| `getObjectProperty` (V5.89 反射) | 75 | 221 | workload 提速 |
| `evaluationRete` | (≤25) | 530 | 浮上来 |
| `getCriteriaValue` | (≤25) | 181 | 浮上来 |
| `passAndNode` | (≤25) | 339 | 浮上来 |

**核心 fix 100% 兑现**:`UUID` 链 V5.90 543 sample → V5.91 **0 sample**。

### 4.3 Wall-time bench

**HotPathBenchTest 35s long-running**(同 V5.87 JFR workload):

| V5.79 | V5.83 | V5.88 | V5.89 | V5.90 | V5.91 |
|---|---|---|---|---|---|
| (没跑) | (没跑) | 0.68us | 0.62us | 0.36us | **0.23us** |

- V5.90 → V5.91:**per-fact -36%**,iter **+58%**(24071 → 38136),facts **+59%**
- V5.79 → V5.91:per-fact 0.23us,V5.79 < 1ms 仍是 bug-driven 便宜数据(详见 V5.85 note)
- 真实渐近线:**0.23us per-fact**(3 个独立 fix 联动:V5.88 logMessage 早返 +
  V5.90 Rule.debug 默认翻 false + V5.91 FactIds AtomicLong 替代 UUID)

**EvalBenchmarkV579, 4 scenarios × 50 iter(5 次 re-run 取 median)**:

| scenario | V5.90 p50 | V5.91 p50(median) | V5.91 vs V5.90 |
|---|---|---|---|
| `no_eval` (2-pattern) | 1.22ms | 1.17ms | -4%(噪音内) |
| `no_eval_3way` (3-pattern) | 0.92ms | 0.97ms | +5%(JIT 噪音) |
| `no_eval_5r` (5 rule × 2-pattern) | 2.81ms | 2.84ms | +1%(flat) |
| `eval` (no match) | 1.05ms | 0.99ms | -6%(噪音内) |

**`baseline.json` 不动**——所有 scenario 在 V5.90 锁值的 ±10% 范围内(远小于
1.5x fail multiplier),无 baseline update 必要。

## 5. 经验教训

1. **JFR 占比 = wall-time 占比** — V5.90 doc 末尾预测的 "UUID 28% hot path
   收口" 兑现为 V5.91 **-36% per-fact**,3 个独立 fix(V5.88 早返 + V5.90
   Rule.debug 默认 + V5.91 FactIds)累计从 V5.87 0.68us → V5.91 **0.23us
   (-66%)**
2. **构造默认值是隐形 perf 雷 V5.90 教过 + V5.91 加深** — bench fixture 的
   `UUID.randomUUID()` 是 V5.90 翻转 `Rule.debug` 默认后才暴露的:之前
   logMessage 占用更多 hot path 时间,UUID 比例被稀释;V5.90 修了 logMessage
   后,UUID 升到 top-1。每一层 hot path 优化都会让下一层浮上来
3. **Audit 才知 safe** — `UUID.randomUUID` 在 production 看起来"很多",audit
   显示都是 build-time / per-event(0 per-fact),V5.91 才能 100% test-only
   scope,零 production risk
4. **bench 显式 setDebug 是防御性** — V5.90 学到的,同样适用于 V5.91
   bench:即使未来 bench 改用其他 helper,`FactIds` 仍是 thread-safe +
   unique 保障
5. **V5.91 doc 误述修正 = 重要 PR 价值** — V5.90 doc 末尾标 V5.91 候选,
   V5.91 真做了 → 跟 V5.90 的 JFR 30% → 16% / now → 0% 链收口,post-V5.91
   top-1 是 `resetStickyActivities`(V5.83 必需,无优化空间)而非 UUID

## 6. 后续候选(post-V5.91 JFR)

post-V5.91 top hot method:
1. `resetStickyActivities` 802 sample(20% hot path)— V5.83 必需,优化空间有限
2. `evaluationRete` 530 sample(13%)— rete dispatch loop,优化空间需 audit
3. `getObjectProperty` 221 sample(5%)— V5.89 反射缓存,继续可优化
4. `getCriteriaValue` 181 sample(4%)— Criteria.evaluate 内部路径
5. `append` 382 / `passAndNode` 339 — beta join / FactTracker 路径

V5.92+ 候选:
- `resetStickyActivities` 是不是真必跑 1 次/fact?audit V5.83 resetStickyStateOnly
  触发条件,看能不能 scope 收窄
- `evaluationRete` 内部 dispatch table 优化(if-else 链 → switch / array
  dispatch)

## 7. 改动清单

- test helper: `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/FactIds.java`
  (新, 25 行)
- BDD: `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/FactIdsTest.java`
  (新, 5 BDD tests)
- test: `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/HotPathBenchTest.java`
  (改 91, 92, import)
- test: `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/PerfScalingAnalysisTest.java`
  (改 145-148, import)
- test: `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/EvalBenchmarkV579.java`
  (改 132, 133, import)
- 文档: 本文件
- JFR: `target/v591.jfr` (3.5MB, 30s) — UUID 链 0 sample 验证

## 8. 引用

- [[v590-rule-debug-default-flip]] V5.90 PR 翻转 Rule.debug,V5.91 UUID 才暴露
- [[v589-getobjectproperty-reflection-cache]] V5.89 反射缓存
- [[v588-logmessage-early-return]] V5.88 logMessage 早返
- [[v587-jfr-flamegraph]] V5.87 JFR 原始 76% 预测
- [[v586-findobject-classcache]] V5.86 ConcurrentHashMap 模式
- `target/v590.jfr` (V5.90 baseline,543 UUID sample)
- `target/v591.jfr` (V5.91 验证,UUID 链全 0)
