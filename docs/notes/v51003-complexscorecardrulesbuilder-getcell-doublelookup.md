# V5.100.3 — `ComplexScorecardRulesBuilder.getCell` 砍 containsKey + get 双 lookup (TD-19.5.4)

## Context

V5.100.2 立的 "build-time HashMap.containsKey + get 双 lookup → get == null" 原则在
ComplexScorecard 的<strong>孪生方法</strong>上落地. `ComplexScorecardRulesBuilder.getCell:127-143`
跟 V5.100.2 `DecisionTableRulesBuilder.getCell` 100% 同构 — 两个方法 backward search loop +
containsKey + get + throw 完全一样, 只是操作 `ComplexScorecardDefinition` 而非 `DecisionTable`.

### V5.100.3 修复前 (containsKey + get):

```java
private Cell getCell(ComplexScorecardDefinition table, int row, int column) {
    Map<String, Cell> cellMap = table.getCellMap();
    Cell cell = null;

    for (int i = row; i > -1; --i) {
        String key = table.buildCellKey(i, column);
        if (cellMap.containsKey(key)) {        // hash lookup #1 (per iter)
            cell = cellMap.get(key);            // hash lookup #2 (per iter, only when hit)
            break;
        }
    }

    if (cell == null) {
        throw new RuleException("Decision table cell[" + row + "," + column + "] not exist.");
    } else {
        return cell;
    }
}
```

### V5.100.3 修复后 (get == null):

```java
private Cell getCell(ComplexScorecardDefinition table, int row, int column) {
    Map<String, Cell> cellMap = table.getCellMap();
    Cell cell = null;

    // V5.100.3 — 砍 containsKey + get 双 lookup, 套 V5.93 原则...
    for (int i = row; i > -1; --i) {
        String key = table.buildCellKey(i, column);
        cell = cellMap.get(key);                // single hash lookup
        if (cell != null) {
            break;
        }
    }

    if (cell == null) {
        throw new RuleException("Decision table cell[" + row + "," + column + "] not exist.");
    } else {
        return cell;
    }
}
```

**关键等价性证明** (跟 V5.100.2 一致):

1. **Cell 永不为 null**: `ComplexScorecardDefinition.java:173` 唯一 `put` 是
   `cellMap.put(buildCellKey(cell.getRow(), cell.getCol()), cell)`, cell 是 builder 内部的
   `Cell` 实例 (反编译收尾, 无 null 风险), 无 `put(key, null)` 风险.
2. **iterator 顺序一致**: 两种写法都用 `for (int i = row; i > -1; --i)` backward search.
3. **break 行为保留**: 两种写法都 `cell = ...; break;` 命中后退出 loop.
4. **throw path 保留**: `if (cell == null) throw new RuleException(...)` 100% 保留.
5. **boundary row=0 保留**: 两种写法都 `i > -1`, row=0 跑 1 次后停.

**跟 V5.100.2 的差异** (不动): 本方法<strong>没有</strong> `if (cellMap == null) throw` 外层
guard — `ComplexScorecardDefinition.addCell` 内部 lazy-init cellMap, 但 `getCellMap()` 在无
addCell 时返 null → 本方法 loop 内 `cellMap.get(key)` NPE. 这是 pre-existing 行为, V5.100.3
不动 (本 PR 只砍 containsKey, 不加 null guard). 所以 BDD test 不测 null-cellMap.

## 改动

### 文件 1: `ComplexScorecardRulesBuilder.java` (1 改动 + 8 行 V5.93-pattern 注释)

### 文件 2 (新 BDD): `ComplexScorecardRulesBuilderGetCellTest.java` (8 tests, `@Nested` + Gherkin `@DisplayName`)

跟 V5.100.2 同模式 (反射 unwrap InvocationTargetException), 5 nested class: RowHit /
RowspanBackwardSearch / BothMiss / RowZero / GetEqualsNullContract.

## Verification

### Step 1 — BDD + 全量回归

```bash
mvn test -pl lib/ruleforge-core -Dtest=ComplexScorecardRulesBuilderGetCellTest
mvn test -pl lib/ruleforge-core
```

- BDD: **8/8 pass** (锁 V5.100.3 修法行为不变性)
- 全量: **755/755 pass** (was 747 → +8 BDD tests), 零 regression

### Step 2 — JFR 信号验证

`getCell` 是 build-time per-scorecard-build 调用, JFR 0 sample 预期. 跟 V5.100 / V5.100.2
同档 pure code elegance closure.

## 复用现有 utility / 模式

- 完全沿 V5.100.2 立的模式 (build-time backward search loop + containsKey + get + throw →
  get == null). 0 新工具, 0 新 API.
- 跟 V5.100.2 是孪生 (两个 getCell 方法 100% 同构, 只是操作不同 Definition 类).

## Skip 维持

- `ScoreRule.java:63-64` — runtime 评分路径, computeIfAbsent-style cache lookup, 留 V5.100.4
  (形状不同: cache-or-create, 不是 find-in-loop).
- `RulesRebuilder.java:614, 633` — **有 null-value 风险**, V5.93 原则不适用, 不动.
- `EngineContext.java:44` — duplicate detection (throw on dup), 不是 first-wins, 不动.
- `EngineContext.findFunctionDescriptor` deprecation — 留 V5.100.5+ (2 caller 改 + 删 method).

## 风险 / 已知 trade-off

1. **null-value 风险 audit**: 唯一 put site `ComplexScorecardDefinition.java:173`, value 是
   builder 内部 Cell 实例, 无 `put(key, null)` 风险. 等价性 100% 成立.
2. **iterator 状态等价性**: 循环结构不变 (V5.100.3 只改 lookup), iterator 状态一致.
3. **break / throw / boundary 保留**: 3 处行为 100% 保留 (BDD 显式 lock).
4. **null-cellMap pre-existing NPE**: 本方法无 null guard, getCellMap() null 时 loop NPE.
   pre-existing 行为, V5.100.3 不动 (不测, 不加 guard).
5. **JFR 0 sample**: build-time per-scorecard-build, 不在 rete hot path.

## 引用

- [[v593-evaluationcontext-double-lookup]] V5.93 立的原则
- [[project-v51002-decisiontablerulesbuilder-findcellincolumn-doublelookup]] V5.100.2 
  (V5.100.3 的孪生, 完全同构)
- [[feedback-version-x999-xcap]] V5.100.3 = V5.100 第三个 Fix (Fix 位 = 3)
- 未来 V5.100.4+ 候选: ScoreRule cache-or-create / EngineContext.findFunctionDescriptor 
  deprecation / AndBuilder.buildCriterion outer / KnowledgeSessionImpl labeled loop
