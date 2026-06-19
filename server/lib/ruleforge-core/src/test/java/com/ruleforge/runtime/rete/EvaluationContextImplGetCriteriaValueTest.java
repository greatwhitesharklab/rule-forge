package com.ruleforge.runtime.rete;
import com.ruleforge.engine.EvaluationContext;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.engine.WorkingMemory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * V5.93 — {@link EvaluationContextImpl#getCriteriaValue(String)} 契约 BDD。
 *
 * <p>锁 V5.93 修法(删除冗余 {@code containsKey} 双 lookup)的行为不变性:
 * <ul>
 *   <li>{@code getCriteriaValue(id)} 不存在的 key → 返 {@code null}(HashMap 行为)</li>
 *   <li>{@code getCriteriaValue(id)} 已存在 key + 非 null 值 → 返 该值</li>
 *   <li>{@code getCriteriaValue(id)} 已存在 key + null 值 → 返 {@code null}(HashMap 行为,跟"不存在"无法区分,但等价)</li>
 *   <li>{@code storeCriteriaValue(id, obj)} 后能读到</li>
 *   <li>{@code clean()} 后能清空(下个 fact 走新缓存)</li>
 * </ul>
 *
 * <p><b>Why V5.93 选这条</b>:post-V5.92 JFR 30s HotPathBenchTest 抓 top-1 是
 * {@code String.hashCode} 546 sample(15% hot path)+ 各种 HashMap 操作合
 * 53% of hot path(总 2201 sample)。audit 发现
 * {@link EvaluationContextImpl#getCriteriaValue} 实现有经典双 lookup 反模式:
 * <pre>
 * if (!criteriaValueMap.containsKey(id)) {  // 1 HashMap op
 *     return null;
 * }
 * return criteriaValueMap.get(id);  // 1 HashMap op = 2 ops total
 * </pre>
 * {@link HashMap#get(Object)} 已对 missing key 返 {@code null},{@code containsKey}
 * 检查冗余。每 criteria 每 fact 跑一次(per-fact hot),省 1 HashMap op 省 1
 * String.hashCode,预期 per-fact -5~10%。
 *
 * <p><b>行为等价性</b>:HashMap.put 允许 null 值,get 对 "key 不存在" 和 "key 存在但 null 值"
 * 都返 null — 两个 case 在 HashMap 语义上不可区分,所以 V5.93 fix 不会改变外部
 * 可观察行为。BDD 锁这层契约。
 *
 * <p><b>对比 V5.92</b>:V5.92 flat list iter(无反射),V5.93 砍 HashMap 双 lookup。
 * 两者都属 "V5.87 JFR 抓 hot path → 找数据结构的 cost 模式 → 简化 API 调用"
 * 模式,跟 V5.86 / V5.89 / V5.91 一致:audit 后 fix 反模式。
 *
 * @see com.ruleforge.docs.notes.v593-evaluationcontext-double-lookup V5.93 完整 doc
 * @since 5.93
 */
@DisplayName("V5.93 — EvaluationContextImpl.getCriteriaValue 行为契约 (修双 lookup)")
class EvaluationContextImplGetCriteriaValueTest {

    private EvaluationContextImpl ctx;
    private WorkingMemory mockWM;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        // V5.81 — 走 EngineContextWirer(真实 ValueCompute / AssertorEvaluator)
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
    @DisplayName("missing key 返回 null")
    class MissingKey {

        // Given 干净 EvaluationContext
        // When getCriteriaValue("unknown")
        // Then 返 null
        @Test
        @DisplayName("missing key → null (HashMap.get 默认行为)")
        void missingKeyReturnsNull() {
            assertThat(ctx.getCriteriaValue("unknown")).isNull();
        }
    }

    @Nested
    @DisplayName("stored value 可读")
    class StoredValue {

        // Given storeCriteriaValue("k1", "v1")
        // When getCriteriaValue("k1")
        // Then 返 "v1"
        @Test
        @DisplayName("stored String value 可读")
        void storedStringValueIsReadable() {
            ctx.storeCriteriaValue("k1", "v1");
            assertThat(ctx.getCriteriaValue("k1")).isEqualTo("v1");
        }

        // Given storeCriteriaValue("k2", 42) (Integer)
        // When getCriteriaValue("k2")
        // Then 返 42
        @Test
        @DisplayName("stored Integer value 可读")
        void storedIntegerValueIsReadable() {
            ctx.storeCriteriaValue("k2", 42);
            assertThat(ctx.getCriteriaValue("k2")).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("null value 跟 missing key 不可区分 (HashMap 语义)")
    class NullValueSemantics {

        // Given storeCriteriaValue("k3", null)
        // When getCriteriaValue("k3")
        // Then 返 null(跟 missing key 行为等价,HashMap 语义)
        @Test
        @DisplayName("stored null value 跟 missing key 同样返 null (HashMap 语义保留)")
        void storedNullValueReturnsNullLikeMissingKey() {
            ctx.storeCriteriaValue("k3", null);
            // HashMap.get 对 "key 不存在" 和 "key 存在但 null" 都返 null,
            // V5.93 fix 保留这层语义(契约)
            assertThat(ctx.getCriteriaValue("k3")).isNull();
        }
    }

    @Nested
    @DisplayName("clean() 清空缓存")
    class CleanBehavior {

        // Given storeCriteriaValue("k1", "v1")
        // When clean() then getCriteriaValue("k1")
        // Then 返 null(下个 fact 走新缓存)
        @Test
        @DisplayName("clean() 后 stored value 不可读 (per-fact 缓存失效)")
        void cleanClearsStoredValues() {
            ctx.storeCriteriaValue("k1", "v1");
            assertThat(ctx.getCriteriaValue("k1")).isEqualTo("v1");
            ctx.clean();
            assertThat(ctx.getCriteriaValue("k1")).isNull();
        }
    }
}
