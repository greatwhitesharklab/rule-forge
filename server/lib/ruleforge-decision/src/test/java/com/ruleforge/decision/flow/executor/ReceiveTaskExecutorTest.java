package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.bus.FlowResumer;
import com.ruleforge.decision.flow.bus.InMemoryMessageBus;
import com.ruleforge.decision.flow.bus.MessageBusPublisher;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V5.38 C1 — ReceiveTaskExecutor 行为规范。
 *
 * <p>5 BDD:
 * <ul>
 *   <li>有 messageRef:subscribe 调 1 次 + 抛 AsyncNodeSuspendException(waitRef=channel)</li>
 *   <li>channel = "message:&lt;name&gt;"(跟 SendTask 配对)</li>
 *   <li>subscription stash 到 ctx.busSubscriptions</li>
 *   <li>无 messageRef:抛 FlowExecutionException</li>
 *   <li>flowRunId 桥接到 exception payload</li>
 * </ul>
 */
@DisplayName("ReceiveTaskExecutor 行为")
class ReceiveTaskExecutorTest {

    private InMemoryMessageBus bus;
    private MessageBusPublisher publisher;
    private FlowResumer flowResumer;
    private ReceiveTaskExecutor executor;

    @BeforeEach
    void setUp() {
        bus = new InMemoryMessageBus();
        publisher = new MessageBusPublisher(bus);
        flowResumer = new FlowResumer(null, null, null) {
            @Override public void resumeFromMessage(
                com.ruleforge.decision.flow.bus.Message message) { /* stub */ }
        };
        executor = new ReceiveTaskExecutor(bus, publisher, flowResumer);
    }

    private FlowNode receiveNode(String messageRef) {
        return new FlowNode("recv1", NodeType.RECEIVE_TASK, "Wait Callback",
            Map.of(), null, null, List.of(), false,
            null, null, messageRef);
    }

    @Nested
    @DisplayName("正常路径")
    class HappyPath {

        @Test
        @DisplayName("Given RECEIVE_TASK + messageRef, when execute, then bus.subscribe 1 次 + 抛 AsyncNodeSuspendException")
        void subscribes_and_suspends() {
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-1");

            AsyncNodeSuspendException ex = assertThrows(AsyncNodeSuspendException.class,
                () -> executor.execute(receiveNode("callback_signal"), ctx));

            assertEquals(AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA, ex.getWaitType());
            assertEquals("message:callback_signal", ex.getWaitRef());
            assertEquals(1, bus.subscriberCount("message:callback_signal"));
        }

        @Test
        @DisplayName("Given RECEIVE_TASK, when execute, then subscription stash 到 ctx.busSubscriptions")
        void subscription_stashed_in_context() {
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-2");
            assertThrows(AsyncNodeSuspendException.class,
                () -> executor.execute(receiveNode("payment_received"), ctx));
            assertEquals(1, ctx.getBusSubscriptions().size(),
                "subscription 应被 stash 进 ctx.busSubscriptions,给 Runner COMPLETED/FAIL 出口 close");
        }

        @Test
        @DisplayName("Given RECEIVE_TASK, when execute, then exception payload 含 messageRef + flowRunId")
        void exception_payload_bridge() {
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-3");

            AsyncNodeSuspendException ex = assertThrows(AsyncNodeSuspendException.class,
                () -> executor.execute(receiveNode("payment_received"), ctx));

            assertEquals("payment_received", ex.getPayload().get("messageRef"));
            assertEquals("fr-3", ex.getPayload().get("flowRunId"));
        }
    }

    @Nested
    @DisplayName("异常路径")
    class FailurePath {

        @Test
        @DisplayName("Given RECEIVE_TASK + messageRef=null, when execute, then 抛 FlowExecutionException")
        void missing_message_ref_throws() {
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-x");
            assertThrows(FlowExecutionException.class,
                () -> executor.execute(receiveNode(null), ctx));
        }

        @Test
        @DisplayName("Given RECEIVE_TASK + messageRef=空, when execute, then 抛 FlowExecutionException")
        void blank_message_ref_throws() {
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-x");
            assertThrows(FlowExecutionException.class,
                () -> executor.execute(receiveNode(""), ctx));
        }
    }
}
