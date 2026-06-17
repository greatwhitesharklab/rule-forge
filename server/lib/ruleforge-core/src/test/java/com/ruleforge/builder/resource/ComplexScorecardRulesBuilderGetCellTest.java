package com.ruleforge.builder.resource;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.scorecard.ComplexScorecardDefinition;
import com.ruleforge.model.table.Cell;
import com.ruleforge.rete.test.EngineContextWirer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5.100.3 — {@link ComplexScorecardRulesBuilder#getCell(ComplexScorecardDefinition, int, int)}
 * 契约 BDD。
 *
 * <p>锁 V5.100.3 修法(1 处 {@code containsKey + get} 双 lookup → 1 处 {@code get == null}
 * 单 lookup) 的行为不变性。 本方法是 V5.100.2 {@code DecisionTableRulesBuilder.getCell}
 * 的<strong>孪生</strong> — 两个方法 backward search loop + containsKey + get + throw
 * 100% 同构, 只是操作 ComplexScorecardDefinition 而非 DecisionTable。
 *
 * <p>不变性 (跟 V5.100.2 一致):
 * <ul>
 *   <li>行 row 命中 cell → return cell</li>
 *   <li>行 row miss, 上方行命中 (rowspan backward search) → return 上方 cell</li>
 *   <li>行 row miss + 上方行都 miss → 抛 RuleException("Decision table cell[?,?] not exist.")</li>
 *   <li>row=0 命中 → return row 0 cell (boundary)</li>
 *   <li>row=0 miss → 抛 RuleException (loop 跑 1 次, cell null, 走 throw path)</li>
 * </ul>
 *
 * <p><b>Why V5.100.3 选这条</b>: V5.100.2 立的 "build-time HashMap.containsKey + get 双 lookup
 * → get == null" 原则在 ComplexScorecard 的孪生方法上落地。 跟 V5.100.2 完全同档 (build-time
 * per-scorecard-build, JFR 0 sample 预期)。
 *
 * <p>行为关键: {@code Cell} 永不为 null (ComplexScorecardDefinition.java:173 唯一 put 是
 * {@code cellMap.put(buildCellKey(cell.getRow(), cell.getCol()), cell)}, cell 是 builder
 * 内部的 Cell 实例, 无 {@code put(key, null)} 风险), 所以 {@code map.get(key) == null} 跟
 * {@code !map.containsKey(key)} 100% 等价。
 *
 * <p><b>跟 V5.100.2 的差异</b>: 本方法<strong>没有</strong> {@code if (cellMap == null) throw}
 * 外层 guard (ComplexScorecardDefinition.addCell 内部 lazy-init cellMap, 但 getCellMap 在
 * 无 addCell 时返 null → 本方法 loop 内 NPE). 这是 pre-existing 行为, V5.100.3 不动 (本 PR
 * 只砍 containsKey, 不加 null guard). 所以本 test 不测 null-cellMap (pre-existing NPE, 跟
 * V5.100.2 的 RuleException guard 不同)。
 *
 * @see com.ruleforge.docs.notes.v51003-complexscorecardrulesbuilder-getcell-doublelookup V5.100.3 完整 doc
 * @since 5.100.3
 */
@DisplayName("V5.100.3 — ComplexScorecardRulesBuilder.getCell 契约 (double lookup 砍, V5.100.2 孪生)")
class ComplexScorecardRulesBuilderGetCellTest {

    private ComplexScorecardRulesBuilder builder;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @BeforeEach
    void setUp() {
        builder = new ComplexScorecardRulesBuilder();
    }

