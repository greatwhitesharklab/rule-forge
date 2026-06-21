package com.ruleforge.runtime.agenda;

import com.ruleforge.action.ActionValue;
import com.ruleforge.debug.MessageItem;
import com.ruleforge.engine.AssertorEvaluator;
import com.ruleforge.engine.EngineContext;
import com.ruleforge.engine.KnowledgeSession;
import com.ruleforge.engine.ValueCompute;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.plugin.EnginePluginRegistry;
import com.ruleforge.runtime.event.impl.ActivationAfterFiredEventImpl;
import com.ruleforge.runtime.rete.ContextImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V6.9.15 — {@link ActivationImpl#execute} 状态机契约 BDD。
 *
 * <p>锁 V6.9.15 收口 (L47-100 4-level nested if/else → early return chain)
 * 的 4 条路径行为不变性:
 * <ul>
 *   <li><b>!enabled</b>: 返回 null, 不 fire AfterFiredEvent, processed=false</li>
 *   <li><b>enabled + effectiveDate &gt; now</b>: 返回 null, 不 fire AfterFiredEvent</li>
 *   <li><b>enabled + expiresDate &lt; now</b>: 返回 null, 不 fire AfterFiredEvent</li>
 *   <li><b>enabled + 日期合法</b>: 返回 rule, fire AfterFiredEvent, processed=true</li>
 * </ul>
 *
 * <p><b>Why V6.9.15 选这条</b>: v69_pipeline P0 #3, ActivationImpl L47-100
 * 4-level nested if/else 是反编译 state machine artifact (V5.96 skip 模式
 * 适用)。 early return chain 跟 V5.100.5/V6.0/V6.2/V6.9.14 同档 pure
 * code elegance closure。 Inner rule type dispatch (L61-92 LoopRule /
 * ScoreRule / normal Rhs) 是 legitimate type-dispatch, 不 flatten。
 * L37 + L83 addTipMsg 是 error context (RuleAssertException 诊断), 按
 * v69_pipeline #4 标记保留。
 */
@DisplayName("V6.9.15 — ActivationImpl.execute state machine 4-path 行为契约")
class ActivationImplExecuteFlattenTest {

    private MockedStatic<EngineContext> engineContextMock;
    private KnowledgeSession session;
    private ContextImpl context;
    private List<MessageItem> messageItems;

    @BeforeEach
    void setUp() {
        engineContextMock = mockStatic(EngineContext.class);
        EnginePluginRegistry registry = mock(EnginePluginRegistry.class);
        when(registry.getAssertorEvaluator()).thenReturn(new AssertorEvaluator());
        when(registry.getValueCompute()).thenReturn(new ValueCompute());
        engineContextMock.when(EngineContext::getAssertorEvaluator).thenReturn(new AssertorEvaluator());
        engineContextMock.when(EngineContext::getValueCompute).thenReturn(new ValueCompute());
        EngineContext.init(registry);

        session = mock(KnowledgeSession.class);
        messageItems = new ArrayList<>();
        context = new ContextImpl(session, new HashMap<>(), messageItems);
    }

    @AfterEach
    void tearDown() {
        engineContextMock.close();
    }

    /** Build a Rule with no Rhs (just the gating fields). */
    private Rule newRule() {
        Rule rule = new Rule();
        rule.setName("test-rule");
        rule.setFile("test.drl");
        rule.setRhs(new Rhs());
        return rule;
    }

    /** Past date (effective 1 hour ago). */
    private static Date pastDate() {
        return new Date(System.currentTimeMillis() - 3_600_000L);
    }

    /** Future date (1 hour from now). */
    private static Date futureDate() {
        return new Date(System.currentTimeMillis() + 3_600_000L);
    }

    @Nested
    @DisplayName("!enabled 路径")
    class DisabledRule {

        @Test
        @DisplayName("enabled=false → 返回 null, 不 fire AfterFiredEvent, processed=false")
        void disabledReturnsNull() {
            Rule rule = newRule();
            rule.setEnabled(false);
            ActivationImpl activation = new ActivationImpl(rule);

            List<RuleInfo> executed = new ArrayList<>();
            List<ActionValue> actionValues = new ArrayList<>();
            Object result = activation.execute(context, executed, actionValues);

            assertThat(result).isNull();
            assertThat(activation.isProcessed()).isFalse();
            // executedRules 在 L41 已 add (before enable check),这是 pre-existing 行为
            assertThat(executed).hasSize(1);
            // BeforeFiredEvent L40 总是 fire, AfterFiredEvent L94 只在 normal path fire
            verify(session, times(1)).fireEvent(any());
            verify(session, never()).fireEvent(any(ActivationAfterFiredEventImpl.class));
        }
    }

    @Nested
    @DisplayName("enabled + 未生效 路径")
    class NotYetEffective {

        @Test
        @DisplayName("effectiveDate > now → 返回 null, 不 fire AfterFiredEvent")
        void futureEffectiveReturnsNull() {
            Rule rule = newRule();
            rule.setEnabled(true);
            rule.setEffectiveDate(futureDate());
            ActivationImpl activation = new ActivationImpl(rule);

            List<RuleInfo> executed = new ArrayList<>();
            List<ActionValue> actionValues = new ArrayList<>();
            Object result = activation.execute(context, executed, actionValues);

            assertThat(result).isNull();
            assertThat(activation.isProcessed()).isFalse();
            verify(session, times(1)).fireEvent(any());
            verify(session, never()).fireEvent(any(ActivationAfterFiredEventImpl.class));
        }

        @Test
        @DisplayName("effectiveDate=null → 不被 effective 检查拦住, 进入下一关 (无 expiresDate → 正常执行)")
        void nullEffectiveFallsThrough() {
            Rule rule = newRule();
            rule.setEnabled(true);
            // effectiveDate = null, expiresDate = null
            ActivationImpl activation = new ActivationImpl(rule);

            List<RuleInfo> executed = new ArrayList<>();
            List<ActionValue> actionValues = new ArrayList<>();
            Object result = activation.execute(context, executed, actionValues);

            // 没有 expiresDate + 没有 actions,走 normal path
            assertThat(result).isSameAs(rule);
            assertThat(activation.isProcessed()).isTrue();
        }
    }

    @Nested
    @DisplayName("enabled + 已过期 路径")
    class Expired {

        @Test
        @DisplayName("expiresDate < now → 返回 null, 不 fire AfterFiredEvent")
        void pastExpiresReturnsNull() {
            Rule rule = newRule();
            rule.setEnabled(true);
            // effectiveDate = null (跳过第一关), expiresDate = past (过第二关)
            rule.setExpiresDate(pastDate());
            ActivationImpl activation = new ActivationImpl(rule);

            List<RuleInfo> executed = new ArrayList<>();
            List<ActionValue> actionValues = new ArrayList<>();
            Object result = activation.execute(context, executed, actionValues);

            assertThat(result).isNull();
            assertThat(activation.isProcessed()).isFalse();
            verify(session, times(1)).fireEvent(any());
            verify(session, never()).fireEvent(any(ActivationAfterFiredEventImpl.class));
        }
    }

    @Nested
    @DisplayName("正常执行路径")
    class NormalExecution {

        @Test
        @DisplayName("enabled + 日期合法 + 空 Rhs → 返回 rule, processed=true")
        void normalReturnsRule() {
            Rule rule = newRule();
            rule.setEnabled(true);
            // effectiveDate/expiresDate 都 null → 跳过两个日期关
            ActivationImpl activation = new ActivationImpl(rule);

            List<RuleInfo> executed = new ArrayList<>();
            List<ActionValue> actionValues = new ArrayList<>();
            Object result = activation.execute(context, executed, actionValues);

            assertThat(result).isSameAs(rule);
            assertThat(activation.isProcessed()).isTrue();
        }

        @Test
        @DisplayName("enabled + 过去生效 + 未来过期 → 返回 rule, processed=true")
        void validDatesReturnRule() {
            Rule rule = newRule();
            rule.setEnabled(true);
            rule.setEffectiveDate(pastDate());
            rule.setExpiresDate(futureDate());
            ActivationImpl activation = new ActivationImpl(rule);

            List<RuleInfo> executed = new ArrayList<>();
            List<ActionValue> actionValues = new ArrayList<>();
            Object result = activation.execute(context, executed, actionValues);

            assertThat(result).isSameAs(rule);
            assertThat(activation.isProcessed()).isTrue();
        }

        @Test
        @DisplayName("enabled + past + past (已过期但生效过) → 返回 null (expires 关卡)")
        void bothPastReturnsNull() {
            Rule rule = newRule();
            rule.setEnabled(true);
            rule.setEffectiveDate(pastDate());
            rule.setExpiresDate(pastDate());
            ActivationImpl activation = new ActivationImpl(rule);

            List<RuleInfo> executed = new ArrayList<>();
            List<ActionValue> actionValues = new ArrayList<>();
            Object result = activation.execute(context, executed, actionValues);

            assertThat(result).isNull();
            assertThat(activation.isProcessed()).isFalse();
        }
    }
}
