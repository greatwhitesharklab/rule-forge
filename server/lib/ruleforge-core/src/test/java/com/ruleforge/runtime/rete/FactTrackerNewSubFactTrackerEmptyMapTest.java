package com.ruleforge.runtime.rete;

import com.ruleforge.model.rule.lhs.BaseCriteria;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.rete.test.EngineContextWirer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.9.1 — {@link FactTracker#newSubFactTracker()} 行为契约 BDD。
 *
 * <p>锁 V6.9.1 微优化 (父 {@code objectCriteriaMap} 为空时跳过 {@code putAll}) 的行为不变性:
 * <ul>
 *   <li><b>父空 + sub 空</b>: 父 tracker 是空 (ReteInstance.enter() L70 创建的), sub 应共享空 map (跳过 putAll)</li>
 *   <li><b>父有 entry + sub copy</b>: 父 tracker 含 entry 时, sub 应有同 entry (现有行为保留)</li>
 *   <li><b>sub 跟父独立</b>: sub 修改不污染父 (已有契约, V5.97 锁过)</li>
 *   <li><b>sub 多次调</b>: 每次 newSubFactTracker 返新实例 (独立 sub tracker)</li>
 * </ul>
 *
 * <p><b>Why V6.9.1 选这条</b>: JFR 显示 {@code FactTracker.<init>} +
 * {@code FactTracker.newSubFactTracker} 在 rete hot path (V5.100.9 报告),
 * 其中 {@code ReteInstance.enter() L70} 创建的空 tracker 会立即被多次
 * {@code AbstractActivity.visitPaths L47} 调用 {@code newSubFactTracker()},每次都
 * {@code new HashMap() + putAll(emptyMap)} 浪费。当父 map 是空,跳过 putAll 节省
 * HashMap.putAll 调用 + putAll 内部 arraycopy。多个 sub tracker 各自独立(每次 new),
 * 共享空 HashMap 引用不冲突(sub 修改时如果共享空 map 才会冲突 — 但父永远不被修改因为
 * 父是 ReteInstance.enter 创建的空 tracker,只用作 source-of-truth)。
 *
 * <p><b>本测试不验证共享引用</b>: 因为实际 fix 是跳过 putAll,不共享引用 (sub tracker
 * 各自独立,各自可独立 addObjectCriteria)。测试只验证行为等价。
 */
@DisplayName("V6.9.1 — FactTracker.newSubFactTracker 空 map 跳过 putAll 契约")
class FactTrackerNewSubFactTrackerEmptyMapTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    private BaseCriteria dummyCriteria() {
        return new Criteria();
    }

    @Nested
    @DisplayName("父空 + sub 空 — V6.9.1 优化路径")
    class EmptyParentEmptySub {

        // Given: 一个空 FactTracker (新建,无 addObjectCriteria)
        // When:  newSubFactTracker()
        // Then:  sub 应为空 + 不抛错 (V6.9.1: 跳过 putAll,节省 HashMap.putAll 调用)
        @Test
        @DisplayName("父空 → sub 也应空 (行为不变性)")
        void emptyParentYieldsEmptySub() {
            FactTracker parent = new FactTracker();
            FactTracker sub = parent.newSubFactTracker();

            assertThat(sub.getObjectCriteriaMap()).isEmpty();
        }

        // Given: 一个空 FactTracker
        // When:  连续 newSubFactTracker() 多次
        // Then:  每次返新独立实例 + 都为空
        @Test
        @DisplayName("多次 newSubFactTracker 返独立空 sub 实例")
        void multipleNewSubsAreIndependent() {
            FactTracker parent = new FactTracker();
            FactTracker sub1 = parent.newSubFactTracker();
            FactTracker sub2 = parent.newSubFactTracker();
            FactTracker sub3 = parent.newSubFactTracker();

            assertThat(sub1).isNotSameAs(sub2);
            assertThat(sub2).isNotSameAs(sub3);
            assertThat(sub1.getObjectCriteriaMap()).isEmpty();
            assertThat(sub2.getObjectCriteriaMap()).isEmpty();
            assertThat(sub3.getObjectCriteriaMap()).isEmpty();
        }
    }

    @Nested
    @DisplayName("父有 entry + sub copy — 现有行为保留")
    class NonEmptyParentCopiesEntries {

        // Given: 父 tracker addObjectCriteria("key1", c)
        // When:  newSubFactTracker()
        // Then:  sub 应含 "key1" → [c] entry (putAll 路径)
        @Test
        @DisplayName("父有 entry → sub 含同 entry (现有行为保留)")
        void nonEmptyParentCopiesToSub() {
            FactTracker parent = new FactTracker();
            BaseCriteria c = dummyCriteria();
            parent.addObjectCriteria("key1", c);

            FactTracker sub = parent.newSubFactTracker();
            assertThat(sub.getObjectCriteriaMap()).hasSize(1);
            assertThat(sub.getObjectCriteriaMap().get("key1")).containsExactly(c);
        }

        // Given: 父 tracker addObjectCriteria("key1", c1) + ("key2", c2)
        // When:  newSubFactTracker()
        // Then:  sub 应含 2 个 entry
        @Test
        @DisplayName("父多 entry → sub copy 全 entry")
        void multipleEntriesCopied() {
            FactTracker parent = new FactTracker();
            BaseCriteria c1 = dummyCriteria();
            BaseCriteria c2 = dummyCriteria();
            parent.addObjectCriteria("key1", c1);
            parent.addObjectCriteria("key2", c2);

            FactTracker sub = parent.newSubFactTracker();
            assertThat(sub.getObjectCriteriaMap()).hasSize(2);
            assertThat(sub.getObjectCriteriaMap().get("key1")).containsExactly(c1);
            assertThat(sub.getObjectCriteriaMap().get("key2")).containsExactly(c2);
        }
    }

    @Nested
    @DisplayName("sub 跟父 map 独立 (V5.97 契约保留)")
    class SubIndependentFromParent {

        // Given: 父有 entry "key1" → [c1]
        // When:  sub.addObjectCriteria("key2", c2) 加新 key
        // Then:  父仍只有 1 entry (key1), sub 有 2 entry (key1 + key2)
        // 注: V5.97 实现的 putAll 是 shallow copy (List 引用共享) + addObjectCriteria dedup
        // 兜底,同 key 重复 add 会被 dedup 跳过 (List.contains 检查)。本测试验证 "新 key"
        // 路径 sub 跟父 map 是独立的。
        @Test
        @DisplayName("sub addObjectCriteria 新 key 不污染父 map")
        void subAddNewKeyDoesNotPolluteParentMap() {
            FactTracker parent = new FactTracker();
            BaseCriteria c1 = dummyCriteria();
            BaseCriteria c2 = dummyCriteria();
            parent.addObjectCriteria("key1", c1);

            FactTracker sub = parent.newSubFactTracker();
            sub.addObjectCriteria("key2", c2);

            // 父 map 仍只有 "key1"
            assertThat(parent.getObjectCriteriaMap()).hasSize(1);
            assertThat(parent.getObjectCriteriaMap()).containsKey("key1");
            assertThat(parent.getObjectCriteriaMap()).doesNotContainKey("key2");

            // sub map 有 "key1" + "key2"
            assertThat(sub.getObjectCriteriaMap()).hasSize(2);
            assertThat(sub.getObjectCriteriaMap()).containsKey("key1");
            assertThat(sub.getObjectCriteriaMap()).containsKey("key2");
        }

        // Given: 父空 (V6.9.1 跳过 putAll 路径)
        // When:  sub.addObjectCriteria("key1", c)
        // Then:  父仍空, sub 有 1 entry (key1)
        @Test
        @DisplayName("父空时 sub addObjectCriteria 新 key → 父 map 仍空 (V6.9.1 不破坏隔离)")
        void subAddOnEmptyParentDoesNotPolluteParent() {
            FactTracker parent = new FactTracker();
            FactTracker sub = parent.newSubFactTracker();

            BaseCriteria c = dummyCriteria();
            sub.addObjectCriteria("key1", c);

            assertThat(parent.getObjectCriteriaMap()).isEmpty();
            assertThat(sub.getObjectCriteriaMap()).hasSize(1);
            assertThat(sub.getObjectCriteriaMap().get("key1")).containsExactly(c);
        }
    }
}