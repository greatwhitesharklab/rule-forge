# V5.100 — `KnowledgeBuilder.addToLibraryMap` 砍 containsKey + put 双 lookup (TD-19.5.4)

## Context

V5.93 立的 "HashMap.get 已能区分 absent vs null value" 原则 (砍 containsKey + get
双 lookup, save 1 hash lookup per call)。 V6.1 AndActivity.enter + V5.97 FactTracker.addObjectCriteria
已经用本原则砍过。 `KnowledgeBuilder.addToLibraryMap:298-307` 是本原则的另一处 build-time
double lookup 候选, 这次收口。

### V5.100 修复前 (containsKey + put):

```java
private void addToLibraryMap(Map<String, Library> map, List<Library> libraries) {
    if (libraries != null) {
        for (Library lib : libraries) {
            String path = lib.getPath();
            if (!map.containsKey(path)) {       // hash lookup #1
                map.put(path, lib);             // hash lookup #2 (only when absent)
            }
        }
    }
}
```

### V5.100 修复后 (get == null + put):

```java
private void addToLibraryMap(Map<String, Library> map, List<Library> libraries) {
    // V5.100 — 砍 containsKey + put 双 lookup。 跟 V5.93 EvaluationContext 原则:
    // `map.get(key) == null` 已能区分 "key absent" 与 "key present with null value"。
    // 本场景 `value` 永远是 Library 对象 (非 null, 反编译收尾, 无 put(key, null) 风险),
    // 所以等价。 Library.first-wins 契约保留 (single-writer last-wins 反例: 后到
    // duplicate path 跳过, 跟原 containsKey + put 行为 100% 一致)。
    if (libraries != null) {
        for (Library lib : libraries) {
            String path = lib.getPath();
            if (map.get(path) == null) {        // single hash lookup, return null if absent OR null-value
                map.put(path, lib);
            }
        }
    }
}
```

**关键等价性证明**:

1. **本场景 Library 永不为 null**: `Library` 是 `@Data` POJO, 构造必须传
   `path/version/type` (3-arg constructor)。 `addToLibraryMap` 没 `put(key, null)`
   风险, 所以 `map.get(path) == null` 跟 `!map.containsKey(path)` 100% 等价 —
   两者都表示 "this key 没装过 Library"。

2. **iterator 顺序一致**: 两种写法都按 List.iterator 顺序遍历, enhanced for
   跟原 for-each 都推进同一个 List iterator 状态 (本场景 V5.100 没改循环结构)。

3. **filter 顺序一致**: 本方法没 filter — `map.containsKey(path)` 跟 `map.get(path) == null`
   都是同一 map 同一 key 的 lookup, 没变化。

4. **first-wins 契约保留**: 重复 path 时 `!containsKey` 跳过后续, V5.100 `get == null`
   也跳过后续 (因为已装上, get 返回非 null), 行为 100% 一致。

5. **null guard 保留**: `if (libraries != null)` 外层 guard 100% 保留, 空 list
   也是 no-op。

## 改动

### 文件 1: `KnowledgeBuilder.java` (1 改动 + 8 行 V5.93-pattern 注释)

```diff
     private void addToLibraryMap(Map<String, Library> map, List<Library> libraries) {
+        // V5.100 — 砍 containsKey + put 双 lookup。 跟 V5.93 EvaluationContext 原则:
+        // `map.get(key) == null` 已能区分 "key absent" 与 "key present with null value"。
+        // 本场景 `value` 永远是 Library 对象 (非 null, 反编译收尾, 无 put(key, null) 风险),
+        // 所以等价。 Library.first-wins 契约保留 (single-writer last-wins 反例: 后到
+        // duplicate path 跳过, 跟原 containsKey + put 行为 100% 一致)。
         if (libraries != null) {
             for (Library lib : libraries) {
                 String path = lib.getPath();
-                if (!map.containsKey(path)) {
+                if (map.get(path) == null) {
                     map.put(path, lib);
                 }
             }
         }
     }
```

### 文件 2 (新 BDD): `KnowledgeBuilderAddToLibraryMapTest.java` (10 tests, `@Nested` + Gherkin `@DisplayName`)

锁 V5.100 修法的行为不变性 (private method 用反射调用, 跟 V6.1 AndActivityTest 同模式):

- `NullLibraries.nullLibrariesWithExistingMap` / `nullLibrariesWithEmptyMap`:
  libraries == null + map 已装 → map 不变 (no NPE)
- `EmptyLibraries.emptyListNoOp`: libraries 空 list → map 不变
- `DifferentPaths.threeDistinctPathsInstalled` / `singleLibraryInstalled`:
  不同 path → 全装上 (3 entry / 1 entry)
- `DuplicatePaths.duplicatePathFirstWins`: 重复 path 跨多个 library → first-wins
  (后续 duplicate 跳过, 不覆盖)
- `DuplicatePaths.existingFirstWinsOverNew`: map 已装 + 新 list duplicate path →
  保留旧 value, 不覆盖
- `NullValueDistinction.mixedDuplicateAndNewPath`: 1 duplicate + 1 new path →
  duplicate 跳过, new 装上
