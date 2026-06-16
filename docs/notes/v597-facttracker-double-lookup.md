# V5.97 — FactTracker.addObjectCriteria 砍双 lookup (V5.93 模式 fix)

## Context

V5.96 清理完 var123 后,V5.95 立的"双 lookup 砍"原则(本仓库 V5.93 起)还在 `FactTracker.addObjectCriteria`
留有同样的反模式:

```java
if (objectCriteriaMap.containsKey(obj)) {       // lookup 1
    List<BaseCriteria> list = objectCriteriaMap.get(obj);  // lookup 2
    if (!list.contains(criteria)) {
        list.add(criteria);
    }
} else {
    List<BaseCriteria> list = new ArrayList<>();
    list.add(criteria);
    objectCriteriaMap.put(obj, list);
}
```

跟 V5.93 `EvaluationContextImpl.getCriteriaValue` 和 V5.94 `Criteria.getPartValue` 1:1 同一反模式。
V5.96 doc 立的 V5.97 候选 = "FactTracker double lookup kill"。

## 改动

### `FactTracker.addObjectCriteria` (1 file, 1 method)

把 `containsKey + get` 双 lookup 改成 `get + null check`:

```java
List<BaseCriteria> list = objectCriteriaMap.get(obj);
if (list != null) {
    if (!list.contains(criteria)) {
        list.add(criteria);
    }
} else {
    list = new ArrayList<>();
    list.add(criteria);
    objectCriteriaMap.put(obj, list);
}
```

**省 1 HashMap.containsKey + 1 String.hashCode per call**。

### 行为等价性

`HashMap.get` 对 "key 不存在" 和 "key 存在但 null 值" 都返 null,跟 `containsKey` 行为等价。
**本方法 put 后 list 永远非 null**(new ArrayList 立即 add 1 个 criteria),所以:
- get==null 路径只有 "key 不存在" 这一种可能
- 跟 `containsKey==false` 100% 等价
- 跟 V5.93/V5.94 相比,**无 null-stored 风险**

### 新 BDD test:`FactTrackerAddObjectCriteriaTest` (5 tests)

锁 V5.97 修法行为不变性:
- `FirstAddCreatesList`: first add → 创建新 list
- `SecondAddAppends`: 同 obj 不同 criteria → 追加
- `DuplicateAddDedup`: 同 obj 同 criteria → dedup(契约保留)
- `HashMapInstancePath`: HashMap 实例 → 走 `HashMap.class.getName()` 特殊 key
- `NewSubFactTracker`: `newSubFactTracker()` 复制 map 共享

## 验证

```bash
mvn test -pl lib/ruleforge-core
# Tests run: 670, Failures: 0, Errors: 0, Skipped: 0  (665 + 5 new)

mvn test -pl lib/ruleforge-core -Dtest=EvalBenchmarkV579
# no_eval_5r: p50 1.40→1.33ms (-5%, noise)
# no_eval_3way: 0.69ms
# eval: 0.28ms (noise)
# no_eval: 0.95ms (noise)
```

JFR 30s `HotPathBenchTest` post-V5.97:
- `HashMap.containsKey` 不再在 top 15 hot method 列表
- `EvaluationContextImpl.getCriteriaValue` 498 → 437 sample (-12%)
- 其它 noise 范围

按 V5.94 lesson "JFR big signal + small wall-time = small ROI, fix 价值在可读性" — V5.97 跟 V5.93/V5.94 一档,wall-time 在 noise floor,**价值在延续原则 + 消除反模式 + 后续可优化 `newSubFactTracker` 的 `putAll` 全量复制**。

## 风险 / trade-off

- **零 production behavior 变化** — 670 测试 100% pass
- **零 null-stored 风险** — list 永远非 null 后 put
- **延续 V5.93 立的"砍双 lookup"原则** — 跟 V5.93/V5.94 同类 fix,提高 codebase 一致性
- **未来 V5.98+ 候选**:
  - `newSubFactTracker` 的 `putAll` 全量复制(per-fact allocation)→ 共享引用 + 不可变包装
  - `EvaluationContextImpl` HashMap → 2-array 小型 store(N ≤ 8 linear scan)
  - `Utils.getObjectProperty` MethodHandle 而非 Method.invoke

## 引用

- [[v593-evaluationcontext-double-lookup]] V5.93 立原则
- [[v594-partvalue-double-lookup]] V5.94 同模式
- [[v596-var123-cleanup]] V5.96 上一个 PR(doc 里立 V5.97 候选)
- `docs/notes/v597-facttracker-double-lookup.md` 本 PR 完整 doc
