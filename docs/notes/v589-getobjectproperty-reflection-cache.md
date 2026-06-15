# V5.89 — `Utils.getObjectProperty` 反射缓存(消除 JFR 240 sample)

> **TL;DR**:`Utils.getObjectProperty` 替换 apache commons `PropertyUtils` 反射链
> (`getSimpleProperty` + `DefaultResolver.next` + `getNestedProperty` + `getPropertyDescriptor`),
> 改用 `Class<?> -> Map<String, Method>` 二级 ConcurrentHashMap cache + `Method.invoke`。
>
> **JFR 验证(V5.88 → V5.89,30s HotPathBenchTest)**:
> - `PropertyUtilsBean.getSimpleProperty` 131 → **0** (-100%)
> - `DefaultResolver.next` 109 → **0** (-100%)
> - 全部 `PropertyUtilsBean.*` reflection chain → **0**
> - 替代为 `DirectMethodHandleAccessor.invoke` 38 sample(收口率 84%)
>
> **Wall-time 验证**:
> - EvalBenchmarkV579 `no_eval_3way` 1.14ms → **1.03ms** (-10%)
> - EvalBenchmarkV579 `no_eval` 2.91ms → **2.70ms** (-7%)
> - PerfScaling dual N=10000 1.10us → **0.62us** (**-44%**)
> - HotPathBenchTest per-fact 0.68us → **0.62us** (-9%)
> - 全量回归 **638/638 pass**

## 1. 起因

V5.87 JFR 35s long-running 抓出 post-V5.88 剩余 top hot path 是 apache commons BeanUtils
反射链:

| method | V5.88 sample | 占比 |
|---|---|---|
| `PropertyUtilsBean.getSimpleProperty` | 131 | 6.5% |
| `PropertyUtilsBean.expression.DefaultResolver.next` | 109 | 5.4% |
| 其他(`getNestedProperty` / `getProperty` / `getPropertyDescriptor`) | 计入反射链 | — |

合计 **240+ sample(12% of post-V5.88 hot path)**,全部来自
`com.ruleforge.Utils.getObjectProperty(object, property)` 调
`org.apache.commons.beanutils.PropertyUtils.getProperty(object, property)`。

V5.89 收口。审计 27+ caller(`ValueCompute` / `Criteria` / `PropertyCriteria` /
`AbstractLeftPart` / `CollectLeftPart` / `FromLeftPart` / 11 个 `*FunctionDescriptor` /
`ListAction`)全部传**简单属性名**(单一字段如 `"name"`/`"score"`/`"$stream"`),**无
nested/indexed/mapped 形态**。

## 2. 改动

### 2.1 production: `server/lib/ruleforge-core/.../Utils.java`

**新增 imports**:
```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
```

**移除 import**:`org.apache.commons.beanutils.PropertyUtils`(无其他引用);
`BeanUtils` 保留(`setObjectProperty` 仍用)。

**新增 static 字段 + NO_GETTER sentinel**:
```java
private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Method>>
    GETTER_CACHE = new ConcurrentHashMap<>();

@SuppressWarnings("unused")
private static String __v589NoGetterSentinel() { return ""; }

private static final Method NO_GETTER;
static {
    try {
        NO_GETTER = Utils.class.getDeclaredMethod("__v589NoGetterSentinel");
    } catch (NoSuchMethodException e) {
        throw new ExceptionInInitializerError(e);
    }
}
```

**`getObjectProperty` 替换**:
```java
public static Object getObjectProperty(Object object, String property) {
    if (object == null) {
        throw new RuleException("Cannot read property [" + property + "] of null object.");
    }
    // 1) Map fast path — GeneralEntity extends HashMap 走这里, 零反射。
    if (object instanceof Map) {
        return ((Map<?, ?>) object).get(property);
    }
    // 2) POJO path — 反射 cache。
    Class<?> clazz = object.getClass();
    ConcurrentHashMap<String, Method> propMap = GETTER_CACHE.get(clazz);
    if (propMap == null) {
        propMap = GETTER_CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
    }
    Method getter = propMap.get(property);
    if (getter == null) {
        getter = propMap.computeIfAbsent(property, p -> resolveGetter(clazz, p));
    }
    if (getter == NO_GETTER) {
        throw new RuleException("No readable property [" + property
            + "] on class " + clazz.getName());
    }
    try {
        return getter.invoke(object);
    } catch (IllegalAccessException e) {
        throw new RuleException(e);
    } catch (InvocationTargetException e) {
        // 对齐 PropertyUtils 语义: 抛底层 cause
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        if (cause instanceof Exception) throw new RuleException((Exception) cause);
        throw new RuleException(e);
    }
}

private static Method resolveGetter(Class<?> clazz, String property) {
    String cap = capitalize(property);
    try { return clazz.getMethod("get" + cap); } catch (NoSuchMethodException ignore) {}
    try {
        Method m = clazz.getMethod("is" + cap);
        if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
            return m;
        }
    } catch (NoSuchMethodException ignore) {}
    return NO_GETTER;
}

private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    char c = s.charAt(0);
    if (Character.isUpperCase(c)) return s;
    char[] arr = s.toCharArray();
    arr[0] = Character.toUpperCase(c);
    return new String(arr);
}
```

