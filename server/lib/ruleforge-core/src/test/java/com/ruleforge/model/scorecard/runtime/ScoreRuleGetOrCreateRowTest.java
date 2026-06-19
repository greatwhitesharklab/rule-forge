package com.ruleforge.model.scorecard.runtime;
import com.ruleforge.engine.Context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.100.4 — {@code ScoreRule.getOrCreateRow(Map, int)} cache-or-create 契约 BDD。
 *
 * <p>锁 V5.100.4 修法 (从 execute() 抽出的 private static helper, 砍 containsKey + get 双
 * lookup → get + null check) 的行为不变性:
 * <ul>
 *   <li>rowNumber 首次见 (cache miss) → 新建 RowItemImpl, setRowNumber, 装入 map, return 新建</li>
 *   <li>rowNumber 重复见 (cache hit) → 复用 map 已有的 RowItemImpl (reference equal), 不新建, 不覆盖</li>
 *   <li>cache hit 返回的 RowItemImpl 上的后续 setScore/addCellItem 累积到同一个实例</li>
 *   <li>不同 rowNumber 互不干扰 (各缓存各的 RowItemImpl)</li>
 * </ul>
 *
 * <p><b>Why V5.100.4 选这条</b>: V5.93 立的 "HashMap.get 已能区分 absent vs null value" 原则
 * (砍 containsKey + get 双 lookup). V5.100.0/1/2/3 已经用本原则砍过 (build-time + runtime
 * per-fire-rule + build-time per-DRL-parse + build-time per-scorecard-build). V5.100.4 是
 * runtime per-scorecard-eval 版本 — ScoreRule.execute 内的 rowMap 累积, computeIfAbsent-
 * style cache-or-create (不是 find-in-loop), 比 build-time 频 (JFR noise level 预期).
 *
 * <p>行为关键: {@code RowItemImpl} 永不为 null (rowMap 是 execute 局部 map, 唯一 put 是
 * {@code put(rowNumber, new RowItemImpl())}, 永不为 null, 无 {@code put(key, null)} 风险),
 * 所以 {@code map.get(key) == null} 跟 {@code !map.containsKey(key)} 100% 等价.
 *
 * <p><b>Why 抽出 helper</b>: execute() 跑完整 sub-session (KnowledgeSessionFactory +
 * Context + parentSession), 无法 clean-input 单测. 抽出 private static helper
 * {@code getOrCreateRow(Map, int)} 既是 cache-or-create 的纯函数封装 (缩短 execute()),
 * 又让 V5.100.4 逻辑可单测 (clean inputs: Map + int, 不依赖重装配).
 *
 * @see com.ruleforge.docs.notes.v51004-scorerule-rowmap-cacheorcreate-doublelookup V5.100.4 完整 doc
 * @since 5.100.4
 */
@DisplayName("V5.100.4 — ScoreRule.getOrCreateRow cache-or-create 契约 (double lookup 砍)")
class ScoreRuleGetOrCreateRowTest {

    /** Reflective access to private static {@code getOrCreateRow(Map, int)}. */
    @SuppressWarnings("unchecked")
    private RowItemImpl invokeGetOrCreateRow(Map<Integer, RowItemImpl> rowMap, int rowNumber) throws Exception {
        Method m = ScoreRule.class.getDeclaredMethod("getOrCreateRow", Map.class, int.class);
        m.setAccessible(true);
        return (RowItemImpl) m.invoke(null, rowMap, rowNumber);
    }

    // ─── cache miss: 首次见 rowNumber → 新建 + 装入 ──────────────────────────

    @Nested
    @DisplayName("cache miss: 首次见 rowNumber → 新建 RowItemImpl, setRowNumber, 装入 map")
    class CacheMiss {

        // Given: 空 rowMap
        // When:  getOrCreateRow(rowMap, 5)
        // Then:  返回非 null RowItemImpl, getRowNumber() == 5, rowMap 装入 1 entry (5 → 该实例)
        @Test
        @DisplayName("空 map + 首次见 row 5 → 新建 + setRowNumber(5) + 装入 map")
        void emptyMapCreatesNewRowItem() throws Exception {
            Map<Integer, RowItemImpl> rowMap = new HashMap<>();

            RowItemImpl rowItem = invokeGetOrCreateRow(rowMap, 5);

            assertThat(rowItem).isNotNull();
            assertThat(rowItem.getRowNumber()).isEqualTo(5);
            assertThat(rowMap).containsOnlyKeys(5);
            assertThat(rowMap.get(5)).isSameAs(rowItem);  // 装入的是返回的同一实例
        }

