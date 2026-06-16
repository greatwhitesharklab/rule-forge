# V5.100.1 — `ExecuteCommonFunctionAction.execute` 砍 containsKey + (findFunctionDescriptor | get) 双 lookup (TD-19.5.4)

## Context

V5.100 立的 "build-time HashMap.containsKey + put 双 lookup → get == null + put" 原则
runtime 版。 `ExecuteCommonFunctionAction.java:24-30` 有 2 处 containsKey 检查:
- line 26: `if (EngineContext.getFunctionDescriptorMap().containsKey(name))` 
- line 28: `else if (EngineContext.getFunctionDescriptorLabelMap().containsKey(label))`

跟 V5.93 EvaluationContext / V5.97 FactTracker.addObjectCriteria / V6.1 AndActivity.enter
/ V5.100 KB.addToLibraryMap 同档 pure V5.93-pattern double lookup 砍。

### V5.100.1 修复前 (containsKey + findFunctionDescriptor | containsKey + get):

```java
FunctionDescriptor function = null;
if (EngineContext.getFunctionDescriptorMap().containsKey(name)) {     // hash lookup #1
    function = EngineContext.findFunctionDescriptor(name);             // hash lookup #2 + throw
} else if (EngineContext.getFunctionDescriptorLabelMap().containsKey(label)) {  // hash lookup #3
    function = EngineContext.getFunctionDescriptorLabelMap().get(label);         // hash lookup #4
}
```

### V5.100.1 修复后 (get == null):

```java
function = EngineContext.getFunctionDescriptorMap().get(name);    // hash lookup #1
if (function == null) {
    function = EngineContext.getFunctionDescriptorLabelMap().get(label);  // hash lookup #2
}
```

节省 2 个 containsKey hash lookup (line 26 + line 28 各 1 个) — 命中场景下从
2-3 lookup 降到 1 lookup, miss-then-fallback 场景下从 4 lookup 降到 2 lookup。

**关键等价性证明**:

1. **FunctionDescriptor 永不为 null**: `EngineContext.init:36-52` 唯一 `put` 是
   `put(name, fun)` + `put(label, fun)`, `fun` 是从 `r.getFunctionDescriptors()` 
   取出 (Spring 收集的 FunctionDescriptor bean, 永不为 null), 无 `put(key, null)` 风险。
   所以 `map.get(key) == null` 跟 `!map.containsKey(key)` 100% 等价 — 两者都表示
   "this key 没装过 FunctionDescriptor"。

2. **iterator / lookup 顺序一致**: 两种写法都是 byName first + byLabel fallback,
   顺序 100% 一致 (V5.100.1 没改优先级)。

3. **null 抛错行为保留**: 原代码 byName miss + byLabel miss → `function` 仍 null →
   `if (function == null) throw new RuleException("Function[" + name + "] not exist.")` 
   100% 保留。 V5.100.1 显式 lock (BothMiss nested class + assertThatThrownBy)。

4. **`findFunctionDescriptor` 不再用**: V5.100.1 不再调 `findFunctionDescriptor` (改用本地
   `map.get(name) + null check`)。 但 `findFunctionDescriptor` 仍是 public method, 
   `CommonFunctionLeftPart.java:27` + `ValueCompute.java:180` 还在用 — 公共 API 不变,
   只是本文件不再依赖。

5. **null guard 保留**: `name` / `label` 是 `String`, 可以是 null (用户传 null 进来)。
   原代码: `containsKey(null)` 在 HashMap 中永远 false (HashMap 不允许 null key, 但
   实际看 JDK 实现: `containsKey(null)` 在 HashMap 中走 special null key path, 返 false
   if not put null key), 所以原代码 `name == null` 永远走 byLabel fallback。 V5.100.1
   同样: `get(null)` 走 special null key path, 返 null, fallback byLabel — 行为一致。

## 改动

### 文件 1: `ExecuteCommonFunctionAction.java` (1 改动 + 11 行 V5.93-pattern 注释)

