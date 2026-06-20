package com.ruleforge.runtime.rete;

import com.ruleforge.debug.MsgType;
import com.ruleforge.engine.EvaluationContext;
import com.ruleforge.engine.KnowledgeSession;
import com.ruleforge.model.rule.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V6.9.11 — {@link TerminalActivity#enter} 行为契约 BDD。
 *
 * <p>锁 V6.9.11 收口 (L29-31 {@code context.logMsg} 调用门控在
 * {@code rule.getDebug()} flag 上) 的行为不变性:
 * <ul>
 *   <li><b>rule.debug=false (V5.90 默认)</b>: 不调 logMsg, 跳过 "√√√ 规则匹配" 字符串拼接</li>
 *   <li><b>rule.debug=true</b>: 调 logMsg with RuleMatch</li>
 * </ul>
 *
 * <p><b>Why V6.9.11 选这条</b>: 跟 V5.88 (CriteriaActivity.logMessage 早返) / V5.95
 * (Criteria.addTipMsg) / V5.90 (Rule.debug 默认) / V6.9.9-V6.9.10 (action logMsg 门控) 同档。
 * TerminalActivity.enter 是 per-fire-rule hot path (规则完全匹配时触发), 字符串拼接 + MessageItem
 * 分配可以省。CriteriaActivity.logMessage 已经在 V5.88 门控 (L70-72), 无需重复。
 *
 * <p><b>Rule.debug 语义</b>: {@link Boolean} 可空, V5.90 起默认 false。门控条件用
 * {@code Boolean.TRUE.equals(rule.getDebug())} 兼容 null。
 */
@DisplayName("V6.9.11 — TerminalActivity.enter logMsg debug gate")
class TerminalActivityTest {

    private TerminalActivity activity;
    private Rule rule;
    private EvaluationContext context;
    private KnowledgeSession session;

    @BeforeEach
    void setUp() {
        rule = mock(Rule.class);
        when(rule.getName()).thenReturn("TestRule");
        when(rule.getFile()).thenReturn("test.drl");

        context = mock(EvaluationContext.class);
        // TerminalActivity.enter L26: `(KnowledgeSession) context.getWorkingMemory()`
        // 需要直接 mock 成 KnowledgeSession(interface 继承自 WorkingMemory)
        session = mock(KnowledgeSession.class);
        when(context.getWorkingMemory()).thenReturn(session);

        activity = new TerminalActivity(rule);
    }

    @AfterEach
    void tearDown() {
    }

    @Nested
    @DisplayName("debug gate: logMsg 只在 rule.debug=true 时调")
    class DebugGate {

        @Test
        @DisplayName("rule.debug=false (V5.90 默认) → 不调 logMsg")
        void debugFalseSkipsLogMsg() {
            when(rule.getDebug()).thenReturn(false);
            FactTracker tracker = mock(FactTracker.class);

            Collection<FactTracker> result = activity.enter(context, new Object(), tracker);

            assertThat(result).hasSize(1);
            verify(context, never()).logMsg(anyString(), any(MsgType.class));
        }

        @Test
        @DisplayName("rule.debug=null → 不调 logMsg (Boolean.TRUE.equals(null) = false)")
        void debugNullSkipsLogMsg() {
            when(rule.getDebug()).thenReturn(null);
            FactTracker tracker = mock(FactTracker.class);

            Collection<FactTracker> result = activity.enter(context, new Object(), tracker);

            assertThat(result).hasSize(1);
            verify(context, never()).logMsg(anyString(), any(MsgType.class));
        }

        @Test
        @DisplayName("rule.debug=true → 调 logMsg with RuleMatch")
        void debugTrueCallsLogMsg() {
            when(rule.getDebug()).thenReturn(true);
            FactTracker tracker = mock(FactTracker.class);

            Collection<FactTracker> result = activity.enter(context, new Object(), tracker);

            assertThat(result).hasSize(1);
            verify(context).logMsg(anyString(), eq(MsgType.RuleMatch));
        }
    }
}