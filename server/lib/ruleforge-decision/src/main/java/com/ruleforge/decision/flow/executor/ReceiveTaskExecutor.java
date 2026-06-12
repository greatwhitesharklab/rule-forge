package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.bus.FlowResumer;
import com.ruleforge.decision.flow.bus.MessageBus;
import com.ruleforge.decision.flow.bus.MessageBusPublisher;
import com.ruleforge.decision.flow.bus.MessageKind;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * V5.38 C1 — BPMN {@code <bpmn:receiveTask>} 单 pool 内的消息接收节点。
 *
 * <p>行为:
 * <ol>
 *   <li>从 {@code node.messageRef} 取消息名(必填)</li>
 *   <li>算 channel = {@code message:<messageRef>}(走 {@link MessageKind.Message} prefix)</li>
 *   <li>{@code bus.subscribe(channel, flowResumer::resumeFromMessage)} — 桥接:对端 publish → resume</li>
 *   <li>subscription stash 到 {@code ctx.busSubscriptions} — Runner traverse
 *       在 COMPLETED/FAIL 出口 close(不 SUSPEND 关)</li>
 *   <li>抛 {@link AsyncNodeSuspendException} — Runner traverse 写 PENDING_ASYNC</li>
 * </ol>
 *
 * <p>订阅时 handler 是 {@code flowResumer::resumeFromMessage},因为
 * {@link FlowResumer#resumeFromMessage} 已经会从 payload 拿 flowRunId / flowId / currentNodeId
 * / vars 然后调 {@code engine.resume}。publisher 端在 C1 Send Task publish 时已把
 * flowRunId / currentNodeId 写进 payload。
 *
 * <p>对比 B0 {@link MessageFlowStartExecutor}:
 * <ul>
 *   <li>B0 在 START_EVENT 节点触发,channel = {@code pool:<src>_to_<tgt>:<name>}</li>
 *   <li>C1 在 RECEIVE_TASK 节点触发,channel = {@code message:<name>}</li>
 * </ul>
 */
@Slf4j
@Component
public class ReceiveTaskExecutor implements NodeExecutor {

    private final MessageBus bus;
    private final MessageBusPublisher publisher;   // 留接口 — 未来 C1+ 增强需要
    private final FlowResumer flowResumer;

    @Autowired
    public ReceiveTaskExecutor(@Lazy MessageBus bus,
                                @Lazy MessageBusPublisher publisher,
                                FlowResumer flowResumer) {
        this.bus = bus;
        this.publisher = publisher;
        this.flowResumer = flowResumer;
    }

    @Override
    public String supportedType() {
        return "RECEIVE_TASK";
    }

    @Override
    public void execute(FlowNode node, FlowContext ctx) {
        String messageRef = node.getMessageRef();
        if (messageRef == null || messageRef.isBlank()) {
            throw new FlowExecutionException(
                "ReceiveTask node " + node.getNodeId() + " missing messageRef attribute");
        }
        String channel = MessageKind.channelFor(MessageKind.Message.INSTANCE, messageRef);

        MessageBus.Subscription sub = bus.subscribe(channel, flowResumer::resumeFromMessage);
        ctx.addBusSubscription(sub);

        log.info("[RECEIVE-TASK] flowRunId={} nodeId={} messageRef={} channel={} → subscribed",
            ctx.getFlowRunId(), node.getNodeId(), messageRef, channel);

        throw new AsyncNodeSuspendException(
            node.getNodeId(), "RECEIVE_TASK",
            AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA, channel,
            Map.of(
                "messageRef", messageRef,
                "flowRunId", ctx.getFlowRunId(),
                "flowId", ctx.getCurrentBpmn() != null
                    ? "" /* 当前单 pool 走 FlowDefinitionRepo,flowId 由 engine.resume 从 state 拿 */
                    : ""
            ),
            null);
    }
}
