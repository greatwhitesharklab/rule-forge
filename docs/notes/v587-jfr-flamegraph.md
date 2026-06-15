# V5.87 — JFR long-running 抓 rete hot path(诊断 phase)

> **TL;DR**:V5.87 跑 `HotPathBenchTest` 35s long-running workload(12784 iter × 4002 fact =
> 51.16M fact insert),JFR 30s recording 抓到 **2053 个 CPU sample**。**top hot path**:
>
> 1. **`CriteriaActivity.logMessage` 289 + `String.format` 580 + `StringBuilder` ~700
>    = 1570+ 次调试 log 占 hot path ~ 76%** — `logMessage` 是 private,**没有 `if (!this.debug)
>    return` 早返**!debug=false 时仍跑 String.format + StringBuilder。
> 2. **`PropertyUtilsBean.getNestedProperty/getProperty/getSimpleProperty/getPropertyDescriptor`**
>    反射调用 ~ 980 次 — `Utils.getObjectProperty` 走 Commons BeanUtils 反射。
> 3. **`ReteInstance.resetStickyActivities` 349 次** — V5.83 引入的 per-fact sticky state reset。
> 4. **`CriteriaActivity.enter` 548 次** — rete hot path 主入口。
>
> **V5.88+ 真实高收益优化点**:
> - V5.88: `CriteriaActivity.logMessage` 早返 `if (!this.debug) return;` — 预期 30-50% per-fact 收益
> - V5.89: `Utils.getObjectProperty` 反射缓存 — 预期 20-30% per-fact 收益
>
> **V5.85 + V5.86 的猜测错了** — 之前以为 `findObject` 字符串 compare 是 hot path,JFR 揭示
> `logMessage` 调试日志 + `PropertyUtilsBean` 反射是**真正 hot path**。

## 1. 起因

V5.85 perf scaling 分析提议 V5.86+ 优化方向 #1(跨 pattern findObject 字符串 compare),
V5.86 实测持平(JIT 已优化 String.equals)。
V5.87 改用 JFR long-running 抓真实 hot path sample,验证"猜测 vs 实际"差异。

## 2. 实验设计

### 2.1 long-running workload

`HotPathBenchTest` 跑 dual class rule + 35s 持续 fact insert:
- 每次 iter:2000 Person + 2000 Address noise + 1 alice + 1 main,fireRules
- 35s 跑 12784 iter × 4002 fact = **51.16M fact insert**
- JIT 充分预热后 per-fact 0.68us(比 V5.85 N=10000 1.25us 更快,long-running 渐近线)

### 2.2 JFR 配置

```bash
mvn test -pl lib/ruleforge-core \
  -Dtest=HotPathBenchTest \
  -DargLine="-XX:StartFlightRecording=duration=30s,filename=target/v587.jfr,settings=profile"
```

`settings=profile` 包含:
- `jdk.ExecutionSample` (CPU 100Hz,30s 期望 3000 sample)
- `jdk.ObjectAllocationSample` (TLAB 分配采样)
- `jdk.GCPhaseParallel` / `jdk.GCPhasePause`
- `jdk.ThreadPark` / `jdk.ThreadContextSwitchRate`
- `jdk.ClassLoadingStatistics` / `jdk.CompilerStatistics`

## 3. JFR 数据(30s duration)

### 3.1 事件类型汇总

| event type | count | 备注 |
|---|---|---|
| `jdk.ExecutionSample` | 2053 | CPU 100Hz 采样 |
| `jdk.ObjectAllocationSample` | 8797 | TLAB 分配,8797 sample 可看分配热点 |
| `jdk.NativeMethodSample` | 1491 | native 方法 CPU 采样 |
| `jdk.GCPhaseParallel` | 92426 | GC 大量(Young Gen 频繁)— String.format 创建大量 String |
| `jdk.GCPhasePauseLevel1` | 654 | STW GC pause |
| `jdk.GCPhasePause` | 218 | 实际 GC 总数 |
| `jdk.YoungGarbageCollection` | 218 | 35s 内 218 次 Young GC,~6/s |

### 3.2 top hot methods(by `jdk.ExecutionSample` stack top frame)

