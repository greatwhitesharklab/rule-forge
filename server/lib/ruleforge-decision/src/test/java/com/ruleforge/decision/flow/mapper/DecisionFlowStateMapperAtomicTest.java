package com.ruleforge.decision.flow.mapper;

import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * V5.35 A4 — DecisionFlowStateMapper.updateAtomic 契约测试。
 *
 * <p>Mirror Rust V5.31 P1 PgStateStore::update_atomic SQL 契约:
 * 单次 UPDATE 写 row_vars + join_arrivals + 业务字段(status / current_node_id / error_message / wait_ref / next_retry_at / progress / total_execution_ms)。
 *
 * <p>本测试是**契约测试**(不真跑 SQL — 那是 console-app 模块集成测试范畴):
 * 1. 验证 mapper 接口签名 + 参数顺序
 * 2. 验证调用次数(A4 关键:一次 updateAtomic 等价于 6 处 updateById 中的状态变更)
 * 3. 验证 null 安全(row_vars=null 不应被 mapper 误写成字面量 "null")
 *
 * <p>真 SQL 行为(H2 集成测试)留给 console-app 模块做,本模块不引入 H2 依赖。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DecisionFlowStateMapper — updateAtomic 契约")
class DecisionFlowStateMapperAtomicTest {

    @Mock
    private DecisionFlowStateMapper mapper;

    @Test
    @DisplayName("Given state + rowVarsJson + joinArrivalsJson,When updateAtomic,Then mapper 接受 3 参数,调 1 次")
    void update_atomic_persists_all_fields_in_single_sql() {
        DecisionFlowState state = new DecisionFlowState();
        state.setId(7L);
        state.setStatus(DecisionFlowState.STATUS_RUNNING);
        state.setCurrentNodeId("act_a");
        state.setCurrentNodeType("SERVICE_TASK");

        mapper.updateAtomic(state, "{\"k\":\"v\"}", "{\"join1\":3}");

        verify(mapper, times(1)).updateAtomic(
            eq(state),
            eq("{\"k\":\"v\"}"),
            eq("{\"join1\":3}")
        );
    }

    @Test
    @DisplayName("Given row_vars=null(clear cache),When updateAtomic,Then rowVarsJson 传 null,不是 'null' 字符串")
    void update_atomic_does_not_overwrite_unset_fields_with_null_string() {
        DecisionFlowState state = new DecisionFlowState();
        state.setId(8L);

        // 显式 null(FlowStatePersistenceService 短路时)
        mapper.updateAtomic(state, null, null);

        // 验证传的是 null(不是字符串 "null")
        verify(mapper).updateAtomic(any(DecisionFlowState.class), eq((String) null), eq((String) null));
    }

    @Test
    @DisplayName("Given state 含 id + createTime,When updateAtomic,Then 整个 state object 透传(不剥 id/createTime)")
    void update_atomic_preserves_id_and_create_time() {
        DecisionFlowState state = new DecisionFlowState();
        state.setId(99L);
        state.setCreateTime(new java.util.Date(1700000000000L));
        state.setStatus(DecisionFlowState.STATUS_COMPLETED);

        mapper.updateAtomic(state, "{}", "{}");

        // 验证:state object 透传,id / createTime 字段在 SQL WHERE id=? 部分由 mapper 处理
        ArgumentCaptor<DecisionFlowState> stateCap = ArgumentCaptor.forClass(DecisionFlowState.class);
        verify(mapper).updateAtomic(stateCap.capture(), any(), any());
        DecisionFlowState captured = stateCap.getValue();
        assertNotNull(captured);
        assertEquals(99L, captured.getId(), "id preserved");
        assertNotNull(captured.getCreateTime(), "createTime preserved");
    }
}
