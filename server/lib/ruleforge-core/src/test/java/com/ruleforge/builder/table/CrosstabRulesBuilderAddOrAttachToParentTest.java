package com.ruleforge.builder.table;

import com.ruleforge.builder.table.CellRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.9.14 — {@link CrosstabRulesBuilder#addOrAttachToParent} 行为契约 BDD。
 *
 * <p>锁 V6.9.14 收口 (从 addTopCell L206-215 + addLeftCell L240-249 抽
 * `addOrAttachToParent(range, start, end, ranges)` helper 消 8 行重复) 的行为不变性:
 * <ul>
 *   <li><b>start==1</b>: range 直接 add 到 ranges 列表</li>
 *   <li><b>start&gt;1 + 已有 parent range 包含 [start,end]</b>: range add 为 child (parent.addChildRange)</li>
 *   <li><b>start&gt;1 + 无 parent range</b>: range 直接 add 到 ranges 列表 (跟 start==1 一样)</li>
 * </ul>
 *
 * <p><b>Why V6.9.14 选这条</b>: v69_pipeline P0 #1, 验证 STATE + v69_pipeline anchor
 * 机制 (loop dynamic 唤醒读 STATE → 选 P0 第一项 → 跑 BDD+TDD+merge)。
 * addTopCell L206-215 + addLeftCell L240-249 8 行 100% 同构 pattern, 抽 helper
 * pure code elegance + dead-code reduction, 零 perf (build-time only)。
 */
@DisplayName("V6.9.14 — CrosstabRulesBuilder.addOrAttachToParent 行为契约")
class CrosstabRulesBuilderAddOrAttachToParentTest {

    private CrosstabRulesBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new CrosstabRulesBuilder();
    }

    /** Invoke private addOrAttachToParent via reflection. */
    @SuppressWarnings("unchecked")
    private void invokeAddOrAttach(CellRange range, int start, int end, List<CellRange> ranges) throws Exception {
        Method m = CrosstabRulesBuilder.class.getDeclaredMethod(
            "addOrAttachToParent", CellRange.class, int.class, int.class, List.class);
        m.setAccessible(true);
        m.invoke(builder, range, start, end, ranges);
    }

    @Nested
    @DisplayName("start==1 路径")
    class StartOne {

        @Test
        @DisplayName("start==1 → range 直接 add 到 ranges (无 findParentRange)")
        void addsDirectlyWhenStartIsOne() throws Exception {
            CellRange range = new CellRange();
            range.setStart(1);
            range.setEnd(1);
            List<CellRange> ranges = new ArrayList<>();

            invokeAddOrAttach(range, 1, 1, ranges);

            assertThat(ranges).hasSize(1);
            assertThat(ranges.get(0)).isSameAs(range);
        }

        @Test
        @DisplayName("start==1 + ranges 已有内容 → 新 range append 末尾")
        void appendsToExistingRanges() throws Exception {
            CellRange existing = new CellRange();
            existing.setStart(1);
            existing.setEnd(1);
            List<CellRange> ranges = new ArrayList<>();
            ranges.add(existing);

            CellRange newRange = new CellRange();
            newRange.setStart(1);
            newRange.setEnd(2);
            invokeAddOrAttach(newRange, 1, 2, ranges);

            assertThat(ranges).hasSize(2);
            assertThat(ranges.get(0)).isSameAs(existing);
            assertThat(ranges.get(1)).isSameAs(newRange);
        }
    }

    @Nested
    @DisplayName("start>1 + 有 parent 路径")
    class StartGreaterWithParent {

        @Test
        @DisplayName("start>1 + 已有 parent range 包含 [start,end] → addChildRange")
        void attachesToParentWhenFound() throws Exception {
            // parent: start=1, end=5 (covers everything)
            CellRange parent = new CellRange();
            parent.setStart(1);
            parent.setEnd(5);
            List<CellRange> ranges = new ArrayList<>();
            ranges.add(parent);

            CellRange child = new CellRange();
            child.setStart(2);
            child.setEnd(3);
            invokeAddOrAttach(child, 2, 3, ranges);

            // 行为: child 不会加到顶层 ranges, 而是 parent.addChildRange
            // parent.addChildRange 内部维护 children 列表
            assertThat(ranges).hasSize(1);
            assertThat(ranges.get(0)).isSameAs(parent);
            assertThat(parent.getChildren()).hasSize(1);
            assertThat(parent.getChildren().get(0)).isSameAs(child);
        }
    }

    @Nested
    @DisplayName("start>1 + 无 parent 路径")
    class StartGreaterNoParent {

        @Test
        @DisplayName("start>1 + 无 parent range (ranges 空) → 直接 add")
        void addsDirectlyWhenNoParent() throws Exception {
            CellRange range = new CellRange();
            range.setStart(2);
            range.setEnd(3);
            List<CellRange> ranges = new ArrayList<>();

            invokeAddOrAttach(range, 2, 3, ranges);

            assertThat(ranges).hasSize(1);
            assertThat(ranges.get(0)).isSameAs(range);
        }

        @Test
        @DisplayName("start>1 + parent range 不包含 [start,end] → 直接 add (不附着)")
        void addsDirectlyWhenParentDoesNotCover() throws Exception {
            // parent: start=1, end=2 (does not cover [3,4])
            CellRange parent = new CellRange();
            parent.setStart(1);
            parent.setEnd(2);
            List<CellRange> ranges = new ArrayList<>();
            ranges.add(parent);

            CellRange newRange = new CellRange();
            newRange.setStart(3);
            newRange.setEnd(4);
            invokeAddOrAttach(newRange, 3, 4, ranges);

            assertThat(ranges).hasSize(2);
            assertThat(ranges.get(0)).isSameAs(parent);
            assertThat(ranges.get(1)).isSameAs(newRange);
            // parent 没 children (新 range 没被附着)
            assertThat(parent.getChildren()).isEmpty();
        }
    }
}