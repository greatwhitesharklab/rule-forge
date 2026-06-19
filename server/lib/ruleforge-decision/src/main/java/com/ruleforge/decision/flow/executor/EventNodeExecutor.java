package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.EndEventKind;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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

    private final NodeExecutorRegistry registry;

    /**
     * V5.101 — {@code @Lazy} 打破构造器循环: {@link NodeExecutorRegistry} 构造器要
     * {@code List<NodeExecutor>} (含本 bean), 本 bean 构造器又要 NodeExecutorRegistry。
     * Spring 6+ 无法打破构造器循环 (即便 allow-circular-references=true, 那只对 setter/field
     * 有效)。 {@code @Lazy} 让 Spring 注入 registry 的延迟代理, 先创建 EventNodeExecutor,
     * 再创建 NodeExecutorRegistry (List 里就有 ready 的 EventNodeExecutor), 代理延迟解析到
     * 真实 registry, 循环打破。 V5.76 (PR #140) Spring-ify flow executor 引入该循环。
     */
    public EventNodeExecutor(@Lazy NodeExecutorRegistry registry) {
        this.registry = registry;
    }

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
            context.currentToken().setThrownError(e.errorRef());
            log.info("[FLOW-END] {} (error) ref={}", node.getName(), e.errorRef());
            throw new FlowExecutionException(
                "ErrorEnd at node " + node.getNodeId() + " ref=" + e.errorRef());
        } else if (kind instanceof EndEventKind.Escalation e) {
            context.currentToken().setThrownError(e.escalationRef());
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
                CompensationRunner.runHandlersForActivity(context.currentDef(), context, reg, ce.attachedTo());
            }
            // 跟 None 一样自然结束(没 throw)
        } else if (kind instanceof EndEventKind.MessageEnd me) {
            // V5.36 A6 — thrownError = "message:<name>"
            context.currentToken().setThrownError(me.errorRef());
            log.info("[FLOW-END] {} (messageEnd) ref={}", node.getNodeId(), me.errorRef());
            throw new FlowExecutionException(
                "MessageEnd at node " + node.getNodeId() + " ref=" + me.errorRef());
        } else if (kind instanceof EndEventKind.SignalEnd se) {
            // V5.36 A6 — thrownError = "signal:<name>"
            context.currentToken().setThrownError(se.errorRef());
            log.info("[FLOW-END] {} (signalEnd) ref={}", node.getNodeId(), se.errorRef());
            throw new FlowExecutionException(
                "SignalEnd at node " + node.getNodeId() + " ref=" + se.errorRef());
        }
    }

    /**
     * V5.36 A6 — 拿 NodeExecutorRegistry(Spring:构造注入;测试:Holder fallback)。
     * 跟 CompensationThrowExecutor / IntermediateEventExecutor 同套路。
     */
    private NodeExecutorRegistry resolveRegistry(FlowContext ctx) {
        // 1. Spring 注入的 registry(生产环境)
        if (registry != null) return registry;
        // 2. Holder(测试 fallback)
        return CompensationThrowExecutor.Holder.REGISTRY;
    }
}
