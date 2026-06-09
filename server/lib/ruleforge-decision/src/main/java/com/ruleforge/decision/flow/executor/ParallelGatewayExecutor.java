package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parallel Gateway 节点执行器(简化版 join-all)。
 * <p>
 * Phase 1 简化:流程定义里不允许真正并行 — 一个 token 跑到底,parallelGateway 当 noop。
 * 等后续 phase 扩展为完整 token 机制。
 */
@Slf4j
@Component
public class ParallelGatewayExecutor implements NodeExecutor {

    @Override
    public String supportedType() {
        return "PARALLEL_GATEWAY";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        log.debug("[PARALLEL-GATEWAY] {} (simplified: noop, single-token execution)", node.getName());
    }
}
