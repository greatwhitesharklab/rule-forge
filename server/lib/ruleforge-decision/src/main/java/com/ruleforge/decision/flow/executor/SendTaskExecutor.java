package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.bus.MessageBusPublisher;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * V5.38 C1 — BPMN {@code <bpmn:sendTask>} 单 pool 内的消息发送节点。
 *
 * <p>行为:
 * <ol>
 *   <li>从 {@code node.messageRef} 取消息名(必填)</li>
 *   <li>payload = ctx.vars 的快照(防御性复制)+ payload 桥接字段(flowRunId / currentNodeId)</li>
 *   <li>调 {@link MessageBusPublisher#publishMessage} — 派发到 {@code message:<name>} channel</li>
 *   <li><b>不</b>抛 — 让 source flow 走完该节点继续</li>
 * </ol>
 *
 * <p>channel 命名跟 B0 跨池 message flow 隔离:
 * <ul>
 *   <li>Send/Receive Task → {@code message:<name>}</li>
 *   <li>MessageFlow(B0 跨池)→ {@code pool:<from>_to_<to>:<name>}</li>
 * </ul>
 *
 * <p>对比 B0 {@link MessageFlowEndExecutor}:
 * <ul>
 *   <li>B0 在 END_EVENT 节点触发,ExistenceIR 端是 messageFlowId(指向 collab.messageFlow)</li>
 *   <li>C1 在 SEND_TASK 节点触发,IR 端是 messageRef(直接给 message 命名)</li>
 * </ul>
 */
@Slf4j
@Component
public class SendTaskExecutor implements NodeExecutor {

    private final MessageBusPublisher publisher;

    @Autowired
    public SendTaskExecutor(@Lazy MessageBusPublisher publisher) {
        this.publisher = publisher;
    }

    /** 节点类型 — NodeExecutorRegistry 据此路由(普通 SEND_TASK,不走 multi-instance 分支)。 */
    @Override
    public String supportedType() {
        return "SEND_TASK";
    }

    @Override
    public void execute(FlowNode node, FlowContext ctx) {
        String messageRef = node.getMessageRef();
        if (messageRef == null || messageRef.isBlank()) {
            throw new FlowExecutionException(
                "SendTask node " + node.getNodeId() + " missing messageRef attribute");
        }
        // 防御性复制 vars — publish 是 fire-and-forget,不希望 publish 后 vars 改动回灌
        Map<String, Object> payload = new HashMap<>(ctx.getVars() == null ? Map.of() : ctx.getVars());
        payload.put("flowRunId", ctx.getFlowRunId());
        payload.put("currentNodeId", node.getNodeId());

        int delivered = publisher.publishMessage(
            messageRef, payload, ctx.getFlowRunId(), node.getNodeId());

        log.info("[SEND-TASK] flowRunId={} nodeId={} messageRef={} delivered={}",
            ctx.getFlowRunId(), node.getNodeId(), messageRef, delivered);
        // 不抛 — Continue,让 Runner 走 sequenceFlow 继续
    }
}
