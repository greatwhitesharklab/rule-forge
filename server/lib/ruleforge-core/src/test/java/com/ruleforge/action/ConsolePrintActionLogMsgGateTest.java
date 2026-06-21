package com.ruleforge.action;

import com.ruleforge.debug.MsgType;
import com.ruleforge.engine.Context;
import com.ruleforge.engine.ValueCompute;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.plugin.EnginePluginRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V6.9.21 — {@link ConsolePrintAction#execute} logMsg 门控契约 BDD。
 *
 * <p>锁 V6.9.21 收口 (ConsolePrintAction.java L16-30: 3 处 context.logMsg 调用 →
 * `if (this.debug)` 门控) 的行为不变性:
 * <ul>
 *   <li><b>debug=false (default)</b>: 3 处 logMsg 全部 SKIP, 不写 "☢☢☢ 控制台输出" 到 context</li>
 *   <li><b>debug=true</b>: 3 处 logMsg 全部触发, BigDecimal / Double / else fallback 都走</li>
 *   <li><b>value 求值在 debug=false 时也仍执行</b>: complexValueCompute 仍被调用 (语义保留,
 *       只是结果不写入 context log)</li>
 * </ul>
 *
 * <p><b>Why V6.9.21</b>: v69_pipeline P1-2 — action layer logMsg 最后一个 file, 收口
 * V6.9.9.1 series (V6.9.16 + V6.9.17 已收 scorecardImpl + scoreRule)。 AbstractAction
 * 已暴露 `protected boolean debug` + `setDebug(boolean)`, 无需新 field。
 */
@DisplayName("V6.9.21 — ConsolePrintAction logMsg 门控契约")
class ConsolePrintActionLogMsgGateTest {

    private ConsolePrintAction action;
    private Context context;
    private ValueCompute valueCompute;

    @BeforeEach
    void setUp() {
        action = new ConsolePrintAction();
        action.setValue(mock(Value.class));

        // EngineContext needs registry for ValueCompute
        EnginePluginRegistry r = mock(EnginePluginRegistry.class);
        valueCompute = mock(ValueCompute.class);
        when(r.getValueCompute()).thenReturn(valueCompute);
        com.ruleforge.engine.EngineContext.init(r);

        context = mock(Context.class);
    }

    @Test
    @DisplayName("debug=false (default) — 3 处 logMsg 全部 SKIP")
    void debugFalseSkipsAllLogMsg() {
        // default action.debug = false
        when(valueCompute.complexValueCompute(any(), any(), any(), any()))
            .thenReturn("hello");

        action.execute(context, null, List.of());

        verify(context, never()).logMsg(anyString(), any());
    }

    @Test
    @DisplayName("debug=true + BigDecimal — logMsg 触发 1 次")
    void debugTrueBigDecimalTriggersLogMsg() {
        action.setDebug(true);
        when(valueCompute.complexValueCompute(any(), any(), any(), any()))
            .thenReturn(new java.math.BigDecimal("42.5"));

        action.execute(context, null, List.of());

        verify(context, times(1)).logMsg(
            org.mockito.ArgumentMatchers.contains("控制台输出"),
            eq(MsgType.ConsoleOutput));
    }

    @Test
    @DisplayName("debug=true + Double — logMsg 触发 1 次")
    void debugTrueDoubleTriggersLogMsg() {
        action.setDebug(true);
        when(valueCompute.complexValueCompute(any(), any(), any(), any()))
            .thenReturn(3.14d);

        action.execute(context, null, List.of());

        verify(context, times(1)).logMsg(
            org.mockito.ArgumentMatchers.contains("3.14"),
            eq(MsgType.ConsoleOutput));
    }

    @Test
    @DisplayName("debug=true + null content — 走 else fallback, 写 'null'")
    void debugTrueNullContentFallbackWritesNull() {
        action.setDebug(true);
        when(valueCompute.complexValueCompute(any(), any(), any(), any()))
            .thenReturn(null);

        action.execute(context, null, List.of());

        verify(context, times(1)).logMsg(
            org.mockito.ArgumentMatchers.contains("null"),
            eq(MsgType.ConsoleOutput));
    }

    @Test
    @DisplayName("debug=true + String — 走 else fallback, 写原 String")
    void debugTrueStringFallbackWritesString() {
        action.setDebug(true);
        when(valueCompute.complexValueCompute(any(), any(), any(), any()))
            .thenReturn("plain text");

        action.execute(context, null, List.of());

        verify(context, times(1)).logMsg(
            org.mockito.ArgumentMatchers.contains("plain text"),
            eq(MsgType.ConsoleOutput));
    }
}
