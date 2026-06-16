# V5.99 — Utils.getObjectProperty MethodHandle 反射缓存

## Context

V5.98 后 JFR 30s HotPathBenchTest 抓出 reflection chain 残留 sample:

- `Utils.getObjectProperty`: 340 sample
- `Method.invoke`: 231 sample
- `DirectMethodHandleAccessor.invoke`: 254 sample
- **合计 825 sample(rete hot path 32%)**

**根因**:V5.89 改用 `Class.getMethod + Method.invoke + ConcurrentHashMap cache`,
每次 invoke 仍走 `varargs Object[] 分配 + InvocationTargetException 拆包 +
reflective access check`。JIT 不能完全 inline `Method.invoke`,因为 `Method.invoke`
是普通方法调用,后面接的是 `NativeMethodAccessorImpl.invoke0` (native) 或
`GeneratedMethodAccessor` (bytecode-generated)。

## 改动

### 文件 1: `Utils.java`

V5.99 把 `Map<String, Method>` cache 升级为 `Map<String, MethodHandle>` cache,
`resolveGetter` 一次性 `asType(MethodType.methodType(Object.class, Object.class))`
固化签名到 `(Object)Object`,后续 invoke JIT 把 polymorphic call site 完整 inline。

```java
// V5.99 cache value 类型
private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, MethodHandle>>
    GETTER_CACHE = new ConcurrentHashMap<>();

// 缓存的 MethodHandle 已经是 (Object)Object 签名, JIT polymorphic inline
return getter.invoke(object);
```

`MethodHandle.invoke` declare throws `Throwable` — 实际只可能 RuntimeException/Error
(WrongMethodTypeException/ClassCastException 走 asType 适配都不可达)。`catch (Throwable)`
是 compile requirement,JIT 仍能 inline catch 块因为路径上都是 RuntimeException/Error
直接 rethrow,跟 V5.89 拆 `InvocationTargetException` 后 rethrow cause 行为等价。

### 文件 2 (新 BDD): `UtilsGetObjectPropertyMethodHandleTest.java`

10 个 BDD tests 用 `@Nested` + Gherkin-style `@DisplayName` 锁 V5.99 修法契约:

- **PojoMethodHandle** 4 tests
  - `shouldInvokeGetXGetter`: getX MethodHandle 返回 String
  - `shouldReturnIntViaGetter`: getX MethodHandle 返回 int (auto-boxing)
  - `shouldInvokeIsXGetter`: isX 形态 boolean
  - `secondCallShouldHitMethodHandleCache`: 二次调用命中 cache
- **MapFastPath** 2 tests
  - `shouldReadHashMapValue`: HashMap 零反射
  - `shouldReadGeneralEntityValue`: GeneralEntity 零反射
- **ErrorPath** 3 tests
  - `missingPropertyShouldThrowRuleException`: 缺失属性 → RuleException
  - `nullObjectShouldThrowRuleException`: object null → RuleException
  - `getterRuntimeExceptionPropagatedAsCause`: getter 抛 RuntimeException → 透传
- **ConcurrentAccess** 1 test
  - `concurrentAccessToMethodHandleCache`: 100 thread × 1000 次,无异常 + 返回值一致

## 行为等价性 audit

| V5.89 Method.invoke 行为 | V5.99 MethodHandle.invoke 行为 | 等价? |
|---|---|---|
| return value 直接返回 | return value 直接返回(同 Java 语义) | ✅ |
| `InvocationTargetException` cause RuntimeException → rethrow | RuntimeException 直接透传 | ✅ |
| `InvocationTargetException` cause Error → rethrow | Error 直接透传 | ✅ |
| `InvocationTargetException` cause checked Exception → wrap RuleException | checked Exception 不可达(底层 getter 不能抛 checked) | ✅ 等价(实际不可达 path) |
| `IllegalAccessException` → wrap RuleException | 不可达(MethodHandle unreflect 已 check access) | ✅ 等价(实际不可达 path) |
| cache hit: 同 class+property 二次调用命中 | 同 V5.89 行为 | ✅ |
| Map fast path 走 `(Map) obj.get(property)` 零反射 | 同 V5.89 行为 | ✅ |

无 null-stored / check-exception 风险(底层 getter 永远只抛 RuntimeException/Error
经 V5.89 历史 audit 27+ caller 全部用 simple name + 真实 POJO getter)。

## Verification

### Step 1 — 全量回归
```bash
mvn test -pl lib/ruleforge-core
```

