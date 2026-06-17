# V5.100.7 — `KnowledgeSessionImpl.activeRule`/`activeAgendaGroup` labeled loop flatten (TD-19.5.7)

## Context

V5.96 skip 部分收口. KnowledgeSessionImpl 3 处 labeled loops:
- `activeRule` label42 (line 416) — **未用 label** (无 break/continue label42), Fernflower
  artifact, plain while. **安全 flatten**.
- `activeAgendaGroup` `while(true){do{do}while(isNotYetEffective)}while(isExpired)}` (无 label) —
  find-valid 状态机, 2 filter (skip not-effective + skip expired). **中等 flatten** (套 V6.3/V6.4).
- `evaluationRete` label84 + label82 (line 311/349) — **ACTIVE label** (`continue label84` /
  `continue label82`), 真 per-fact hot path. **不动** (V5.96 skip 维持, 需独立大 characterization
  test 投资).

V5.100.7 收前 2 处 (低频 runtime active*), evaluationRete 留.

### V5.100.7 修复前 (activeRule):

```java
Iterator var4 = unitList.iterator();
label42:                                    // 未用 label (Fernflower artifact)
while (var4.hasNext()) {
    ReteInstanceUnit insUnit = (ReteInstanceUnit) var4.next();
    if (insUnit.getRuleName().equals(ruleName) && isWithinValidPeriod(insUnit)) {
        ReteInstance reteIns = insUnit.getReteInstance();
        for (Object fact : this.allFactsList) { ... }
    }
}
this.evaluationContext.clean();
```

### V5.100.7 修复后 (activeRule):

```java
for (ReteInstanceUnit insUnit : unitList) {
    if (insUnit.getRuleName().equals(ruleName) && isWithinValidPeriod(insUnit)) {
        ReteInstance reteIns = insUnit.getReteInstance();
        for (Object fact : this.allFactsList) { ... }
    }
}
this.evaluationContext.clean();
```

### V5.100.7 修复前 (activeAgendaGroup):

```java
Iterator var3 = unitList.iterator();
while (true) {
    ReteInstanceUnit insUnit;
    do {
        do {
            if (!var3.hasNext()) {
                return;
            }
            insUnit = (ReteInstanceUnit) var3.next();
        } while (isNotYetEffective(insUnit));   // filter 1: skip not-effective
    } while (isExpired(insUnit));                // filter 2: skip expired
    // process valid insUnit
    ...
}
```

### V5.100.7 修复后 (activeAgendaGroup):

```java
for (ReteInstanceUnit insUnit : unitList) {
    if (isNotYetEffective(insUnit)) {
        continue;   // filter 1: skip not-effective
    }
    if (isExpired(insUnit)) {
        continue;   // filter 2: skip expired
    }
    // process valid insUnit
    ...
}
// implicit return (for 耗尽, 跟原 while(true) !hasNext return 等价)
```

**关键等价性证明**:

1. **activeRule label42 未用**: grep 确认无 `break label42` / `continue label42`, 是纯 Fernflower
   artifact. plain while → enhanced for 100% 等价.
2. **activeAgendaGroup find-valid 语义保留**: 原 2-level do-while (skip not-effective OR skip
   expired) → enhanced for + 2 continue (同 V6.3/V6.4 N-filter 模式). 两种写法都 skip
   not-effective + skip expired, 处理 valid.
3. **return 时机一致**: 原 `while(true)` 在 `!var3.hasNext()` 时 return; enhanced for 耗尽后方法
   自然结束 (无 trailing clean — 跟原 activeAgendaGroup 一致, 它不像 activeRule 有 clean()).
4. **内层 process-body 不动**: rete enter + addTrackers 100% 保留.

## 改动

### 文件 1: `KnowledgeSessionImpl.java` (2 loop flatten)

- `activeRule`: `Iterator var4 + label42 plain while` → enhanced for (label42 未用)
- `activeAgendaGroup`: `while(true){do{do}while(isNotYetEffective)}while(isExpired)}` →
  enhanced for + 2 continue (套 V6.3/V6.4)

### 文件 2: `KnowledgeSessionImplActiveGroupTest.java` (扩展 V5.100.6, +5 NonEmptyLoopBody tests)

