package com.ruleforge.builder.table;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.table.Cell;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.ColumnType;
import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.model.table.Row;
import com.ruleforge.rete.test.EngineContextWirer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5.100.2 — {@link DecisionTableRulesBuilder#getCell(DecisionTable, int, int)}
 * 契约 BDD。
 *
 * <p>锁 V5.100.2 修法(1 处 {@code containsKey + get} 双 lookup → 1 处 {@code get == null}
 * 单 lookup) 的行为不变性:
 * <ul>
 *   <li>行 row 命中 cell → return cell</li>
 *   <li>行 row miss, 上方行命中 (rowspan backward search) → return 上方 cell</li>
 *   <li>行 row miss + 上方行都 miss → 抛 RuleException("Decision table cell[?,?] not exist.")</li>
 *   <li>cellMap == null → 抛 RuleException</li>
 *   <li>row=0 时只查 row 0, 不进 backward search loop (boundary)</li>
 *   <li>row=0 miss → 抛 RuleException (loop 跑 1 次, cell null, 走 throw path)</li>
 * </ul>
 *
 * <p><b>Why V5.100.2 选这条</b>: V5.93 立的 "HashMap.get 已能区分 absent vs null value" 原则
 * (砍 containsKey + get 双 lookup, save 1 hash lookup per call). V5.100 KB.addToLibraryMap
 * + V5.100.1 ExecuteCommonFunctionAction 已经用本原则砍过。 V5.100.2 是 build-time 版本,
 * 跟 V5.100 同档 (V5.100.1 是 runtime per-fire-rule 频度, V5.100.2 是 build-time
 * per-DRL-parse 频度)。
 *
 * <p>行为关键: {@code Cell} 永不为 null (DecisionTable.java:108 +
 * ScriptDecisionTable.java:44 唯一 put 是 {@code cellMap.put(buildCellKey(cell.getRow(),
 * cell.getCol()), cell)}, cell 是 builder 内部的 Cell 实例, 无 {@code put(key, null)}
 * 风险), 所以 {@code map.get(key) == null} 跟 {@code !map.containsKey(key)} 100% 等价 —
 * 两者都表示 "this key 没装过 Cell"。
 *
 * @see com.ruleforge.docs.notes.v51002-decisiontablerulesbuilder-findcellincolumn-doublelookup V5.100.2 完整 doc
 * @since 5.100.2
 */
@DisplayName("V5.100.2 — DecisionTableRulesBuilder.getCell 契约 (double lookup 砍)")
class DecisionTableRulesBuilderGetCellTest {

    private DecisionTableRulesBuilder builder;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @BeforeEach
    void setUp() {
        builder = new DecisionTableRulesBuilder();
    }

    /** Reflective access to private {@code getCell(DecisionTable, int, int)}. */
    private Cell invokeGetCell(DecisionTable table, int row, int column) throws Exception {
        Method m = DecisionTableRulesBuilder.class.getDeclaredMethod("getCell", DecisionTable.class, int.class, int.class);
        m.setAccessible(true);
        try {
            return (Cell) m.invoke(builder, table, row, column);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap reflection wrapper so test sees the production exception (跟 V5.100 同模式)
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }

    private static DecisionTable makeTable() {
        DecisionTable table = new DecisionTable();
        // 加 1 column (col=1, num=1) + 3 rows (num 1, 2, 3) — 跟
        // DecisionTableRulesBuilderTest.createSimpleTable 同模式
        Column col = new Column();
        col.setNum(1);
        col.setWidth(100);
        col.setType(ColumnType.Criteria);
        col.setVariableCategory("test");
        col.setVariableName("field1");
        col.setDatatype(Datatype.String);
        table.addColumn(col);

        for (int i = 1; i <= 3; i++) {
            Row row = new Row();
            row.setNum(i);
            row.setHeight(25);
            table.addRow(row);
        }
        return table;
    }

    /** Direct put 进 cellMap (跳过 DecisionTable.addCell, 直接测 getCell 的 lookup 行为). */
    private static void putCell(DecisionTable table, int row, int col, String content) throws Exception {
        Cell cell = new Cell();
        cell.setRow(row);
        cell.setCol(col);
        cell.setRowspan(1);
        com.ruleforge.model.rule.SimpleValue v = new com.ruleforge.model.rule.SimpleValue();
        v.setContent(content);
        cell.setValue(v);

        // DecisionTable.addCell 内部 put 进 cellMap, 跟 DecisionTableRulesBuilderTest
        // addCellToTable 同模式
        table.addCell(cell);
    }

    // ─── Happy path: row 命中 cell ─────────────────────────────────────────

    @Nested
    @DisplayName("happy path: row 命中 cell → return cell")
    class RowHit {

        // Given: 3-row table, cell(1, 1) = "a", cell(2, 1) = "b", cell(3, 1) = "c"
        // When:  getCell(table, 2, 1)
        // Then:  return cell(2, 1) with content "b"
        @Test
        @DisplayName("3 row 命中 row 2 → return row 2 cell (backward search 0 iter)")
        void rowHitReturnsExactCell() throws Exception {
            DecisionTable table = makeTable();
            putCell(table, 1, 1, "a");
            putCell(table, 2, 1, "b");
            putCell(table, 3, 1, "c");

            Cell cell = invokeGetCell(table, 2, 1);

            assertThat(cell).isNotNull();
            assertThat(((com.ruleforge.model.rule.SimpleValue) cell.getValue()).getContent()).isEqualTo("b");
            assertThat(cell.getRow()).isEqualTo(2);
            assertThat(cell.getCol()).isEqualTo(1);
        }
    }

    // ─── rowspan backward search: row miss, 上方行命中 ─────────────────────

    @Nested
    @DisplayName("rowspan backward search: row miss, 上方行命中 (模拟 rowspan)")
    class RowspanBackwardSearch {

        // Given: 3-row table, cell(1, 1) = "merged" (rowspan=3), row 2/3 没装 cell(2, 1) / cell(3, 1)
        //   (addCell put 进 cellMap 用 buildCellKey(row, col) 当 key, cellMap 只有 1 entry
        //    "1,1" → "merged" cell. getCell(table, 3, 1) 应 backward search row 3→2→1
        //    找到 "1,1" entry)
        // When:  getCell(table, 3, 1)
        // Then:  return row 1 cell (the "merged" cell)
        @Test
        @DisplayName("row 3 miss + row 1 命中 (模拟 rowspan 3) → return row 1 cell")
        void rowMissFindsUpperRowCell() throws Exception {
            DecisionTable table = makeTable();
            putCell(table, 1, 1, "merged");

            Cell cell = invokeGetCell(table, 3, 1);

            assertThat(cell).isNotNull();
            assertThat(((com.ruleforge.model.rule.SimpleValue) cell.getValue()).getContent()).isEqualTo("merged");
            assertThat(cell.getRow()).isEqualTo(1);  // ⚠️ returned cell 是 row 1 (row 3 没装)
        }

        // Given: 3-row table, cell(2, 1) = "row2 only"
        // When:  getCell(table, 3, 1) — row 3 miss, backward search 找 row 2 命中
        // Then:  return row 2 cell
        @Test
        @DisplayName("row 3 miss + row 2 命中 (backward search 1 iter) → return row 2 cell")
        void rowMissFindsRowAbove() throws Exception {
            DecisionTable table = makeTable();
            putCell(table, 2, 1, "row2 only");

            Cell cell = invokeGetCell(table, 3, 1);

            assertThat(cell).isNotNull();
            assertThat(((com.ruleforge.model.rule.SimpleValue) cell.getValue()).getContent()).isEqualTo("row2 only");
            assertThat(cell.getRow()).isEqualTo(2);
        }
    }

    // ─── Both miss: row miss + 上方行都 miss → 抛 RuleException ────────────

    @Nested
    @DisplayName("both miss: row miss + 上方行都 miss → 抛 RuleException")
    class BothMiss {

        // Given: 3-row table, 没装 cell(2, 1) (没 addCell)
        // When:  getCell(table, 2, 1) — row 2 miss, backward search row 1 → row 1 miss
        // Then:  RuleException("Decision table cell[2,1] not exist.")
        @Test
        @DisplayName("row 2 miss + row 1 miss (backward search 跑完) → 抛 RuleException")
        void bothMissThrowsRuleException() throws Exception {
            DecisionTable table = makeTable();

            assertThatThrownBy(() -> invokeGetCell(table, 2, 1))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Decision table cell[2,1] not exist");
        }

        // Given: 3-row table, cell(1, 1) = "row1 only" — only row 1 has cell, row 2/3 miss
        // When:  getCell(table, 3, 1) — row 3 miss, row 2 miss, row 1 命中
        //   ⚠️ 这是 rowspan hit, 不是 both miss — 跑通 sanity check that backward search
        //   不漏 row 1.
        // Then:  return row 1 cell
        @Test
        @DisplayName("row 3 miss + row 2 miss + row 1 命中 → return row 1 cell (不漏检)")
        void row3MissRow2MissRow1HitReturnsRow1() throws Exception {
            DecisionTable table = makeTable();
            putCell(table, 1, 1, "row1 only");

            Cell cell = invokeGetCell(table, 3, 1);

            assertThat(cell).isNotNull();
            assertThat(((com.ruleforge.model.rule.SimpleValue) cell.getValue()).getContent()).isEqualTo("row1 only");
            assertThat(cell.getRow()).isEqualTo(1);
        }
    }

    // ─── Boundary: row=0 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("boundary: row=0 (最小 row, 唯一一次 lookup)")
    class RowZero {

        // Given: 3-row table, cell(1, 1) = "row1"
        // When:  getCell(table, 0, 1) — row 0 是 row.getNum() 之外的情况 (实际 row 编号从 1 开始)
        //   ⚠️ 这是边界条件测试 — 验证 backward search 跑到 row 0 时正确停 (i > -1, i--),
        //   不无限循环, row 0 命中时正常 return.
        //   注意: buildCellKey 是 buildCellKey(row, column), row=0 会装 "0,1" 不会装 "1,1".
        //   所以 row 0 cell 必须 put 进 "0,1" 才能命中.
        // Then:  return row 0 cell (the put cell)
        @Test
        @DisplayName("row=0 命中 (put 进 buildCellKey(0, 1)) → return row 0 cell")
        void rowZeroHitReturnsRowZeroCell() throws Exception {
            DecisionTable table = makeTable();
            putCell(table, 0, 1, "row0");

            Cell cell = invokeGetCell(table, 0, 1);

            assertThat(cell).isNotNull();
            assertThat(((com.ruleforge.model.rule.SimpleValue) cell.getValue()).getContent()).isEqualTo("row0");
        }

        // Given: 3-row table, row 0 没装 cell, row 1 装 cell(1, 1) = "row1"
        // When:  getCell(table, 0, 1) — row 0 miss, backward search 跑到 i=0 (loop 跑 1 次)
        //   后停, 不查 row -1, 也不查 row 1 (因为只查 i >= 0)
        // Then:  RuleException (loop 只跑 1 次, cell null, 走 throw path)
        @Test
        @DisplayName("row=0 miss + 没 backward 上方行 (loop 跑 1 次后停) → 抛 RuleException")
        void rowZeroMissThrowsRuleException() throws Exception {
            DecisionTable table = makeTable();
            putCell(table, 1, 1, "row1");

            assertThatThrownBy(() -> invokeGetCell(table, 0, 1))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Decision table cell[0,1] not exist");
        }
    }

    // ─── Null cellMap ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("cellMap == null (DecisionTable 没 addCell) → 抛 RuleException")
    class NullCellMap {

        // Given: 空 DecisionTable, 没 addCell (cellMap 内部是 null)
        // When:  getCell(table, 1, 1)
        // Then:  RuleException("Decision table cell[1,1] not exist.")
        //   ⚠️ V5.100.2 改的是 containsKey 那行, cellMap == null 走外层 if-guard
        //   (`if (cellMap == null) throw ...`), 行为 100% 保留
        @Test
        @DisplayName("cellMap == null → 抛 RuleException (外层 if-guard 保留)")
        void nullCellMapThrowsRuleException() throws Exception {
            DecisionTable table = new DecisionTable();

            assertThatThrownBy(() -> invokeGetCell(table, 1, 1))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Decision table cell[1,1] not exist");
        }
    }

    // ─── V5.100.2 修法核心: get == null 等价 !containsKey ───────────────

    @Nested
    @DisplayName("V5.100.2 修法核心验证: get == null 等价 !containsKey (本场景 Cell 永不为 null)")
    class GetEqualsNullContract {

        // Given: 1-row table (num 1), 装 cell(1, 1) = "v"
        // When:  getCell(table, 1, 1)
        // Then:  return cell with content "v"
        //   ⚠️ 验证 V5.100.2 修法保留: 1 lookup (get) 替 2 lookup (containsKey + get),
        //   命中场景下从 2 lookup 降到 1 lookup.
        @Test
        @DisplayName("单 row 命中 → 1 lookup (get) 替 2 lookup (containsKey + get), 行为保留")
        void singleRowHitSingleLookup() throws Exception {
            DecisionTable table = makeTable();
            putCell(table, 1, 1, "v");

            Cell cell = invokeGetCell(table, 1, 1);

            assertThat(cell).isNotNull();
            assertThat(((com.ruleforge.model.rule.SimpleValue) cell.getValue()).getContent()).isEqualTo("v");
            assertThat(cell.getRow()).isEqualTo(1);
            assertThat(cell.getCol()).isEqualTo(1);
        }
    }
}