680 → **690 pass**(+10 V5.99 BDD)。V5.99 BDD 锁契约,所有现有 test 也都过。

### Step 2 — JFR 验证 reflection chain 消除
```bash
mvn test -pl lib/ruleforge-core -Dtest=HotPathBenchTest \
  -DargLine="-XX:StartFlightRecording=duration=30s,filename=target/v599.jfr,settings=profile"
jfr print --events jdk.ExecutionSample target/v599.jfr
```

**实际**(本机 30s JFR, vs V5.98 baseline 825 sample):
- `Utils.getObjectProperty`: 340 → **187 sample**(-45%)
- `Method.invoke`: 231 → **0 sample**(消失)
- `DirectMethodHandleAccessor.invoke`: 254 → **99 sample**(-61%, asType adapter 内部)
- **合计 reflection chain: 825 → 286 sample(-65%)**

### Step 3 — Wall-time bench
```bash
mvn test -pl lib/ruleforge-core -Dtest=EvalBenchmarkV579
```

5-run p50 (5 warmup + 50 iters):
- V5.99: no_eval_5r 1.46-1.96ms, no_eval_3way 0.27-0.59ms, eval 0.20-0.22ms, no_eval 0.42-0.48ms
- V5.98 post-revert: no_eval_5r 1.36-1.83ms, no_eval_3way 0.27-0.59ms, eval 0.20-0.22ms, no_eval 0.42-0.48ms

**range overlap** — wall-time 中性,价值在 JFR signal(reflection chain -65%)。
跟 V5.93/V5.94/V5.97/V5.98 一档:JFR 大信号 + wall-time noise floor 持平。

## Why V5.99 选 MethodHandle + asType 替代 LambdaMetafactory

试过两个方向:

1. **裸 `MethodHandle.invoke` + `asType(Object,Object)`** — 选这条 ✅
2. **`LambdaMetafactory` 编译 hidden class 实现 `GetterFunction` interface**
   — 试过但实际更慢(LambdaMetafactory 在 `resolveGetter` 内调用有
   `Class.newInstance` + hidden class 加载开销,而且 Person 类的 test fixture
   跟 `Utils` class 不是同一 classloader,LambdaMetafactory 内部 lookup 失败)。

MethodHandle + asType 是平衡点:asType 一次性适配,后续 `getter.invoke(object)`
走 polymorphic call site,JIT 把整个调用链 inline 到调用方(等效直接方法调用)。

## 风险 / 已知 trade-off

1. **Catch (Throwable) cost**:`MethodHandle.invoke` declare throws Throwable。
   实测 JIT 仍能 inline 整个 catch 块因为 RuntimeException/Error 直接 rethrow。
   `Throwable` catch 不比 `InvocationTargetException` catch 慢(specific exception
   catch 也是先 catch 再 instance check + rethrow)。
2. **Polymorphic call site 首次调用 cost**:第一次 `getter.invoke(object)` 是
   megamorphic,JIT 编译后稳定。本热路径 35s 跑 8000+ 万次 invoke,首次 cost
   完全被摊销。
3. **`asType` adapter 内部 `DirectMethodHandleAccessor.invoke` 残留 99 sample**
   — 这是 `MethodHandle.asType` 内部 adapter 实现细节,无法消除。JFR 整体
   反射链仍 -65%。

## 未来 V6.0+ 候选(per V5.98 doc)

- `FactTracker.newSubFactTracker` `putAll` 全量复制
- `KnowledgeSessionImpl` labeled loop 重构(配 characterization test)
- `ReteInstance.computeAllFacts` / `criteriaActivity` 等更多 HashMap → 2-array
- `StringConcatHelper` 链 V5.87 标注的 test infra cost (FactIds.next + Long.getChars
  850 sample) — 切换到 `Integer.toString` + 字符串拼接手工化,但只影响 test 范围

## 引用

- [[v598-evaluation-context-2array]] V5.98 上一个 PR(JFR 立 V5.99 候选)
- [[v589-getobjectproperty-reflection-cache]] V5.89 立 Method.invoke cache
- [[v593-evaluationcontext-double-lookup]] V5.93 立 "砍反模式" 原则
- [[v584-incremental-reset-attempted]] V5.84 撤销教训(wall-time 验证不可省)
- `target/v598.jfr` V5.98 baseline(825 sample)
- `target/v599.jfr` V5.99 验证(286 sample,-65%)