- `MultiCallAccumulation.twoCallsAccumulateState`: 2 次 call 累计状态
- `ListElementNullSafety.nullElementInListThrowsNpe`: list 含 null element →
  lib.getPath() 抛 NPE (现有契约保留, V5.100 不改 null element 语义)

## Verification

### Step 1 — BDD + 全量回归

```bash
mvn test -pl lib/ruleforge-core -Dtest=KnowledgeBuilderAddToLibraryMapTest
mvn test -pl lib/ruleforge-core
```

- BDD: **10/10 pass** (锁 V5.100 修法行为不变性)
- 全量: **731/731 pass** (was 721 → +10 BDD tests), 零 regression

### Step 2 — JFR 信号验证

`addToLibraryMap` 是 build-time 调用 (per-knowledge-base build, 不是 per-fact hot path),
JFR 0 sample 预期。 本 PR **0 perf 信号预期**, 跟 V6.1 / V5.97 / V5.93 同档 pure
code elegance closure。

## 复用现有 utility / 模式

- 完全沿 V6.1 / V5.97 / V5.93 立的 "HashMap.get 已能区分 absent vs null value" 原则
  (V6.1 AndActivity.enter + V5.97 FactTracker.addObjectCriteria 直接套到 V5.100
  KnowledgeBuilder.addToLibraryMap)
- 0 新工具, 0 新 API, 纯 1-line double lookup 砍
- 跟 V6.0 AndBuilder.java:66 inner + V6.2 AbstractActivity.visitPaths dead-code +
  V6.3 KnowledgeBase 3-level flatten + V6.4 LeftParser 2-level flatten 同档
  (反编译 artifact / code quality closure)

## Skip 维持 (V5.96 / V6.0 / V6.1 / V6.2 / V6.3 / V6.4 立的 scope 外)

- `AndBuilder.buildCriterion` 外层 `Iterator var7` state machine — 太复杂, 跟本 PR 无关
- `KnowledgeSessionImpl` labeled loops (3 places) — 跟本 PR 无关
- `ExecuteCommonFunctionAction.java:26,28` — runtime action, 2 个 `containsKey` 调用,
  但 action.invocation 走 EngineContext (static registry), 风险高, 留 V5.101+
- `EngineContext.java:44` — V5.76 era, `byName.containsKey(fun.getName())`,
  static registry, 留 V5.101+ 配合 EngineContext 重构时再审
- `RulesRebuilder.getVariableByName:614` — `if (namedMap.containsKey(category)) category = namedMap.get(category)`
  **有 null-value 风险** (namedMap 装可能 null value), V5.93 原则不适用, 不动
- `DecisionTableRulesBuilder.findCellInColumn:106` — build-time, 但是更复杂的
  logic, 留 V5.101+

## 风险 / 已知 trade-off

1. **null-value 风险 audit**: `addToLibraryMap` 唯一 `put` 是 `put(path, lib)`,
   `lib` 是 `Library` 对象 (从 List<Library> 取出, 反编译收尾 caller 责任传非 null),
   无 `put(key, null)` 风险。 等价性 100% 成立。
2. **iterator 状态等价性**: 两种写法循环结构相同 (V5.100 没改循环结构, 只改 lookup),
   iterator 状态一致 (JDK 语义保证)。
3. **first-wins 契约保留**: 现有 `put` 只在 key absent 时执行 (V5.100 改成 key value
   == null 时执行, 本场景 100% 等价), first-wins 100% 保留。
4. **private method 反射调用**: BDD test 用反射调 private method (跟 V6.1
   AndActivityTest 同模式), production code 不改 public API 契约。
5. **JFR 0 sample**: 本方法是 build-time per-knowledge-base-build 调用, 不在 rete
   hot path。 V5.100 是 pure code elegance closure, 跟 V5.93 / V5.97 / V6.1 同档。
6. **null element 契约**: list 含 null element 时 lib.getPath() 抛 NPE (现有契约),
   V5.100 显式 lock 这行为 (ListElementNullSafety nested class), 不改 caller 责任。

## 引用

- [[v593-evaluationcontext-double-lookup]] V5.93 立 "HashMap.get 已能区分 absent vs null value" 原则
- [[v597-facttracker-doublelookup]] V5.97 FactTracker.addObjectCriteria 砍 double lookup
  (V5.100 直接套用其模式)
- [[v611-andactivity-doublelookup]] V6.1 AndActivity.enter 砍 double lookup
  (V5.100 直接套用其模式)
- [[v596-var123-cleanup]] V5.96 立 "零反编译 var123" 原则
- [[v600-andbuilder-var123]] V6.0 立 "重新审计内层独立性" 原则
- [[v622-abstractactivity-deadcode]] V6.2 AbstractActivity.visitPaths dead-code
- [[v633-knowledgebase-dowhile-flatten]] V6.3 KnowledgeBase 3-level do-while flatten
- [[v644-leftparser-commonfunction-flatten]] V6.4 LeftParser 2-level do-while flatten
- 未来 V5.101+ 候选: AndBuilder.buildCriterion outer state machine / EngineContext 重构 /
  DecisionTableRulesBuilder.findCellInColumn build-time lookup / KnowledgeSessionImpl
  labeled loop characterization test
