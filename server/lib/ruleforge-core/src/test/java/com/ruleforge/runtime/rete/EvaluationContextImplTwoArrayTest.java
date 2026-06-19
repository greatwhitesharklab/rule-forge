package com.ruleforge.runtime.rete;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.engine.WorkingMemory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * V5.98 — {@link EvaluationContextImpl} 2-array small store 契约 BDD。
 *
 * <p>锁 V5.98 修法(用 {@code Object[] keys + Object[] values + linear scan}
 * 替代 {@code HashMap<String, Object>})的行为不变性:
 * <ul>
 *   <li>miss → null(跟 HashMap 行为等价)</li>
 *   <li>store 后 get 能读到</li>
 *   <li>同 key 多次 store → 后写覆盖前写</li>
 *   <li>{@code clean()} 后 size=0,get 全部返 null</li>
 *   <li>超 8 entries 自动 grow,行为不变</li>
 *   <li>{@code partValueExist} 仍正确(用 linear scan)</li>
 * </ul>
 *
 * <p><b>Why V5.98 选这条</b>:V5.97 JFR 30s HotPathBenchTest 显示
 * {@code HashMap.get/put/clear} 链在 rete hot path 占 ~779 sample
 * (CriteriaActivity.enter 35%):
 * <ul>
 *   <li>{@code EvaluationContextImpl.getCriteriaValue}: 437 sample</li>
 *   <li>{@code storePartValue}: 156 sample</li>
 *   <li>{@code clean}: 186 sample</li>
 * </ul>
 * 2-array store(N ≤ 8 linear scan,超 8 自动 grow)消除 hash + bucket walk,
 * 典型 per-fact N=1-5 直接 array access + String.equals。
 *
 * <p><b>行为等价性</b>:{@code HashMap.get} 对 "key 不存在" 和 "key 存在但 null 值"
 * 都返 null。Linear scan 找不到 → 返 null,跟 HashMap 行为等价。
 * <b>本方法 store 后 value 永远非 null</b>(CriteriaActivity.enter 的
 * {@code storeCriteriaValue(criteriaId, response)} 传 {@code EvaluateResponse} 非 null),
 * 所以 null-stored 路径不可达,跟 V5.93/V5.94/V5.97 同一档 — 无 null-stored 风险。
 *
 * @see com.ruleforge.docs.notes.v598-evaluation-context-2array V5.98 完整 doc
 * @since 5.98
 */
@DisplayName("V5.98 — EvaluationContextImpl 2-array store 行为契约")
class EvaluationContextImplTwoArrayTest {

    private EvaluationContextImpl ctx;
    private WorkingMemory mockWM;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @BeforeEach
    void setUp() {
        mockWM = mock(WorkingMemory.class);
        Map<String, String> varCatMap = new HashMap<>();
        List<MessageItem> debugMsgs = Collections.emptyList();
        ctx = new EvaluationContextImpl(mockWM, varCatMap, debugMsgs);
    }

    @Nested
    @DisplayName("criteriaValueMap 行为")
    class CriteriaMap {

        // Given 干净 ctx
        // When storeCriteriaValue("k", v) then getCriteriaValue("k")
        // Then 返 v
        @Test
        @DisplayName("store + get round-trip")
        void storeGetRoundTrip() {
            Object value = new Object();
            ctx.storeCriteriaValue("k1", value);
            assertThat(ctx.getCriteriaValue("k1")).isSameAs(value);
        }

        // Given store "k" → v1
        // When  store "k" → v2
        // Then  get "k" 返 v2(后写覆盖)
        @Test
        @DisplayName("同 key 多次 store → 后写覆盖前写")
        void sameKeyOverwrites() {
            Object v1 = new Object();
            Object v2 = new Object();
            ctx.storeCriteriaValue("k", v1);
            ctx.storeCriteriaValue("k", v2);
            assertThat(ctx.getCriteriaValue("k")).isSameAs(v2);
        }

        // Given 干净 ctx
        // When getCriteriaValue("unknown")
        // Then 返 null
        @Test
        @DisplayName("missing key → null")
        void missingKeyReturnsNull() {
            assertThat(ctx.getCriteriaValue("unknown")).isNull();
        }

        // Given store 12 entries(超 inline 8)
        // When get 任一
        // Then 返对应 value(走 grow path)
        @Test
        @DisplayName("超 8 entries 走 grow path 行为不变")
        void growPathBeyond8() {
            for (int i = 0; i < 12; i++) {
                ctx.storeCriteriaValue("k" + i, "v" + i);
            }
            for (int i = 0; i < 12; i++) {
                assertThat(ctx.getCriteriaValue("k" + i)).isEqualTo("v" + i);
            }
        }
    }

    @Nested
    @DisplayName("partValueMap 行为")
    class PartMap {

        // Given 干净 ctx
        // When storePartValue("leftId", v) then getPartValue("leftId")
        // Then 返 v
        @Test
        @DisplayName("store + get round-trip")
        void storeGetRoundTrip() {
            ctx.storePartValue("leftId", 42);
            assertThat(ctx.getPartValue("leftId")).isEqualTo(42);
        }

        // Given 干净 ctx
        // When partValueExist("unknown")
        // Then 返 false
        @Test
        @DisplayName("missing key → partValueExist 返 false")
        void missingKeyPartValueExist() {
            assertThat(ctx.partValueExist("unknown")).isFalse();
        }

        // Given store "leftId" → v
        // When  partValueExist("leftId")
        // Then  返 true
        @Test
        @DisplayName("stored key → partValueExist 返 true")
        void storedKeyPartValueExist() {
            ctx.storePartValue("leftId", "v");
            assertThat(ctx.partValueExist("leftId")).isTrue();
        }

        // Given storePartValue("k3", null) (V5.94 契约)
        // When partValueExist("k3")
        // Then 返 true(已 store 过,即使 value null)
        @Test
        @DisplayName("stored null value → partValueExist 返 true(linear scan 找到 key)")
        void storedNullValuePartValueExist() {
            ctx.storePartValue("k3", null);
            assertThat(ctx.partValueExist("k3")).isTrue();
        }
    }

    @Nested
    @DisplayName("clean() reset")
    class CleanBehavior {

        // Given store k1, k2 都到 ctx
        // When clean() then get(k1), get(k2)
        // Then 都 null
        @Test
        @DisplayName("clean() 后所有 entries 不可读")
        void cleanResetsAllMaps() {
            ctx.storeCriteriaValue("k1", "v1");
            ctx.storePartValue("k2", "v2");
            ctx.clean();
            assertThat(ctx.getCriteriaValue("k1")).isNull();
            assertThat(ctx.getPartValue("k2")).isNull();
        }

        // Given 一次 clean 循环 store 10 entries + clean + 再 store 10 entries
        // When  get 任一
        // Then  都正确(走 grow path 不残留旧 entry)
        @Test
        @DisplayName("clean() 后再 store 走 grow path 不残留旧数据")
        void cleanAllowsRegrow() {
            for (int i = 0; i < 10; i++) ctx.storeCriteriaValue("a" + i, i);
            ctx.clean();
            for (int i = 0; i < 10; i++) ctx.storeCriteriaValue("b" + i, i);
            for (int i = 0; i < 10; i++) {
                assertThat(ctx.getCriteriaValue("b" + i)).isEqualTo(i);
                assertThat(ctx.getCriteriaValue("a" + i)).isNull();
            }
        }
    }
}