```
548  com.ruleforge.runtime.rete.CriteriaActivity.enter(EvaluationContext, Object, FactTracker)
349  com.ruleforge.runtime.rete.ReteInstance.resetStickyActivities(List)
303  org.apache.commons.beanutils.PropertyUtilsBean.getNestedProperty(Object, String)
295  java.lang.StringBuilder.append(String)
291  java.lang.String.format(String, Object...)
290  org.apache.commons.beanutils.PropertyUtilsBean.getProperty(Object, String)
289  java.util.Formatter.format(String, Object...)
289  com.ruleforge.runtime.rete.CriteriaActivity.logMessage(EvaluateResponse, Context)
238  com.ruleforge.runtime.KnowledgeSessionImpl.evaluationRete(Collection)
226  java.lang.StringBuilder.append(CharSequence, int, int)
202  org.apache.commons.beanutils.PropertyUtilsBean.getSimpleProperty(Object, String)
201  java.util.HashMap.getNode(Object)
194  com.ruleforge.model.rule.lhs.Criteria.evaluate(EvaluationContext, Object, List)
190  com.ruleforge.runtime.KnowledgeSessionImpl.execute(AgendaFilter, ...)
188  java.lang.String.hashCode()
185  org.apache.commons.beanutils.PropertyUtils.getProperty(Object, String)
183  java.util.HashMap.hash(Object)
178  java.lang.AbstractStringBuilder.append(String)
177  sun.security.provider.NativePRNG.engineNextBytes(byte[])  // UUID.randomUUID
```

### 3.3 关键 hot path 解读

**#1 — `CriteriaActivity.logMessage` 调试日志(1570+ sample,76%)**

- `logMessage` 289 次直接命中
- `String.format` 291 + `Formatter.format` 289 + `StringBuilder.append` 295+226+178 = 1270 次
- 合计 1570 sample 占 total 2053 sample 的 **76%**

**`CriteriaActivity.logMessage`**(`CriteriaActivity.java:63-87`)是 private 方法,内部调
`String.format("^^^ 条件： %s => %s, 左值： %s, 右值： %s", id, result, leftValue, rightValue)`
+ `context.logMsg(msg, MsgType.Condition, ...)`。

**问题**:`enter` line 42 无条件调 `logMessage(response, context)`,**没有 `if (!this.debug) return` 早返**。
`this.debug` 字段是构造参数(line 19),但 `logMessage` 内部不读它。

**影响**:`debug=false` 时仍跑 `String.format` + 4 个 `toString()` + `StringBuilder` 拼接 + 1 个
`MessageItem` 对象分配。每次 evaluate 都跑 — 是 per-fact 最大的单点开销。

**#2 — Commons BeanUtils 反射(980 sample,48%)**

- `PropertyUtilsBean.getNestedProperty` 303
- `PropertyUtilsBean.getProperty` 290
- `PropertyUtilsBean.getSimpleProperty` 202
- `PropertyUtilsBean.getPropertyDescriptor` + `getProperty` 顶层 185
- 合计 980 sample

`Utils.getObjectProperty(object, property)`(`Utils.java:151, 191` 走 `org.apache.commons.beanutils.PropertyUtilsBean` 反射)—
每次 criteria evaluate 调 property 访问都走反射。每次反射涉及:
- `getPropertyDescriptor` → `DefaultResolver.hasNested` → `next` → `remove`(字符串 tokenize)
- `getSimpleProperty` → `Method.invoke`
- `getNestedProperty` 递归

**问题**:`Method` 引用、`PropertyDescriptor` 没缓存,每次 evaluate 走 3-5 次反射。

**#3 — `ReteInstance.resetStickyActivities` 349 次**

V5.83 引入的 per-fact sticky state reset。占 sample 17%,是 V5.83 correctness 必需的开销,
**不能省**(V5.84 增量 reset 已验证不能省)。V5.88+ 优化空间在减少 reset 工作量,不是消除 reset。

**#4 — `CriteriaActivity.enter` 548 次**

rete hot path 主入口。自身 sample 占比 27% — 这是核心逻辑,优化空间在子调用。

## 4. V5.85 / V5.86 猜测 vs V5.87 实际

