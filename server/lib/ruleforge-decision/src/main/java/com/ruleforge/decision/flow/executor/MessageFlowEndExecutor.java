package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.bus.MessageBusPublisher;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.BpmnDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.MessageFlow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * V5.37 B0 — BPMN §12 MessageFlow 发送端(EndEvent 节点带 messageFlowId)。
 *
 * <p>行为:
 * <ol>
 *   <li>按 {@code node.messageFlowId} 查 {@link BpmnDefinition} 找 {@link MessageFlow}</li>
 *   <li>构造 payload:vars + flowRunId + currentNodeId + flowId(给 {@code FlowResumer.resumeFromMessage} 桥接用)</li>
 *   <li>{@code messageBusPublisher.publishPoolMessage(srcPool, tgtPool, name, payload, flowRunId, currentNodeId)}</li>
 *   <li><b>不</b>抛 — 让 source flow 正常 end 到 COMPLETED</li>
 * </ol>
 */
@Slf4j
@Component
public class MessageFlowEndExecutor {

    private final MessageBusPublisher publisher;

    @Autowired
    public MessageFlowEndExecutor(@Lazy MessageBusPublisher publisher) {
        this.publisher = publisher;
    }

    public static class Holder {
        public static volatile MessageFlowEndExecutor INSTANCE;
    }

    public static class Bridge {
        public static MessageFlowEndExecutor resolve() {
            MessageFlowEndExecutor inst = Holder.INSTANCE;
            if (inst == null) {
                throw new FlowExecutionException(
                    "MessageFlowEndExecutor not initialized — call Holder.INSTANCE = ... in test setup");
            }
            return inst;
        }
    }

    public void execute(FlowNode node, FlowContext ctx) {
        String messageFlowId = node.getMessageFlowId();
        if (messageFlowId == null || messageFlowId.isBlank()) {
            throw new FlowExecutionException(
                "MessageFlowEndExecutor called on node without messageFlowId: " + node.getNodeId());
        }
        BpmnDefinition bpmn = ctx.getCurrentBpmn();
        if (bpmn == null) {
            throw new FlowExecutionException(
                "FlowContext.currentBpmn is null — MessageFlowEnd requires collaboration context");
        }
        MessageFlow mf = bpmn.findMessageFlow(messageFlowId)
            .orElseThrow(() -> new FlowExecutionException(
                "MessageFlow not found by id: " + messageFlowId
                + " (node=" + node.getNodeId() + " pool=" + ctx.getCurrentPoolId() + ")"));

        // payload 携带 flowRunId/flowId/currentNodeId/vars 4 件套 — FlowResumer.resumeFromMessage 依赖
        Map<String, Object> payload = new HashMap<>(ctx.getVars() == null ? Map.of() : ctx.getVars());
        payload.put("flowRunId", ctx.getFlowRunId());
        payload.put("flowId", bpmn.processes().values().stream()
            .filter(d -> {
                // 找到 sourcePool 所属的 processId(flowId)
                // 简化:遍历 processes,看 currentPoolId 是哪个 participant 的 processRef
                BpmnDefinition self = bpmn;
                return self.findParticipant(ctx.getCurrentPoolId())
                    .map(p -> p.getProcessRef().equals(d.getProcessId()))
                    .orElse(false);
            })
            .findFirst()
            .map(d -> d.getProcessId())
            .orElse(ctx.getCurrentPoolId() == null ? "" : ctx.getCurrentPoolId()));
        payload.put("currentNodeId", node.getNodeId());

        int delivered = publisher.publishPoolMessage(
            mf.getSourceParticipantId(), mf.getTargetParticipantId(), mf.getName(),
            payload, ctx.getFlowRunId(), node.getNodeId());

        log.info("[MSG-FLOW-END] poolId={} nodeId={} channel={} delivered={}",
            ctx.getCurrentPoolId(), node.getNodeId(), mf.channelName(), delivered);
        // 不抛 — 让 source flow 走完到 COMPLETED
    }
}
