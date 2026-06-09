package com.ruleforge.decision.flow.state;

import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.FlowDefinitionRepo;
import com.ruleforge.decision.flow.engine.FlowEngine;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 挂起任务恢复器(替代 Flowable async-executor)。
 * <p>
 * 定时(30s)扫 nd_decision_flow_state 中 PENDING_ASYNC / WAITING_CALLBACK 行,
 * 抢到锁的:
 * 1. 校验 flow_xml_version 跟当前 FlowDefinitionRepo 是否一致
 * 2. 一致 → 调 flowEngine.resume() 推进
 * 3. 不一致 → 标 FAILED
 *
 * <p>USER_TASK(人工决策 0/1)wait_type,next_retry_at=NULL,业务方主动 POST /flow/decision,
 * 本 job 不会自动恢复 — 因为 next_retry_at 是 NULL 永远不满足条件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowStateRecoveryJob {

    private final DecisionFlowStateMapper mapper;
    private final FlowStatePersistenceService persistence;
    private final FlowDefinitionRepo repo;
    private final FlowEngine engine;

    @Value("${ruleforge.flow.worker-id:flow-recovery-1}")
    private String workerId;

    /**
     * 每 30s 扫一次,前 60s 让应用启动完。
     */
    @Scheduled(fixedDelayString = "${ruleforge.flow.recovery-interval-ms:30000}",
               initialDelayString = "${ruleforge.flow.recovery-initial-delay-ms:60000}")
    public void scan() {
        try {
            List<DecisionFlowState> candidates = mapper.selectRecoverable(20);
            if (candidates.isEmpty()) return;

            log.info("[FLOW-RECOVERY] found {} suspended flows to recover", candidates.size());
            for (DecisionFlowState state : candidates) {
                tryRecover(state);
            }
        } catch (Exception e) {
            log.error("[FLOW-RECOVERY] scan failed: {}", e.getMessage(), e);
        }
    }

    private void tryRecover(DecisionFlowState state) {
        // 1. 抢锁
        if (!persistence.tryLock(state.getId(), workerId)) {
            log.debug("[FLOW-RECOVERY] lock contended, skip flowRunId={}", state.getFlowRunId());
            return;
        }
        try {
            // 2. 校验 xml version
            FlowDefinition def = repo.getOrLoad(state.getFlowId());
            if (!def.getSourceXmlHash().equals(state.getFlowXmlVersion())) {
                log.warn("[FLOW-RECOVERY] flow_xml_version mismatch for flowRunId={}, marking FAILED",
                    state.getFlowRunId());
                state.setStatus(DecisionFlowState.STATUS_FAILED);
                state.setErrorMessage("Flow XML changed since suspend; manual intervention required");
                persistence.update(state);
                return;
            }

            // 3. 反序列化 vars → 构造 FlowContext
            FlowContext ctx = new FlowContext();
            ctx.setFlowRunId(state.getFlowRunId());
            ctx.setCurrentNodeId(state.getCurrentNodeId());
            ctx.setVars(persistence.deserializeVars(state));

            // 4. resume
            log.info("[FLOW-RECOVERY] resuming flowRunId={} from node={}",
                state.getFlowRunId(), state.getCurrentNodeId());
            engine.resume(def, ctx, state.getCurrentNodeId());
        } catch (Exception e) {
            log.error("[FLOW-RECOVERY] failed to recover flowRunId={}: {}",
                state.getFlowRunId(), e.getMessage(), e);
            state.setStatus(DecisionFlowState.STATUS_FAILED);
            state.setErrorMessage("Recovery failed: " + e.getMessage());
            state.setRetryCount(state.getRetryCount() == null ? 1 : state.getRetryCount() + 1);
            persistence.update(state);
        } finally {
            persistence.releaseLock(state.getId());
        }
    }

    /** 给测试 / 外部手动触发用的入口 */
    public void scanNow() {
        scan();
    }

    /** 给测试生成稳定 workerId 用 */
    String getWorkerId() {
        return workerId == null ? ("worker-" + UUID.randomUUID()) : workerId;
    }
}
