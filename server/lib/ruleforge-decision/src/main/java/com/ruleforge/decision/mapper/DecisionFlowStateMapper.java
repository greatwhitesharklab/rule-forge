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
}
