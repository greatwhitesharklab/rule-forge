package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.ConditionEvaluator;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.FlowNodeRunner;
import com.ruleforge.decision.flow.engine.Token;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.SequenceFlow;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V5.34 A3 — 共享 compensation handler runner。
 *
 * <p>Mirror Rust V5.31 P0 {@code compensation.rs::run_handlers} 契约。
 *
 * <p>职责:从一个 {@code CompensationThrow} 节点触发后,
 * 1. pop innermost scope(栈顶)
 * 2. 遍历 {@code def.attachedCompensations} 倒序(activity + handler 倒序),跳过已 dedup 的 pair
 * 3. 对每个 pair,找到 handler node 的 outgoing 第一个 target 节点
 * 4. 跑 mini-traverse(handler sub-flow);vars union-merge 回 outer ctx
 * 5. handler failure → 累积到 {@link CompensationTrace}.failures,继续下一个
 * 6. handler suspend → 透传 AsyncNodeSuspendException(外层 traverse catch 写 WAITING_CALLBACK)
 * 7. 全部跑完 → 返回 trace
 *
 * <p>v0 简化(跟 Rust 端一致):保守地跑所有声明的 handler(不区分 activity 是否 completed)。
 */
@Slf4j
public final class CompensationRunner {

    private CompensationRunner() {}

    /**
     * 跑 innermost scope 的 compensation handlers。
     *
     * @param def     流程定义
     * @param ctx     outer 流程上下文
     * @param reg     节点执行器注册中心
     * @return handler 跑完的 trace(failures 列表)
     * @throws FlowExecutionException stack 空时报 "CompensationNoScope" 错
     * @throws AsyncNodeSuspendException handler sub-flow 抛 Suspend 时透传
     */
    public static CompensationTrace runHandlers(FlowDefinition def, FlowContext ctx,
                                                NodeExecutorRegistry reg) {
        CompensationTrace trace = new CompensationTrace();

        // V5.31 P0 v0 — pop 栈顶 scope(throw 是 BPMN "current scope" 默认)
        List<String> stack = ctx.getCompensationStack();
        if (stack.isEmpty()) {
            throw new FlowExecutionException(
                "CompensationThrow with no open scope (empty stack) at flowRunId=" + ctx.getFlowRunId());
        }
        String poppedScope = stack.remove(stack.size() - 1);
        log.debug("[COMP-POP] flowRunId={} popped scope={}, remaining={}",
            ctx.getFlowRunId(), poppedScope, stack.size());

        // 收集候选 handler pairs(活动 + handler ids,倒序,跳过已 dedup 的)
        List<HandlerPair> handlers = collectHandlerPairs(def, ctx);
        log.debug("[COMP-CAND] flowRunId={} candidates={}", ctx.getFlowRunId(), handlers.size());

        for (HandlerPair pair : handlers) {
            // 先记 dedup,即使后续 handler 失败也不重跑(resume 透传 Suspend 时也跳过)
            String key = pair.activityId + "::" + pair.handlerNodeId;
            ctx.getCompensatedHandlers().add(key);

            String startNodeId = resolveSubFlowStart(def, pair.handlerNodeId);
            if (startNodeId == null) {
                log.warn("[COMP-SKIP] handler {} has no outgoing target, skipping", pair.handlerNodeId);
                continue;
            }
            log.debug("[COMP-RUN] activityId={} handlerNodeId={} startNodeId={}",
                pair.activityId, pair.handlerNodeId, startNodeId);

            try {
                runHandlerSubFlow(def, startNodeId, ctx, reg, pair);
            } catch (AsyncNodeSuspendException ex) {
                // V5.31 P0 — handler sub-flow suspend 透传,outer traverse catch 写 WAITING_CALLBACK
                log.info("[COMP-SUSPEND] handler {} suspended, propagating", pair.handlerNodeId);
                throw ex;
            } catch (Exception ex) {
                log.warn("[COMP-FAIL] handler {} failed: {}, continuing with next",
                    pair.handlerNodeId, ex.getMessage());
                trace.failures.add(pair.handlerNodeId + ": " + ex.getMessage());
            }
        }
        return trace;
    }