**`setObjectProperty` 保持不变**(仍走 `BeanUtils.setProperty`)。

### 2.2 BDD test: `UtilsGetObjectPropertyTest`(新,8 tests)

3 个 @Nested:
1. **PojoGetter**(4 tests): `getX` 返 String/int / `isX` 返 boolean / 二次调用命中 cache (`isSameAs`)
2. **MapFastPath**(2 tests): `HashMap` 取 value / `GeneralEntity extends HashMap` 取 value
3. **ErrorPath**(2 tests): 缺失属性抛 `RuleException` / null object 抛 `RuleException`

**关键 fix**: fixture `Person` 改为 `public static final class`(顶层 static),避免
`@Nested` 内部类 synthetic `this$0` 字段干扰 `PropertyUtilsBean` 反射解析。

## 3. 验证

### 3.1 单元 + 全量回归

- `UtilsGetObjectPropertyTest`: **8/8 pass**
- `mvn test -pl lib/ruleforge-core`: **638/638 pass**(原 630 + 8 新),无回归

### 3.2 JFR 二次抓取(同 V5.88 baseline,30s HotPathBenchTest)

V5.89 top-15 leaf sample(1962 sample 总数,V5.88 是 2089):
```
215  java.util.Formatter.format(Locale, String, Object[])        ← V5.88 121, 微增
185  java.lang.String.hashCode                                   ← V5.88 144
173  com.ruleforge.runtime.rete.ReteInstance.resetStickyActivities ← V5.88 137
156  java.lang.AbstractStringBuilder.appendChars                 ← V5.88 160
 99  java.util.Formatter.parse
 90  java.lang.AbstractStringBuilder.inflateIfNeededFor
 64  sun.security.provider.SecureRandom.updateState
 55  com.ruleforge.runtime.rete.AbstractActivity.doPassAndNode
 51  sun.security.provider.SecureRandom.engineNextBytes
 51  sun.security.provider.NativePRNG$RandomIO.implNextBytes
 50  java.util.Formatter.parse
 48  java.util.Formatter.format(String, Object[])
 42  java.util.HashMap.putVal
 42  com.ruleforge.model.rule.lhs.Criteria.evaluate
 40  java.util.HashMap.putAll
```

**`PropertyUtilsBean.*` 全部 → 0**(top 25 都没出现):
- `getSimpleProperty` 131 → 0
- `DefaultResolver.next` 109 → 0
- 反射 chain 总 240 sample → `DirectMethodHandleAccessor.invoke` 38 sample(收口率 84%)

V5.88 vs V5.89 对比(同 30s workload):

| hot method | V5.88 | V5.89 | 变化 |
|---|---|---|---|
| **`PropertyUtilsBean.getSimpleProperty`** | **131** | **0** | **-100%** ✅ |
| **`DefaultResolver.next`** | **109** | **0** | **-100%** ✅ |
| `Method.invoke` (`DirectMethodHandleAccessor.invoke`) | (反射链 240) | 38 | 收口 -84% |
| `resetStickyActivities` | 137 | 173 | +26% (workload 差异) |
| `StringBuilder.appendChars` | 160 | 156 | -3% |
| `String.hashCode` | 144 | 185 | +28% (workload 差异) |

### 3.3 Wall-time bench(EvalBenchmarkV579, 4 scenarios × 50 iter)

| scenario | V5.83 | V5.88 | V5.89 | V5.89 vs V5.88 |
|---|---|---|---|---|
| `no_eval` (2-pattern) | 3.0ms | 2.91ms | **2.70ms** | **-7%** |
| `no_eval_3way` (3-pattern) | 1.3ms | 1.14ms | **1.03ms** | **-10%** |
| `no_eval_5r` (5 rule × 2-pattern) | 5.2ms | 5.50ms | 5.35ms | -3% |
| `eval` (no match) | 1.2ms | 1.23ms | **1.12ms** | **-9%** |

### 3.4 Wall-time bench(PerfScalingAnalysisTest, N=10000)

| class | V5.85 | V5.88 | V5.89 | V5.89 vs V5.88 |
|---|---|---|---|---|
| single | 0.68us | 0.68us | **0.62us** | **-9%** |
| dual | 1.10us | 1.10us | **0.62us** | **-44%** |

Dual N=10000 -44% 是意外大赢 — V5.85 dual per-fact 是 V5.79 3-pattern 模拟,
2-pattern join + reflection 开销最重,V5.89 收口效果最明显。

### 3.5 HotPathBenchTest 35s long-running(同 V5.87 JFR workload)

