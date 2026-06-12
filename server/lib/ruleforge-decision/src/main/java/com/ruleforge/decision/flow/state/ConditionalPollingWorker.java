package com.ruleforge.decision.flow.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.flow.engine.ConditionEvaluator;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.FlowDefinitionRepo;
import com.ruleforge.decision.flow.engine.FlowEngine;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V5.36 A7 — Conditional Intermediate Event 轮询 worker。
 *
 * <p>Mirror V5.32 补充契约:Conditional intermediate event 挂起时抛
 * {@code AsyncNodeSuspendException(waitRef="conditional:<nodeId>")}。除了业务方
 * 主动写 {@code current_awaiting_value} 恢复,本 worker 定时扫
 * {@code wait_ref LIKE 'conditional:%'} 的行,重新求值条件:
 * <ul>
 *   <li>条件 evaluate = true → 标 COMPLETED + 调 {@link FlowEngine#resume} 推进</li>
 *   <li>条件 evaluate = false → 留 PENDING_ASYNC(下次再扫)</li>
 *   <li>条件 evaluate 抛错 / vars 缺 {@code __condition__} → 留 PENDING_ASYNC + log warn(不阻断)</li>
 *   <li>非 {@code conditional:*} waitRef(timer / message / signal / userTask)→ 跳过(留给 {@link FlowStateRecoveryJob})</li>
 * </ul>
 *
 * <p>条件文本存哪?V5.32 Rust v0 不把条件文本存进 row_vars — Java 端为了 polling
 * worker 能重新求值,需要在 IntermediateEventExecutor 挂起时把 condition 写到
 * {@code row_vars.__condition__}。{@code AsyncNodeSuspendException.getPayload()}
 * 拿到的 condition 通过 {@link FlowNodeRunner} 的 onSuspend → persistence 序列化时
 * 已经放进 row_vars(由 V5.33 A0 existing path 处理)。
 *
 * <p>v0 简化:本 worker 不抢锁(condition polling 是 idempotent,就算多个 worker 同事
 * 推同一行也没事 — second worker 看到的 state 已经是 COMPLETED,会跳过)。
 */
@Slf4j
@Component
public class ConditionalPollingWorker {

    /** waitRef 前缀 = conditional intermediate event。 */
    public static final String CONDITIONAL_PREFIX = "conditional:";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DecisionFlowStateMapper mapper;
    private final FlowStatePersistenceService persistence;
    private final FlowDefinitionRepo repo;
    private final FlowEngine engine;
    private final ConditionEvaluator evaluator;

    @Value("${ruleforge.flow.worker-id:flow-recovery-1}")
    private String workerId;

    public ConditionalPollingWorker(DecisionFlowStateMapper mapper,
                                     FlowStatePersistenceService persistence,
                                     FlowDefinitionRepo repo,
                                     FlowEngine engine,
                                     ConditionEvaluator evaluator) {
        this.mapper = mapper;
        this.persistence = persistence;
        this.repo = repo;
        this.engine = engine;
        this.evaluator = evaluator;
    }

    /**
     * 每 30s 扫一次(跟 {@link FlowStateRecoveryJob} 同节奏,initial 60s 让应用启动)。
     * 复用 {@code mapper.selectRecoverable(20)} — 已包括 PENDING_ASYNC / WAITING_CALLBACK。
     */
    @Scheduled(fixedDelayString = "${ruleforge.flow.conditional-polling-interval-ms:30000}",
               initialDelayString = "${ruleforge.flow.conditional-polling-initial-delay-ms:60000}")
    public void scan() {
        try {
            List<DecisionFlowState> candidates = mapper.selectRecoverable(20);
            if (candidates.isEmpty()) return;
            log.info("[COND-POLL] scanning {} candidates", candidates.size());
            for (DecisionFlowState state : candidates) {
                try {
                    pollOnce(state);
                } catch (Exception e) {
                    log.warn("[COND-POLL] failed to poll flowRunId={}: {}",
                        state.getFlowRunId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[COND-POLL] scan failed: {}", e.getMessage(), e);
        }
    }

    /** 给测试用的入口(单条 state poll)。 */
    public void pollOnce() {
        List<DecisionFlowState> candidates = mapper.selectRecoverable(20);
        for (DecisionFlowState state : candidates) {
            try {
                pollOnce(state);
            } catch (Exception e) {
                log.warn("[COND-POLL] failed to poll flowRunId={}: {}",
                    state.getFlowRunId(), e.getMessage());
            }
        }
    }

    /** 单条 state 评估 + (可能)resume。 */
    private void pollOnce(DecisionFlowState state) {
        String waitRef = state.getWaitRef();
        if (waitRef == null || !waitRef.startsWith(CONDITIONAL_PREFIX)) {
            // 不是 conditional:跳过(留给 FlowStateRecoveryJob)
            return;
        }

        // 1. 拿 vars(从 row_vars 反序列化) + 抽出 __condition__
        Map<String, Object> vars = persistence.deserializeVars(state);
        Object condObj = vars.get("__condition__");
        if (!(condObj instanceof String condition) || condition.isBlank()) {
            // 没 condition 文本 — 不该被本 worker 接管;留挂
            log.debug("[COND-POLL] flowRunId={} missing __condition__ in vars, skip", state.getFlowRunId());
            return;
        }

        // 2. 重新求值
        boolean satisfied;
        try {
            satisfied = evaluator.evaluate(condition, vars);
        } catch (Exception e) {
            log.warn("[COND-POLL] flowRunId={} condition eval failed: {}, keep pending",
                state.getFlowRunId(), e.getMessage());
            return;
        }

        if (!satisfied) {
            log.debug("[COND-POLL] flowRunId={} condition '{}' still false, keep pending",
                state.getFlowRunId(), condition);
            return;
        }

        // 3. 条件 true → resume
        log.info("[COND-POLL] flowRunId={} condition '{}' satisfied → resume", state.getFlowRunId(), condition);
        try {
            FlowDefinition def = repo.getOrLoad(state.getFlowId());
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId(state.getFlowRunId());
            ctx.setCurrentNodeId(state.getCurrentNodeId());
            ctx.setVars(vars);
            persistence.deserializeJoinArrivals(state, ctx);
            engine.resume(def, ctx, state.getCurrentNodeId());
        } catch (Exception e) {
            log.error("[COND-POLL] flowRunId={} resume failed: {}", state.getFlowRunId(), e.getMessage(), e);
        }
    }

    /** 给测试生成稳定 workerId 用 */
    String getWorkerId() {
        return workerId == null ? ("worker-" + UUID.randomUUID()) : workerId;
    }
}
