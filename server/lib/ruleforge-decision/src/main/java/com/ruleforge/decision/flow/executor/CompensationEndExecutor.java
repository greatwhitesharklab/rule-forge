package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V5.34 A3 — {@code <bpmn:compensateEndEvent ruleforge:scopeId="..."/>} 节点执行器。
 *
 * <p>Mirror Rust V5.31 P0 {@code compensation_end.rs} 契约:
 * 1. 读 {@code ruleforge:scopeId}(缺省时用 nodeId)
 * 2. pop stack 顶(若匹配);不匹配或 stack 空 → warn + 留 stack 不动
 *
 * <p>V5.31 P0 v0 best-effort:不因 stack 形态错报错,只 warn。
 */
@Slf4j
@Component
public class CompensationEndExecutor implements NodeExecutor {

    @Override
    public String supportedType() {
        return "COMPENSATION_END";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        String scopeId = node.attr("ruleforge", "scopeId");
        if (scopeId == null || scopeId.isBlank()) {
            scopeId = node.getNodeId();
        }
        java.util.List<String> stack = context.getCompensationStack();
        if (stack.isEmpty()) {
            log.warn("[COMP-END-EMPTY] flowRunId={} nodeId={} scopeId={} — empty stack, warn + noop",
                context.getFlowRunId(), node.getNodeId(), scopeId);
            return;
        }
        // 倒序找匹配 scopeId(V5.31 P0 v0:通常栈顶匹配;若不匹配 warn + 留 stack)
        int topIdx = stack.size() - 1;
        if (scopeId.equals(stack.get(topIdx))) {
            stack.remove(topIdx);
            log.info("[COMP-END-POP] flowRunId={} nodeId={} scopeId={}, stack size={}",
                context.getFlowRunId(), node.getNodeId(), scopeId, stack.size());
        } else {
            // 倒序找前面是否匹配
            int matchIdx = -1;
            for (int i = stack.size() - 1; i >= 0; i--) {
                if (scopeId.equals(stack.get(i))) {
                    matchIdx = i;
                    break;
                }
            }
            if (matchIdx >= 0) {
                stack.remove(matchIdx);
                log.warn("[COMP-END-MID-POP] flowRunId={} nodeId={} scopeId={} — popped from non-top position (idx={})",
                    context.getFlowRunId(), node.getNodeId(), scopeId, matchIdx);
            } else {
                log.warn("[COMP-END-MISMATCH] flowRunId={} nodeId={} scopeId={} — top is '{}', warn + leave stack intact",
                    context.getFlowRunId(), node.getNodeId(), scopeId, stack.get(topIdx));
            }
        }
    }
}
