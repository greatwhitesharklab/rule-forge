package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.bus.FlowResumer;
import com.ruleforge.decision.flow.bus.MessageBus;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.BpmnDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.MessageFlow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * V5.37 B0 — BPMN §12 MessageFlow 接收端(StartEvent 节点带 messageFlowId)。
 *
 * <p>行为:
 * <ol>
 *   <li>按 {@code node.messageFlowId} 查 {@link BpmnDefinition} 找 {@link MessageFlow}</li>
 *   <li>算 channel = {@code pool:&lt;src&gt;_to_&lt;tgt&gt;:&lt;name&gt;}</li>
 *   <li>{@code bus.subscribe(channel, flowResumer::resumeFromMessage)} — 桥接:对端 publish → resume</li>
 *   <li>subscription stash 到 {@code ctx.busSubscriptions} — Runner traverse
 *       在 COMPLETED/FAIL 出口 close(不 SUSPEND 关)</li>
 *   <li>抛 {@link AsyncNodeSuspendException} — Runner traverse 写 PENDING_ASYNC</li>
 * </ol>
 *
 * <p>测试场景用 {@link Holder} 显式拿依赖,跟 CompensationThrowExecutor 同套路。
 */
@Slf4j
@Component
public class MessageFlowStartExecutor {

    private final MessageBus bus;
    private final FlowResumer flowResumer;

    @Autowired
    public MessageFlowStartExecutor(@Lazy MessageBus bus, FlowResumer flowResumer) {
        this.bus = bus;
        this.flowResumer = flowResumer;
    }

    /**
     * Holder — 测试场景用(Spring @Component 注入的依赖可能没初始化)。
     * 跟 {@code CompensationThrowExecutor.Holder} 同套路,显式 lazy fallback。
     */
    public static class Holder {
        public static volatile MessageFlowStartExecutor INSTANCE;
    }

    /**
     * 走 Holder 调(给 EventNodeExecutor entry 早返用 — 它不会 Spring inject)。
     * 实际生产路径走 Spring @Component 注入。
     */
    public static class Bridge {
        public static MessageFlowStartExecutor resolve() {
            MessageFlowStartExecutor inst = Holder.INSTANCE;
            if (inst == null) {
                throw new FlowExecutionException(
                    "MessageFlowStartExecutor not initialized — call Holder.INSTANCE = ... in test setup");
            }
            return inst;
        }
    }

    /**
     * 节点执行入口(被 EventNodeExecutor 早返调)。
     *
     * @return 永远 throw AsyncNodeSuspendException(正常路径)
     */
    public void execute(FlowNode node, FlowContext ctx) {
        String messageFlowId = node.getMessageFlowId();
        if (messageFlowId == null || messageFlowId.isBlank()) {
            throw new FlowExecutionException(
                "MessageFlowStartExecutor called on node without messageFlowId: " + node.getNodeId());
        }
        BpmnDefinition bpmn = ctx.getCurrentBpmn();
        if (bpmn == null) {
            throw new FlowExecutionException(
                "FlowContext.currentBpmn is null — MessageFlowStart requires collaboration context");
        }
        MessageFlow mf = bpmn.findMessageFlow(messageFlowId)
            .orElseThrow(() -> new FlowExecutionException(
                "MessageFlow not found by id: " + messageFlowId
                + " (node=" + node.getNodeId() + " pool=" + ctx.getCurrentPoolId() + ")"));

        String channel = mf.channelName();
        MessageBus.Subscription sub = bus.subscribe(channel, flowResumer::resumeFromMessage);
        ctx.addBusSubscription(sub);

        log.info("[MSG-FLOW-START] poolId={} nodeId={} channel={} → subscribed",
            ctx.getCurrentPoolId(), node.getNodeId(), channel);

        throw new AsyncNodeSuspendException(
            node.getNodeId(), "MESSAGE_FLOW_START",
            AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA, channel,
            Map.of(
                "messageFlowId", messageFlowId,
                "targetPool", mf.getTargetParticipantId()
            ),
            null);
    }
}
