package com.ruleforge.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.7 — {@link ActivationGroupRegistry#isActivated(String)} /
 * {@link ActivationGroupRegistry#markActivated(String)} 行为不变性 characterization test BDD。
 *
 * <p>锁 V6.7 优化 (List → HashSet) 的行为不变性:
 * <ul>
 *   <li><b>行为不变</b>: isActivated 返回 boolean, markActivated 幂等 (重复 add 同 id 不变)</li>
 *   <li><b>clear() 幂等</b>: clear 后 isActivated 返 false, markActivated 可重新 add</li>
 *   <li><b>多个不同 id 独立</b>: add A 不影响 B 的 contains 判断</li>
 * </ul>
 *
 * <p><b>Why V6.7 选这条</b>: {@code evaluationRete} 主循环每 activation group 调用
 * {@code isActivated(id)} ({@code KnowledgeSessionImpl.L249}), 旧实现是
 * {@code List<String>.contains()} = O(n) linear scan, 累计 active group 数大时浪费。
 * HashSet 改 {@code contains()} = O(1) hash lookup。{@code markActivated} 每次
 * evaluationRete trackers 非空时调 ({@code KnowledgeSessionImpl.L271})。
 *
 * <p><b>低频 hot path</b>: 跟 V5.100.6 同档, 用户显式 / 引擎触发 activation group
 * 不是 per-fact 路径。JFR noise level 预期, perf 收益主要在 activation group 多时
 * (10+ group 同 session)。
 */
@DisplayName("V6.7 — ActivationGroupRegistry.isActivated / markActivated 行为不变性")
class ActivationGroupRegistryHashSetTest {

    @Nested
    @DisplayName("基础行为")
    class BasicBehavior {

        // Given: 空 registry
        // When:  调 isActivated("g1")
        // Then: 返 false (未激活)
        @Test
        @DisplayName("空 registry isActivated 返 false")
        void emptyRegistryReturnsFalse() {
            ActivationGroupRegistry reg = new ActivationGroupRegistry();
            assertThat(reg.isActivated("g1")).isFalse();
        }

        // Given: 空 registry
        // When:  markActivated("g1") + isActivated("g1")
        // Then: 返 true
        @Test
        @DisplayName("markActivated 后 isActivated 返 true")
        void markThenCheckReturnsTrue() {
            ActivationGroupRegistry reg = new ActivationGroupRegistry();
            reg.markActivated("g1");
            assertThat(reg.isActivated("g1")).isTrue();
        }

        // Given: 空 registry
        // When:  markActivated("g1") + markActivated("g1") (重复)
        // Then: isActivated("g1") 仍返 true (幂等, HashSet.add 返 false 不抛错)
        @Test
        @DisplayName("markActivated 重复同 id 应幂等 (HashSet add 返 false)")
        void markActivatedIsIdempotent() {
            ActivationGroupRegistry reg = new ActivationGroupRegistry();
            reg.markActivated("g1");
            reg.markActivated("g1");
            reg.markActivated("g1");
            assertThat(reg.isActivated("g1")).isTrue();
        }
    }

    @Nested
    @DisplayName("多 id 独立")
    class MultiIdIsolation {

        // Given: registry 已 mark g1, g3
        // When:  isActivated g1, g2, g3
        // Then: g1/g3 → true, g2 → false
        @Test
        @DisplayName("多个不同 id 互不干扰")
        void multipleIdsAreIndependent() {
            ActivationGroupRegistry reg = new ActivationGroupRegistry();
            reg.markActivated("g1");
            reg.markActivated("g3");

            assertThat(reg.isActivated("g1")).isTrue();
            assertThat(reg.isActivated("g2")).isFalse();
            assertThat(reg.isActivated("g3")).isTrue();
        }

        // Given: registry 已 mark 多个 id
        // When:  clear() + isActivated 各 id
        // Then: 全返 false
        @Test
        @DisplayName("clear() 后所有 id isActivated 返 false")
        void clearResetsAll() {
            ActivationGroupRegistry reg = new ActivationGroupRegistry();
            reg.markActivated("g1");
            reg.markActivated("g2");
            reg.markActivated("g3");
            reg.clear();

            assertThat(reg.isActivated("g1")).isFalse();
            assertThat(reg.isActivated("g2")).isFalse();
            assertThat(reg.isActivated("g3")).isFalse();
        }

        // Given: registry 已 mark + clear
        // When:  markActivated("g1") + isActivated
        // Then: 返 true (clear 后可重新 add)
        @Test
        @DisplayName("clear 后 markActivated 重新生效")
        void canRemarkAfterClear() {
            ActivationGroupRegistry reg = new ActivationGroupRegistry();
            reg.markActivated("g1");
            reg.clear();
            reg.markActivated("g1");
            assertThat(reg.isActivated("g1")).isTrue();
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        // Given: 空 registry
        // When:  isActivated(null)
        // Then: 返 false (不抛 NullPointerException)
        @Test
        @DisplayName("isActivated(null) 不抛错, 返 false")
        void isActivatedNullReturnsFalse() {
            ActivationGroupRegistry reg = new ActivationGroupRegistry();
            assertThat(reg.isActivated(null)).isFalse();
        }

        // Given: 空 registry
        // When:  markActivated(null) + isActivated(null)
        // Then: HashSet 容许 null (1 个 null entry)
        @Test
        @DisplayName("markActivated(null) 不抛错 (HashSet 容许 null)")
        void markActivatedNullDoesNotThrow() {
            ActivationGroupRegistry reg = new ActivationGroupRegistry();
            reg.markActivated(null);
            // 注: HashSet 容许 1 个 null entry, isActivated(null) 应返 true
            assertThat(reg.isActivated(null)).isTrue();
        }

        // Given: registry mark g1, g1 (重复)
        // When:  clear() (幂等)
        // Then: clear 后 size 0
        @Test
        @DisplayName("clear() 幂等 (重复 clear 不抛错)")
        void clearIsIdempotent() {
            ActivationGroupRegistry reg = new ActivationGroupRegistry();
            reg.markActivated("g1");
            reg.clear();
            reg.clear();  // 二次 clear 不抛
            reg.clear();
            assertThat(reg.isActivated("g1")).isFalse();
        }
    }
}