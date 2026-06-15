package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.executor.EventNodeExecutor;
import com.ruleforge.decision.flow.executor.GatewayNodeExecutor;
import com.ruleforge.decision.flow.executor.NodeExecutor;
import com.ruleforge.decision.flow.executor.NodeExecutorRegistry;
import com.ruleforge.decision.flow.executor.ParallelGatewayExecutor;
import com.ruleforge.decision.flow.executor.UserTaskNodeExecutor;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import com.ruleforge.decision.flow.state.FlowStatePersistenceService;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V5.39 B0 — StatefulDecisionFlow 行为规范。
 *
 * <p>5 BDD 分 5 组:start 写 DB / 含 RECEIVE_TASK 走 WAITING_CALLBACK / resume 推到 COMPLETED /
 * start 抛异常 FAILED / 多次 start 返回不同 flowRunId。
 *
 * <p>测试模式:mock DecisionFlowStateMapper + FlowStatePersistenceService,Runner 用真
 * 的(3 参 ctor 注入 mock mapper,让 persistence service lazy 派生自 mapper);
 * Repos 端 mock。
 */
@DisplayName("StatefulDecisionFlow 行为")
class StatefulDecisionFlowTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    static class StubActionExecutor implements NodeExecutor {
        final Map<String, java.util.function.BiConsumer<FlowNode, FlowContext>> handlers = new HashMap<>();

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

    /**
     * 构造一个引擎 + mock mapper:start 路径会写 DB(resume 路径则用 mock 让 selectByFlowRunId 返回 stub)。
     * Persistence service 走 production 构造(persistenceService 4 参 ctor,直接 mock 也行,
     * 但 runner.traverse 走的是它;我们让它 lazy 派生自 mock mapper — 那样 traverse
     * 内部对 serializeForAtomicUpdate 的调用是 mock-friendly 的)。
     */
    private FlowEngine newEngine(DecisionFlowStateMapper mapper, NodeExecutor... executors) {
        List<NodeExecutor> list = new ArrayList<>();
        for (NodeExecutor e : executors) list.add(e);
        list.add(new EventNodeExecutor(null));
        list.add(new GatewayNodeExecutor());
        list.add(new ParallelGatewayExecutor());
        list.add(new UserTaskNodeExecutor());
        FlowNodeRunner runner = new FlowNodeRunner(
            new NodeExecutorRegistry(list), new ConditionEvaluator(), mapper);
        FlowDefinitionRepo repo = mock(FlowDefinitionRepo.class);
        return new FlowEngine(repo, runner);
    }

    /**
     * 同 {@link #newEngine},但 mock repo 在 getOrLoad 时返回 caller 提供的 def — 给 resume 路径用。
     */
    private FlowEngine newEngineWithDef(DecisionFlowStateMapper mapper, FlowDefinition def,
                                         NodeExecutor... executors) {
        List<NodeExecutor> list = new ArrayList<>();
        for (NodeExecutor e : executors) list.add(e);
        list.add(new EventNodeExecutor(null));
        list.add(new GatewayNodeExecutor());
        list.add(new ParallelGatewayExecutor());
        list.add(new UserTaskNodeExecutor());
        FlowNodeRunner runner = new FlowNodeRunner(
            new NodeExecutorRegistry(list), new ConditionEvaluator(), mapper);
        FlowDefinitionRepo repo = mock(FlowDefinitionRepo.class);
        when(repo.getOrLoad(def.getProcessId())).thenReturn(def);
        return new FlowEngine(repo, runner);
    }

    @Nested
    @DisplayName("Group 1 — start 写 DB + 返回 flowRunId")
    class StartWritesDb {

