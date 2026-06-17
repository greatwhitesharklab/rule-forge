# V5.100.6 — `KnowledgeSessionImpl.activeRule`/`activeAgendaGroup` 砍 containsKey+get 双 lookup (TD-19.5.4)

## Context

V5.93 原则系列的最后一处安全 containsKey+get (V5.100.0-5 之后的收口).
`KnowledgeSessionImpl.activeRule:403` + `activeAgendaGroup:429` 两处同档:
`if (!map.containsKey(group)) throw; else { list = map.get(group); ... }`.

### V5.100.6 修复前 (activeRule):

```java
public void activeRule(String activationGroupName, String ruleName) {
    if (!this.activationReteInstancesMap.containsKey(activationGroupName)) {   // hash lookup #1
        throw new RuleException("Activation group [" + activationGroupName + "] not exist!");
    } else {
        List<ReteInstanceUnit> unitList = this.activationReteInstancesMap.get(activationGroupName);  // hash lookup #2
        Iterator var4 = unitList.iterator();   // V5.96 skip: labeled loop
        label42: while (var4.hasNext()) { ... }
        this.evaluationContext.clean();
    }
}
```

### V5.100.6 修复后:

```java
public void activeRule(String activationGroupName, String ruleName) {
    // V5.100.6 — 砍 containsKey + get 双 lookup...
    List<ReteInstanceUnit> unitList = this.activationReteInstancesMap.get(activationGroupName);  // single lookup
    if (unitList == null) {
        throw new RuleException("Activation group [" + activationGroupName + "] not exist!");
    } else {
        Iterator var4 = unitList.iterator();   // V5.96 skip: labeled loop 不动
        label42: while (var4.hasNext()) { ... }
        this.evaluationContext.clean();
    }
}
```

activeAgendaGroup 同档. 节省 1 个 containsKey hash lookup per active* 调用 (低频: 用户显式激活
rule group).

**关键等价性证明**:

1. **value 永不为 null**: `activationReteInstancesMap` / `agendaReteInstancesMap` 的 value 是
   `List<ReteInstanceUnit>`, 由 `Rete.buildGroupRetesInstance` 用
   `map.computeIfAbsent(name, k -> new ArrayList<>())` 装入 (永非 null ArrayList).
   KnowledgeSessionImpl 内 putAll 只从非 null 源 map 拷 (evaluationRete 内 do-while guard).
   无 `put(key, null)` 风险.
2. **throw 路径保留**: group 不存在 → `get == null` → throw RuleException. 跟原 `!containsKey`
   100% 等价.
3. **内层 labeled loop 不动**: activeRule 的 `Iterator var4 + label42` + activeAgendaGroup 的
   `Iterator var3 + while(true) do-while` 是 V5.96 显式 skip 的 state machine (runtime hot path,
   需独立 characterization test 投资). V5.100.6 只砍 containsKey, 内层 100% 保留.

## 改动

### 文件 1: `KnowledgeSessionImpl.java` (2 处 containsKey → get + null check)

- `activeRule:402-406`: `if (!containsKey) throw else { get }` → `get; if (null) throw else { ... }`
- `activeAgendaGroup:428-432`: 同档
- 各 + V5.100.6 注释 (含 "不动内层 labeled loop" 标注)

### 文件 2 (新 BDD): `KnowledgeSessionImplActiveGroupTest.java` (8 tests, `@Nested` + Gherkin `@DisplayName`)

5 nested class: ActiveRuleGroupNotExist / ActiveRuleGroupExist / ActiveAgendaGroupNotExist /
ActiveAgendaGroupExist / MapsIsolated.

- `ActiveRuleGroupNotExist.groupNotExistThrows`: activation group 不存在 → 抛
- `ActiveRuleGroupExist.groupExistEmptyListNoThrow`: group 存在 (空 list) → 不抛 (loop no-op)
- `ActiveRuleGroupExist.differentGroupStillThrows`: g1 存在但查 g2 → 抛
- `ActiveAgendaGroupNotExist.groupNotExistThrows`: agenda group 不存在 → 抛
- `ActiveAgendaGroupExist.groupExistEmptyListNoThrow`: agenda group 存在 → 不抛
- `ActiveAgendaGroupExist.differentGroupStillThrows`: ag1 存在但查 ag2 → 抛
- `MapsIsolated.activationEntryDoesNotSatisfyAgendaLookup`: activationMap 有 "shared" 但
  agendaMap 没 → activeAgendaGroup 仍抛 (两 map 互不干扰)
