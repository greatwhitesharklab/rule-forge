package com.ruleforge.decision.flow.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.executor.NodeExecutor;
import com.ruleforge.decision.flow.executor.NodeExecutorRegistry;
import com.ruleforge.decision.flow.executor.ParallelGatewayExecutor;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.ir.SequenceFlow;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 决策流 traverse 引擎(V5.33 A0 — worklist 多 token 模型)。
 *
 * <p>路由优先级(选 nextNode):
 * <ol>
 *   <li>userTask 后的 binary 决策 — 匹配 ruleforge:decisionValue</li>
 *   <li>exclusiveGateway 后的 condition — UEL 解析</li>
 *   <li>exclusiveGateway 后的 percent — 加权随机</li>
 *   <li>默认第一条 (isDefault = true 或唯一 outgoing)</li>
 * </ol>
 *
 * <p>状态机:
 * <ul>
 *   <li>起步写 PENDING → RUNNING</li>
 *   <li>抛 AsyncNodeSuspendException → WAITING_CALLBACK / PENDING_ASYNC + return</li>
 *   <li>抛 Exception → FAILED + 抛 FlowExecutionException</li>
 *   <li>traverse 完 → COMPLETED</li>
 * </ul>
 *
 * <p><b>V5.33 A0 fork/join 行为</b>(mirror Rust V5.28 P6):
 * <ul>
 *   <li>fork 在 PARALLEL_GATEWAY 上拍快照,推 N 个 sub-token 到 worklist</li>
 *   <li>join 在下游 PARALLEL_GATEWAY 上 union-merge 兄弟 token.vars(末班胜出)+ visited 集</li>
 *   <li>first Suspend/Fail 短路整 fork(返回 WAITING_CALLBACK / FAILED)</li>
 *   <li>per-token vars 隔离;session 仍共享(per-flowRunId 唯一)</li>
 * </ul>
 */
