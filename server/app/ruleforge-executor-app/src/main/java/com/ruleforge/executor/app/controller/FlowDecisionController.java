package com.ruleforge.executor.app.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.exception.DecisionAsyncPendingException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.FlowDefinitionRepo;
import com.ruleforge.decision.flow.engine.FlowEngine;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.state.FlowStatePersistenceService;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * USER_TASK 二元决策提交端点。
 * <p>
 * 业务系统拿到 DecisionResponse.asyncPending(taskRef=userTaskNodeId) 后,展示给操作员,
 * 操作员按 0/1 提交决策,本端点接收决策,恢复自建执行器把流程推到 endEvent。
 * <p>
 * 协议:POST /{root.path}/flow/decision?flowRunId=xxx&decision=0|1
 * <p>
 * 不在自建执行器引擎里跑 USER_TASK 的 evaluate 路径通过 DecisionServiceImpl 抛
 * DecisionAsyncPendingException,然后 asyncPending.waitRef 暴露 userTaskNodeId —
 * 业务系统拿到后调本端点;但 flowRunId 怎么传到业务方?目前通过 savePendingLog 写到
 * nd_decision_flow_log.resultData.asyncFlowRunId(DecisionServiceImpl.savePendingLog 已经写)。
 * 业务方查日志拿到 flowRunId,再调本端点。
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/flow")
@RequiredArgsConstructor
public class FlowDecisionController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DecisionFlowStateMapper stateMapper;
    private final FlowStatePersistenceService persistence;
    private final FlowDefinitionRepo flowDefinitionRepo;
    private final FlowEngine flowEngine;

    @PostMapping(value = "/decision")
    public Map<String, Object> submitDecision(@RequestParam String flowRunId,
                                              @RequestParam String decision) {
        if (decision == null || (!"0".equals(decision) && !"1".equals(decision))) {
            throw new FlowExecutionException("decision must be 0 or 1, got: " + decision);
        }
        long t0 = System.currentTimeMillis();
        DecisionFlowState state = stateMapper.selectByFlowRunId(flowRunId);
        if (state == null) {
            throw new FlowExecutionException("No flow state for flowRunId=" + flowRunId);
        }
        if (!DecisionFlowState.STATUS_WAITING_CALLBACK.equals(state.getStatus())
            && !DecisionFlowState.STATUS_PENDING_ASYNC.equals(state.getStatus())) {
            throw new FlowExecutionException("flowRunId=" + flowRunId
                + " is not awaiting callback (status=" + state.getStatus() + ")");
        }
        if (!"USER_TASK".equals(state.getWaitType())) {
            throw new FlowExecutionException("flowRunId=" + flowRunId
                + " is not awaiting user decision (waitType=" + state.getWaitType() + ")");
        }

        // 1. 拉 IR definition
        FlowDefinition def = flowDefinitionRepo.getOrLoad(state.getFlowId());

        // 2. 反序列化 ctx.vars
        FlowContext ctx = new FlowContext();
        ctx.setFlowRunId(flowRunId);
        ctx.setVars(persistence.deserializeVars(state));
        ctx.setCurrentNodeId(state.getCurrentNodeId());

        // 3. 把 decision 写进 vars.<decisionField>(从 payload 读字段名)
        String decisionField = readDecisionField(state);
        ctx.getVars().put(decisionField, decision);
        log.info("[FLOW-DECISION] resuming flowRunId={} from node={} decision={}->vars.{}",
            flowRunId, state.getCurrentNodeId(), decision, decisionField);

        // 4. resume 自建执行器(从 userTask 节点开始,userTaskNodeExecutor 走 binary 路由)
        DecisionFlowState finalState;
        try {
            finalState = flowEngine.resume(def, ctx, state.getCurrentNodeId());
        } catch (DecisionAsyncPendingException e) {
            // 再次挂起(嵌套 USER_TASK)— 也允许
            log.info("[FLOW-DECISION] re-suspended: flowRunId={} waitRef={}", flowRunId, e.getWaitRef());
            return Map.of("result", "PENDING", "waitRef", e.getWaitRef());
        }

        if (DecisionFlowState.STATUS_FAILED.equals(finalState.getStatus())) {
            return Map.of("result", "FAILED", "error", String.valueOf(finalState.getErrorMessage()));
        }
        log.info("[FLOW-DECISION] completed: flowRunId={} status={} took={}ms",
            flowRunId, finalState.getStatus(), System.currentTimeMillis() - t0);
        return Map.of("result", "COMPLETED", "flowRunId", flowRunId,
            "status", finalState.getStatus());
    }

    /**
     * 从 state 读 decisionField 名字 — Step 4 写 rowVars 时 decisionField 已经在 vars 里;
     * 我们的 UserTaskNodeExecutor 在设置 currentAwaitingField 时已经把字段名放在了 vars 里
     * 或者在 state 的 waitRef/row_vars metadata 里;这里从 vars 里反查。
     */
    @SuppressWarnings("unchecked")
    private String readDecisionField(DecisionFlowState state) {
        Map<String, Object> vars = persistence.deserializeVars(state);
        // currentAwaitingField 是 FlowContext 内部字段,没序列化到 rowVars;从 USER_TASK 节点的
        // BPMN 定义读 ruleforge:decisionField(IR 已经解析过),但 state 没存 raw XML。
        // 简化:从前端拿时通过 waitRef=userTaskNodeId 反查,这里用约定值"approved"作为兜底。
        // 业务方应该在 USER_TASK 节点 attrs 里写 ruleforge:decisionField,恢复时从 IR 节点读。
        // 当前实现:从 vars 的 _decisionField 键读(UserTaskNodeExecutor 写入)— 不存在则默认 "decision"。
        Object fieldFromVars = vars.get("_decisionField");
        if (fieldFromVars instanceof String s && !s.isBlank()) {
            return s;
        }
        return "decision";
    }
}