```diff
     @Override
     public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
         FunctionDescriptor function = null;
-        if (EngineContext.getFunctionDescriptorMap().containsKey(name)) {
-            function = EngineContext.findFunctionDescriptor(name);
-        } else if (EngineContext.getFunctionDescriptorLabelMap().containsKey(label)) {
-            function = EngineContext.getFunctionDescriptorLabelMap().get(label);
-        }
+        // V5.100.1 — 砍 containsKey + (findFunctionDescriptor | get) 双 lookup, 套 V5.93
+        // 原则: `map.get(key) == null` 已能区分 absent vs null-value. 本场景 value 永为
+        // FunctionDescriptor 对象 (非 null, EngineContext.init 唯一 put 是 put(name, fun)
+        // + put(label, fun), 无 put(key, null) 风险), 所以等价. byName first (跟原顺序
+        // 一致), byLabel fallback. findFunctionDescriptor 内部也是 `get(name) + throw
+        // if null`, 跟 V5.93 模式 100% 一致 (用本地 get 替 findFunctionDescriptor 以保留
+        // "not found 时 fallback 到 label" 行为, 不能直接用 findFunctionDescriptor 因为
+        // 它会 throw). 节省 2 个 containsKey hash lookup (line 26 + line 28).
+        function = EngineContext.getFunctionDescriptorMap().get(name);
+        if (function == null) {
+            function = EngineContext.getFunctionDescriptorLabelMap().get(label);
+        }
```

### 文件 2 (新 BDD): `ExecuteCommonFunctionActionLookupTest.java` (7 tests, `@Nested` + Gherkin `@DisplayName`)

锁 V5.100.1 修法的行为不变性:

- `ByNameHit.byNameHitReturnsByNameResult`: byName 命中 → 跑 byName 的 function, ActionValue 装上
- `ByNameMissByLabelHit.byNameMissFallsBackToByLabel`: byName miss + byLabel 命中 → fallback
- `ByNameMissByLabelHit.byNameHitSkipsByLabelEvenIfDifferentFunction`: byName 命中 + byLabel 命中
  不同 function → 优先 byName, 不用 byLabel (验证 lookup 顺序保留)
- `BothMiss.bothMissThrowsRuleException`: byName miss + byLabel miss → 抛 RuleException
- `BothMiss.emptyRegistryThrowsRuleException`: registry 空 → 抛 RuleException
- `NameNull.nullNameFallsBackToByLabel`: name == null + byLabel 命中 → fallback
- `ByNameHitBehaviorPreserved.byNameHitRunsFunctionDoFunction`: 验证 V5.100.1 行为保留
  (不再经 findFunctionDescriptor, 跑 fn.doFunction 直接装上 ActionValue)

## Verification

### Step 1 — BDD + 全量回归

```bash
mvn test -pl lib/ruleforge-core -Dtest=ExecuteCommonFunctionActionLookupTest
mvn test -pl lib/ruleforge-core
```

- BDD: **7/7 pass** (锁 V5.100.1 修法行为不变性)
- 全量: **738/738 pass** (was 731 → +7 BDD tests), 零 regression

### Step 2 — JFR 信号验证

`action.execute` 是 per-fire-rule 调用频度 (rule activation 触发时跑, 不是 per-fact
hot path)。 JFR 应该 noise level, 不是 top 15。 本 PR **0 perf 信号预期**, 跟
V5.93 / V5.97 / V6.1 / V5.100 同档 pure code elegance closure。

## 复用现有 utility / 模式

- 完全沿 V5.93 / V5.97 / V6.1 / V5.100 立的 "HashMap.get 已能区分 absent vs null value" 原则
- 0 新工具, 0 新 API, 纯 4 行 (2 个 if 块) → 4 行 (1 个 if 块 + 1 个赋值) 化简
- 跟 V6.0 / V6.1 / V6.2 / V6.3 / V6.4 / V5.100 同档 (反编译 artifact / code quality closure)

## Skip 维持 (V5.96 / V6.0+ / V5.100 立的 scope 外)

- `AndBuilder.buildCriterion` 外层 `Iterator var7` state machine — 太复杂, 跟本 PR 无关
- `KnowledgeSessionImpl` labeled loops (3 places) — 跟本 PR 无关
- `EngineContext.java:44` — V5.76 era, `byName.containsKey(fun.getName())`, **不能直接
  砍** — 该 containsKey 是 **duplicate detection** (跟 V5.100 first-wins 反例: 重复就
  抛 RuntimeException, 不是 skip)。 V5.93 原则不适用, 不动。
- `RulesRebuilder.getVariableByName:614` — `if (namedMap.containsKey(category)) category = namedMap.get(category)`
  **有 null-value 风险** (namedMap 装可能 null value), V5.93 原则不适用, 不动
- `DecisionTableRulesBuilder.findCellInColumn:106` — build-time, 但是更复杂的
  logic, 留 V5.100.2+

## 风险 / 已知 trade-off

1. **null-value 风险 audit**: `EngineContext.init:36-52` 唯一 `put` 是
   `put(name, fun)` + `put(label, fun)`, `fun` 是 FunctionDescriptor bean (Spring 收集,
   永不为 null), 无 `put(key, null)` 风险。 等价性 100% 成立。
2. **iterator / lookup 状态等价性**: 两种写法都是 byName first + byLabel fallback,
   lookup 顺序一致 (V5.100.1 没改优先级, 只改 lookup 实现)。
3. **null 抛错行为保留**: byName miss + byLabel miss → `function` 仍 null →
   `if (function == null) throw new RuleException(...)` 100% 保留 (V5.100.1
   显式 lock, BothMiss nested class)。
4. **`findFunctionDescriptor` 不再用**: 本文件不再调 `findFunctionDescriptor`, 但
   method 仍是 public (CommonFunctionLeftPart.java:27 + ValueCompute.java:180
   还在用)。 公共 API 不变, 只是本文件不再依赖。 V5.100.1 不删该 method, 留给
   后续 cleanup 评估 (V5.100.2+ 候选: 评估是否能让 CommonFunctionLeftPart + ValueCompute
   也用 map.get + null check, 然后 EngineContext.findFunctionDescriptor 变成
   deprecated, 最后删)。
5. **name == null 行为保留**: 原代码 `containsKey(null)` 永远 false, 走 byLabel fallback;
   V5.100.1 同样 `get(null)` 走 special null key path, 返 null, fallback byLabel —
   行为一致 (NameNull nested class 显式 lock)。
6. **JFR noise level**: 本方法是 per-fire-rule 调用, 不是 per-fact hot path, JFR
   应该 noise level (不在 top 15)。 V5.100.1 是 pure code elegance closure,
   跟 V5.93 / V5.97 / V6.1 / V5.100 同档。
7. **新 test infra**: `ExecuteCommonFunctionActionLookupTest` 是 ruleforge-core
   第一个 `com.ruleforge.action` 包下的 test, 创了新的 `action/` test dir (之前
   core 没有这个 test dir)。 跟 KnowledgeBuilderTest / KnowledgeBaseGetKnowledgePackageTest
   同 pattern (BDD + Gherkin + reflection-free 直接调 public API)。

## 引用

- [[v593-evaluationcontext-double-lookup]] V5.93 立 "HashMap.get 已能区分 absent vs null value" 原则
- [[v597-facttracker-doublelookup]] V5.97 FactTracker.addObjectCriteria 砍 double lookup
- [[v611-andactivity-doublelookup]] V6.1 AndActivity.enter 砍 double lookup
- [[project-v5100-knowledgebuilder-addtolibrarymap-doublelookup]] V5.100 KB.addToLibraryMap
  砍 double lookup (V5.100.1 直接套用其模式, runtime 版)
- [[feedback-version-x999-xcap]] V5.100.1 = V5.100 第一个 Fix (Fix 位 = 1)
- 未来 V5.100.2+ 候选: DecisionTableRulesBuilder.findCellInColumn build-time lookup /
  EngineContext.findFunctionDescriptor deprecation 评估 / AndBuilder.buildCriterion outer
  state machine / KnowledgeSessionImpl labeled loop characterization test