    /** 倒序遍历 attachedCompensations(activity 倒序 + handler 倒序),跳过 dedup 已记录的 pair。 */
    private static List<HandlerPair> collectHandlerPairs(FlowDefinition def, FlowContext ctx) {
        List<HandlerPair> out = new ArrayList<>();
        Map<String, List<String>> attached = def.getAttachedCompensations();
        if (attached == null || attached.isEmpty()) return out;
        // activity 倒序(LinkedHashMap 倒序遍历用 new ArrayList 反转)
        List<String> activityIds = new ArrayList<>(attached.keySet());
        Collections.reverse(activityIds);
        for (String activityId : activityIds) {
            List<String> handlerIds = attached.get(activityId);
            List<String> reversed = new ArrayList<>(handlerIds);
            Collections.reverse(reversed);
            for (String handlerId : reversed) {
                String key = activityId + "::" + handlerId;
                if (ctx.getCompensatedHandlers().contains(key)) continue;
                out.add(new HandlerPair(activityId, handlerId));
            }
        }
        return out;
    }

    /** 找 handler 节点 outgoing 第一个 target 节点 id;handler 自己没 outgoing 时返回 null。 */
    private static String resolveSubFlowStart(FlowDefinition def, String handlerNodeId) {
        FlowNode handler = def.getNode(handlerNodeId);
        if (handler == null || handler.getOutgoingIds().isEmpty()) return null;
        SequenceFlow first = def.getEdge(handler.getOutgoingIds().get(0));
        return first == null ? null : first.getTargetId();
    }

    /**
     * 跑 handler sub-flow(独立 ctx, vars 从 outer 克隆,compensationStack 留空,
     * sub-token 推到 worklist)。vars union-merge 回 outer ctx。
     */
    private static void runHandlerSubFlow(FlowDefinition def, String startNodeId,
                                          FlowContext outerCtx, NodeExecutorRegistry reg,
                                          HandlerPair pair) {
        FlowContext subCtx = new FlowContext();
        subCtx.setFlowRunId(outerCtx.getFlowRunId());
        subCtx.setSession(outerCtx.getSession());
        subCtx.setOutputModel(outerCtx.getOutputModel());
        subCtx.setInsertedEntities(outerCtx.getInsertedEntities());

        Token subToken = new Token("tok-comp-" + UUID.randomUUID());
        subToken.setCurrentNodeId(startNodeId);
        subCtx.getActiveTokens().add(subToken);
        subCtx.setCurrentToken(subToken);
        // 关键:setCurrentToken **之后** setVars,这样 vars 写到 subToken.vars
        // (FlowContext.setVars 委托给 currentToken,若 currentToken==null 会写到 ctx 字段,导致后续 traverse 看不到)
        subCtx.setVars(new java.util.HashMap<>(outerCtx.getVars()));
        // compensationStack 留空(handler sub-flow 不递归 compensation,v0 简化)

        // stateMapper=null 走 stub(state 不持久化;handler sub-flow 失败就只 log,不影响 outer 状态)
        FlowNodeRunner runner = new FlowNodeRunner(reg, new ConditionEvaluator(), null);
        runner.traverse(def, subCtx, startNodeId);

        // union-merge sub-flow 写回的 vars 进 outer ctx(同 key 末班胜出)
        if (subCtx.getCurrentToken() != null) {
            outerCtx.getVars().putAll(subCtx.getCurrentToken().getVars());
        } else {
            outerCtx.getVars().putAll(subCtx.getVars());
        }
    }

    /** handler(activity, handler_node) pair。 */
    private record HandlerPair(String activityId, String handlerNodeId) {}

    /** 跑完返回的 trace(failures 列表)。 */
    public static final class CompensationTrace {
        public final List<String> failures = new ArrayList<>();
    }
}
