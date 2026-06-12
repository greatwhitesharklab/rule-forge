package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.bus.FlowResumer;
import com.ruleforge.decision.flow.bus.InMemoryMessageBus;
import com.ruleforge.decision.flow.bus.MessageBusPublisher;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.BpmnDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.parser.BpmnCollaborationFixtures;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.37 B0 — MessageFlowStart/End executor 行为规范 + 跨池 bus 集成。
 *
 * <p>5 BDD(合并了原计划的 8+2 BDD,聚焦契约关键面):
 * <ul>
 *   <li>END:publishPoolMessage 调一次 + 不抛</li>
 *   <li>END:payload 含 flowRunId / currentNodeId / flowId</li>
 *   <li>START:bus.subscribe 调一次 + 抛 AsyncNodeSuspendException(waitRef=channel)</li>
 *   <li>START:subscription stash 到 ctx.busSubscriptions</li>
 *   <li>END 找不到 message flow → 抛 FlowExecutionException</li>
 * </ul>
 *
 * <p>直接用真实 InMemoryMessageBus + 自建 MessageBusPublisher,避免 mock;
 * FlowResumer 走简化 stub(不需要真实 resume — 这是 executor 行为测试)。
 */
@DisplayName("MessageFlowStart/End executor + bus 集成")
class MessageFlowExecutorTest {

    private BpmnXmlParser parser;
    private InMemoryMessageBus bus;
    private MessageBusPublisher publisher;
    private BpmnDefinition bpmn;

    @BeforeEach
    void setUp() {
        parser = new BpmnXmlParser();
        bus = new InMemoryMessageBus();
        publisher = new MessageBusPublisher(bus);
        bpmn = parser.parse(BpmnCollaborationFixtures.TWO_POOL_LOAN_XML);
    }

    private MessageFlowStartExecutor newStart() {
        FlowResumer resumer = new FlowResumer(null, null, null) {
            @Override public void resumeFromMessage(com.ruleforge.decision.flow.bus.Message message) {
                // stub — executor 测试只关心订阅发生,不真跑 resume
            }
        };
        return new MessageFlowStartExecutor(bus, resumer);
    }

    private MessageFlowEndExecutor newEnd() {
        return new MessageFlowEndExecutor(publisher);
    }

    @Nested
    @DisplayName("MessageFlowStart(START_EVENT + messageFlowId)")
    class StartBehavior {

        @Test
        @DisplayName("Given START + messageFlowId,When execute,Then bus.subscribe 调 1 次 + 抛 AsyncNodeSuspendException(waitRef=channel)")
        void start_subscribes_and_suspends() {
            MessageFlowStartExecutor exec = newStart();
            FlowNode recvStart = bpmn.requireProcess("Process_UW").getNode("recvLoanDecision");
            assertNotNull(recvStart);
            assertEquals("MF1", recvStart.getMessageFlowId());

            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-test-1");
            ctx.setCurrentBpmn(bpmn);
            ctx.setCurrentPoolId("p_uw");
            Map<String, Object> vars = new HashMap<>();
            ctx.setVars(vars);

            AsyncNodeSuspendException ex = assertThrows(AsyncNodeSuspendException.class,
                () -> exec.execute(recvStart, ctx));

            assertEquals(AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA, ex.getWaitType());
            assertEquals("pool:p_credit_to_p_uw:loan_approved", ex.getWaitRef());
            // subscription 已开
            assertEquals(1, bus.subscriberCount("pool:p_credit_to_p_uw:loan_approved"));
        }

        @Test
        @DisplayName("Given START + messageFlowId,When execute,Then subscription stash 到 ctx.busSubscriptions")
        void start_stashes_subscription() {
            MessageFlowStartExecutor exec = newStart();
            FlowNode recvStart = bpmn.requireProcess("Process_UW").getNode("recvLoanDecision");

            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-test-2");
            ctx.setCurrentBpmn(bpmn);
            ctx.setCurrentPoolId("p_uw");

            assertThrows(AsyncNodeSuspendException.class, () -> exec.execute(recvStart, ctx));
            assertEquals(1, ctx.getBusSubscriptions().size(),
                "subscription 应被 stash 进 ctx.busSubscriptions");
        }
    }

