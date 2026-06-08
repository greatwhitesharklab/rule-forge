package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FlowEngine 决策流执行入口。
 * <p>
 * start(flowId, ctx):新 evaluate 调,从 startNodeId 开始 traverse
 * resume(def, ctx, resumeNodeId):从 userTask 恢复时调,从指定节点继续
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowEngine {

    private final FlowDefinitionRepo repo;
    private final FlowNodeRunner runner;

    public DecisionFlowState start(String flowId, FlowContext ctx) {
        if (ctx.getFlowRunId() == null) {
            throw new FlowExecutionException("FlowContext.flowRunId is required");
        }
        FlowDefinition def = repo.getOrLoad(flowId);
        return runner.traverse(def, ctx, def.getStartNodeId());
    }

    public DecisionFlowState resume(FlowDefinition def, FlowContext ctx, String resumeNodeId) {
        if (resumeNodeId == null) {
            throw new FlowExecutionException("resumeNodeId is required");
        }
        return runner.traverse(def, ctx, resumeNodeId);
    }

    public DecisionFlowState resume(String flowId, FlowContext ctx, String resumeNodeId) {
        FlowDefinition def = repo.getOrLoad(flowId);
        return resume(def, ctx, resumeNodeId);
    }
}
