package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.executor.ActionNodeExecutor;
import com.ruleforge.decision.flow.executor.EventNodeExecutor;
import com.ruleforge.decision.flow.executor.GatewayNodeExecutor;
import com.ruleforge.decision.flow.executor.NodeExecutor;
import com.ruleforge.decision.flow.executor.NodeExecutorRegistry;
import com.ruleforge.decision.flow.executor.ParallelGatewayExecutor;
import com.ruleforge.decision.flow.executor.UserTaskNodeExecutor;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * V5.39 B0 — StatelessDecisionExecutor 行为规范。
 *
 * <p>5 BDD 分 5 组:基本求值 / 多次 execute 隔离 / 异常不污染 / 无 DB 持久化 / 异步节点拒绝。
 *
 * <p>测试模式:不接 Spring 容器,手工 new 出 NodeExecutorRegistry + stub ActionExecutor,
 * 绕过 stateMapper(传 null 模拟无 DB 持久化路径)。Repo 用 Mockito mock 即可 — Stateless
 * 路径不查 repo。
 */
@DisplayName("StatelessDecisionExecutor 行为")
class StatelessDecisionExecutorTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    /**
     * Stub ActionNodeExecutor:按 {@code ruleforge:method} 路由,handler 可选写入 vars
     * 或抛异常。
     */
    static class StubActionExecutor implements NodeExecutor {
        final java.util.Map<String, java.util.function.BiConsumer<FlowNode, FlowContext>> handlers = new HashMap<>();

        StubActionExecutor register(String method,
                                     java.util.function.BiConsumer<FlowNode, FlowContext> handler) {
            handlers.put(method, handler);
            return this;
        }

        @Override
        public String supportedType() {
            return "SERVICE_TASK:action";
        }

        @Override
        public void execute(FlowNode node, FlowContext context) {
            String m = node.attr("ruleforge", "method");
            java.util.function.BiConsumer<FlowNode, FlowContext> h = handlers.get(m);
            if (h == null) {
                throw new IllegalStateException("No handler for method=" + m);
            }
            h.accept(node, context);
        }
    }

    private FlowNodeRunner newRunner(NodeExecutor... executors) {
        List<NodeExecutor> list = new ArrayList<>();
        for (NodeExecutor e : executors) list.add(e);
        list.add(new EventNodeExecutor(null));
        list.add(new GatewayNodeExecutor());
        list.add(new ParallelGatewayExecutor());
        list.add(new UserTaskNodeExecutor());
        return new FlowNodeRunner(new NodeExecutorRegistry(list), new ConditionEvaluator(), null);
    }

    private FlowEngine newEngine(NodeExecutor... executors) {
        return newEngineWithRepo(mock(FlowDefinitionRepo.class), executors);
    }

    private FlowEngine newEngineWithRepo(FlowDefinitionRepo repo, NodeExecutor... executors) {
        return new FlowEngine(repo, newRunner(executors));
    }

    @Nested
    @DisplayName("Group 1 — 基本求值")
    class BasicEvaluation {

        @Test
        @DisplayName("Given serviceTask 写 var,When execute,Then 返回的 vars 包含写入值")
        void execute_simple_decision_graph_returns_written_vars() {
            StubActionExecutor action = new StubActionExecutor()
                .register("write_amount", (n, c) -> c.vars().getVars().put("approved_amount", 10000));

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="loan">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t1" ruleforge:taskType="action" ruleforge:method="write_amount"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t1"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t1" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowEngine engine = newEngine(action);

            Map<String, Object> result = engine.execute(def, Map.of("applicant", "alice"));

            assertNotNull(result);
            assertEquals(10000, result.get("approved_amount"));
            assertEquals("alice", result.get("applicant"));
        }
    }

    @Nested
    @DisplayName("Group 2 — 多次 execute 隔离(无副作用)")
    class MultipleExecutesAreIsolated {

        @Test
        @DisplayName("Given caller 传入 'counter' 的初始值,When 第二次 execute 用不同初值,Then 第二次的 result 不带第一次的初值")
        void execute_twice_does_not_share_state() {
            // 关键:验证 stateless execute 内部用的是 caller 传入的 vars(防御性复制),
            // 而不是某种"上一次执行的"残留 vars。两次都跑同一个 def,handler 不写新 key,
            // 所以如果 engine 内部有 cross-call 污染,result 会有"不属于 caller"的 key。
            StubActionExecutor action = new StubActionExecutor()
                .register("read", (n, c) -> {
                    // 只读,写什么取决于 caller 给的 initial 是不是被保留
                    Object v = c.vars().getVars().get("initial");
                    c.vars().getVars().put("read_back", v);
                });

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p1">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t" ruleforge:taskType="action" ruleforge:method="read"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowEngine engine = newEngine(action);

            Map<String, Object> r1 = engine.execute(def, Map.of("initial", "first"));
            assertEquals("first", r1.get("read_back"));
            assertFalse(r1.containsKey("leaked_from_run2"),
                "first run must not contain keys added by second run");

            Map<String, Object> r2 = engine.execute(def, Map.of("initial", "second"));
            assertEquals("second", r2.get("read_back"));
            // 第二次跑的 result 不应包含任何"第一次跑留下"的 key
            assertEquals(2, r2.size(),
                "second run result should only contain caller-provided 'initial' + 'read_back', got: "
                    + r2.keySet());
        }

        @Test
        @DisplayName("Given caller 改返回值,When 第三次 execute,Then 不受 caller 修改影响(返回 map 副本)")
        void returned_map_is_defensive_copy() {
            StubActionExecutor action = new StubActionExecutor()
                .register("noop", (n, c) -> {});

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p2">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t" ruleforge:taskType="action" ruleforge:method="noop"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowEngine engine = newEngine(action);

            Map<String, Object> r1 = engine.execute(def, Map.of("k", "v"));
            // caller 修改 r1
            r1.put("caller_mutation", "polluted");
            // 第二次跑:r1 的修改不应渗到 engine 内部
            Map<String, Object> r2 = engine.execute(def, Map.of("k", "v"));
            assertFalse(r2.containsKey("caller_mutation"));
            assertNotSame(r1, r2);
        }
    }

    @Nested
    @DisplayName("Group 3 — 异常不污染引擎")
    class ExceptionDoesNotPolluteEngine {

        @Test
        @DisplayName("Given node executor 抛业务异常,When execute,Then 抛 FlowExecutionException + 后续 execute 仍能用")
        void execute_throwing_business_exception_does_not_corrupt_engine() {
            StubActionExecutor action = new StubActionExecutor()
                .register("fail", (n, c) -> {
                    throw new IllegalStateException("business logic failed");
                })
                .register("ok", (n, c) -> c.vars().getVars().put("ran_ok", true));

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p3">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t1" ruleforge:taskType="action" ruleforge:method="fail"/>
                    <bpmn:serviceTask id="t2" ruleforge:taskType="action" ruleforge:method="ok"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t1"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t1" targetRef="t2"/>
                    <bpmn:sequenceFlow id="e3" sourceRef="t2" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowEngine engine = newEngine(action);

            // 第一次抛
            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> engine.execute(def, new HashMap<>()));
            assertTrue(ex.getMessage().contains("Node t1 failed")
                || ex.getMessage().contains("failed"),
                "should wrap business exception: " + ex.getMessage());

            // 引擎仍能处理下一次 execute(用 ok 那个 def)
            String okXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p3ok">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t" ruleforge:taskType="action" ruleforge:method="ok"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition okDef = parser.parseSingleProcess(okXml);
            Map<String, Object> r2 = engine.execute(okDef, new HashMap<>());
            assertEquals(true, r2.get("ran_ok"));
        }
    }

    @Nested
    @DisplayName("Group 4 — 无 DB 持久化")
    class NoDbPersistence {

        @Test
        @DisplayName("Given stateMapper=null(测试场景),When execute,Then 不写 DB(repo 也不被查)")
        void execute_does_not_persist_to_db() {
            // stateMapper=null 是测试配置 — production 也会走 traverse 但 stateMapper 不为 null
            // 我们这里反着验证:即使 stateMapper 为 null,execute 也能跑通(因为 stateless 路径不写)
            StubActionExecutor action = new StubActionExecutor()
                .register("noop", (n, c) -> c.vars().getVars().put("ran", true));

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p4">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t" ruleforge:taskType="action" ruleforge:method="noop"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            // 用 captured repo 来验证:stateless execute 不应触发 repo.getOrLoad
            FlowDefinitionRepo repo = mock(FlowDefinitionRepo.class);
            FlowEngine engine = newEngineWithRepo(repo, action);
            Map<String, Object> result = engine.execute(def, new HashMap<>());
            assertEquals(true, result.get("ran"));
            org.mockito.Mockito.verify(repo, org.mockito.Mockito.never()).getOrLoad(org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Nested
    @DisplayName("Group 5 — 异步节点拒绝(角色越界)")
    class AsyncNodeRejected {

        @Test
        @DisplayName("Given 流程含 userTask,When execute,Then 抛 FlowExecutionException(waitType 提示)")
        void execute_on_user_task_throws_with_helpful_message() {
            // 用真实 UserTaskNodeExecutor,ruleforge:waitType="USER_TASK" 会触发 AsyncNodeSuspendException
            // 为了不依赖 IR 内部构造 USER_TASK,这里用 stub executor 模拟抛 AsyncNodeSuspendException
            com.ruleforge.decision.exception.AsyncNodeSuspendException suspender =
                new com.ruleforge.decision.exception.AsyncNodeSuspendException(
                    "t1", "USER_TASK", "USER_TASK", "userTaskNodeId",
                    Map.of("field", "decision"), null);

            StubActionExecutor action = new StubActionExecutor()
                .register("suspend", (n, c) -> { throw suspender; });

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p5">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t1" ruleforge:taskType="action" ruleforge:method="suspend"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t1"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t1" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowEngine engine = newEngine(action);

            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> engine.execute(def, new HashMap<>()));
            // 提示:角色越界,应建议用 StatefulDecisionFlow
            assertTrue(ex.getMessage().contains("Stateless executor cannot suspend"),
                "should mention stateless cannot suspend: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("StatefulDecisionFlow"),
                "should suggest StatefulDecisionFlow: " + ex.getMessage());
        }
    }
}