        // Given: rowMap 已装 row 1 (pre-existing)
        // When:  getOrCreateRow(rowMap, 2) — row 2 是新的
        // Then:  返回 row 2 的新实例, rowMap 现在有 2 entry (1 + 2), row 1 不受影响
        @Test
        @DisplayName("非空 map + 首次见 row 2 → 新建 row 2, row 1 不受影响")
        void nonEmptyMapCreatesNewRowItemWithoutAffectingExisting() throws Exception {
            Map<Integer, RowItemImpl> rowMap = new HashMap<>();
            RowItemImpl preExisting = new RowItemImpl();
            preExisting.setRowNumber(1);
            rowMap.put(1, preExisting);

            RowItemImpl row2 = invokeGetOrCreateRow(rowMap, 2);

            assertThat(row2).isNotNull();
            assertThat(row2.getRowNumber()).isEqualTo(2);
            assertThat(rowMap).containsOnlyKeys(1, 2);
            assertThat(rowMap.get(1)).isSameAs(preExisting);  // row 1 未被覆盖
            assertThat(rowMap.get(2)).isSameAs(row2);
        }
    }

    // ─── cache hit: 重复见 rowNumber → 复用, 不新建 ─────────────────────────

    @Nested
    @DisplayName("cache hit: 重复见 rowNumber → 复用已有 RowItemImpl (reference equal)")
    class CacheHit {

        // Given: rowMap 已装 row 3 (pre-existing instance)
        // When:  getOrCreateRow(rowMap, 3) — row 3 重复
        // Then:  返回 pre-existing 实例 (reference equal), 不新建, map size 不变
        @Test
        @DisplayName("重复见 row 3 → 复用 pre-existing 实例 (isSameAs), map size 不变")
        void repeatRowNumberReusesExistingInstance() throws Exception {
            Map<Integer, RowItemImpl> rowMap = new HashMap<>();
            RowItemImpl preExisting = new RowItemImpl();
            preExisting.setRowNumber(3);
            rowMap.put(3, preExisting);

            RowItemImpl rowItem = invokeGetOrCreateRow(rowMap, 3);

            assertThat(rowItem).isSameAs(preExisting);  // 复用, 不是新建
            assertThat(rowMap).containsOnlyKeys(3);
            assertThat(rowMap).hasSize(1);  // 没新增 entry
        }

        // Given: rowMap 已装 row 1 (pre-existing, setScore(42))
        // When:  getOrCreateRow(rowMap, 1) — row 1 重复
        // Then:  返回 pre-existing (有 score 42), 复用契约保留 (cache-or-create 的 cache path)
        @Test
        @DisplayName("重复见 row 1 (pre-existing 已 setScore) → 复用, score 保留")
        void repeatRowNumberPreservesExistingScore() throws Exception {
            Map<Integer, RowItemImpl> rowMap = new HashMap<>();
            RowItemImpl preExisting = new RowItemImpl();
            preExisting.setRowNumber(1);
            preExisting.setScore(42);
            rowMap.put(1, preExisting);

            RowItemImpl rowItem = invokeGetOrCreateRow(rowMap, 1);

            assertThat(rowItem).isSameAs(preExisting);
            assertThat(rowItem.getScore()).isEqualTo(42);  // 已有 score 保留
        }
    }

    // ─── V5.100.4 修法核心: cache-or-create 累积行为 (跟 execute() 真实用法一致) ──

    @Nested
    @DisplayName("V5.100.4 修法核心: 累积行为 (模拟 execute() loop 内多次同 row 调用)")
    class AccumulationBehavior {

