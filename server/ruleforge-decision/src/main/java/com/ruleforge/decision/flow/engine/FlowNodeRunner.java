package com.ruleforge.decision.flow.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.executor.NodeExecutor;
import com.ruleforge.decision.flow.executor.NodeExecutorRegistry;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.ir.SequenceFlow;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 决策流 traverse 引擎(单 token 模式)。
 *
 * 路由优先级(选 nextNode):
 * 1. userTask 后的 binary 决策 — 匹配 ruleforge:decisionValue
 * 2. exclusiveGateway 后的 condition — UEL 解析
 * 3. exclusiveGateway 后的 percent — 加权随机
 * 4. 默认第一条 (isDefault = true 或唯一 outgoing)
 *
 * 状态机:
 * - 起步写 PENDING → RUNNING
 * - 抛 AsyncNodeSuspendException → WAITING_CALLBACK / PENDING_ASYNC + return
 * - 抛 Exception → FAILED + 抛 FlowExecutionException
 * - traverse 完 → COMPLETED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowNodeRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NodeExecutorRegistry registry;
    private final ConditionEvaluator conditionEvaluator;
    private final DecisionFlowStateMapper stateMapper;
    private final Random random = new Random();

    /**
     * traverse 入口。写 PENDING → RUNNING,顺序遍历节点。
     *
     * @param def         流程定义
     * @param ctx         执行上下文
     * @param startNodeId 起始节点 id
     * @return 最终状态(RUNNING / WAITING_CALLBACK / COMPLETED / FAILED)
     */
    public DecisionFlowState traverse(FlowDefinition def, FlowContext ctx, String startNodeId) {
        if (startNodeId == null) {
            throw new FlowExecutionException("Flow has no start node: " + def.getProcessId());
        }

        // 1. 写 PENDING 状态行
        DecisionFlowState state = upsertState(def, ctx, DecisionFlowState.STATUS_PENDING, startNodeId, null, null);

        // 2. traverse
        String nodeId = startNodeId;
        int visitedCount = 0;
        Set<String> visited = new HashSet<>();
        while (nodeId != null) {
            if (visited.size() > 1000) {
                throw new FlowExecutionException("Possible infinite loop: visited > 1000 nodes");
            }
            if (!visited.add(nodeId)) {
                throw new FlowExecutionException("Loop detected: node " + nodeId + " visited twice");
            }

            FlowNode node = def.getNode(nodeId);
            if (node == null) {
                throw new FlowExecutionException("Node not found: " + nodeId);
            }

            ctx.setCurrentNodeId(nodeId);
            state.setStatus(DecisionFlowState.STATUS_RUNNING);
            state.setCurrentNodeId(nodeId);
            state.setCurrentNodeType(node.getType().name());
            state.setFlowXmlVersion(def.getSourceXmlHash());
            stateMapper.updateById(state);

            NodeExecutor executor = registry.resolve(node);
            try {
                executor.execute(node, ctx);
            } catch (AsyncNodeSuspendException ex) {
                return onSuspend(state, ctx, node, ex);
            } catch (Exception ex) {
                state.setStatus(DecisionFlowState.STATUS_FAILED);
                state.setErrorMessage(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                stateMapper.updateById(state);
                throw new FlowExecutionException("Node " + nodeId + " failed: " + ex.getMessage(), ex);
            }

            // 选下一节点
            nodeId = nextNode(def, node, ctx);
            visitedCount++;
        }

        // 3. 完成
        state.setStatus(DecisionFlowState.STATUS_COMPLETED);
        state.setCurrentNodeId(null);
        state.setCurrentNodeType(null);
        state.setProgress(1.0);
        state.setTotalExecutionMs(System.currentTimeMillis() - state.getCreateTime().getTime());
        stateMapper.updateById(state);
        log.info("[FLOW-COMPLETED] flowId={} flowRunId={} nodes={}",
            def.getProcessId(), ctx.getFlowRunId(), visitedCount);
        return state;
    }

    /**
     * 决定下一节点 id。null 表示流程结束。
     */
    public String nextNode(FlowDefinition def, FlowNode node, FlowContext ctx) {
        if (def.getEndNodeIds().contains(node.getNodeId())) {
            return null;
        }
        if (node.getOutgoingIds().isEmpty()) {
            return null;
        }
        if (node.getOutgoingIds().size() == 1) {
            return def.getEdge(node.getOutgoingIds().get(0)).getTargetId();
        }

        // 多 outgoing — 走路由

        // 1. userTask 后的 binary 决策
        if (node.getType() == NodeType.USER_TASK
            && ctx.getCurrentAwaitingField() != null
            && ctx.getVars().containsKey(ctx.getCurrentAwaitingField())) {
            Object value = ctx.getVars().get(ctx.getCurrentAwaitingField());
            String target = matchDecisionValue(def, node, String.valueOf(value));
            if (target != null) {
                ctx.setCurrentAwaitingField(null);  // 清除,后续节点不再走 binary
                return target;
            }
        }

        // 2. condition (UEL)
        String conditionTarget = matchCondition(def, node, ctx.getVars());
        if (conditionTarget != null) return conditionTarget;

        // 3. percent (加权随机)
        String percentTarget = matchPercent(def, node);
        if (percentTarget != null) return percentTarget;

        // 4. default (无 conditionExpression / 无 percent)
        for (String outId : node.getOutgoingIds()) {
            SequenceFlow e = def.getEdge(outId);
            if (e.isDefault() || e.getConditionExpression() == null) {
                return e.getTargetId();
            }
        }

        // 实在没有,fallback 第一条
        return def.getEdge(node.getOutgoingIds().get(0)).getTargetId();
    }

    private String matchDecisionValue(FlowDefinition def, FlowNode node, String value) {
        for (String outId : node.getOutgoingIds()) {
            SequenceFlow e = def.getEdge(outId);
            if (value.equals(e.attr("ruleforge:decisionValue"))) {
                return e.getTargetId();
            }
        }
        return null;
    }

    private String matchCondition(FlowDefinition def, FlowNode node, Map<String, Object> vars) {
        for (String outId : node.getOutgoingIds()) {
            SequenceFlow e = def.getEdge(outId);
            String expr = e.getConditionExpression();
            if (expr == null || expr.isBlank()) continue;
            try {
                if (conditionEvaluator.evaluate(expr, vars)) {
                    return e.getTargetId();
                }
            } catch (Exception ex) {
                log.warn("Condition eval failed for expr={} at edge={}: {}",
                    expr, outId, ex.getMessage());
            }
        }
        return null;
    }

    private String matchPercent(FlowDefinition def, FlowNode node) {
        int total = 0;
        boolean anyPercent = false;
        for (String outId : node.getOutgoingIds()) {
            Integer p = def.getEdge(outId).getPercent();
            if (p != null) {
                total += p;
                anyPercent = true;
            }
        }
        if (!anyPercent) return null;

        int target = random.nextInt(Math.max(total, 1));
        int cumulative = 0;
        for (String outId : node.getOutgoingIds()) {
            Integer p = def.getEdge(outId).getPercent();
            if (p != null) {
                cumulative += p;
                if (target < cumulative) return def.getEdge(outId).getTargetId();
            }
        }
        // fallback 最后一条带 percent 的
        for (int i = node.getOutgoingIds().size() - 1; i >= 0; i--) {
            SequenceFlow e = def.getEdge(node.getOutgoingIds().get(i));
            if (e.getPercent() != null) return e.getTargetId();
        }
        return null;
    }

    private DecisionFlowState onSuspend(DecisionFlowState state, FlowContext ctx,
                                        FlowNode node, AsyncNodeSuspendException ex) {
        state.setCurrentNodeId(node.getNodeId());
        state.setCurrentNodeType(node.getType().name());
        try {
            state.setRowVars(MAPPER.writeValueAsString(ctx.getVars()));
        } catch (Exception e) {
            log.warn("Failed to serialize rowVars: {}", e.getMessage());
        }
        state.setWaitRef(ex.getWaitRef());
        state.setNextRetryAt(ex.getNextRetryAt() == null ? null : java.util.Date.from(ex.getNextRetryAt()));
        // wait_type 暂存到 currentNodeType 后缀(step 2 改造加列)
        // 这里通过 errorMessage 临时传递 waitType 给上层 catch
        state.setErrorMessage("WAIT_TYPE=" + ex.getWaitType());
        state.setStatus(DecisionFlowState.STATUS_WAITING_CALLBACK);
        stateMapper.updateById(state);
        log.info("[FLOW-SUSPENDED] flowId={} flowRunId={} nodeId={} waitType={} waitRef={}",
            state.getFlowId(), ctx.getFlowRunId(), node.getNodeId(), ex.getWaitType(), ex.getWaitRef());
        return state;
    }

    private DecisionFlowState upsertState(FlowDefinition def, FlowContext ctx,
                                          String status, String nodeId, String error, Instant nextRetryAt) {
        // Phase 1 简化:每次新建一行(step 2 优化成 updateByFlowRunId)
        DecisionFlowState state = stateMapper.selectByFlowRunId(ctx.getFlowRunId());
        if (state == null) {
            state = new DecisionFlowState();
            state.setFlowId(def.getProcessId());
            state.setFlowRunId(ctx.getFlowRunId());
        }
        state.setStatus(status);
        state.setCurrentNodeId(nodeId);
        if (error != null) state.setErrorMessage(error);
        if (nextRetryAt != null) state.setNextRetryAt(java.util.Date.from(nextRetryAt));
        if (state.getId() == null) {
            stateMapper.insert(state);
        } else {
            stateMapper.updateById(state);
        }
        return state;
    }
}
