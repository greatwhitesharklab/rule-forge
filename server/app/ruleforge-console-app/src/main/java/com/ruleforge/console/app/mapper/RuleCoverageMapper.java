package com.ruleforge.console.app.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 规则覆盖率分析 Mapper — 规则触发频率、已触发规则名
 */
public interface RuleCoverageMapper {

    /**
     * 规则触发频率 — JOIN rule_log + flow_log 按规则名分组
     */
    @Select({
            "<script>",
            "SELECT rl.rule_name, rl.rule_type, fl.rule_package_path, fl.flow_id,",
            "  COUNT(*) AS fire_count,",
            "  AVG(rl.duration_ms) AS avg_duration_ms,",
            "  MAX(rl.duration_ms) AS max_duration_ms",
            " FROM nd_decision_rule_log rl",
            " JOIN nd_decision_flow_log fl ON rl.flow_log_id = fl.id",
            " WHERE fl.created_at &gt;= #{startTime} AND fl.created_at &lt;= #{endTime}",
            " <if test='rulePackagePath != null'> AND fl.rule_package_path = #{rulePackagePath} </if>",
            " GROUP BY rl.rule_name, rl.rule_type, fl.rule_package_path, fl.flow_id",
            " ORDER BY fire_count DESC",
            "</script>"
    })
    List<Map<String, Object>> aggregateRuleFireFrequency(
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime,
            @Param("rulePackagePath") String rulePackagePath
    );

    /**
     * 全量曾触发的规则名 — 用于覆盖率对比
     */
    @Select("SELECT DISTINCT rule_name FROM nd_decision_rule_log")
    List<String> findAllFiredRuleNames();
}
