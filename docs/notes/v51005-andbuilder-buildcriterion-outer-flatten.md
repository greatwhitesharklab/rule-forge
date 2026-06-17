# V5.100.5 — `AndBuilder.buildCriterion` 外层 state machine flatten (TD-19.5.7)

## Context

V5.96 立的 skip 收口. `AndBuilder.buildCriterion:32-94` 是最后一个 decompiled outer state
machine (KnowledgeSessionImpl labeled loops 之外). V5.96 22 文件 var123 cleanup 时外层
`Iterator var7 + while(true){do{...}while(nodes==null)}` find-non-null 状态机被显式 skip
("complex state machine, 需 characterization test"). V6.0 收了内层 `Iterator var11`
(simple iteration), 外层留下. V5.100.5 收外层.

V6.3 KnowledgeBase + V6.4 LeftParser 立了 "N-level do-while find-first → enhanced for +
N continue" 模式, V5.100.5 直接套用 (do-while-find-non-null → for + null-check-continue).

### V5.100.5 修复前 (外层 state machine):

```java
ConditionNode currentCriteriaNode = null;
Iterator var7 = criterions.iterator();

while (true) {
    List nodes;
    do {
        if (!var7.hasNext()) {
            // TERMINAL: build result, return
            List<BaseReteNode> result = new ArrayList();
            if (criterions.size() == 1 && currentCriteriaNode != null) { ... return result; }
            if (andNode == null) { ... return result; }
            if (andNode != null && currentCriteriaNode != null) {
                currentCriteriaNode.addLine(andNode);
            }
            result.add(andNode);
            return result;
        }

        Criterion criterion = (Criterion) var7.next();
        List<ConditionNode> prevNodes = new ArrayList();
        if (currentCriteriaNode != null) {
            prevNodes.add(currentCriteriaNode);
        }
        nodes = this.buildCriterion(criterion, context, prevNodes);
    } while (nodes == null);   // skip criteria that build to null

    // V6.0 内层 (process nodes)
    for (Object obj : nodes) { ... }
}
```

### V5.100.5 修复后 (enhanced for + continue + terminal block 移后):

```java
ConditionNode currentCriteriaNode = null;
for (Criterion criterion : criterions) {
    List<ConditionNode> prevNodes = new ArrayList();
    if (currentCriteriaNode != null) {
        prevNodes.add(currentCriteriaNode);
    }

    List nodes = this.buildCriterion(criterion, context, prevNodes);
    if (nodes == null) {
        continue;   // skip null-building criteria (原 do-while nodes==null)
    }

    // V6.0 内层 (process nodes) — 不动
    for (Object obj : nodes) { ... }
}

// TERMINAL block (原 !var7.hasNext() 分支, 移到 for 之后)
List<BaseReteNode> result = new ArrayList();
if (criterions.size() == 1 && currentCriteriaNode != null) { ... return result; }
if (andNode == null) { ... return result; }
if (andNode != null && currentCriteriaNode != null) {
    currentCriteriaNode.addLine(andNode);
}
result.add(andNode);
return result;
```

**关键等价性证明**:

1. **find-non-null 语义保留**: 原外层 do-while-find-non-null (build criterion, 若 null 则
   skip 重新 build 下一个) → `for + if (nodes == null) continue;`. 两种写法都 skip null-building
   criteria, 处理 non-null.
2. **terminal block 时机一致**: 原 do-while 顶部 `!var7.hasNext()` 分支只在 iterator 耗尽时
   return; V5.100.5 terminal block 移到 for 之后, for 自然耗尽后到达 — 等价 (两者都只在全部
   criterion 处理完后到达 terminal).
3. **currentCriteriaNode / andNode 累积一致**: 两者都在内层 V6.0 for 中 mutate
   currentCriteriaNode + andNode, 跨 iteration 累积, terminal block 用累积状态构造 result.
4. **V6.0 内层不动**: 内层 `for (Object obj : nodes)` 是 V6.0 已收的 simple iteration, V5.100.5
   不动 (100% 保留).
5. **`criterions.size() != 0` → `!criterions.isEmpty()`**: 同 line 的 guard 顺手 V5.96 化 (1-token
   可读性, 不改行为).

## 改动

### 文件 1: `AndBuilder.java` (state machine flatten + 删 `import java.util.Iterator`)

- 26 行 (`while(true){do{...}while(nodes==null); ...}`) → 22 行 (`for + continue + terminal block`)
- 删 `import java.util.Iterator;` (不再用 raw Iterator)

### 文件 2 (新 BDD): `AndBuilderBuildCriterionTest.java` (7 tests, `@Nested` + Gherkin `@DisplayName`)

5 nested class: SingleCriterion / SameTypeChain / CrossTypeJoin / ZeroCriteria /
FlattenPreservation.

- `SingleCriterion.singleCriterionReturnsCriteriaNodeOnly`: 1 criterion → [CriteriaNode]
  (passthrough, terminal block size==1 分支)
- `SameTypeChain.sameTypeTwoCriteriaChainNotJoin`: 2 同 type criteria → [CriteriaNode]
  (rete-sharing chain, 无 AndNode — 同 ObjectTypeNode 下链式串, 不需 join)
- `CrossTypeJoin.crossTypeTwoCriteriaBuildAndNode`: 跨 type 2 criteria → [AndNode]
  (join 2 ObjectTypeNode)
