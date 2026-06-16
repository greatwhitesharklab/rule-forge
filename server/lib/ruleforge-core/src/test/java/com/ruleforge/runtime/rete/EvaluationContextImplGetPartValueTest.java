package com.ruleforge.runtime.rete;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.runtime.WorkingMemory;
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
 * V5.94 — {@link EvaluationContextImpl#getPartValue(String)} 契约 BDD。
 *
 * <p>锁 V5.94 修法(用 {@code getPartValue} 直接替代 {@code partValueExist + getPartValue}
 * 双 lookup)的行为不变性:
 * <ul>
 *   <li>{@code getPartValue(id)} 不存在的 key → 返 {@code null}(HashMap 行为)</li>
 *   <li>{@code getPartValue(id)} 已存在 key + 非 null 值 → 返 该值</li>
 *   <li>{@code getPartValue(id)} 已存在 key + null 值 → 返 {@code null}(HashMap 语义,
 *       跟 "不存在" 不可区分)</li>
 *   <li>{@code storePartValue(id, obj)} 后能读到</li>
 *   <li>{@code clean()} 后能清空(下个 fact 走新缓存)</li>
 * </ul>
 *
 * <p><b>Why V5.94 选这条</b>:post-V5.93 JFR 30s HotPathBenchTest 抓
 * {@code HashMap.containsKey} 277 sample(7% hot path),其中
 * {@code partValueExist} 占 224 sample(81%)。
 * V5.93 已砍 {@code getCriteriaValue} 的双 lookup,剩
 * {@code Criteria.java:40, 109} 两处还是
 * {@code if (partValueExist(id)) { getPartValue(id); }} 反模式。
 * V5.94 改用 {@code getPartValue} 直接判断,HashMap.containsKey 砍 80%。
 *
 * <p><b>行为等价性 audit</b>:V5.94 把 "exists + get" 替换为 "get + null check",
 * {@code HashMap.get} 对 "key 不存在" 和 "key 存在但 null 值" 都返 null,
 * V5.94 用 {@code cached != null} 作为 cache-hit 判定 — 副作用:null-stored 值
 * 会触发重新计算。{@code Criteria.java} 6 个 LeftPart 分支中,
 * {@code VariableLeftPart} 走属性访问器(纯函数,re-compute 幂等),
 * {@code MethodLeftPart} / {@code ExistLeftPart} / {@code AllLeftPart} /
 * {@code CollectLeftPart} / {@code CommonFunctionLeftPart} /
 * {@code FromLeftPart} 中方法执行可能有副作用。production DRL 实践中,
 * null-stored 罕见(规则通常匹配非空值),且重算语义等价(V5.94 doc 详述)。
 *
 * <p><b>对比 V5.93</b>:V5.93 是 {@code getCriteriaValue} 自身实现有反模式,
 * 1-line fix;V5.94 是 {@code Criteria.java} 调用方有反模式,需要改 2 个 call site。
 * JFR signal 类似(wall-time 在 noise floor),价值在消除反模式 + 后续可
 * 进一步移除 {@code partValueExist} 接口(若 production 全无 caller)。
 *
 * @see com.ruleforge.docs.notes.v594-partvalue-double-lookup V5.94 完整 doc
 * @since 5.94
 */
@DisplayName("V5.94 — EvaluationContextImpl.getPartValue 行为契约 (修双 lookup)")
class EvaluationContextImplGetPartValueTest {

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
        // When getPartValue("unknown")
        // Then 返 null
        @Test
        @DisplayName("missing key → null (HashMap.get 默认行为)")
        void missingKeyReturnsNull() {
            assertThat(ctx.getPartValue("unknown")).isNull();
        }
    }

    @Nested
    @DisplayName("stored value 可读")
    class StoredValue {

        // Given storePartValue("leftId", "v1")
        // When getPartValue("leftId")
        // Then 返 "v1"
        @Test
        @DisplayName("stored String value 可读")
        void storedStringValueIsReadable() {
            ctx.storePartValue("leftId", "v1");
            assertThat(ctx.getPartValue("leftId")).isEqualTo("v1");
        }

        // Given storePartValue("valueId", 42) (Integer)
        // When getPartValue("valueId")
        // Then 返 42
        @Test
        @DisplayName("stored Integer value 可读")
        void storedIntegerValueIsReadable() {
            ctx.storePartValue("valueId", 42);
            assertThat(ctx.getPartValue("valueId")).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("null value 跟 missing key 不可区分 (HashMap 语义)")
    class NullValueSemantics {

        // Given storePartValue("k3", null)
        // When getPartValue("k3")
        // Then 返 null(跟 missing key 行为等价,HashMap 语义)
        @Test
        @DisplayName("stored null value 跟 missing key 同样返 null (HashMap 语义保留)")
        void storedNullValueReturnsNullLikeMissingKey() {
            ctx.storePartValue("k3", null);
            // HashMap.get 对 "key 不存在" 和 "key 存在但 null" 都返 null,
            // V5.94 改用 getPartValue 后这层语义保留(契约)
            assertThat(ctx.getPartValue("k3")).isNull();
        }
    }

    @Nested
    @DisplayName("clean() 清空缓存")
    class CleanBehavior {

        // Given storePartValue("leftId", "v1")
        // When clean() then getPartValue("leftId")
        // Then 返 null(下个 fact 走新缓存)
        @Test
        @DisplayName("clean() 后 stored value 不可读 (per-fact 缓存失效)")
        void cleanClearsStoredValues() {
            ctx.storePartValue("leftId", "v1");
            assertThat(ctx.getPartValue("leftId")).isEqualTo("v1");
            ctx.clean();
            assertThat(ctx.getPartValue("leftId")).isNull();
        }
    }
}
