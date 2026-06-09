package com.ruleforge.console.app.mapper.clickhouse;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Phase 8: ClickHouse 方言的规则覆盖率分析 Mapper.
 *
 * <p>方法签名与 {@link com.ruleforge.console.app.mapper.RuleCoverageMapper} 一致,
 * 表引用加 {@code final} 触发 ReplacingMergeTree 去重。
 */
public interface ChRuleCoverageMapper {

    @Select({
            "<script>",
            "SELECT rl.rule_name, rl.rule_type, fl.rule_package_path, fl.flow_id,",
            "  COUNT(*) AS fire_count,",
            "  AVG(rl.duration_ms) AS avg_duration_ms,",
            "  MAX(rl.duration_ms) AS max_duration_ms",
            " FROM nd_decision_rule_log final rl",
            " JOIN nd_decision_flow_log final fl ON rl.flow_log_id = fl.id",
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

    @Select("SELECT DISTINCT rule_name FROM nd_decision_rule_log final")
    List<String> findAllFiredRuleNames();
}
