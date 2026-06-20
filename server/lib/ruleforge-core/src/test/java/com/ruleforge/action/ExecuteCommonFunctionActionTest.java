package com.ruleforge.action;

import com.ruleforge.debug.MsgType;
import com.ruleforge.engine.Context;
import com.ruleforge.engine.EngineContext;
import com.ruleforge.engine.WorkingMemory;
import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionContext;
import com.ruleforge.model.function.FunctionDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
 * V6.9.10 — {@link ExecuteCommonFunctionAction#execute} 行为契约 BDD。
 *
 * <p>锁 V6.9.10 收口 (L56-58 {@code context.logMsg} 调用门控在 {@code this.debug}
 * flag 上) 的行为不变性:
 * <ul>
 *   <li><b>debug=false (V5.90 默认)</b>: 不调 logMsg, 跳过字符串拼接</li>
 *   <li><b>debug=true</b>: 调 logMsg, 走 "*** 执行函数：" 路径</li>
 * </ul>
 *
 * <p><b>Why V6.9.10 选这条</b>: 跟 V5.88 (Rule.logMessage) / V5.95 (Criteria.addTipMsg) /
 * V5.90 (Rule.debug 默认) / V6.9.9.1 (VariableAssignAction) / V6.9.9.2 (ExecuteMethodAction)
 * 同档。V6.9.10 关闭第 3 个 action 子类 logMsg 调用, action 层 debug 日志收口近闭环。
 *
 * <p><b>Mocking</b>: 跟 V6.9.9.2 同模式 mockStatic(EngineContext.class), 用 init(registry)
 * 注入静态 map (functionDescriptorMap + functionDescriptorLabelMap), EngineContext.init
 * 必须在 mockStatic 块内调, 否则 getFunctionDescriptorMap().get(name) NPE。
 */
@DisplayName("V6.9.10 — ExecuteCommonFunctionAction.execute logMsg debug gate")
class ExecuteCommonFunctionActionTest {

    private ExecuteCommonFunctionAction action;
    private Context context;
    private WorkingMemory workingMemory;
    private MockedStatic<EngineContext> engineContextStatic;
    private FunctionDescriptor function;

    // 简单测试函数:无 argument, doFunction 直接返 "ok"
    public static class TestFunction implements FunctionDescriptor {
        @Override public Argument getArgument() { return null; }
        @Override public Object doFunction(Object object, String property,
                                            FunctionContext ctx) {
            return "ok";
        }
        @Override public String getName() { return "testFunc"; }
        @Override public String getLabel() { return "TestFunc"; }
        @Override public boolean isDisabled() { return false; }
    }

    @BeforeEach
    void setUp() {
        action = new ExecuteCommonFunctionAction();
        context = mock(Context.class);
        workingMemory = mock(WorkingMemory.class);

        action.setName("testFunc");
        action.setLabel("TestFunc");

        // setup EngineContext static
        function = new TestFunction();
        Map<String, FunctionDescriptor> byName = new HashMap<>();
        byName.put("testFunc", function);
        Map<String, FunctionDescriptor> byLabel = new HashMap<>();
        byLabel.put("TestFunc", function);

        engineContextStatic = mockStatic(EngineContext.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        EngineContext.init(mock(com.ruleforge.plugin.EnginePluginRegistry.class));
        // 直接覆盖 static maps (init 后覆盖)
        // EngineContext 没有 setter for maps, 但 maps 是 package-private 不可外部改
        // 用 mockStatic 把 getFunctionDescriptorMap().get(name) 拦截
        engineContextStatic.when(() -> EngineContext.getFunctionDescriptorMap()).thenReturn(byName);
        engineContextStatic.when(() -> EngineContext.getFunctionDescriptorLabelMap()).thenReturn(byLabel);

        when(context.getWorkingMemory()).thenReturn(workingMemory);
    }

    @AfterEach
    void tearDown() {
        engineContextStatic.close();
    }

    @Nested
    @DisplayName("debug gate: logMsg 只在 action.debug=true 时调")
    class DebugGate {

        @Test
        @DisplayName("debug=false (V5.90 默认) → 不调 logMsg")
        void debugFalseSkipsLogMsg() {
            action.setDebug(false);

            ActionValue result = action.execute(context, new Object(), new ArrayList<>());

            assertThat(result).isNotNull();
            assertThat(result.getActionId()).isEqualTo("testFunc");
            verify(context, never()).logMsg(anyString(), any(MsgType.class));
        }

        @Test
        @DisplayName("debug=true → 调 logMsg with ExecuteFunction")
        void debugTrueCallsLogMsg() {
            action.setDebug(true);

            ActionValue result = action.execute(context, new Object(), new ArrayList<>());

            assertThat(result).isNotNull();
            assertThat(result.getActionId()).isEqualTo("testFunc");
            verify(context).logMsg(anyString(), eq(MsgType.ExecuteFunction));
        }
    }
}