        @Test
        @DisplayName("Given stateMapper mock 接受 insert,When start(def, vars),Then mapper.insert 被调 + 返回非空 flowRunId")
        void start_persists_pending_state_and_returns_uuid() {
            StubActionExecutor action = new StubActionExecutor()
                .register("noop", (n, c) -> c.vars().getVars().put("ran", true));
            DecisionFlowStateMapper mapper = mock(DecisionFlowStateMapper.class);
            // selectByFlowRunId 在 upsertState(PENDING 起步)内部会被调一次
            when(mapper.selectByFlowRunId(any())).thenReturn(null);

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="loan">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t" ruleforge:taskType="action" ruleforge:method="noop"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowEngine engine = newEngine(mapper, action);

            String flowRunId = engine.start(def, Map.of("applicant", "bob"));

            // 验证:flowRunId 是 UUID 形式
            assertNotNull(flowRunId);
            assertTrue(flowRunId.matches("[0-9a-f-]{36}"),
                "flowRunId should be UUID format, got: " + flowRunId);

            // 验证:DB 写了 — mapper.insert 被调过至少一次
            ArgumentCaptor<DecisionFlowState> stateCaptor = ArgumentCaptor.forClass(DecisionFlowState.class);
            verify(mapper, atLeastOnce()).insert(stateCaptor.capture());
            DecisionFlowState inserted = stateCaptor.getValue();
            assertEquals(flowRunId, inserted.getFlowRunId());
            assertEquals("loan", inserted.getFlowId());
        }
    }

    @Nested
    @DisplayName("Group 2 — start 含 RECEIVE_TASK → WAITING_CALLBACK")
    class StartWithAsyncNodeEndsWaiting {

        @Test
        @DisplayName("Given serviceTask 抛 AsyncNodeSuspendException(模拟 RECEIVE),When start,Then 不抛 + state 变 WAITING_CALLBACK")
        void start_async_node_results_in_waiting_callback() {
            StubActionExecutor action = new StubActionExecutor()
                .register("suspend", (n, c) -> {
                    throw new AsyncNodeSuspendException(
                        "t1", "RECEIVE_TASK", "RECEIVE_TASK", "msg:foo",
                        Map.of("channel", "msg:foo"), null);
                });
            DecisionFlowStateMapper mapper = mock(DecisionFlowStateMapper.class);
            when(mapper.selectByFlowRunId(any())).thenReturn(null);

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p2">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t1" ruleforge:taskType="action" ruleforge:method="suspend"/>
                    <bpmn:serviceTask id="t2" ruleforge:taskType="action" ruleforge:method="noop"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t1"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t1" targetRef="t2"/>
                    <bpmn:sequenceFlow id="e3" sourceRef="t2" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowEngine engine = newEngine(mapper, action);

            // start 不抛 — 异步挂起是正常路径
            String flowRunId = engine.start(def, new HashMap<>());
            assertNotNull(flowRunId);

            // 验证:DB 走 updateAtomic(V5.35 A4 复合原子化写)写了 WAITING_CALLBACK 状态
            ArgumentCaptor<DecisionFlowState> captor = ArgumentCaptor.forClass(DecisionFlowState.class);
            verify(mapper, atLeastOnce()).updateAtomic(captor.capture(), any(), any());
            DecisionFlowState lastUpdate = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertEquals(DecisionFlowState.STATUS_WAITING_CALLBACK, lastUpdate.getStatus());
            assertTrue(lastUpdate.getErrorMessage() != null
                && lastUpdate.getErrorMessage().contains("RECEIVE_TASK"),
                "errorMessage should encode WAIT_TYPE=RECEIVE_TASK: " + lastUpdate.getErrorMessage());
            assertEquals("msg:foo", lastUpdate.getWaitRef());
        }
    }

    @Nested
    @DisplayName("Group 3 — resume 推到 COMPLETED")
    class ResumeAdvancesToCompleted {

