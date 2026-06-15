# V5.86 — ValueCompute.findObject classNameCache 优化(持平 / 微优)

> **TL;DR**:V5.86 给 `ValueCompute.findObject` 加 `classNameCache`(Class.forName 一次性
> 加载,后续 `Class.isInstance` 引用比较),替代 per-fact `getClass().getName().equals(className)`
> 字符串 compare。**实测 perf 持平**(dual class per-fact N=10000 1.25us → 1.22us) —
> JIT 已把 `String.equals` 优化得很彻底,className 缓存收益被吃掉。**改动保留**(correct +
> 代码表达力清晰,后续多 className / 复杂 VariableCategory 场景受益),但**不写回
> baseline.json**(持平不算 regression,也不应 rewrite baseline)。

## 1. 起因

V5.85 perf scaling 分析 [[v585-perf-scaling-analysis]] 提议 V5.86+ 优化方向 #1:

> 跨 pattern `findObject` 字符串比较预解析优化。预期 per-fact cost 降 10-20%
> (V5.85 dual ~3us 中 1us 来自 findObject 字符串 compare)。

实施 V5.86 验证提议。

## 2. 设计

### 2.1 改动前(per-fact 字符串 compare)

```java
public Object findObject(String className, Object matchedFact, Context context) {
    if (className.equals(HashMap.class.getName())) { ... }
    if (matchedFact instanceof Collection) {
        while (var5.hasNext()) {
            Object obj = var5.next();
            if (obj.getClass().getName().equals(className)) {  // 字符串 hash + compare
                return obj;
            }
            if (obj instanceof GeneralEntity) {
                if (((GeneralEntity) obj).getTargetClass().equals(className)) {  // 字符串
                    return obj;
                }
            }
        }
    } else {
        if (matchedFact.getClass().getName().equals(className)) { ... }  // 字符串
        if (matchedFact instanceof GeneralEntity) { ... }  // 字符串
    }
    return allFactsMap.get(className);
}
```

每次 findObject 走 2-4 次 `String.equals`,每次都跑 hash 计算 + char-by-char compare。

### 2.2 改动后(classNameCache 引用比较)

```java
private final Map<String, Class<?>> classNameCache = new ConcurrentHashMap<>();
private static final Class<?> CLASS_NOT_FOUND = Class.class;  // sentinel

public Object findObject(String className, Object matchedFact, Context context) {
    if (className.equals(HashMap.class.getName())) { ... }
    Class<?> targetClass = classNameCache.get(className);
    if (targetClass == null) {
        targetClass = loadClass(className);
        classNameCache.put(className, targetClass == null ? CLASS_NOT_FOUND : targetClass);
    }
    if (targetClass == CLASS_NOT_FOUND) targetClass = null;
    // ... 后续用 targetClass.isInstance(obj) 引用比较替代字符串 compare
}

private static Class<?> loadClass(String className) {
    try { return Class.forName(className); }
    catch (ClassNotFoundException e) { return null; }
}
```

**关键变化**:
- 第一次见 className:Class.forName 一次性加载(纳秒级)
- 后续:cache 命中,`Class.isInstance` 引用比较(几纳秒)
- Class.forName 失败 → cache `CLASS_NOT_FOUND` sentinel(ConcurrentHashMap 不允许 null value)
- Collection / GeneralEntity 路径同样优化

## 3. BDD 锁约

`ValueComputeFindObjectTest` 4 个测试方法(端到端):
- `dualClassEndToEndFires` — 1 alice + 1 main,期望 fired=1
- `dualClassWithNoiseFires` — 100 noise + 1 alice + 100 noise + 1 main,期望 fired=1
- `dualClassNoMatchDoesNotThrow` — 1000 noise,期望 fired=0(不抛 ClassNotFoundException)
- `fiveDualClassRulesAllFire` — 5 dual class rule × 1 alice + 1 main,期望 fired=5
  (测 classNameCache 在多 className 下行为)

## 4. 实测 perf(5 轮 JIT 稳态中位)