新 `NonEmptyLoopBody` nested class, 用真实 `ReteInstanceUnit` (从 Foo rete newReteInstance,
无 effective/expires 日期 → valid) 锁 loop body 行为:
- `activeRuleMatchingValidUnitRunsLoopBody`: 匹配 + 有效 unit → 跑 loop body, 不抛
- `activeRuleNonMatchingRuleNameSkipsInnerBlock`: ruleName 不匹配 → skip 内层 block, 不抛
- `activeRuleMultipleUnitsOnlyMatchingProcessed`: 3 unit 只匹配 1 → loop 全遍历
- `activeAgendaGroupValidUnitRunsLoopBody`: 有效 unit → 跑 loop body, 不抛
- `activeAgendaGroupMultipleValidUnitsAllProcessed`: 3 unit 全有效 → loop 全跑

(总 13 tests: V5.100.6 的 8 + V5.100.7 的 5)

## Verification

### Step 1 — BDD + 全量回归

```bash
mvn test -pl lib/ruleforge-core -Dtest=KnowledgeSessionImplActiveGroupTest
mvn test -pl lib/ruleforge-core
```

- BDD: **13/13 pass** (V5.100.6 的 8 group-existence + V5.100.7 的 5 loop-body)
- 全量: **783/783 pass** (was 778 → +5 BDD tests), 零 regression

### Step 2 — JFR 信号验证

activeRule/activeAgendaGroup 低频 (用户显式激活 rule group, 不是 per-fact hot path). JFR noise
level 预期. 本 PR **0 perf 信号预期**, pure code elegance closure (砍 Fernflower labeled-loop artifact).

## 复用现有 utility / 模式

- activeAgendaGroup 完全沿 V6.3 KnowledgeBase + V6.4 LeftParser + V5.100.5 AndBuilder 的
  "do-while-find-non-null → enhanced for + N continue" 模式 (本个是 2-filter 变种)
- activeRule 是更简单的 "unused-label plain while → enhanced for" (Fernflower artifact 收口)
- 0 新工具, 0 新 API

## Skip 维持

- **`evaluationRete` label84 + label82** (line 311/349) — ACTIVE label (`continue label84` /
  `continue label82`), 真 per-fact hot path (THE rete evaluation loop). flatten 需处理
  continue-to-outer-loop 语义 + 大 characterization test 投资. 仍 V5.96 skip, 不动.
- `RulesRebuilder.java:614/633` — null-value 风险, V5.93 原则不适用.
- `EngineContext.java:44` — duplicate detection.
- `putKnowledgeSession:518` — session 参数 null-value 风险.

## 风险 / 已知 trade-off

1. **activeRule label42 未用 audit**: grep 确认无 break/continue label42, plain while → enhanced for
   100% 等价.
2. **activeAgendaGroup find-valid 等价**: 2-level do-while → enhanced for + 2 continue, skip
   语义 + return 时机一致 (5 NonEmptyLoopBody tests + 全量 783 pass 显式 lock).
3. **return 时机一致**: 原 `while(true) !hasNext return` → for 耗尽后方法结束 (无 trailing clean,
   跟原 activeAgendaGroup 一致).
4. **内层 process-body 不动**: rete enter + addTrackers 100% 保留.
5. **evaluationRete 不动**: ACTIVE label + 真 hot path, V5.96 skip 维持.
6. **JFR noise level**: 低频, 砍 Fernflower labeled-loop artifact, pure code elegance closure.
7. **真实 ReteInstanceUnit fixture**: NonEmptyLoopBody 用 rete.newReteInstance() 建 valid unit
   (无日期 → isWithinValidPeriod=true), allFactsList 空 → loop body 跑 rete enter no-op. 锁
   iteration + filter 行为, 不依赖实际 rete matching.

## 引用

- [[v596-var123-cleanup]] V5.96 立的 skip (KnowledgeSessionImpl labeled loops)
- [[v633-knowledgebase-dowhile-flatten]] [[v644-leftparser-commonfunction-flatten]] 
  V6.3/V6.4 立的 do-while-find-first → enhanced for + N continue 模式 (activeAgendaGroup 套用)
- [[project-v51005-andbuilder-buildcriterion-outer-flatten]] V5.100.5 同模式 (find-non-null)
- [[project-v51006-knowledgesessionimpl-activegroup-containskey]] V5.100.6 同方法 containsKey
  (V5.100.7 扩展同一 test 文件)
- [[feedback-version-x999-xcap]] V5.100.7 = V5.100 第七个 Fix (Fix 位 = 7)
- 未来候选: evaluationRete label84/label82 flatten (ACTIVE label + 真 hot path, 需大投资)
