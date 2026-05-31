package com.ruleforge.console.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.console.app.entity.SimulationResultEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 仿真对比结果 Mapper
 */
public interface SimulationResultMapper extends BaseMapper<SimulationResultEntity> {

    @Select("SELECT * FROM nd_simulation_result WHERE simulation_run_id = #{runId} " +
            "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<SimulationResultEntity> selectByRunId(@Param("runId") Long runId,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    @Select("SELECT divergence_severity, COUNT(*) AS cnt FROM nd_simulation_result " +
            "WHERE simulation_run_id = #{runId} GROUP BY divergence_severity")
    List<Map<String, Object>> countBySeverity(@Param("runId") Long runId);

    @Select("SELECT COUNT(*) FROM nd_simulation_result WHERE simulation_run_id = #{runId} AND has_divergence = 1")
    int countDivergent(@Param("runId") Long runId);

    @Select("SELECT COUNT(*) FROM nd_simulation_result WHERE simulation_run_id = #{runId}")
    int countByRunId(@Param("runId") Long runId);
}
