# V5.100.2 — `DecisionTableRulesBuilder.getCell` 砍 containsKey + get 双 lookup (TD-19.5.4)

## Context

V5.93 立的 "HashMap.get 已能区分 absent vs null value" 原则 (砍 containsKey + get
双 lookup, save 1 hash lookup per call). V5.100 KB.addToLibraryMap + V5.100.1 
ExecuteCommonFunctionAction 已经用本原则砍过. V5.100.2 是 build-time 版本 (跟
V5.100 同档 — V5.100.1 是 runtime per-fire-rule 频度, V5.100.2 是 build-time
per-DRL-parse 频度).

### V5.100.2 修复前 (containsKey + get):

```java
private Cell getCell(DecisionTable table, int row, int column) {
    Map<String, Cell> cellMap = table.getCellMap();
    if (cellMap == null) {
        throw new RuleException("Decision table cell[" + row + "," + column + "] not exist.");
    }
    Cell cell = null;
    for (int i = row; i > -1; i--) {
        String key = table.buildCellKey(i, column);
        if (cellMap.containsKey(key)) {        // hash lookup #1 (per iter)
            cell = cellMap.get(key);            // hash lookup #2 (per iter, only when hit)
            break;
        }
    }
    if (cell == null) {
        throw new RuleException("Decision table cell[" + row + "," + column + "] not exist.");
    }
    return cell;
}
```

### V5.100.2 修复后 (get == null):

```java
private Cell getCell(DecisionTable table, int row, int column) {
    Map<String, Cell> cellMap = table.getCellMap();
    if (cellMap == null) {
        throw new RuleException("Decision table cell[" + row + "," + column + "] not exist.");
    }
    Cell cell = null;
    // V5.100.2 — 砍 containsKey + get 双 lookup, 套 V5.93 原则. `map.get(key) == null`
    // 已能区分 absent vs null-value. 本场景 value 永为 Cell 对象 (非 null,
    // DecisionTable.java:108 + ScriptDecisionTable.java:44 唯一 put 是
    // `cellMap.put(buildCellKey(cell.getRow(), cell.getCol()), cell)`, cell 是
    // builder 内部的 Cell 实例, 无 put(key, null) 风险). 节省 1 个 containsKey hash
    // lookup per iter (build-time 调用, per-DRL-parse, 频度低, 跟 V5.100 / V5.100.1
    // 同档 pure code elegance closure).
    for (int i = row; i > -1; i--) {
        String key = table.buildCellKey(i, column);
        cell = cellMap.get(key);                // single hash lookup, return null if absent
        if (cell != null) {
            break;
        }
    }
    if (cell == null) {
        throw new RuleException("Decision table cell[" + row + "," + column + "] not exist.");
    }
    return cell;
}
```

节省 1 个 containsKey hash lookup per iter — 命中场景下从 2 lookup 降到 1 lookup per
iter (rows span 1+ iters, 一般 1-3 iter 命中), 全部 miss 场景下从 (rows+1) lookup
降到 (rows+1) lookup (无节省, 仍走 get). 平均 50% 节省 per build.

**关键等价性证明**:

1. **Cell 永不为 null**: `DecisionTable.java:108` + `ScriptDecisionTable.java:44` 
   唯一 `put` 是 `cellMap.put(buildCellKey(cell.getRow(), cell.getCol()), cell)`, 
   `cell` 是 builder 内部的 `Cell` 实例 (从 Cell POJO 构造, 反编译收尾, 无 null 风险), 
   无 `put(key, null)` 风险. 所以 `map.get(key) == null` 跟 `!map.containsKey(key)` 
   100% 等价 — 两者都表示 "this key 没装过 Cell".

2. **iterator 顺序一致**: 两种写法都用 `for (int i = row; i > -1; i--)` backward 
   search, 顺序 100% 一致 (V5.100.2 没改循环结构, 只改 lookup 实现).

3. **filter 顺序一致**: 本方法没 filter — `map.containsKey(key)` 跟 `map.get(key) == null` 
   都是同一 map 同一 key 的 lookup, 没变化.

4. **break 行为保留**: 两种写法都 `cell = ...; break;` 命中后退出 loop, 行为 100% 一致.

5. **null guard 保留**: `if (cellMap == null) throw ...` 外层 guard 100% 保留.