        // Given: 空 rowMap, 模拟 execute() loop 内对 row 7 调用 3 次 (3 个 cellItem + 1 score)
        //   - 第 1 次: cache miss → 新建 row 7 RowItemImpl, addCellItem("c1")
        //   - 第 2 次: cache hit → 复用, addCellItem("c2")
        //   - 第 3 次: cache hit → 复用, setScore(99)
        // Then:  3 次返回同一实例 (reference equal), 最终该实例有 2 cellItems + score 99
        //   ⚠️ 这正是 execute() 内 "同 row 多个 ScoreRuntimeValue 累积到一个 RowItemImpl"
        //   的契约 — V5.100.4 get + null check 保留这个 cache-or-create 累积语义.
        @Test
        @DisplayName("同 row 7 调用 3 次 → 3 次同实例, 累积 2 cellItems + score")
        void sameRowAccumulatesAcrossCalls() throws Exception {
            Map<Integer, RowItemImpl> rowMap = new HashMap<>();

            RowItemImpl first = invokeGetOrCreateRow(rowMap, 7);
            first.addCellItem(new CellItem("c1", 1));

            RowItemImpl second = invokeGetOrCreateRow(rowMap, 7);
            second.addCellItem(new CellItem("c2", 2));

            RowItemImpl third = invokeGetOrCreateRow(rowMap, 7);
            third.setScore(99);

            assertThat(first).isSameAs(second).isSameAs(third);  // 3 次同一实例
            assertThat(rowMap).hasSize(1);  // 只有 row 7 一个 entry
            assertThat(first.getCellItems()).hasSize(2);  // 2 cellItems 累积
            assertThat(first.getScore()).isEqualTo(99);
        }

        // Given: 空 rowMap, 模拟 execute() loop 内对 row 1 + row 2 交替调用
        // Then:  row 1 和 row 2 是不同实例, 各自累积各自的 cellItems, 互不干扰
        @Test
        @DisplayName("不同 row 交替调用 → 各自独立实例 + 独立累积, 互不干扰")
        void differentRowsAccumulateIndependently() throws Exception {
            Map<Integer, RowItemImpl> rowMap = new HashMap<>();

            RowItemImpl row1a = invokeGetOrCreateRow(rowMap, 1);
            row1a.addCellItem(new CellItem("c1a", 1));

            RowItemImpl row2 = invokeGetOrCreateRow(rowMap, 2);
            row2.addCellItem(new CellItem("c2", 2));

            RowItemImpl row1b = invokeGetOrCreateRow(rowMap, 1);  // row 1 重复
            row1b.addCellItem(new CellItem("c1b", 3));

            assertThat(row1a).isSameAs(row1b);  // row 1 两次是同一实例
            assertThat(row1a).isNotSameAs(row2);  // row 1 ≠ row 2
            assertThat(rowMap).containsOnlyKeys(1, 2);
            assertThat(row1a.getCellItems()).hasSize(2);  // c1a + c1b
            assertThat(row2.getCellItems()).hasSize(1);  // c2
        }
    }

    // ─── Boundary: rowNumber = 0 / 负数 ────────────────────────────────────

    @Nested
    @DisplayName("boundary: rowNumber = 0 / 负数 (key 合法, 跟正数一致)")
    class BoundaryRowNumber {

        // Given: 空 rowMap
        // When:  getOrCreateRow(rowMap, 0)
        // Then:  新建 row 0 RowItemImpl (Integer key 0 合法), 跟正数行为一致
        @Test
        @DisplayName("rowNumber = 0 → 新建 + 装入 (Integer key 0 合法)")
        void rowZeroTreatedAsValidKey() throws Exception {
            Map<Integer, RowItemImpl> rowMap = new HashMap<>();

            RowItemImpl rowItem = invokeGetOrCreateRow(rowMap, 0);

            assertThat(rowItem).isNotNull();
            assertThat(rowItem.getRowNumber()).isEqualTo(0);
            assertThat(rowMap).containsOnlyKeys(0);
        }

        // Given: 空 rowMap
        // When:  getOrCreateRow(rowMap, -1)
        // Then:  新建 row -1 (负数 key 合法, HashMap 允许)
        //   ⚠️ 实际生产 rowNumber 从 1 开始 (ScoreRuntimeValue.getRowNumber), 但
        //   getOrCreateRow 不校验 key 范围, 负数也合法.
        @Test
        @DisplayName("rowNumber = -1 → 新建 + 装入 (负数 key 合法, 不校验范围)")
        void negativeRowNumberTreatedAsValidKey() throws Exception {
            Map<Integer, RowItemImpl> rowMap = new HashMap<>();

            RowItemImpl rowItem = invokeGetOrCreateRow(rowMap, -1);

            assertThat(rowItem).isNotNull();
            assertThat(rowItem.getRowNumber()).isEqualTo(-1);
            assertThat(rowMap).containsOnlyKeys(-1);
        }
    }
}