- `CrossTypeJoin.threeMixedCriteriaBuildAndNode`: 3 mix-type criteria → [AndNode]
  (state machine 跑 3 iter 不卡死 — 原 while(true) 卡死风险)
- `ZeroCriteria.nullCriterionsThrowsRuleException`: criterions==null → RuleException
- `FlattenPreservation.repeatedBuildStableStructure`: 2 次独立 build → 结构稳定 (无 sticky state)
- `FlattenPreservation.singleCriterionPassthroughBranch`: 1 criterion passthrough 分支走通

**Test fixture 模式**: 直接调 `andBuilder.buildCriterion(and, context)` (不走 ReteBuilder.buildRete),
因为 buildCriterion 输出 `List<BaseReteNode>` 可直接断言. BuildContext 用 BuildContextImpl +
ResourceLibrary (Foo + Bar 2 category), ObjectTypeNode 按需 build. 子 criterion 是 flat
Criteria (非 nested And/Or), 走 `this.buildCriteria` 直调, 不需 ReteBuilder.criterionBuilders 装配.

**rete 语义发现 (写 test 时暴露)**:
- AndNode 只在**跨 object-type join**时出现, **同 type 多 criteria 走 chain** (rete-sharing,
  链式串到同一 ObjectTypeNode 下, 不需 AndNode). 这是 buildCriteria 内 fetchSameCriteriaNode +
  CriterionBuilder 的 object-type matching 决定的, 跟 AndBuilder 无关.
- AndNode 是 join 节点, downstream 在 buildCriterion 阶段不 build, 所以
  `andNode.getChildrenNodes()` 可空 — 锁的是 AndNode 存在 (instanceof), 不是 children.

## Verification

### Step 1 — BDD + 全量回归

```bash
mvn test -pl lib/ruleforge-core -Dtest=AndBuilderBuildCriterionTest
mvn test -pl lib/ruleforge-core
```

- BDD: **7/7 pass** (锁 V5.100.5 flatten 行为不变性)
- 全量: **770/770 pass** (was 763 → +7 BDD tests), 零 regression
- flatten 行为保留: 763 个 pre-existing test (含 SingleRuleFiresBDD / EvalBenchmark / rete
  构造全套) 全 pass, 证明 buildCriterion 重写后 rete 构造 100% 等价

### Step 2 — JFR 信号验证

`buildCriterion` 是 build-time per-rule-build 调用 (rete 构造, 不是 per-fact hot path), JFR 0
sample 预期. 本 PR **0 perf 信号预期**, pure code elegance closure.

## 复用现有 utility / 模式

- 完全沿 V6.3 KnowledgeBase + V6.4 LeftParser 立的 "N-level do-while find-first → enhanced
  for + N continue" 模式 (V5.100.5 是 find-non-null 变种, 同模式)
- 0 新工具, 0 新 API, 纯 state machine artifact 化简
- V6.0 内层 (Iterator var11 → enhanced for) 100% 保留

## Skip 维持

- `KnowledgeSessionImpl` labeled loops (3 places: line 347/363/433 Iterator var + label84/label82
  state machine) — 仍 V5.96 skip, 跟本 PR 无关. KnowledgeSessionImpl 是 runtime hot path (per-fact
  rete evaluation), flatten 风险高, 留 characterization test 投资更大的后续.
- `KnowledgeSessionImpl` activeRule/activeAgendaGroup (line 403/429) containsKey — 低频 +
  V5.96-skip 相邻, 留后续.
- `RulesRebuilder.java:614/633` — null-value 风险, V5.93 原则不适用.
- `EngineContext.java:44` — duplicate detection, 不是 first-wins.

## 风险 / 已知 trade-off

1. **state machine 等价性**: find-non-null 语义 + terminal block 时机 + currentCriteriaNode/andNode
   累积 3 处 100% 等价 (V5.100.5 显式 lock, 全量 770 pass 含 rete 构造全套证明).
2. **terminal block 移位风险**: 原 terminal 在 do-while 顶部 (reachable only on !hasNext),
   V5.100.5 移到 for 之后 (reachable only on for 耗尽). 两者都只在全部 criterion 处理完后到达,
   等价. 全量 rete 构造 test 验证.
3. **V6.0 内层保留**: 内层 process-nodes for 100% 保留 (V6.0 已收, 不再动).
4. **`size() != 0` → `!isEmpty()`**: 同 line guard 1-token 可读性改进, 不改行为 (V5.96 模式).
5. **JFR 0 sample**: build-time per-rule-build, 不在 rete hot path. pure code elegance closure.
6. **characterization test 直接调 buildCriterion**: 不走 ReteBuilder.buildRete (输出
   List<BaseReteNode> 直接可断言), 避免 rete-graph 遍历的脆弱性.

## 引用

- [[v596-var123-cleanup]] V5.96 立的 skip (AndBuilder outer state machine)
- [[v600-andbuilder-var123]] V6.0 收内层 (Iterator var11)
- [[v633-knowledgebase-dowhile-flatten]] V6.3 立 "do-while-find-first → enhanced for + N continue" 模式
- [[v644-leftparser-commonfunction-flatten]] V6.4 同模式 (V5.100.5 直接套用)
- [[feedback-version-x999-xcap]] V5.100.5 = V5.100 第五个 Fix (Fix 位 = 5)
- 未来 V5.100.6+ 候选: KnowledgeSessionImpl labeled loops (需大 characterization test 投资) /
  KnowledgeSessionImpl active* containsKey (低频) / 其它