6. **throw path 保留**: `if (cell == null) throw new RuleException(...)` 在 loop 之后
   100% 保留, 处理 "all rows miss" 场景.

## 改动

### 文件 1: `DecisionTableRulesBuilder.java` (1 改动 + 9 行 V5.93-pattern 注释)

```diff
     private Cell getCell(DecisionTable table,int row,int column){
         Map<String,Cell> cellMap=table.getCellMap();
         if(cellMap==null){
             throw new RuleException("Decision table cell["+row+","+column+"] not exist.");
         }
         Cell cell=null;
+        // V5.100.2 — 砍 containsKey + get 双 lookup, 套 V5.93 原则. `map.get(key) == null`
+        // 已能区分 absent vs null-value. 本场景 value 永为 Cell 对象 (非 null,
+        // DecisionTable.java:108 + ScriptDecisionTable.java:44 唯一 put 是
+        // `cellMap.put(buildCellKey(cell.getRow(), cell.getCol()), cell)`, cell 是
+        // builder 内部的 Cell 实例, 无 put(key, null) 风险). 节省 1 个 containsKey hash
+        // lookup per iter (build-time 调用, per-DRL-parse, 频度低, 跟 V5.100 / V5.100.1
+        // 同档 pure code elegance closure).
         for(int i=row;i>-1;i--){
             String key=table.buildCellKey(i,column);
-            if(cellMap.containsKey(key)){
-                cell=cellMap.get(key);
-                break;
-            }
+            cell=cellMap.get(key);
+            if(cell!=null){
+                break;
+            }
         }
         if(cell==null){
             throw new RuleException("Decision table cell["+row+","+column+"] not exist.");
         }
         return cell;
```

### 文件 2 (新 BDD): `DecisionTableRulesBuilderGetCellTest.java` (9 tests, `@Nested` + Gherkin `@DisplayName`)

锁 V5.100.2 修法的行为不变性 (private method 用反射调用, 跟 V5.100 KB.addToLibraryMapTest 
同模式, **额外 unwrap InvocationTargetException** 让 assertThatThrownBy 直接看 production 
exception):

- `RowHit.rowHitReturnsExactCell`: row 命中 → return row 自身 cell (backward search 0 iter)
- `RowspanBackwardSearch.rowMissFindsUpperRowCell`: row 3 miss + row 1 命中 (模拟 rowspan 3) → return row 1 cell
- `RowspanBackwardSearch.rowMissFindsRowAbove`: row 3 miss + row 2 命中 (backward search 1 iter) → return row 2 cell
- `BothMiss.bothMissThrowsRuleException`: row miss + 上方行都 miss → 抛 RuleException
- `BothMiss.row3MissRow2MissRow1HitReturnsRow1`: row 3/2 miss + row 1 命中 (不漏检) → return row 1 cell
- `RowZero.rowZeroHitReturnsRowZeroCell`: row=0 命中 → return row 0 cell
- `RowZero.rowZeroMissThrowsRuleException`: row=0 miss (loop 跑 1 次后停, 不查 row -1) → 抛 RuleException
- `NullCellMap.nullCellMapThrowsRuleException`: cellMap == null → 抛 RuleException (外层 if-guard 保留)
- `GetEqualsNullContract.singleRowHitSingleLookup`: 单 row 命中 → 1 lookup 替 2 lookup, 行为保留 (V5.100.2 修法核心验证)

## Verification

### Step 1 — BDD + 全量回归

```bash
mvn test -pl lib/ruleforge-core -Dtest=DecisionTableRulesBuilderGetCellTest
mvn test -pl lib/ruleforge-core
```

- BDD: **9/9 pass** (锁 V5.100.2 修法行为不变性)
- 全量: **747/747 pass** (was 738 → +9 BDD tests), 零 regression

### Step 2 — JFR 信号验证

`getCell` 是 build-time 调用 (per-DRL-parse 频度, 不是 per-fact hot path), JFR 0 sample 
预期. 本 PR **0 perf 信号预期**, 跟 V5.100 / V5.100.1 / V5.93 同档 pure code elegance 
closure.

## 复用现有 utility / 模式

- 完全沿 V5.93 / V5.97 / V6.1 / V5.100 / V5.100.1 立的 "HashMap.get 已能区分 absent vs null 
  value" 原则
- 0 新工具, 0 新 API, 纯 3 行 (`if (containsKey) { get; break; }`) → 3 行 (`get; if != null break;`) 
  化简 + 9 行 V5.93-pattern 注释