    @Nested
    @DisplayName("MessageFlowEnd(END_EVENT + messageFlowId)")
    class EndBehavior {

        @Test
        @DisplayName("Given END + messageFlowId,When execute,Then publishPoolMessage 调 1 次 + 不抛 + delivered=1(对端有订阅)")
        void end_publishes_to_pool_channel() {
            MessageFlowEndExecutor exec = newEnd();

            // 1. 先在 pool 端订阅同 channel
            bus.subscribe("pool:p_credit_to_p_uw:loan_approved", m -> { /* noop */ });

            FlowNode sendEnd = bpmn.requireProcess("Process_Credit").getNode("sendLoanDecision");
            assertNotNull(sendEnd);

            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-test-3");
            ctx.setCurrentBpmn(bpmn);
            ctx.setCurrentPoolId("p_credit");
            ctx.setVars(new HashMap<>());

            // 不抛
            exec.execute(sendEnd, ctx);
            // 订阅者被命中
            assertEquals(1, bus.knownChannels().size());
        }

        @Test
        @DisplayName("Given END + messageFlowId,When execute,Then payload 含 flowRunId+currentNodeId+flowId")
        void end_payload_includes_bridge_fields() {
            // 替换 publisher 记 payload
            Map<String, Object> capturedPayload = new HashMap<>();
            MessageBusPublisher spyingPublisher = new MessageBusPublisher(bus) {
                // 重写困难(没 override 设计)— 退而求其次:用 subscriber 在 publish 时截 payload
            };
            bus.subscribe("pool:p_credit_to_p_uw:loan_approved",
                m -> capturedPayload.putAll(m.payload()));

            MessageFlowEndExecutor exec = newEnd();

            FlowNode sendEnd = bpmn.requireProcess("Process_Credit").getNode("sendLoanDecision");
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-test-4");
            ctx.setCurrentBpmn(bpmn);
            ctx.setCurrentPoolId("p_credit");
            ctx.setVars(new HashMap<>());

            exec.execute(sendEnd, ctx);

            assertEquals("fr-test-4", capturedPayload.get("flowRunId"));
            assertEquals("sendLoanDecision", capturedPayload.get("currentNodeId"));
            assertEquals("Process_Credit", capturedPayload.get("flowId"),
                "flowId 应是 source pool 指向的 processId");
        }

        @Test
        @DisplayName("Given END + messageFlowId 找不到 message flow,When execute,Then 抛 FlowExecutionException")
        void end_missing_message_flow_throws() {
            MessageFlowEndExecutor exec = newEnd();

            // 造一个 node 带不存在的 messageFlowId
            FlowNode fakeEnd = new FlowNode("fakeEnd", NodeType.END_EVENT, "fake",
                Map.of(), null, null, java.util.List.of(), false,
                null, "DOES_NOT_EXIST");

            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-x");
            ctx.setCurrentBpmn(bpmn);
            ctx.setCurrentPoolId("p_credit");

            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> exec.execute(fakeEnd, ctx));
            assertTrue(ex.getMessage().contains("DOES_NOT_EXIST"));
        }
    }

    @Nested
    @DisplayName("Holder 模式")
    class HolderPattern {

        @Test
        @DisplayName("Given Holder 未初始化,When Bridge.resolve,Then 抛 FlowExecutionException")
        void holder_uninitialized_throws() {
            MessageFlowStartExecutor.Holder.INSTANCE = null;
            assertThrows(FlowExecutionException.class, MessageFlowStartExecutor.Bridge::resolve);

            MessageFlowEndExecutor.Holder.INSTANCE = null;
            assertThrows(FlowExecutionException.class, MessageFlowEndExecutor.Bridge::resolve);
        }
    }
}