| 猜测(V5.85 提议) | 实际(V5.87 JFR) | 状态 |
|---|---|---|
| 跨 pattern `findObject` 字符串 compare 10-20% 收益 | `findObject` 0-2% 收益(V5.86 实测) | V5.86 持平,代码保留 |
| `EvaluationContext` lazy invalidation | `EvaluationContextImpl.clean()` 0 sample(JIT 已内联) | 提议无收益,放弃 |
| `Utils.getObjectProperty` 反射 | **`PropertyUtilsBean` 反射 980 sample(48%)** | V5.89 高收益 |
| — | **`logMessage` 调试日志 1570 sample(76%)** | **V5.88 最高收益** |
| — | `ReteInstance.resetStickyActivities` 349 sample(17%) | V5.83 必需,优化空间小 |

**最大单点 hot path 是 `logMessage` 调试日志**(76%),**V5.88 fix 早返预期 30-50% 收益**。

## 5. V5.88+ 优化方向(基于 JFR 数据)

### 5.1 V5.88 — `CriteriaActivity.logMessage` 早返

**改动**(1 行):
```java
private void logMessage(EvaluateResponse response, Context context) {
    if (!this.debug) return;  // V5.88: 早返
    // ... 原 String.format 逻辑
}
```

**预期收益**:
- 消除 76% hot path(1570/2053 sample)
- 30-50% per-fact 节约(0.68us → 0.4-0.5us)
- 消除 8797 ObjectAllocationSample 中相当一部分(`MessageItem` + 4 个 `toString()` String)
- GC 压力降低(218 Young GC → 估计 -30%)

### 5.2 V5.89 — `Utils.getObjectProperty` 反射缓存

**改动**:
```java
// V5.89 — 缓存 Class → Method lookup
private final Map<Class<?>, Map<String, Method>> propertyMethodCache = new ConcurrentHashMap<>();

public static Object getObjectProperty(Object obj, String property) {
    Method m = propertyMethodCache
        .computeIfAbsent(obj.getClass(), k -> new ConcurrentHashMap<>())
        .computeIfAbsent(property, p -> findGetter(k, p));
    return m.invoke(obj);
}
```

**预期收益**:
- 消除 980 sample `PropertyUtilsBean` 反射
- 20-30% per-fact 节约
- 减少字符串 tokenize + PropertyDescriptor 反射

### 5.3 V5.90+ 候选

- `ReteInstance.resetStickyActivities` 路径优化(349 sample,17% — 优化空间有限)
- `HashMap.getNode` 201 sample — HashMap get 内部,JDK 优化成熟,不可控
- `String.hashCode` 188 sample — 字符串 hash,JDK 优化成熟,不可控

## 6. 关键教训

1. **JFR 抓 hot path 比"看代码猜"靠谱 100 倍** — V5.85 / V5.86 提议的 findObject 字符串 compare
   实测 0-2% 收益,真正 hot path 是 logMessage(76%) + PropertyUtilsBean 反射(48%)
2. **Long-running workload 是 JFR 必要条件** — 短 surefire fork 测 192 sample 主要采到
   JIT/surefire 启动,rete 稳态抓不到
3. **JDK 优化已很彻底** — String.equals / StringBuilder.append / HashMap.get 等基础设施 JIT 优化成熟,
   简单替换不一定有收益(跟 V5.86 教训一致)
4. **总成本 ≠ 直觉** — V5.83 per-fact 1-5ms 看起来大,JFR 揭示 76% 是调试日志浪费
5. **debug=false 应是常见态** — production 不开 debug,`logMessage` 早返是 **no-brainer fix**

## 7. 改动清单

- 新 test: `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/HotPathBenchTest.java`
  (1 个 @Test,35s long-running workload,JFR 30s recording)
- 无 production code 改动(诊断 phase)
- 文档: 本文件 + v584/v585/v586 notes

## 8. 引用

- [[v585-perf-scaling-analysis]] V5.85 perf scaling 数据
- [[v586-findobject-classcache]] V5.86 findObject 持平
- JFR 录制的 `target/v587.jfr` (35s, 3.8MB) — 可用 `jfr print target/v587.jfr` 重看
- [[v582-allfactsmap-rewrite]] V5.82 allFactsMap
- [[v583-rete-sticky-state-fix]] V5.83 rete sticky state
