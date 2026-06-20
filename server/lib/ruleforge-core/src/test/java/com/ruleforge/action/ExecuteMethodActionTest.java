package com.ruleforge.action;

import com.ruleforge.debug.MsgType;
import com.ruleforge.engine.Context;
import com.ruleforge.engine.EngineContext;
import com.ruleforge.plugin.EnginePluginRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V6.9.9.2 — {@link ExecuteMethodAction#execute} 行为契约 BDD。
 *
 * <p>锁 V6.9.9.2 收口 (no-arg 分支 L89-91 + with-arg 分支 L71-73 两处
 * {@code context.logMsg} 调用门控在 {@code this.debug} flag 上) 的行为不变性:
 * <ul>
 *   <li><b>debug=false (V5.90 默认)</b>: 不调 logMsg, 跳过字符串拼接</li>
 *   <li><b>debug=true</b>: 调 logMsg, 走 "$$$ 执行动作：" 路径</li>
 *   <li><b>no-arg 方法</b>: 走 L89-91 路径</li>
 * </ul>
 *
 * <p><b>Why V6.9.9.2 选这条</b>: 跟 V5.88 (Rule.logMessage) / V5.95 (Criteria.addTipMsg) /
 * V6.9.9.1 (VariableAssignAction) 同档 debug 门控。两条 logMsg 都是 fire-rule 路径上的
 * 字符串拼接 + MessageItem 分配, V5.90 默认 rule.debug=false 时每次都浪费。
 */
@DisplayName("V6.9.9.2 — ExecuteMethodAction.execute logMsg debug gate")
class ExecuteMethodActionTest {

    private ExecuteMethodAction action;
    private Context context;
    private MockedStatic<EngineContext> engineContextStatic;
    private EnginePluginRegistry registry;

    // 测试 bean:有 no-arg 方法 getValue 返 string
    public static class TestBean {
        public String getValue() {
            return "result";
        }
    }

    @BeforeEach
    void setUp() {
        action = new ExecuteMethodAction();
        context = mock(Context.class);

        action.setBeanId("testBean");
        action.setMethodName("getValue");

        // mock EngineContext.getBean via mockStatic
        registry = mock(EnginePluginRegistry.class);
        TestBean bean = new TestBean();
        engineContextStatic = mockStatic(EngineContext.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        EngineContext.init(registry);  // sets static registry field
        when(registry.getBean("testBean")).thenReturn(bean);
    }

    @AfterEach
    void tearDown() {
        engineContextStatic.close();
    }

    @Nested
    @DisplayName("debug gate: logMsg 只在 action.debug=true 时调")
    class DebugGate {

        @Test
        @DisplayName("debug=false (V5.90 默认) → 不调 logMsg (no-arg 分支)")
        void debugFalseSkipsLogMsg() {
            action.setDebug(false);

            ActionValue result = action.execute(context, new Object(), new ArrayList<>());

            assertThat(result).isNotNull();
            assertThat(result.getActionId()).isEqualTo("getValue");
            verify(context, never()).logMsg(anyString(), any(MsgType.class));
        }

        @Test
        @DisplayName("debug=true → 调 logMsg with ExecuteBeanMethod (no-arg 分支)")
        void debugTrueCallsLogMsg() {
            action.setDebug(true);

            ActionValue result = action.execute(context, new Object(), new ArrayList<>());

            assertThat(result).isNotNull();
            assertThat(result.getActionId()).isEqualTo("getValue");
            verify(context).logMsg(anyString(), eq(MsgType.ExecuteBeanMethod));
        }
    }
}