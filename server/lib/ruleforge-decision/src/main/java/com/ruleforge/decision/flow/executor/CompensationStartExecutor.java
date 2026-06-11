package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V5.34 A3 — {@code <bpmn:compensateStartEvent ruleforge:scopeId="..."/>} 节点执行器。
 *
 * <p>Mirror Rust V5.31 P0 {@code compensation_start.rs} 契约:
 * 1. 读 {@code ruleforge:scopeId}(缺省时用 nodeId)
 * 2. push scopeId 到 ctx.compensationStack
 * 3. consecutive duplicate same id → warn + skip(幂等,防止 scope 嵌套错位)
 *
 * <p>v0 简化:不注册 handlers(handler 注册在 {@link CompensationThrow} 时遍历
 * {@code def.attachedCompensations})。
 */
@Slf4j
@Component
public class CompensationStartExecutor implements NodeExecutor {

    @Override
    public String supportedType() {
        return "COMPENSATION_START";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        String scopeId = node.attr("ruleforge", "scopeId");
        if (scopeId == null || scopeId.isBlank()) {
            // V5.31 P0 兜底:无 scopeId 用 nodeId
            scopeId = node.getNodeId();
        }
        java.util.List<String> stack = context.getCompensationStack();
        // 幂等:栈顶是同一个 scopeId 就 warn + skip
        if (!stack.isEmpty() && scopeId.equals(stack.get(stack.size() - 1))) {
            log.warn("[COMP-START-DUP] flowRunId={} nodeId={} scopeId={} — consecutive duplicate, skipping push",
                context.getFlowRunId(), node.getNodeId(), scopeId);
            return;
        }
        stack.add(scopeId);
        log.info("[COMP-START] flowRunId={} nodeId={} scopeId={}, stack size={}",
            context.getFlowRunId(), node.getNodeId(), scopeId, stack.size());
    }
}
