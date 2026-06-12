package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.EndEventKind;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V5.34 A2 + V5.36 A6 — Start/End Event 节点执行器。
 *
 * <p>Start event:noop,只 log。
 *
 * <p>End event:按 {@link EndEventKind} 路由 8 path:
 * <ul>
 *   <li>{@link EndEventKind.None} → log + Continue(无 thrownError)</li>
 *   <li>{@link EndEventKind.Error} → ctx.thrownError = errorRef + FlowExecutionException("ErrorEnd: ref")</li>
 *   <li>{@link EndEventKind.Escalation} → ctx.thrownError = escalationRef + FlowExecutionException("EscalationEnd: ref")</li>
 *   <li>{@link EndEventKind.Terminate} → V5.30 v0 跟 Error 同 path + FlowExecutionException("Terminated")(token-kill 留 V5.31 P1)</li>
 *   <li><b>V5.36 A6</b> {@link EndEventKind.Cancel} → thrownError + FlowExecutionException("Cancelled")</li>
 *   <li><b>V5.36 A6</b> {@link EndEventKind.Compensation} → 调 {@link CompensationRunner#runHandlersForActivity}(attachedTo)</li>
 *   <li><b>V5.36 A6</b> {@link EndEventKind.MessageEnd} → thrownError = "message:&lt;name&gt;" + FlowExecutionException("MessageEnd")</li>
 *   <li><b>V5.36 A6</b> {@link EndEventKind.SignalEnd} → thrownError = "signal:&lt;name&gt;" + FlowExecutionException("SignalEnd")</li>
 * </ul>
 */
@Slf4j
@Component
public class EventNodeExecutor implements NodeExecutor {

    @Override
    public String supportedType() {
        return "EVENT";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        // V5.37 B0 — messageFlow 端点早返(START 节点带 messageFlowId → MessageFlowStartExecutor;
        // END 节点带 messageFlowId → MessageFlowEndExecutor)。不抛的 end 让 source flow 走完;
        // start 抛 AsyncNodeSuspendException 走 Runner 收口。
        if (node.getMessageFlowId() != null) {
            if (node.getType() == NodeType.START_EVENT) {
                MessageFlowStartExecutor.Bridge.resolve().execute(node, context);
                return;  // unreachable — MessageFlowStart always throws
            }
            if (node.getType() == NodeType.END_EVENT) {
                MessageFlowEndExecutor.Bridge.resolve().execute(node, context);
                return;
            }
        }

        switch (node.getType()) {
            case START_EVENT -> log.debug("[FLOW-START] {}", node.getName());
            case END_EVENT   -> handleEnd(node, context);
            default          -> log.debug("[FLOW-EVENT] {} type={}", node.getName(), node.getType());
        }
    }

    /** End event dispatcher — 按 ruleforge:endType 路由 8 path。 */
    private void handleEnd(FlowNode node, FlowContext context) {
        EndEventKind kind = EndEventKind.fromAttrs(node.getExtensionAttrs());
        if (kind instanceof EndEventKind.None) {
            log.debug("[FLOW-END] {} (normal)", node.getName());
        } else if (kind instanceof EndEventKind.Error e) {
            context.setThrownError(e.errorRef());
            log.info("[FLOW-END] {} (error) ref={}", node.getName(), e.errorRef());
            throw new FlowExecutionException(
                "ErrorEnd at node " + node.getNodeId() + " ref=" + e.errorRef());
        } else if (kind instanceof EndEventKind.Escalation e) {
            context.setThrownError(e.escalationRef());
            log.info("[FLOW-END] {} (escalation) ref={}", node.getName(), e.escalationRef());
            throw new FlowExecutionException(
                "EscalationEnd at node " + node.getNodeId() + " ref=" + e.escalationRef());
        } else if (kind instanceof EndEventKind.Terminate) {
            log.info("[FLOW-END] {} (terminate) v0: not token-kill", node.getNodeId());
            throw new FlowExecutionException(
                "Terminated at node " + node.getNodeId() + " (v0: not token-kill)");
        } else if (kind instanceof EndEventKind.Cancel) {
            // V5.36 A6 — cancel 同 terminate path,token-kill 留 V5.31 P1
            log.info("[FLOW-END] {} (cancel) v0: not token-kill", node.getNodeId());
            throw new FlowExecutionException(
                "Cancelled at node " + node.getNodeId() + " (v0: not token-kill)");
        } else if (kind instanceof EndEventKind.Compensation ce) {
            // V5.36 A6 — 跑 attachedTo activity 的 handlers(不动 stack)
            log.info("[FLOW-END] {} (compensation) attachedTo={}", node.getNodeId(), ce.attachedTo());
            NodeExecutorRegistry reg = resolveRegistry(context);
            if (reg == null) {
                log.warn("[FLOW-END] {} (compensation) no registry available, skipping handler run",
                    node.getNodeId());
            } else {
                CompensationRunner.runHandlersForActivity(context.getCurrentDef(), context, reg, ce.attachedTo());
            }
            // 跟 None 一样自然结束(没 throw)
        } else if (kind instanceof EndEventKind.MessageEnd me) {
            // V5.36 A6 — thrownError = "message:<name>"
            context.setThrownError(me.errorRef());
            log.info("[FLOW-END] {} (messageEnd) ref={}", node.getNodeId(), me.errorRef());
            throw new FlowExecutionException(
                "MessageEnd at node " + node.getNodeId() + " ref=" + me.errorRef());
        } else if (kind instanceof EndEventKind.SignalEnd se) {
            // V5.36 A6 — thrownError = "signal:<name>"
            context.setThrownError(se.errorRef());
            log.info("[FLOW-END] {} (signalEnd) ref={}", node.getNodeId(), se.errorRef());
            throw new FlowExecutionException(
                "SignalEnd at node " + node.getNodeId() + " ref=" + se.errorRef());
        }
    }

    /**
     * V5.36 A6 — 拿 NodeExecutorRegistry(Spring:ApplicationContext 拿;测试:Holder fallback)。
     * 跟 CompensationThrowExecutor / IntermediateEventExecutor 同套路。
     */
    private NodeExecutorRegistry resolveRegistry(FlowContext ctx) {
        // 1. Holder(测试 fallback)
        NodeExecutorRegistry reg = CompensationThrowExecutor.Holder.REGISTRY;
        if (reg != null) return reg;
        // 2. Spring ApplicationContext(Spring 环境)
        try {
            Object bean = com.ruleforge.Utils.getApplicationContext().getBean(NodeExecutorRegistry.class);
            if (bean instanceof NodeExecutorRegistry r) return r;
        } catch (Exception ignore) {
            // ApplicationContext 没初始化(测试场景)
        }
        return null;
    }
}
