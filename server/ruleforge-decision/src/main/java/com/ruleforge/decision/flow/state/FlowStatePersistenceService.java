package com.ruleforge.decision.flow.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * 状态表读写封装。封装 row_vars JSON 序列化 + 锁 CAS。
 * <p>
 * 写入策略:每次状态变更都走 updateById(行数少,锁由 InnoDB 行锁处理)。
 * 抢占式锁(locked_until)用于 FlowStateRecoveryJob 抢行 — 避免两个 worker 同时恢复同一个挂起任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowStatePersistenceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DecisionFlowStateMapper mapper;

    /**
     * 写一行新状态(INSERT, 用于 PENDING 起步)。如果 flowRunId 已存在则改 UPDATE。
     */
    public DecisionFlowState upsert(DecisionFlowState state) {
        if (state.getCreateTime() == null) {
            state.setCreateTime(new java.util.Date());
        }
        DecisionFlowState existing = mapper.selectByFlowRunId(state.getFlowRunId());
        if (existing == null) {
            mapper.insert(state);
        } else {
            state.setId(existing.getId());
            mapper.updateById(state);
        }
        return state;
    }

    /**
     * 更新状态(按 flowRunId)。如果不存在则插入。
     */
    public DecisionFlowState update(DecisionFlowState state) {
        if (state.getFlowRunId() == null) {
            throw new FlowExecutionException("flowRunId is required for update");
        }
        return upsert(state);
    }

    /**
     * 序列化 ctx.vars 到 row_vars,写状态行。
     */
    public void serializeVars(DecisionFlowState state, Map<String, Object> vars) {
        if (vars == null) {
            state.setRowVars(null);
            return;
        }
        try {
            state.setRowVars(MAPPER.writeValueAsString(vars));
        } catch (Exception e) {
            log.warn("Failed to serialize row_vars: {}", e.getMessage());
            state.setRowVars("{}");
        }
    }

    /**
     * 反序列化 row_vars 到 Map。失败返回空 Map(不抛,降级)。
     */
    public Map<String, Object> deserializeVars(DecisionFlowState state) {
        String json = state.getRowVars();
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to deserialize row_vars for flowRunId={}: {}",
                state.getFlowRunId(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 尝试抢占式锁。返回 true 表示拿到锁。
     */
    public boolean tryLock(Long id, String workerId) {
        return mapper.tryLock(id, workerId) > 0;
    }

    /**
     * 释放锁。
     */
    public void releaseLock(Long id) {
        DecisionFlowState state = mapper.selectById(id);
        if (state != null) {
            state.setLockedBy(null);
            state.setLockedAt(null);
            state.setLockedUntil(null);
            mapper.updateById(state);
        }
    }
}
