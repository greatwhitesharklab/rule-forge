package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.EndEventKind;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V5.34 A2 — Start/End Event 节点执行器。
 *
 * <p>Start event:noop,只 log。
 *
 * <p>End event:按 {@link EndEventKind} 路由 4 path:
 * <ul>
 *   <li>{@link EndEventKind.None} → log + Continue(无 thrownError)</li>
 *   <li>{@link EndEventKind.Error} → ctx.thrownError = errorRef + FlowExecutionException("ErrorEnd: ref")</li>
 *   <li>{@link EndEventKind.Escalation} → ctx.thrownError = escalationRef + FlowExecutionException("EscalationEnd: ref")</li>
 *   <li>{@link EndEventKind.Terminate} → V5.30 v0 跟 Error 同 path + FlowExecutionException("Terminated")(token-kill 留 V5.31 P1)</li>
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
        switch (node.getType()) {
            case START_EVENT -> log.debug("[FLOW-START] {}", node.getName());
            case END_EVENT   -> handleEnd(node, context);
            default          -> log.debug("[FLOW-EVENT] {} type={}", node.getName(), node.getType());
        }
    }

    /** End event dispatcher — 按 ruleforge:endType 路由 4 path。 */
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
            log.info("[FLOW-END] {} (terminate) v0: not token-kill", node.getName());
            throw new FlowExecutionException(
                "Terminated at node " + node.getNodeId() + " (v0: not token-kill)");
        }
    }
}
