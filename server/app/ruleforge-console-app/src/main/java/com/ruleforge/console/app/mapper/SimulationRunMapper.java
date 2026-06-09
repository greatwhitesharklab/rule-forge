package com.ruleforge.console.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.console.app.entity.SimulationRunEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 仿真执行记录 Mapper
 */
public interface SimulationRunMapper extends BaseMapper<SimulationRunEntity> {

    @Update("UPDATE nd_simulation_run SET status = #{status}, total_compared = #{totalCompared}, " +
            "total_divergent = #{totalDivergent}, divergence_rate = #{divergenceRate}, " +
            "high_severity_count = #{highCount}, medium_severity_count = #{mediumCount}, " +
            "low_severity_count = #{lowCount}, error_message = #{errorMessage}, " +
            "updated_at = NOW() WHERE id = #{id}")
    int updateProgress(@Param("id") Long id,
                       @Param("status") String status,
                       @Param("totalCompared") int totalCompared,
                       @Param("totalDivergent") int totalDivergent,
                       @Param("divergenceRate") double divergenceRate,
                       @Param("highCount") int highCount,
                       @Param("mediumCount") int mediumCount,
                       @Param("lowCount") int lowCount,
                       @Param("errorMessage") String errorMessage);

    @Select("SELECT * FROM nd_simulation_run WHERE rule_package_path = #{path} " +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<SimulationRunEntity> selectByPackagePath(@Param("path") String rulePackagePath,
                                                   @Param("limit") int limit,
                                                   @Param("offset") int offset);
}