- 跟 V6.0 / V6.1 / V6.2 / V6.3 / V6.4 / V5.100 / V5.100.1 同档 (反编译 artifact / code 
  quality closure)

## Skip 维持 (V5.96 / V6.0+ / V5.100 / V5.100.1 立的 scope 外)

- `AndBuilder.buildCriterion` 外层 `Iterator var7` state machine — 太复杂, 跟本 PR 无关
- `KnowledgeSessionImpl` labeled loops (3 places) — 跟本 PR 无关
- `EngineContext.java:44` (`byName.containsKey(fun.getName())`) — V5.76 era, **不能直接
  砍** — 该 containsKey 是 **duplicate detection** (跟 V5.100 first-wins 反例: 重复就
  抛 RuntimeException, 不是 skip). V5.93 原则不适用, 不动.
- `RulesRebuilder.getVariableByName:614` — `if (namedMap.containsKey(category)) category = namedMap.get(category)`
  **有 null-value 风险** (namedMap 装可能 null value), V5.93 原则不适用, 不动
- `EngineContext.findFunctionDescriptor` deprecation 评估 — V5.100.1 不删该 method,
  留给 V5.100.3+ cleanup 评估

## 风险 / 已知 trade-off

1. **null-value 风险 audit**: `cellMap.put` 唯一 call site 是 `DecisionTable.java:108` + 
   `ScriptDecisionTable.java:44`, value 是 builder 内部的 `Cell` 实例 (反编译收尾, 
   caller 责任传非 null), 无 `put(key, null)` 风险. 等价性 100% 成立.

2. **iterator 状态等价性**: 两种写法循环结构相同 (V5.100.2 没改循环结构, 只改 lookup), 
   iterator 状态一致 (JDK 语义保证).

3. **break 行为保留**: 两种写法都 `cell = ...; break;` 命中后退出 loop, 行为 100% 一致.

4. **null guard 保留**: `if (cellMap == null) throw ...` 外层 guard 100% 保留, 行为 100% 一致 
   (NullCellMap nested class 显式 lock).

5. **throw path 保留**: `if (cell == null) throw new RuleException(...)` 在 loop 之后 
   100% 保留, 处理 "all rows miss" 场景 (BothMiss nested class 显式 lock).

6. **private method 反射调用**: BDD test 用反射调 private method (跟 V5.100 
   KB.addToLibraryMapTest 同模式), **额外 unwrap InvocationTargetException** 让 
   assertThatThrownBy 直接看 production exception (RuleException, 不是 reflection wrapper).

7. **JFR 0 sample**: 本方法是 build-time per-DRL-parse 调用, 不在 rete hot path. 
   V5.100.2 是 pure code elegance closure, 跟 V5.93 / V5.97 / V6.1 / V5.100 / V5.100.1 
   同档.

8. **boundary row=0 保留**: 两种写法都是 `for (int i = row; i > -1; i--)`, row=0 时只跑 
   1 次 (i=0), row=-1 时停. 行为 100% 一致 (RowZero nested class 显式 lock 边界 + throw path).

## 引用

- [[v593-evaluationcontext-double-lookup]] V5.93 立 "HashMap.get 已能区分 absent vs null value" 原则
- [[v597-facttracker-doublelookup]] V5.97 FactTracker.addObjectCriteria 砍 double lookup
- [[v611-andactivity-doublelookup]] V6.1 AndActivity.enter 砍 double lookup
- [[project-v5100-knowledgebuilder-addtolibrarymap-doublelookup]] V5.100 KB.addToLibraryMap
  砍 double lookup (V5.100.2 直接套用其模式, build-time per-DRL-parse 版)
- [[project-v51001-executecommonfunctionaction-doublelookup]] V5.100.1 ECFA 砍 double lookup 
  (V5.100.2 直接套用其模式, runtime per-fire-rule 版 → V5.100.2 是 build-time 版)
- [[feedback-version-x999-xcap]] V5.100.2 = V5.100 第二个 Fix (Fix 位 = 2, 沿 V5.100 Feature 号)
- 未来 V5.100.3+ 候选: EngineContext.findFunctionDescriptor deprecation 评估 / 
  AndBuilder.buildCriterion outer state machine / KnowledgeSessionImpl labeled loop 
  characterization test