    /** Reflective access to private {@code getCell(ComplexScorecardDefinition, int, int)}. */
    private Cell invokeGetCell(ComplexScorecardDefinition table, int row, int column) throws Exception {
        Method m = ComplexScorecardRulesBuilder.class.getDeclaredMethod(
                "getCell", ComplexScorecardDefinition.class, int.class, int.class);
        m.setAccessible(true);
        try {
            return (Cell) m.invoke(builder, table, row, column);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap reflection wrapper so test sees the production exception (跟 V5.100.2 同模式)
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }

    private static ComplexScorecardDefinition makeTable() {
        return new ComplexScorecardDefinition();
    }

    /** Direct put 进 cellMap via addCell (跟 DecisionTableRulesBuilderGetCellTest 同模式). */
    private static void putCell(ComplexScorecardDefinition table, int row, int col, String content) {
        Cell cell = new Cell();
        cell.setRow(row);
        cell.setCol(col);
        cell.setRowspan(1);
        SimpleValue v = new SimpleValue();
        v.setContent(content);
        cell.setValue(v);
        table.addCell(cell);
    }

    // ─── Happy path: row 命中 cell ─────────────────────────────────────────

    @Nested
    @DisplayName("happy path: row 命中 cell → return cell")
    class RowHit {

        // Given: table with cell(1, 1) = "a", cell(2, 1) = "b", cell(3, 1) = "c"
        // When:  getCell(table, 2, 1)
        // Then:  return cell(2, 1) with content "b"
        @Test
        @DisplayName("row 2 命中 → return row 2 cell (backward search 0 iter)")
        void rowHitReturnsExactCell() throws Exception {
            ComplexScorecardDefinition table = makeTable();
            putCell(table, 1, 1, "a");
            putCell(table, 2, 1, "b");
            putCell(table, 3, 1, "c");

            Cell cell = invokeGetCell(table, 2, 1);

            assertThat(cell).isNotNull();
            assertThat(((SimpleValue) cell.getValue()).getContent()).isEqualTo("b");
            assertThat(cell.getRow()).isEqualTo(2);
            assertThat(cell.getCol()).isEqualTo(1);
        }
    }

    // ─── rowspan backward search: row miss, 上方行命中 ─────────────────────

    @Nested
    @DisplayName("rowspan backward search: row miss, 上方行命中 (模拟 rowspan)")
    class RowspanBackwardSearch {

        // Given: table with cell(1, 1) = "merged" only (模拟 rowspan 3)
        // When:  getCell(table, 3, 1) — row 3 miss, backward search 找 row 1 命中
        // Then:  return row 1 cell
        @Test
        @DisplayName("row 3 miss + row 1 命中 (模拟 rowspan 3) → return row 1 cell")
        void rowMissFindsUpperRowCell() throws Exception {
            ComplexScorecardDefinition table = makeTable();
            putCell(table, 1, 1, "merged");

            Cell cell = invokeGetCell(table, 3, 1);

            assertThat(cell).isNotNull();
            assertThat(((SimpleValue) cell.getValue()).getContent()).isEqualTo("merged");
            assertThat(cell.getRow()).isEqualTo(1);  // returned cell 是 row 1 (row 3 没装)
        }

        // Given: table with cell(2, 1) = "row2 only"
        // When:  getCell(table, 3, 1) — row 3 miss, backward search 找 row 2 命中
        // Then:  return row 2 cell
        @Test
        @DisplayName("row 3 miss + row 2 命中 (backward search 1 iter) → return row 2 cell")
        void rowMissFindsRowAbove() throws Exception {
            ComplexScorecardDefinition table = makeTable();
            putCell(table, 2, 1, "row2 only");

            Cell cell = invokeGetCell(table, 3, 1);

            assertThat(cell).isNotNull();
            assertThat(((SimpleValue) cell.getValue()).getContent()).isEqualTo("row2 only");
            assertThat(cell.getRow()).isEqualTo(2);
        }
    }

    // ─── Both miss: row miss + 上方行都 miss → 抛 RuleException ────────────

    @Nested
    @DisplayName("both miss: row miss + 上方行都 miss → 抛 RuleException")
    class BothMiss {

        // Given: table with no cell(2, 1) installed
        // When:  getCell(table, 2, 1) — row 2 miss, backward search row 1 → row 1 miss
        // Then:  RuleException("Decision table cell[2,1] not exist.")
        @Test
        @DisplayName("row 2 miss + row 1 miss (backward search 跑完) → 抛 RuleException")
        void bothMissThrowsRuleException() throws Exception {
            ComplexScorecardDefinition table = makeTable();
            // 只装一个不相关 col 的 cell, 让 row 2/1 col 1 miss
            putCell(table, 1, 2, "unrelated");

            assertThatThrownBy(() -> invokeGetCell(table, 2, 1))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Decision table cell[2,1] not exist");
        }

        // Given: table with cell(1, 1) = "row1 only" — only row 1 has cell
        // When:  getCell(table, 3, 1) — row 3 miss, row 2 miss, row 1 命中
        // Then:  return row 1 cell (sanity: backward search 不漏 row 1)
        @Test
        @DisplayName("row 3 miss + row 2 miss + row 1 命中 → return row 1 cell (不漏检)")
        void row3MissRow2MissRow1HitReturnsRow1() throws Exception {
            ComplexScorecardDefinition table = makeTable();
            putCell(table, 1, 1, "row1 only");

            Cell cell = invokeGetCell(table, 3, 1);

            assertThat(cell).isNotNull();
            assertThat(((SimpleValue) cell.getValue()).getContent()).isEqualTo("row1 only");
            assertThat(cell.getRow()).isEqualTo(1);
        }
    }

    // ─── Boundary: row=0 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("boundary: row=0 (最小 row, 唯一一次 lookup)")
    class RowZero {

        // Given: table with cell(0, 1) = "row0"
        // When:  getCell(table, 0, 1)
        // Then:  return row 0 cell
        @Test
        @DisplayName("row=0 命中 (put 进 buildCellKey(0, 1)) → return row 0 cell")
        void rowZeroHitReturnsRowZeroCell() throws Exception {
            ComplexScorecardDefinition table = makeTable();
            putCell(table, 0, 1, "row0");

            Cell cell = invokeGetCell(table, 0, 1);

            assertThat(cell).isNotNull();
            assertThat(((SimpleValue) cell.getValue()).getContent()).isEqualTo("row0");
        }

        // Given: table with cell(1, 1) = "row1" only
        // When:  getCell(table, 0, 1) — row 0 miss, backward search 跑 1 次后停
        // Then:  RuleException (loop 只跑 1 次, cell null, 走 throw path)
        @Test
        @DisplayName("row=0 miss + 没 backward 上方行 (loop 跑 1 次后停) → 抛 RuleException")
        void rowZeroMissThrowsRuleException() throws Exception {
            ComplexScorecardDefinition table = makeTable();
            putCell(table, 1, 1, "row1");

            assertThatThrownBy(() -> invokeGetCell(table, 0, 1))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Decision table cell[0,1] not exist");
        }
    }

    // ─── V5.100.3 修法核心: get == null 等价 !containsKey ───────────────

    @Nested
    @DisplayName("V5.100.3 修法核心验证: get == null 等价 !containsKey (本场景 Cell 永不为 null)")
    class GetEqualsNullContract {

        // Given: table with cell(1, 1) = "v"
        // When:  getCell(table, 1, 1)
        // Then:  return cell with content "v"
        //   ⚠️ 验证 V5.100.3 修法保留: 1 lookup (get) 替 2 lookup (containsKey + get),
        //   命中场景下从 2 lookup 降到 1 lookup.
        @Test
        @DisplayName("单 row 命中 → 1 lookup (get) 替 2 lookup (containsKey + get), 行为保留")
        void singleRowHitSingleLookup() throws Exception {
            ComplexScorecardDefinition table = makeTable();
            putCell(table, 1, 1, "v");

            Cell cell = invokeGetCell(table, 1, 1);

            assertThat(cell).isNotNull();
            assertThat(((SimpleValue) cell.getValue()).getContent()).isEqualTo("v");
            assertThat(cell.getRow()).isEqualTo(1);
            assertThat(cell.getCol()).isEqualTo(1);
        }
    }
}
