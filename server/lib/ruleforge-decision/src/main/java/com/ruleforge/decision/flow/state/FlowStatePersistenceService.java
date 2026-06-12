package com.ruleforge.decision.flow.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
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
     * V5.33 A0 — 序列化 ctx.joinArrivals 到 join_arrivals JSON,写状态行。
     * 格式: {"join_node_id": arrived_count}。
     */
    public void serializeJoinArrivals(DecisionFlowState state, Map<String, Integer> joinArrivals) {
        if (joinArrivals == null || joinArrivals.isEmpty()) {
            state.setJoinArrivals(null);
            return;
        }
        try {
            state.setJoinArrivals(MAPPER.writeValueAsString(joinArrivals));
        } catch (Exception e) {
            log.warn("Failed to serialize join_arrivals: {}", e.getMessage());
            state.setJoinArrivals("{}");
        }
    }

    /**
     * V5.33 A0 — 反序列化 join_arrivals JSON 回 ctx.joinArrivals。失败降级到空 Map。
     */
    public void deserializeJoinArrivals(DecisionFlowState state, FlowContext ctx) {
        String json = state.getJoinArrivals();
        if (json == null || json.isBlank()) {
            ctx.setJoinArrivals(new HashMap<>());
            return;
        }
        try {
            Map<String, Integer> map = MAPPER.readValue(json,
                new TypeReference<Map<String, Integer>>() {});
            ctx.setJoinArrivals(map);
        } catch (Exception e) {
            log.warn("Failed to deserialize join_arrivals for flowRunId={}: {}",
                state.getFlowRunId(), e.getMessage());
            ctx.setJoinArrivals(new HashMap<>());
        }
    }

    /**
     * 尝试抢占式锁。返回 true 表示拿到锁。
     */
    public boolean tryLock(Long id, String workerId) {
        return mapper.tryLock(id, workerId) > 0;
    }

    /**
     * V5.35 A4 — 复合原子化写 payload。
     * <p>rowVarsJson + joinArrivalsJson 是从 ctx 序列化出的 JSON 字符串,
     * 配合 {@link DecisionFlowStateMapper#updateAtomic} 单次 UPDATE 一次写。
     * 字段为 null 表示"不覆盖"(MyBatis-Plus updateStrategy=NOT_NULL 默认行为)。
     */
    public record AtomicUpdate(String rowVarsJson, String joinArrivalsJson) {}

    /**
     * V5.35 A4 — 序列化 ctx.vars + joinArrivals → JSON 字符串,调一次
     * {@link DecisionFlowStateMapper#updateAtomic} 写库。Fail-soft:
     * 序列化失败 → 写空 Map {@code "{}"},traverse 不阻断。
     *
     * <p>v0 简化:<b>不</b>覆盖 6 处 updateById 的所有字段语义;Caller 已经在 state
     * 上 setStatus / setCurrentNodeId / setErrorMessage 等业务字段,这里只负责
     * 序列化 + 一次原子化 UPDATE。
     *
     * @param state 已由 caller 设好 status / currentNodeId / errorMessage 等业务字段
     * @param ctx   流程上下文(vars 走 currentToken,joinArrivals 走 ctx 字段)
     * @return 序列化 payload(供 caller 调试 / verify)
     */
    public AtomicUpdate serializeForAtomicUpdate(DecisionFlowState state, FlowContext ctx) {
        // 1. rowVars — fail-soft
        String rowVarsJson;
        try {
            Map<String, Object> vars = ctx.getVars(); // 委托 currentToken
            rowVarsJson = vars == null || vars.isEmpty() ? null : MAPPER.writeValueAsString(vars);
        } catch (Exception e) {
            log.warn("Failed to serialize row_vars for flowRunId={}: {}",
                ctx.getFlowRunId(), e.getMessage());
            rowVarsJson = "{}";
        }

        // 2. joinArrivals — fail-soft
        String joinArrivalsJson;
        try {
            Map<String, Integer> joinArrivals = ctx.getJoinArrivals();
            joinArrivalsJson = joinArrivals == null || joinArrivals.isEmpty()
                ? null
                : MAPPER.writeValueAsString(joinArrivals);
        } catch (Exception e) {
            log.warn("Failed to serialize join_arrivals for flowRunId={}: {}",
                ctx.getFlowRunId(), e.getMessage());
            joinArrivalsJson = "{}";
        }

        // 3. 一次原子化 UPDATE
        mapper.updateAtomic(state, rowVarsJson, joinArrivalsJson);

        return new AtomicUpdate(rowVarsJson, joinArrivalsJson);
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
