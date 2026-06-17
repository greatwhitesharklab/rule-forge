# V5.100.4 — `ScoreRule` rowMap cache-or-create 砍 containsKey + get 双 lookup (TD-19.5.4)

## Context

V5.93 立的 "HashMap.get 已能区分 absent vs null value" 原则在 ScoreRule.execute 的 rowMap
累积路径落地. `ScoreRule.java:62-69` 是 computeIfAbsent-style cache-or-create (不是
find-in-loop): 首次见 rowNumber → 新建 RowItemImpl + 装入 map; 重复见 → 复用.

### V5.100.4 修复前 (containsKey + get):

```java
RowItemImpl rowItemImpl;
if (rowMap.containsKey(rowNumber)) {       // hash lookup #1
    rowItemImpl = rowMap.get(rowNumber);     // hash lookup #2 (cache hit only)
} else {
    rowItemImpl = new RowItemImpl();
    rowItemImpl.setRowNumber(rowNumber);
    rowMap.put(rowNumber, rowItemImpl);
}
```

### V5.100.4 修复后 (get + null check, 抽出 helper):

execute() 内:
```java
RowItemImpl rowItemImpl = getOrCreateRow(rowMap, rowNumber);
```

新 helper (private static):
```java
private static RowItemImpl getOrCreateRow(Map<Integer, RowItemImpl> rowMap, int rowNumber) {
    RowItemImpl rowItemImpl = rowMap.get(rowNumber);    // single hash lookup
    if (rowItemImpl == null) {
        rowItemImpl = new RowItemImpl();
        rowItemImpl.setRowNumber(rowNumber);
        rowMap.put(rowNumber, rowItemImpl);
    }
    return rowItemImpl;
}
```

节省 1 个 containsKey hash lookup per repeat-row ActionValue (cache hit 场景 2 → 1 lookup;
cache miss 场景持平). runtime per-scorecard-eval 频度 (比 build-time 频), JFR noise level 预期.

**关键等价性证明**:

1. **RowItemImpl 永不为 null**: rowMap 是 execute 局部 map, 唯一 put 是
   `put(rowNumber, new RowItemImpl())` (永不为 null, 无 `put(key, null)` 风险), 所以
   `get(key) == null` ↔ `!containsKey(key)` 100% 等价.
2. **cache-or-create 契约保留**: cache hit (get != null) → 复用; cache miss (get == null)
   → 新建 + 装入. 跟原 containsKey + else 分支 100% 等价.