```
[V5.87 HotPathBench] duration=35.0s | iters=14121 | facts=56512242 | per-run=2.48ms per-fact=0.62us
```

- V5.88: 12784 iter / 51.16M facts / per-fact=0.68us
- V5.89: **14121 iter / 56.5M facts / per-fact=0.62us**(-9%)

V5.89 同样 35s 内多跑 **10.5% fact** (51M → 56.5M),跟 -9% per-fact 互证。

## 4. 设计要点

### 4.1 Map fast path

`object instanceof Map` 检查**在 cache 查找之前**:
- `GeneralEntity extends HashMap` → 零反射
- 直接传 `HashMap` → 零反射
- POJO → 走 cache

JFR 显示 V5.88 `StringBuilder.appendChars` 160 sample 部分来自 `Map.get` 的 toString
参数解析,V5.89 收口后这部分仍然存在但来源换成了 `PropertyUtilsBean` 链(也是 hot path)。

### 4.2 NO_GETTER sentinel

`Utils.class.getDeclaredMethod("__v589NoGetterSentinel")` 拿 `Utils` 私有 static
方法做 sentinel。`Method.equals` 是 identity-based,所有 "no getter" 槽位共享**同一
Method 对象**,`Map.get(property) == NO_GETTER` 是 O(1) 引用比较。

为什么选 `Utils` 自己:
- `Object.class.getMethod("toString")` 这种会跟真实 POJO getter 冲突(toString
  可以是合法 getter)
- `Utils` 永远不会被 caller 当事实对象查属性,ID 层面不可能 collision

### 4.3 双层 ConcurrentHashMap 模式(跟 V5.86 镜像)

```java
ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Method>> GETTER_CACHE
```

- 外层:`Class<?>` JVM lifetime 稳定,不需要 invalidation
- 内层:per-class 的 property name → Method 映射
- 全部用 `computeIfAbsent` 原子 publish,避免重复扫描 `Class.getMethod`

跟 V5.86 `ValueCompute.classNameCache` 模式完全一致(只是 V5.86 用 String 键
`Class.forName`,V5.89 用 `Class<?>` 键 `Class.getMethod`)。

### 4.4 故意收窄到 simple-name 形态

V5.89 **不支持**:
- nested path `a.b.c`
- indexed `a[0]`
- mapped `a(key)`
- bare method `a()`(无 getX 前缀)

经 audit 27+ caller **全部用 simple name + 真实 POJO getter 形态**,影响零。
如果未来真有 caller 用 nested path,会抛 `RuleException("No readable property...")` —
跟 V5.89 之前 `PropertyUtils` 抛的 `NoSuchMethodException` 行为差异是错误信息更清晰。

## 5. 改动清单

- production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/Utils.java`
  (+50 lines: cache field + sentinel + new getObjectProperty + resolveGetter + capitalize)
- test: `server/lib/ruleforge-core/src/test/java/com/ruleforge/UtilsGetObjectPropertyTest.java`
  (新, 8 BDD tests)
- baseline: `server/lib/ruleforge-core/src/test/resources/perf/baseline.json` (调 no_eval_3way p50 1.14→1.03)
- 文档: 本文件
- JFR: `target/v589.jfr` (3.8MB, 30s)

## 6. 引用

- [[v587-jfr-flamegraph]] JFR 原始 240 sample 数据
- [[v588-logmessage-early-return]] V5.88 上一个 PR
- [[v586-findobject-classcache]] V5.86 ConcurrentHashMap 模式
- [[v585-perf-scaling-analysis]] V5.85 perf scaling
- [[v583-rete-sticky-state-fix]] V5.83 sticky state 必需开销
- [[v582-allfactsmap-rewrite]] V5.82 allFactsMap

## 7. 经验教训

1. **JFR 100% 准确** — V5.87 预测 `PropertyUtilsBean` 反射是 #1 剩余 hot path,V5.89
   fix 后 240 sample 全部消失,跟 `DirectMethodHandleAccessor.invoke` 38 sample 替代,
   收口率 84%
2. **Wall-time vs JFR 占比偏差** — V5.87 文档预测 20-30% per-fact,V5.89 实测:
   - 短期 bench(2000 fact)受 setup+JIT 限制 -7~10%
   - **Long-running asymptote(HotPathBenchTest 35s / 56.5M fact) -9%**
   - **dual N=10000 -44%**(2-pattern join 反射开销最重)
3. **`Map` fast path 必须先于 cache** — `GeneralEntity extends HashMap` 是常见 case,
   零反射必须 fast
4. **sentinel 用 `Method` 不行,用 `Method` 引用 identity** — `Method.equals` 是
   identity-based,所有 "no getter" 共享同一 Method 对象才是 O(1) 命中
5. **`commons-beanutils` dep 保留** — `setObjectProperty` 仍用,只去掉了
   `PropertyUtils.getProperty` 路径