- `MapsIsolated.agendaEntryDoesNotSatisfyActivationLookup`: 反向

**Test fixture**: 用最小 KnowledgePackage (Foo rule, 跟 SingleRuleFiresBDD 同) 构造 session
(initContext 设 evaluationContext), 反射往 activation/agenda map 装测试 entry (生产由
evaluationRete 内 putAll 填). `@BeforeAll EngineContextWirer.wire()` 装配 criterionBuilders.

## Verification

### Step 1 — BDD + 全量回归

```bash
mvn test -pl lib/ruleforge-core -Dtest=KnowledgeSessionImplActiveGroupTest
mvn test -pl lib/ruleforge-core
```

- BDD: **8/8 pass** (锁 V5.100.6 修法行为不变性)
- 全量: **778/778 pass** (was 770 → +8 BDD tests), 零 regression

### Step 2 — JFR 信号验证

activeRule/activeAgendaGroup 是低频调用 (用户显式激活 rule group, 不是 per-fact hot path).
JFR noise level 预期. 跟 V5.100.1 (runtime per-fire-rule) 同档但更低频.

## 复用现有 utility / 模式

- 完全沿 V5.93 / V5.97 / V6.1 / V5.100.0-4 立的 "HashMap.get 已能区分 absent vs null value" 原则
- 0 新工具, 0 新 API, 纯 2 处 `if (!containsKey) throw else { get }` → `get; if (null) throw`
- 内层 V5.96-skip labeled loop 100% 保留

## Skip 维持

- `KnowledgeSessionImpl` labeled loops (3 places: line 347/363/433 Iterator var + label84/label82
  state machine) — 仍 V5.96 skip. runtime hot path (per-fact rete evaluation), flatten 风险高,
  需独立 characterization test 投资.
- `RulesRebuilder.java:614/633` — null-value 风险, V5.93 原则不适用.
- `EngineContext.java:44` — duplicate detection, 不是 first-wins.
- `putKnowledgeSession:518` — session 参数 null-value 风险 (V5.93 transform 会改 null-session
  edge case 行为), 不动.

## 风险 / 已知 trade-off

1. **null-value 风险 audit**: value 永为非 null ArrayList (Rete.buildGroupRetesInstance 用
   computeIfAbsent(new ArrayList<>())). 等价性 100% 成立.
2. **throw 路径保留**: group 不存在 → throw, 跟原 `!containsKey` 100% 等价 (8 BDD tests 显式 lock).
3. **内层 labeled loop 保留**: V5.96 skip 不动, V5.100.6 只砍 containsKey 外层.
4. **两 map 互不干扰**: MapsIsolated nested class 显式 lock (activationMap 有 entry 不满足
   activeAgendaGroup 的 agendaMap lookup, 反向亦然).
5. **JFR noise level**: 低频 (用户显式激活), 不是 per-fact hot path.
6. **session 构造 fixture**: 用最小 KnowledgePackage (Foo rule) 构造 session, 反射设 map entry.
   跟 SingleRuleFiresBDD fixture 同, @BeforeAll wire criterionBuilders.

## 引用

- [[v593-evaluationcontext-double-lookup]] V5.93 立的原则
- [[project-v5100-knowledgebuilder-addtolibrarymap-doublelookup]] V5.100.0
- [[project-v51001-executecommonfunctionaction-doublelookup]] V5.100.1
- [[project-v51002-decisiontablerulesbuilder-findcellincolumn-doublelookup]] V5.100.2
- [[project-v51003-complexscorecardrulesbuilder-getcell-doublelookup]] V5.100.3
- [[project-v51004-scorerule-rowmap-cacheorcreate-doublelookup]] V5.100.4
- [[project-v51005-andbuilder-buildcriterion-outer-flatten]] V5.100.5
- [[feedback-version-x999-xcap]] V5.100.6 = V5.100 第六个 Fix (Fix 位 = 6), V5.93 系列收口
- 未来 V5.100.7+ 候选: KnowledgeSessionImpl labeled loops (需大 characterization test 投资) /
  其它
