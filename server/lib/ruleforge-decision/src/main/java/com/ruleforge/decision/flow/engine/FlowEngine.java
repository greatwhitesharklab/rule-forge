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
 * start(flowId, participantId, ctx):V5.37 B0 多池启动,按 participantId 找 process
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

    /**
     * V5.37 B0 — 多池启动入口。从 repo.getOrLoadBpmn 拿 BpmnDefinition,按 participantId
     * 找 process,delegate 到 runner.traverse(BpmnDefinition, ctx, participantId, startNodeId)。
     */
    public DecisionFlowState start(String flowId, String participantId, FlowContext ctx) {
        if (ctx.getFlowRunId() == null) {
            throw new FlowExecutionException("FlowContext.flowRunId is required");
        }
        if (participantId == null || participantId.isBlank()) {
            throw new FlowExecutionException("participantId is required for multi-pool start");
        }
        com.ruleforge.decision.flow.ir.BpmnDefinition bpmn = repo.getOrLoadBpmn(flowId);
        // startNodeId 由 participant 指向的 process.startNodeId 决定
        com.ruleforge.decision.flow.ir.Participant p = bpmn.findParticipant(participantId)
            .orElseThrow(() -> new FlowExecutionException(
                "Participant not found: " + participantId
                + (bpmn.collaboration() != null ? " in collaboration " + bpmn.collaboration().getId() : "")));
        FlowDefinition def = bpmn.requireProcess(p.getProcessRef());
        return runner.traverse(bpmn, ctx, participantId, def.getStartNodeId());
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
