package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.bus.InMemoryMessageBus;
import com.ruleforge.decision.flow.bus.MessageBusPublisher;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V5.38 C1 — SendTaskExecutor 行为规范。
 *
 * <p>5 BDD:
 * <ul>
 *   <li>有 messageRef:publishMessage 调 1 次 + 不抛 + vars 进 payload + flowRunId/currentNodeId 桥接</li>
 *   <li>无 messageRef:抛 FlowExecutionException</li>
 *   <li>空 messageRef:抛 FlowExecutionException</li>
 *   <li>channel = "message:&lt;name&gt;"(验证 channel 命名)</li>
 *   <li>vars 防御性复制(publish 后改 ctx.vars 不回灌到 payload)</li>
 * </ul>
 */
@DisplayName("SendTaskExecutor 行为")
class SendTaskExecutorTest {

    private InMemoryMessageBus bus;
    private MessageBusPublisher publisher;
    private SendTaskExecutor executor;

    @BeforeEach
    void setUp() {
        bus = new InMemoryMessageBus();
        publisher = new MessageBusPublisher(bus);
        executor = new SendTaskExecutor(publisher);
    }

    private FlowNode sendNode(String messageRef) {
        return new FlowNode("send1", NodeType.SEND_TASK, "Send Doc",
            Map.of(), null, null, java.util.List.of(), false,
            null, null, messageRef);
    }

    @Nested
    @DisplayName("正常路径")
    class HappyPath {

        @Test
        @DisplayName("Given SEND_TASK + messageRef, when execute, then publishMessage 调 1 次 + 不抛 + subscriber 收到")
        void publishes_and_returns() {
            // 先订阅 — 否则 InMemoryMessageBus.knownChannels() 不算 channel(没 subscriber)
            java.util.concurrent.atomic.AtomicInteger delivered = new java.util.concurrent.atomic.AtomicInteger();
            bus.subscribe("message:loan_approved", m -> delivered.incrementAndGet());

            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-1");
            ctx.setVars(new HashMap<>(Map.of("amount", 100)));

            executor.execute(sendNode("loan_approved"), ctx);
            // 不抛 = 通过 + 订阅者收到 1 次
            assertEquals(1, bus.knownChannels().size());
            assertEquals(1, delivered.get());
        }

        @Test
        @DisplayName("Given SEND_TASK, when execute, then channel = 'message:<name>' + vars 进 payload")
        void channel_naming_and_payload() {
            AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
            bus.subscribe("message:loan_approved", m -> captured.set(new HashMap<>(m.payload())));

            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-2");
            Map<String, Object> vars = new HashMap<>();
            vars.put("amount", 5000);
            vars.put("applicant", "alice");
            ctx.setVars(vars);

            executor.execute(sendNode("loan_approved"), ctx);

            Map<String, Object> payload = captured.get();
            assertNotNull(payload);
            assertEquals(5000, payload.get("amount"));
            assertEquals("alice", payload.get("applicant"));
            assertEquals("fr-2", payload.get("flowRunId"));
            assertEquals("send1", payload.get("currentNodeId"));
        }
    }

    @Nested
    @DisplayName("异常路径")
    class FailurePath {

        @Test
        @DisplayName("Given SEND_TASK + messageRef=null, when execute, then 抛 FlowExecutionException")
        void missing_message_ref_throws() {
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-x");
            assertThrows(FlowExecutionException.class,
                () -> executor.execute(sendNode(null), ctx));
        }

        @Test
        @DisplayName("Given SEND_TASK + messageRef='  ', when execute, then 抛 FlowExecutionException(空白视为缺)")
        void blank_message_ref_throws() {
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-x");
            assertThrows(FlowExecutionException.class,
                () -> executor.execute(sendNode("   "), ctx));
        }
    }

    @Nested
    @DisplayName("vars 防御性复制")
    class DefensiveCopy {

        @Test
        @DisplayName("Given publish 后改 ctx.vars, when 验证 payload, then payload 不变(防御性复制成功)")
        void payload_isolated_from_subsequent_var_mutation() {
            AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
            bus.subscribe("message:audit", m -> captured.set(new HashMap<>(m.payload())));

            Map<String, Object> vars = new HashMap<>();
            vars.put("v", 1);
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId("fr-3");
            ctx.setVars(vars);

            executor.execute(sendNode("audit"), ctx);

            // publish 后改 ctx.vars
            vars.put("v", 999);
            vars.put("mutated", true);

            // 收到的 payload 不应该包含 mutated / v 应仍是 1
            assertEquals(1, captured.get().get("v"));
            assertEquals(null, captured.get().get("mutated"));
        }
    }
}
