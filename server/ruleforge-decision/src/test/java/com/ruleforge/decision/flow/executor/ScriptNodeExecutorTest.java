package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ScriptNodeExecutor 行为规范:
 * Given SCRIPT_TASK 节点 + script body
 * When   execute(node, ctx)
 * Then   1) JSR-223 跑脚本(默认 groovy)
 *        2) bindings.parameters 非空时 merge 回 ctx.vars
 *        3) 没有 ScriptEngine(format) 抛 FlowExecutionException
 */
@DisplayName("ScriptNodeExecutor 行为")
class ScriptNodeExecutorTest {

    private ScriptNodeExecutor newExecutor(ScriptEngineManager mock) {
        ScriptNodeExecutor e = new ScriptNodeExecutor();
        e.setEngineManager(mock);
        return e;
    }

    private FlowNode makeNode(String id, String body, String format) {
        return new FlowNode(id, NodeType.SCRIPT_TASK, "脚本",
            new HashMap<>(), body, format, List.of(), false);
    }

    @Nested
    @DisplayName("脚本执行")
    class RunScript {

        @Test
        @DisplayName("Given variables x=10,When 跑 'parameters.y = x * 2',Then ctx.vars.y = 20")
        void runGroovy() throws Exception {
            ScriptEngineManager mgr = mock(ScriptEngineManager.class);
            ScriptEngine engine = mock(ScriptEngine.class);
            when(mgr.getEngineByName("groovy")).thenReturn(engine);
            // engine.eval mock: 把脚本里的 "parameters.y = x * 2" 实际跑出来
            when(engine.eval(any(String.class), any(Bindings.class))).thenAnswer(inv -> {
                Bindings b = inv.getArgument(1);
                Map<String, Object> variables = (Map<String, Object>) b.get("variables");
                Map<String, Object> parameters = (Map<String, Object>) b.get("parameters");
                Object x = variables.get("x");
                parameters.put("y", ((Number) x).intValue() * 2);
                return null;
            });
            ScriptNodeExecutor executor = newExecutor(mgr);

            FlowNode node = makeNode("s1", "parameters.y = x * 2", "groovy");
            FlowContext ctx = new FlowContext();
            ctx.getVars().put("x", 10);

            executor.execute(node, ctx);

            assertEquals(20, ctx.getVars().get("y"));
        }

        @Test
        @DisplayName("Given 空脚本,When 跑,Then noop 不抛")
        void emptyScript() throws Exception {
            ScriptNodeExecutor executor = newExecutor(mock(ScriptEngineManager.class));
            FlowNode node = makeNode("s1", "", "groovy");
            FlowContext ctx = new FlowContext();
            executor.execute(node, ctx);  // 不抛
            assertTrue(ctx.getVars().isEmpty());
        }

        @Test
        @DisplayName("Given format=javascript 引擎不可用,When 跑,Then 抛 FlowExecutionException")
        void unknownFormat() {
            ScriptEngineManager mgr = mock(ScriptEngineManager.class);
            when(mgr.getEngineByName(eq("noSuchScriptEngineFormat"))).thenReturn(null);
            ScriptNodeExecutor executor = newExecutor(mgr);

            FlowNode node = makeNode("s1", "x=1", "noSuchScriptEngineFormat");
            FlowContext ctx = new FlowContext();
            assertThrows(FlowExecutionException.class, () -> {
                try {
                    executor.execute(node, ctx);
                } catch (FlowExecutionException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
