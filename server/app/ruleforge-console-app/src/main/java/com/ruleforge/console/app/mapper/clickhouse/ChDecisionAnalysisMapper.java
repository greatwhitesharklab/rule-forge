package com.ruleforge.console.app.mapper.clickhouse;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Phase 8: ClickHouse 方言的决策日志聚合分析 Mapper.
 *
 * <p>方法签名与 {@link com.ruleforge.console.app.mapper.DecisionAnalysisMapper} 一致,
 * SQL 用 ClickHouse 方言:
 * <ul>
 *   <li>{@code formatDateTime} 替代 {@code DATE_FORMAT}</li>
 *   <li>{@code toDate} 替代 {@code DATE}</li>
 *   <li>{@code stdDevSamp} 替代 {@code STDDEV}</li>
 * </ul>
 */
public interface ChDecisionAnalysisMapper {

    @Select({
            "<script>",
            "SELECT",
            "  formatDateTime(created_at, #{dateFormat}) AS time_bucket,",
            "  COUNT(*) AS total_count,",
            "  SUM(CASE WHEN execution_status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,",
            "  SUM(CASE WHEN execution_status = 'REJECT' THEN 1 ELSE 0 END) AS reject_count,",
            "  SUM(CASE WHEN execution_status NOT IN ('SUCCESS','REJECT') THEN 1 ELSE 0 END) AS error_count,",
            "  AVG(total_time_ms) AS avg_total_time_ms,",
            "  AVG(execution_time_ms) AS avg_execution_time_ms,",
            "  AVG(load_knowledge_time_ms) AS avg_load_time_ms",
            " FROM nd_decision_flow_log final",
            " WHERE created_at &gt;= #{startTime} AND created_at &lt;= #{endTime}",
            " <if test='rulePackagePath != null'> AND rule_package_path = #{rulePackagePath} </if>",
            " <if test='flowId != null'> AND flow_id = #{flowId} </if>",
            " <if test='isGray != null'> AND is_gray = #{isGray} </if>",
            " GROUP BY time_bucket",
            " ORDER BY time_bucket ASC",
            "</script>"
    })
    List<Map<String, Object>> aggregateFlowLogTimeSeries(
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime,
            @Param("rulePackagePath") String rulePackagePath,
            @Param("flowId") String flowId,
            @Param("isGray") Boolean isGray,
            @Param("dateFormat") String dateFormat
    );

    @Select({
            "<script>",
            "SELECT",
            "  rule_package_path,",
            "  flow_id,",
            "  COUNT(*) AS total_count,",
            "  SUM(CASE WHEN execution_status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,",
            "  SUM(CASE WHEN execution_status = 'REJECT' THEN 1 ELSE 0 END) AS reject_count,",
            "  AVG(total_time_ms) AS avg_total_time_ms,",
            "  MAX(total_time_ms) AS max_total_time_ms",
            " FROM nd_decision_flow_log final",
            " WHERE created_at &gt;= #{startTime} AND created_at &lt;= #{endTime}",
            " GROUP BY rule_package_path, flow_id",
            " ORDER BY total_count DESC",
            "</script>"
    })
    List<Map<String, Object>> aggregateFlowLogByPackage(
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime
    );

    @Select({
            "<script>",
            "SELECT reject_code, reject_reason, COUNT(*) AS count",
            " FROM nd_decision_flow_log final",
            " WHERE execution_status = 'REJECT'",
            "   AND created_at &gt;= #{startTime} AND created_at &lt;= #{endTime}",
            " <if test='rulePackagePath != null'> AND rule_package_path = #{rulePackagePath} </if>",
            " GROUP BY reject_code, reject_reason",
            " ORDER BY count DESC",
            " LIMIT #{limit}",
            "</script>"
    })
    List<Map<String, Object>> aggregateRejectDistribution(
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime,
            @Param("rulePackagePath") String rulePackagePath,
            @Param("limit") int limit
    );

    @Select("SELECT DISTINCT rule_package_path FROM nd_decision_flow_log final ORDER BY rule_package_path")
    List<String> findAllPackagePaths();

    @Select({
            "<script>",
            "SELECT",
            "  AVG(CASE WHEN execution_status='SUCCESS' THEN 1.0 ELSE 0.0 END) AS success_rate,",
            "  AVG(CASE WHEN execution_status='REJECT' THEN 1.0 ELSE 0.0 END) AS reject_rate,",
            "  AVG(total_time_ms) AS avg_total_time,",
            "  COUNT(*) AS total_count",
            " FROM nd_decision_flow_log final",
            " WHERE created_at &gt;= #{startTime} AND created_at &lt;= #{endTime}",
            " <if test='rulePackagePath != null'> AND rule_package_path = #{rulePackagePath} </if>",
            "</script>"
    })
    Map<String, Object> computeCurrentWindowStats(
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime,
            @Param("rulePackagePath") String rulePackagePath
    );

    @Select({
            "<script>",
            "SELECT",
            "  AVG(success_rate) AS avg_success_rate,",
            "  AVG(reject_rate) AS avg_reject_rate,",
            "  AVG(avg_total_time) AS baseline_avg_time,",
            "  stdDevSamp(success_rate) AS stddev_success_rate,",
            "  stdDevSamp(reject_rate) AS stddev_reject_rate,",
            "  stdDevSamp(avg_total_time) AS stddev_avg_time",
            " FROM (",
            "   SELECT",
            "     toDate(created_at) AS day_bucket,",
            "     AVG(CASE WHEN execution_status='SUCCESS' THEN 1.0 ELSE 0.0 END) AS success_rate,",
            "     AVG(CASE WHEN execution_status='REJECT' THEN 1.0 ELSE 0.0 END) AS reject_rate,",
            "     AVG(total_time_ms) AS avg_total_time",
            "   FROM nd_decision_flow_log final",
            "   WHERE created_at &gt;= #{baselineStart} AND created_at &lt; #{currentStart}",
            "   <if test='rulePackagePath != null'> AND rule_package_path = #{rulePackagePath} </if>",
            "   GROUP BY toDate(created_at)",
            " ) daily_stats",
            "</script>"
    })
    Map<String, Object> computeAnomalyBaseline(
            @Param("baselineStart") Date baselineStart,
            @Param("currentStart") Date currentStart,
            @Param("rulePackagePath") String rulePackagePath
    );
}