        @Test
        @DisplayName("Given start 走 WAITING_CALLBACK,When resume(flowRunId, flowId, node, vars),Then status 变 COMPLETED")
        void resume_after_waiting_callback_advances_to_completed() {
            StubActionExecutor action = new StubActionExecutor()
                .register("conditional_suspend", (n, c) -> {
                    // resume 后再跑一次 — 此时 vars.decision = "0",不抛
                    Object d = c.vars().getVars().get("decision");
                    if (d == null) {
                        throw new AsyncNodeSuspendException(
                            "t1", "USER_TASK", "USER_TASK", "userTask",
                            Map.of("field", "decision"), null);
                    }
                    c.vars().getVars().put("decided", d);
                });
            DecisionFlowStateMapper mapper = mock(DecisionFlowStateMapper.class);
            when(mapper.selectByFlowRunId(any())).thenReturn(null);

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p3">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t1" ruleforge:taskType="action" ruleforge:method="conditional_suspend"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t1"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t1" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            // resume 路径需要 repo 返回 def
            DecisionFlowStateMapper mapper2 = mock(DecisionFlowStateMapper.class);
            when(mapper2.selectByFlowRunId(any())).thenReturn(null);
            FlowEngine engine = newEngineWithDef(mapper2, def, action);

            // start — 走 WAITING_CALLBACK
            String flowRunId = engine.start(def, new HashMap<>());
            // resume — 此时 vars 里有 decision,handler 不抛
            engine.resume(flowRunId, "p3", "t1", Map.of("decision", "1"));

            // 验证:DB 最后一次 updateAtomic 是 COMPLETED(V5.35 A4 复合原子化写)
            ArgumentCaptor<DecisionFlowState> captor = ArgumentCaptor.forClass(DecisionFlowState.class);
            verify(mapper2, atLeastOnce()).updateAtomic(captor.capture(), any(), any());
            DecisionFlowState last = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertEquals(DecisionFlowState.STATUS_COMPLETED, last.getStatus());
        }
    }

    @Nested
    @DisplayName("Group 4 — start 异常 → FAILED + 抛 FlowExecutionException")
    class StartExceptionEndsFailed {

        @Test
        @DisplayName("Given serviceTask 抛业务异常,When start,Then 抛 FlowExecutionException + DB state 变 FAILED")
        void start_with_throwing_node_marks_failed_and_throws() {
            StubActionExecutor action = new StubActionExecutor()
                .register("fail", (n, c) -> {
                    throw new IllegalStateException("intentional failure");
                });
            DecisionFlowStateMapper mapper = mock(DecisionFlowStateMapper.class);
            when(mapper.selectByFlowRunId(any())).thenReturn(null);

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p4">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t1" ruleforge:taskType="action" ruleforge:method="fail"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t1"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t1" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowEngine engine = newEngine(mapper, action);

            assertThrows(FlowExecutionException.class,
                () -> engine.start(def, new HashMap<>()));

            // 验证:DB 最后一次 updateAtomic 是 FAILED(V5.35 A4 复合原子化写)
            ArgumentCaptor<DecisionFlowState> captor = ArgumentCaptor.forClass(DecisionFlowState.class);
            verify(mapper, atLeastOnce()).updateAtomic(captor.capture(), any(), any());
            DecisionFlowState last = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertEquals(DecisionFlowState.STATUS_FAILED, last.getStatus());
            assertTrue(last.getErrorMessage() != null
                && (last.getErrorMessage().contains("IllegalStateException")
                    || last.getErrorMessage().contains("intentional failure")),
                "errorMessage should mention the original failure: " + last.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("Group 5 — 多次 start 返回不同 flowRunId")
    class MultipleStartReturnsUniqueFlowRunIds {

        @Test
        @DisplayName("Given 同 def,When start 两次,Then 返回两个不同的 UUID")
        void multiple_starts_return_distinct_flow_run_ids() {
            StubActionExecutor action = new StubActionExecutor()
                .register("noop", (n, c) -> {});
            DecisionFlowStateMapper mapper = mock(DecisionFlowStateMapper.class);
            when(mapper.selectByFlowRunId(any())).thenReturn(null);

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p5">
                    <bpmn:startEvent id="s"/>
                    <bpmn:serviceTask id="t" ruleforge:taskType="action" ruleforge:method="noop"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="t" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowEngine engine = newEngine(mapper, action);

            String id1 = engine.start(def, new HashMap<>());
            String id2 = engine.start(def, new HashMap<>());
            assertNotNull(id1);
            assertNotNull(id2);
            assertNotEquals(id1, id2, "two starts must return distinct flowRunIds");
        }
    }
}
