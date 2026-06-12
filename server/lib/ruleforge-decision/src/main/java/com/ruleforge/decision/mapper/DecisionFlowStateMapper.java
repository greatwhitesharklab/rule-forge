package com.ruleforge.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.decision.entity.DecisionFlowState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DecisionFlowStateMapper extends BaseMapper<DecisionFlowState> {

    @Select("SELECT * FROM nd_decision_flow_state WHERE flow_run_id = #{flowRunId} LIMIT 1")
    DecisionFlowState selectByFlowRunId(@Param("flowRunId") String flowRunId);

    @Select("SELECT * FROM nd_decision_flow_state " +
            "WHERE status IN ('PENDING_ASYNC','WAITING_CALLBACK') " +
            "AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
            "AND (locked_until IS NULL OR locked_until <= NOW()) " +
            "ORDER BY id LIMIT #{limit}")
    List<DecisionFlowState> selectRecoverable(@Param("limit") int limit);

    @Update("UPDATE nd_decision_flow_state " +
            "SET locked_by = #{workerId}, locked_at = NOW(), locked_until = DATE_ADD(NOW(), INTERVAL 5 MINUTE) " +
            "WHERE id = #{id} AND (locked_until IS NULL OR locked_until <= NOW())")
    int tryLock(@Param("id") Long id, @Param("workerId") String workerId);

    /**
     * V5.35 A4 — 复合原子化写(SQL 跟 Rust V5.31 P1 PgStateStore::update_atomic 1:1 对齐)。
     * 一次 UPDATE 替代 traverse 期间多次 updateById。
     * 字段语义:
     *   - row_vars / join_arrivals: 来自 ctx 序列化
     *   - status / current_node_id / current_node_type: 业务推进字段
     *   - wait_ref / next_retry_at: suspend(W / PENDING_ASYNC)等待标记
     *   - error_message: fail 路径
     *   - progress / total_execution_ms: 进度 + 累计执行时长
     * <p>MyBatis-Plus 默认行为:null 字段不写入(用 updateStrategy=NOT_NULL),所以未设的字段保留旧值。
     */
    @Update("UPDATE nd_decision_flow_state "
        + "SET status = #{state.status}, "
        + "    current_node_id = #{state.currentNodeId}, "
        + "    current_node_type = #{state.currentNodeType}, "
        + "    row_vars = #{rowVarsJson}, "
        + "    join_arrivals = #{joinArrivalsJson}, "
        + "    error_message = #{state.errorMessage}, "
        + "    wait_ref = #{state.waitRef}, "
        + "    wait_type = #{state.waitType}, "
        + "    next_retry_at = #{state.nextRetryAt}, "
        + "    progress = #{state.progress}, "
        + "    total_execution_ms = #{state.totalExecutionMs}, "
        + "    update_time = NOW() "
        + "WHERE id = #{state.id}")
    int updateAtomic(@Param("state") DecisionFlowState state,
                     @Param("rowVarsJson") String rowVarsJson,
                     @Param("joinArrivalsJson") String joinArrivalsJson);
}