@Slf4j
@Component
public class FlowNodeRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NodeExecutorRegistry registry;
    private final ConditionEvaluator conditionEvaluator;
    /**
     * V5.35 A4 — 复合原子化写 service。null 时退回 direct mapper(测试场景)。
     * 6 处 updateById 中的 5 处(RUNNING / FAIL-after-catch / SUSPEND / FAIL / COMPLETED)
     * 走 {@code persistenceService.serializeForAtomicUpdate(state, ctx)} 单次 UPDATE;
     * PENDING 起步的 insert 走 {@code stateMapper.insert}(id 必须先存在才能 updateAtomic)。
     */
    private final com.ruleforge.decision.flow.state.FlowStatePersistenceService persistenceService;
    /** nullable — 测试场景不走 DB 时为 null(PENDING 起步 insert 路径仍走这个)。 */
    private final DecisionFlowStateMapper stateMapper;
    private final Random random = new Random();

    /**
     * 测试场景 ctor(3 参)— persistenceService 从 mapper lazy 派生(null mapper → null service)。
     */
    public FlowNodeRunner(NodeExecutorRegistry registry, ConditionEvaluator conditionEvaluator,
                          DecisionFlowStateMapper stateMapper) {
        this.registry = registry;
        this.conditionEvaluator = conditionEvaluator;
        this.stateMapper = stateMapper;
        this.persistenceService = stateMapper == null
            ? null
            : new com.ruleforge.decision.flow.state.FlowStatePersistenceService(stateMapper);
    }

    /**
     * Production ctor(4 参 Spring 注入)— 显式传 service,避免再 new 一份。
     */
    public FlowNodeRunner(NodeExecutorRegistry registry, ConditionEvaluator conditionEvaluator,
                          DecisionFlowStateMapper stateMapper,
                          com.ruleforge.decision.flow.state.FlowStatePersistenceService persistenceService) {
        this.registry = registry;
        this.conditionEvaluator = conditionEvaluator;
        this.stateMapper = stateMapper;
        this.persistenceService = persistenceService;
    }

    /**
     * V5.33 A0 — traverse 入口,worklist 多 token 模型。
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
        if (ctx.getFlowRunId() == null) {
            throw new FlowExecutionException("FlowContext.flowRunId is required");
        }

        // V5.34 A3 — 让 CompensationThrowExecutor 等需要 def 的 executor 拿到当前流程
        ctx.setCurrentDef(def);
        // A1 模式:把 registry 注入 Holder,executor 通过 Holder 拿(测试场景下用)
        com.ruleforge.decision.flow.executor.CompensationThrowExecutor.Holder.REGISTRY = this.registry;
        com.ruleforge.decision.flow.executor.CompensationThrowExecutor.Holder.DEF = def;

        // 1. 写 PENDING 状态行(stateMapper nullable for tests)
        DecisionFlowState state = upsertState(def, ctx, DecisionFlowState.STATUS_PENDING, startNodeId, null, null);

        // 2. 初始化代表 token
        if (ctx.getActiveTokens().isEmpty()) {
            Token rootToken = new Token("tok-" + UUID.randomUUID());
            rootToken.setCurrentNodeId(startNodeId);
            ctx.getActiveTokens().add(rootToken);
        }
        if (ctx.getCurrentToken() == null) {
            ctx.setCurrentToken(ctx.getActiveTokens().get(0));
        }

        // 3. worklist 主循环
        Deque<Token> worklist = new ArrayDeque<>(ctx.getActiveTokens());
        int visitedCount = 0;
        boolean forkedAtLeastOnce = false;

        while (!worklist.isEmpty()) {
            Token token = worklist.poll();
            ctx.setCurrentToken(token);
            String nodeId = token.getCurrentNodeId();

            // 推进 token 直到 terminal / suspend / fail / fork
            while (nodeId != null) {
                // 防环(per-token)
                if (token.getVisited().size() > 1000) {
                    failState(state, ctx, "Possible infinite loop: token " + token.getTokenId()
                        + " visited > 1000 nodes");
                    throw new FlowExecutionException("Possible infinite loop: visited > 1000 nodes");
                }
                if (!token.visit(nodeId)) {
                    failState(state, ctx, "Loop detected: node " + nodeId + " visited twice in token "
                        + token.getTokenId());
                    throw new FlowExecutionException("Loop detected: node " + nodeId
                        + " visited twice in token " + token.getTokenId());
                }

                FlowNode node = def.getNode(nodeId);
                if (node == null) {
                    failState(state, ctx, "Node not found: " + nodeId);
                    throw new FlowExecutionException("Node not found: " + nodeId);
                }

                ctx.setCurrentNodeId(nodeId);
                if (stateMapper != null) {
                    state.setStatus(DecisionFlowState.STATUS_RUNNING);
                    state.setCurrentNodeId(nodeId);
                    state.setCurrentNodeType(node.getType().name());
                    state.setFlowXmlVersion(def.getSourceXmlHash());
                    // V5.35 A4 — 单次 atomic UPDATE(rowVars + joinArrivals + 业务字段一次写)
                    persistenceService.serializeForAtomicUpdate(state, ctx);
                }

                NodeExecutor executor = registry.resolve(node);
                try {
                    executor.execute(node, ctx);
                } catch (AsyncNodeSuspendException ex) {
                    // V5.28 P6:first Suspend 短路整 fork
                    return onSuspend(state, ctx, token, ex);
                } catch (Exception ex) {
                    // V5.28 P6:first Fail 短路整 fork
                    if (stateMapper != null) {
                        state.setStatus(DecisionFlowState.STATUS_FAILED);
                        state.setErrorMessage(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                        // V5.35 A4 — atomic update
                        persistenceService.serializeForAtomicUpdate(state, ctx);
                    }
                    throw new FlowExecutionException(
                        "Node " + nodeId + " failed: " + ex.getMessage(), ex);
                }

                // 选下一节点
                NodeTransition transition = nextTransition(def, node, ctx, token);
                visitedCount++;

                if (transition.kind == NodeTransition.Kind.FORK) {
                    // fork: 推 N 个 sub-token 到 worklist + ctx.activeTokens(P0 fallback 退出时需要)
                    forkedAtLeastOnce = true;
                    for (String branchTarget : transition.branchTargets) {
                        Token child = token.fork("tok-" + UUID.randomUUID());
                        child.setCurrentNodeId(branchTarget);
                        worklist.add(child);
                        ctx.getActiveTokens().add(child);
                    }
                    log.info("[FORK] flowRunId={} parent={} branches={} joinTarget={}",
                        ctx.getFlowRunId(), token.getTokenId(), transition.branchTargets, transition.joinNodeId);
                    break;  // 当前 token 暂停推进
                } else if (transition.kind == NodeTransition.Kind.JOIN) {
                    // join: 计数 +1,检查是否齐
                    Integer arrived = ctx.getJoinArrivals().get(transition.joinNodeId);
                    int newCount = (arrived == null ? 0 : arrived) + 1;
                    ctx.getJoinArrivals().put(transition.joinNodeId, newCount);
                    // V5.33 A0 — 暂存到达 join 的 token
                    ctx.getJoinedTokens()
                        .computeIfAbsent(transition.joinNodeId, k -> new ArrayList<>())
                        .add(token);
                    // V5.33 A0 — joinArrivals 变更后写回 state
                    if (stateMapper != null) {
                        try {
                            state.setJoinArrivals(MAPPER.writeValueAsString(ctx.getJoinArrivals()));
                        } catch (Exception e) {
                            log.warn("Failed to serialize join_arrivals: {}", e.getMessage());
                        }
                    }

                    if (newCount < transition.expected) {
                        // 没齐,此 token 走完,等兄弟
                        log.debug("[JOIN-WAIT] flowRunId={} join={} arrived={}/{}",
                            ctx.getFlowRunId(), transition.joinNodeId, newCount, transition.expected);
                        break;
                    } else {
                        // 齐了 — 把所有已到达 join 的 token 的 vars union-merge 到 rootToken
                        // 然后跳到 join 后第一个 outgoing,visited 重置
                        Token rootToken = ctx.getActiveTokens().get(0);
                        List<Token> arrivedTokens = ctx.getJoinedTokens().get(transition.joinNodeId);
                        if (arrivedTokens != null) {
                            for (Token t : arrivedTokens) {
                                if (t != rootToken) {
                                    rootToken.unionMerge(t);
                                }
                            }
                        }
                        // 清空 worklist(齐了的 join 后续推进走 rootToken)
                        worklist.clear();
                        // 切换 currentToken = rootToken,跳到 join 后
                        ctx.setCurrentToken(rootToken);
                        rootToken.setVisited(transition.parentVisited);
                        rootToken.setCurrentNodeId(transition.joinOutgoing);
                        worklist.add(rootToken);
                        log.info("[JOIN] flowRunId={} join={} expected={} → post={}",
                            ctx.getFlowRunId(), transition.joinNodeId, transition.expected, transition.joinOutgoing);
                        break;  // 当前 token 走完;worklist 重新塞 rootToken
                    }
                } else {
                    // pass-through / end
                    nodeId = transition.nextNodeId;
                }
            }
        }

        // 4. 全部 token 都走完 — COMPLETED
        // P0 fallback: 如果没 join 节点(fork 后直接走完),各 branch 写完没 union
        // — 在退出前把 activeTokens 里的所有 token 的 vars union-merge 进 rootToken
        if (!ctx.getActiveTokens().isEmpty()) {
            Token rootToken = ctx.getActiveTokens().get(0);
            for (int i = 1; i < ctx.getActiveTokens().size(); i++) {
                rootToken.unionMerge(ctx.getActiveTokens().get(i));
            }
            ctx.setCurrentToken(rootToken);
        }

        if (stateMapper != null) {
            state.setStatus(DecisionFlowState.STATUS_COMPLETED);
            state.setCurrentNodeId(null);
            state.setCurrentNodeType(null);
            state.setProgress(1.0);
            if (state.getCreateTime() != null) {
                state.setTotalExecutionMs(System.currentTimeMillis() - state.getCreateTime().getTime());
            }
            // V5.35 A4 — atomic update(complete 收口单次写)
            persistenceService.serializeForAtomicUpdate(state, ctx);
        }
        log.info("[FLOW-COMPLETED] flowId={} flowRunId={} nodes={} forked={}",
            def.getProcessId(), ctx.getFlowRunId(), visitedCount, forkedAtLeastOnce);
        return state;
    }

    /** 决定下一节点。null 表示流程结束。 */
    public String nextNode(FlowDefinition def, FlowNode node, FlowContext ctx) {
        NodeTransition t = nextTransition(def, node, ctx, ctx.getCurrentToken());
        return t.nextNodeId;
    }

    /** V5.33 A0 — 拆出 nextTransition,fork/join 走不同分支。 */
    private NodeTransition nextTransition(FlowDefinition def, FlowNode node, FlowContext ctx, Token currentToken) {
        // end 节点 → null
        if (def.getEndNodeIds().contains(node.getNodeId())) {
            return NodeTransition.end();
        }
        if (node.getOutgoingIds().isEmpty()) {
            return NodeTransition.end();
        }

        // V5.33 A0 — fork 检测:PARALLEL_GATEWAY 且多 outgoing
        if (node.getType() == NodeType.PARALLEL_GATEWAY && node.getOutgoingIds().size() > 1) {
            // 收集 branch targets
            List<String> branches = new ArrayList<>();
            for (String outId : node.getOutgoingIds()) {
                SequenceFlow e = def.getEdge(outId);
                branches.add(e.getTargetId());
            }
            // 找 join 节点(启发式:在 def 里查唯一 in-degree = branches.size() 的 PARALLEL_GATEWAY)
            String joinNodeId = ParallelGatewayExecutor.findJoinTarget(def);
            int expected = branches.size();
            // 复制 parent visited(给 join 后用)
            Set<String> parentVisited = new HashSet<>(currentToken.getVisited());
            return NodeTransition.fork(branches, joinNodeId, expected, parentVisited);
        }

        // 1 outgoing → 简单推进
        if (node.getOutgoingIds().size() == 1) {
            String target = def.getEdge(node.getOutgoingIds().get(0)).getTargetId();
            // V5.33 A0 — join 检测:目标是 PARALLEL_GATEWAY 且不是 fork(在度 = 1 的话不是 join)
            // 简化:遍历所有 outgoing,只有当这个 single target 是 PARALLEL_GATEWAY 且 in-degree > 1 才是 join
            FlowNode targetNode = def.getNode(target);
            if (targetNode != null && targetNode.getType() == NodeType.PARALLEL_GATEWAY) {
                int inDegree = countInDegree(def, target);
                if (inDegree > 1) {
                    Set<String> parentVisited = new HashSet<>(currentToken.getVisited());
                    // join 后第一个 outgoing(若 join 是无 outgoing,终止)
                    String joinOutgoing = null;
                    if (!targetNode.getOutgoingIds().isEmpty()) {
                        joinOutgoing = def.getEdge(targetNode.getOutgoingIds().get(0)).getTargetId();
                    }
                    return NodeTransition.joinSingle(target, inDegree, joinOutgoing, parentVisited);
                }
            }
            return NodeTransition.next(target);
        }

        // 多 outgoing — 走路由
        // 1. userTask 后的 binary 决策
        if (node.getType() == NodeType.USER_TASK
            && ctx.getCurrentAwaitingField() != null
            && ctx.getVars().containsKey(ctx.getCurrentAwaitingField())) {
            Object value = ctx.getVars().get(ctx.getCurrentAwaitingField());
            String target = matchDecisionValue(def, node, String.valueOf(value));
            if (target != null) {
                ctx.setCurrentAwaitingField(null);
                return NodeTransition.next(target);
            }
        }

        // 2. condition (UEL)
        String conditionTarget = matchCondition(def, node, ctx.getVars());
        if (conditionTarget != null) return NodeTransition.next(conditionTarget);

        // 3. percent (加权随机)
        String percentTarget = matchPercent(def, node);
        if (percentTarget != null) return NodeTransition.next(percentTarget);

        // 4. default
        for (String outId : node.getOutgoingIds()) {
            SequenceFlow e = def.getEdge(outId);
            if (e.isDefault() || e.getConditionExpression() == null) {
                return NodeTransition.next(e.getTargetId());
            }
        }
        return NodeTransition.next(def.getEdge(node.getOutgoingIds().get(0)).getTargetId());
    }

    /** 数 in-degree(指向 nodeId 的 sequenceFlow 数量)。 */
    private int countInDegree(FlowDefinition def, String nodeId) {
        int n = 0;
        for (SequenceFlow e : def.getEdges()) {
            if (nodeId.equals(e.getTargetId())) n++;
        }
        return n;
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
        for (int i = node.getOutgoingIds().size() - 1; i >= 0; i--) {
            SequenceFlow e = def.getEdge(node.getOutgoingIds().get(i));
            if (e.getPercent() != null) return e.getTargetId();
        }
        return null;
    }

    private DecisionFlowState onSuspend(DecisionFlowState state, FlowContext ctx,
                                        Token token, AsyncNodeSuspendException ex) {
        // V5.33 A0:vars 委托给 currentToken;序列化由 V5.35 A4 接管
        if (stateMapper != null) {
            state.setCurrentNodeId(token.getCurrentNodeId());
            state.setWaitRef(ex.getWaitRef());
            state.setNextRetryAt(ex.getNextRetryAt() == null ? null : java.util.Date.from(ex.getNextRetryAt()));
            state.setErrorMessage("WAIT_TYPE=" + ex.getWaitType());
            state.setStatus(DecisionFlowState.STATUS_WAITING_CALLBACK);
            // V5.35 A4 — atomic update(suspend 单次写 rowVars + joinArrivals + waitRef)
            persistenceService.serializeForAtomicUpdate(state, ctx);
        }
        log.info("[FLOW-SUSPENDED] flowId={} flowRunId={} nodeId={} waitType={} waitRef={}",
            state.getFlowId(), ctx.getFlowRunId(), token.getCurrentNodeId(), ex.getWaitType(), ex.getWaitRef());
        return state;
    }

    /** 失败状态写库(null-safe)。 */
    private void failState(DecisionFlowState state, FlowContext ctx, String message) {
        if (stateMapper == null) return;
        state.setStatus(DecisionFlowState.STATUS_FAILED);
        state.setErrorMessage(message);
        // V5.35 A4 — atomic update(fail 单次写)
        persistenceService.serializeForAtomicUpdate(state, ctx);
    }

    private DecisionFlowState upsertState(FlowDefinition def, FlowContext ctx,
                                          String status, String nodeId, String error, Instant nextRetryAt) {
        // stateMapper nullable — 测试场景
        if (stateMapper == null) {
            // 构造一个内存 state,只用于 traverse 内部传递
            DecisionFlowState stub = new DecisionFlowState();
            stub.setFlowId(def.getProcessId());
            stub.setFlowRunId(ctx.getFlowRunId());
            stub.setStatus(status);
            stub.setCurrentNodeId(nodeId);
            return stub;
        }
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

    /** V5.33 A0 — nextTransition 子结构。 */
    private static class NodeTransition {
        enum Kind { END, NEXT, FORK, JOIN }
        final Kind kind;
        // NEXT
        final String nextNodeId;
        // FORK
        final List<String> branchTargets;
        final String joinNodeId;
        final int expected;
        // JOIN (from a single incoming, to a parallelGateway join)
        final String joinOutgoing;
        // 通用
        final Set<String> parentVisited;

        private NodeTransition(Kind kind, String nextNodeId,
                               List<String> branchTargets, String joinNodeId, int expected,
                               String joinOutgoing, Set<String> parentVisited) {
            this.kind = kind;
            this.nextNodeId = nextNodeId;
            this.branchTargets = branchTargets;
            this.joinNodeId = joinNodeId;
            this.expected = expected;
            this.joinOutgoing = joinOutgoing;
            this.parentVisited = parentVisited;
        }

        static NodeTransition end() {
            return new NodeTransition(Kind.END, null, null, null, 0, null, null);
        }
        static NodeTransition next(String target) {
            return new NodeTransition(Kind.NEXT, target, null, null, 0, null, null);
        }
        static NodeTransition fork(List<String> branches, String joinNodeId, int expected, Set<String> parentVisited) {
            return new NodeTransition(Kind.FORK, null, branches, joinNodeId, expected, null, parentVisited);
        }
        static NodeTransition joinSingle(String joinNodeId, int expected, String joinOutgoing, Set<String> parentVisited) {
            return new NodeTransition(Kind.JOIN, null, null, joinNodeId, expected, joinOutgoing, parentVisited);
        }
    }
}