3. **累积语义保留**: 同 rowNumber 多次调用返回同一实例 (reference equal), 后续 setScore /
   addCellItem 累积到同一个 RowItemImpl (execute 内 "同 row 多个 ScoreRuntimeValue 累积到
   一个 RowItemImpl" 契约 100% 保留).

**Why 抽出 helper**: execute() 跑完整 sub-session (KnowledgeSessionFactory + Context +
parentSession), 无法 clean-input 单测. 抽出 private static helper `getOrCreateRow(Map, int)`
既是 cache-or-create 的纯函数封装 (缩短 execute()), 又让 V5.100.4 逻辑可单测 (clean inputs:
Map + int, 不依赖重装配). 这是 V5.100.4 跟 V5.100.0/1/2/3 的差异 — 前 4 个都是 inline fix, 本
个因为 execute 重依赖而抽出 helper (代码优雅 + 可测双收).

## 改动

### 文件 1: `ScoreRule.java` (1 inline → 1 helper call + 新 helper + javadoc)

- execute() 内 8 行 (if-containsKey-get / else-new-put) → 1 行 (`getOrCreateRow(rowMap, rowNumber)`)
- 新 private static `getOrCreateRow(Map, int)` (~10 行 + javadoc)

### 文件 2 (新 BDD): `ScoreRuleGetOrCreateRowTest.java` (8 tests, `@Nested` + Gherkin `@DisplayName`)

4 nested class: CacheMiss / CacheHit / AccumulationBehavior / BoundaryRowNumber.

- `CacheMiss.emptyMapCreatesNewRowItem` / `nonEmptyMapCreatesNewRowItemWithoutAffectingExisting`
- `CacheHit.repeatRowNumberReusesExistingInstance` / `repeatRowNumberPreservesExistingScore`
- `AccumulationBehavior.sameRowAccumulatesAcrossCalls` (模拟 execute loop 内 3 次同 row 调用 → 累积 2 cellItems + score)
- `AccumulationBehavior.differentRowsAccumulateIndependently` (不同 row 交替 → 各自独立实例)
- `BoundaryRowNumber.rowZeroTreatedAsValidKey` / `negativeRowNumberTreatedAsValidKey`

## Verification

### Step 1 — BDD + 全量回归

```bash
mvn test -pl lib/ruleforge-core -Dtest=ScoreRuleGetOrCreateRowTest
mvn test -pl lib/ruleforge-core
```

- BDD: **8/8 pass** (锁 V5.100.4 修法行为不变性)
- 全量: **763/763 pass** (was 755 → +8 BDD tests), 零 regression

### Step 2 — JFR 信号验证

ScoreRule.execute 是 runtime per-scorecard-eval 调用 (评分卡规则触发时跑, 不是 per-fact
hot path). JFR noise level 预期 (不在 top 15). 本 PR **0 perf 信号预期**, 跟 V5.100.1
(runtime per-fire-rule) 同档, 比 V5.100.0/2/3 (build-time) 稍频但仍是 noise level.

## 复用现有 utility / 模式

- 完全沿 V5.93 / V5.97 / V6.1 / V5.100.0/1/2/3 立的 "HashMap.get 已能区分 absent vs null value" 原则
- 抽出 private static helper 模式: 跟 V5.100.x 其它 fix 的 inline 模式不同, 因为 execute()
  重依赖 (KnowledgeSessionFactory + Context) 无法 clean-input 单测. helper 抽出是代码优雅 +
  可测双收.
- 0 新工具, 0 新 API, 纯 if-containsKey-get-else-new-put → get-nullcheck-new-put 化简

## Skip 维持

- `RulesRebuilder.java:614, 633` — `category = namedMap.get(category)` 重新赋值, **有 null-value
  风险**, V5.93 原则不适用, 不动.
- `EngineContext.java:44` — duplicate detection (throw on dup), 不是 first-wins, 不动.
- `EngineContext.findFunctionDescriptor` deprecation — 留 V5.100.5+ (2 caller 改 + 删 method).
- `KnowledgeSessionImpl.java:403, 429, 518` — group membership checks, 需单独 audit, 留后续.

## 风险 / 已知 trade-off

1. **null-value 风险 audit**: rowMap 唯一 put 是 `put(rowNumber, new RowItemImpl())`, 永不为
   null, 无 `put(key, null)` 风险. 等价性 100% 成立.
2. **cache-or-create 契约保留**: cache hit 复用 / cache miss 新建, 跟原 containsKey + else
   100% 等价 (CacheMiss + CacheHit nested class 显式 lock).
3. **累积语义保留**: 同 rowNumber 多次调用返回同一实例, setScore/addCellItem 累积到同一
   RowItemImpl (AccumulationBehavior nested class 显式 lock — 这是 execute 内真实用法).
4. **helper 抽出行为等价**: getOrCreateRow 是 execute 内逻辑的纯函数封装, 无副作用外泄
   (只 mutate 传入的 rowMap), 跟原 inline 逻辑 100% 等价.
5. **JFR noise level**: runtime per-scorecard-eval, 不是 per-fact hot path. 跟 V5.100.1 同档.
6. **private static helper 反射调用**: BDD test 用反射调 private static method (static, 不
   需要 instance, `m.invoke(null, ...)`). 跟 V5.100.0/1/2/3 的 instance method 反射不同.

## 引用

- [[v593-evaluationcontext-double-lookup]] V5.93 立的原则
- [[project-v5100-knowledgebuilder-addtolibrarymap-doublelookup]] V5.100.0 build-time
- [[project-v51001-executecommonfunctionaction-doublelookup]] V5.100.1 runtime per-fire-rule
- [[project-v51002-decisiontablerulesbuilder-findcellincolumn-doublelookup]] V5.100.2 build-time
- [[project-v51003-complexscorecardrulesbuilder-getcell-doublelookup]] V5.100.3 build-time
- [[feedback-version-x999-xcap]] V5.100.4 = V5.100 第四个 Fix (Fix 位 = 4)
- 未来 V5.100.5+ 候选: EngineContext.findFunctionDescriptor deprecation / 
  KnowledgeSessionImpl group membership containsKey audit / AndBuilder.buildCriterion outer