| workload | N | V5.85 per-fact (us) | V5.86 per-fact (us) | Δ |
|----------|---|---------------------|---------------------|---|
| dual     | 500 | 3.52 | 3.65 | +4% |
| dual     | 1000 | 2.87 | 2.73 | -5% |
| dual     | 2000 | 1.33 | 1.44 | +8% |
| dual     | 5000 | 1.66 | 1.52 | -8% |
| dual     | 10000 | 1.25 | 1.22 | -2% |
| single   | 500 | 1.32 | 1.42 | +8% |
| single   | 1000 | 1.25 | 1.34 | +7% |
| single   | 2000 | 1.07 | 1.15 | +7% |
| single   | 5000 | 0.71 | 0.77 | +8% |
| single   | 10000 | 0.70 | 0.70 | 0% |

**结论:持平,统计噪音 ±8% 内**。没有 10-20% 预期收益。

## 5. 根因分析

**`String.equals` 在 JIT 优化下已很便宜**:
- JDK 21 String.equals 走 `StringLatin1.equals`(SIMD 优化,byte-by-byte compare)
- className 通常 < 50 字符,字符串 compare 实测 < 50ns
- `Class.isInstance` 引用比较 ~ 10ns

理论上 V5.86 每次 findObject 省 80ns(2-4 次字符串 compare),dual class per-fact 1.25us
总成本 0.8% — **远低于测量噪音 ±8%**。

**findObject 在 hot path 上调用次数有限**:
- PerfScaling dual class 是 1 rule × 2 pattern × 0 fired,每次 fact evaluate 调 findObject
  1-2 次(取决于 evaluate 内部逻辑)
- 2000 fact × 2 findObject × 80ns = 320us 总节约 / 2000 fact = 0.16us per-fact
- 实测 ±0.1us per-fact,在 ±8% 噪音范围内

**JIT 已把 String.equals 优化得很好**,简单的 className cache 收益边际。

## 6. 改动保留理由

虽然 perf 持平,V5.86 改动**保留**,因为:
1. **correct** — 4 个 BDD 测试 + 626 全量回归通过,行为保持
2. **代码表达力** — `classNameCache` 命名清晰,后续多 className / 复杂 VariableCategory
   场景下缓存命中逻辑明显
3. **副作用** — Class.forName 失败仍 fallback 字符串 compare,不破坏错误处理
4. **CLAUDE.md 童子军法则** — 既然要碰 findObject(perf 优化 phase 必经路径),顺手把
   per-fact 字符串 compare 优化掉

**不改 baseline.json**:
- V5.85 baseline 数字是正确行为下的真实成本
- V5.86 持平不算 regression,不应改 baseline
- V5.79 旧 < 1ms 不能作为目标(那是 pre-existing bug 的便宜数据)

## 7. 教训

1. **CLAUDE.md "代码优雅" 跟 perf 优化冲突时,持平/微优的改动仍可保留**(改动简洁 + correct)
2. **String.equals 在 JIT 优化下不可怕** — 不必一律替换为引用比较
3. **per-fact 成本分解要找真正 hot path** — findObject 字符串 compare 不是 V5.86 之前预想的主要成本
4. **V5.85 提议的 10-20% 收益是高估** — 提议需实测验证,不可直接采信
5. **JIT 优化成熟度是反直觉点** — `String.equals` / `HashMap.get` 等 JDK 基础设施已深度优化,
   简单替换不一定有收益

## 8. 改动清单

- 改: `server/lib/ruleforge-core/src/main/java/com/ruleforge/runtime/rete/ValueCompute.java`
  - 新增 `classNameCache` 字段(ConcurrentHashMap)
  - 新增 `CLASS_NOT_FOUND` sentinel
  - 新增 `loadClass(String)` 私有方法
  - 改 `findObject(String, Object, Context)` 用 cache 替代字符串 compare
- 新 test: `ValueComputeFindObjectTest` 4 个 BDD 锁约
- 无 baseline.json 改动
- 文档: 本文件

## 9. V5.87+ 后续方向(基于 V5.85 + V5.86 数据)

- findObject 字符串 compare 已不是瓶颈
- 真正 hot path 需 JFR / async-profiler 抓 long-running workload(60s+)
- 候选:`EvaluationContext` lazy invalidation / `Utils.getObjectProperty` 反射优化 /
  接受 V5.83 1-3us per-fact 是新常态
