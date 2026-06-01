package com.ruleforge.console.app.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 决策日志聚合分析 Mapper — @Select 原生 SQL 实现 GROUP BY 聚合查询
 */
public interface DecisionAnalysisMapper {

    /**
     * 时间序列聚合 — 按小时或天分桶统计调用量、成功率、拒绝率、延迟
     */
    @Select({
            "<script>",
            "SELECT",
            "  DATE_FORMAT(created_at, #{dateFormat}) AS time_bucket,",
            "  COUNT(*) AS total_count,",
            "  SUM(CASE WHEN execution_status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,",
            "  SUM(CASE WHEN execution_status = 'REJECT' THEN 1 ELSE 0 END) AS reject_count,",
            "  SUM(CASE WHEN execution_status NOT IN ('SUCCESS','REJECT') THEN 1 ELSE 0 END) AS error_count,",
            "  AVG(total_time_ms) AS avg_total_time_ms,",
            "  AVG(execution_time_ms) AS avg_execution_time_ms,",
            "  AVG(load_knowledge_time_ms) AS avg_load_time_ms",
            " FROM nd_decision_flow_log",
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

    /**
     * 按规则包/决策流汇总统计
     */
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
            " FROM nd_decision_flow_log",
            " WHERE created_at &gt;= #{startTime} AND created_at &lt;= #{endTime}",
            " GROUP BY rule_package_path, flow_id",
            " ORDER BY total_count DESC",
            "</script>"
    })
    List<Map<String, Object>> aggregateFlowLogByPackage(
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime
    );

    /**
     * 拒绝码分布 — Top-N 拒绝原因
     */
    @Select({
            "<script>",
            "SELECT reject_code, reject_reason, COUNT(*) AS count",
            " FROM nd_decision_flow_log",
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

    /**
     * 所有规则包路径 — 用于前端下拉选项
     */
    @Select("SELECT DISTINCT rule_package_path FROM nd_decision_flow_log ORDER BY rule_package_path")
    List<String> findAllPackagePaths();

    /**
     * 当前时间窗口统计 — 用于偏差检测
     */
    @Select({
            "<script>",
            "SELECT",
            "  AVG(CASE WHEN execution_status='SUCCESS' THEN 1.0 ELSE 0.0 END) AS success_rate,",
            "  AVG(CASE WHEN execution_status='REJECT' THEN 1.0 ELSE 0.0 END) AS reject_rate,",
            "  AVG(total_time_ms) AS avg_total_time,",
            "  COUNT(*) AS total_count",
            " FROM nd_decision_flow_log",
            " WHERE created_at &gt;= #{startTime} AND created_at &lt;= #{endTime}",
            " <if test='rulePackagePath != null'> AND rule_package_path = #{rulePackagePath} </if>",
            "</script>"
    })
    Map<String, Object> computeCurrentWindowStats(
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime,
            @Param("rulePackagePath") String rulePackagePath
    );

    /**
     * 历史基线统计 — 日粒度聚合后计算均值与标准差
     */
    @Select({
            "<script>",
            "SELECT",
            "  AVG(success_rate) AS avg_success_rate,",
            "  AVG(reject_rate) AS avg_reject_rate,",
            "  AVG(avg_total_time) AS baseline_avg_time,",
            "  STDDEV(success_rate) AS stddev_success_rate,",
            "  STDDEV(reject_rate) AS stddev_reject_rate,",
            "  STDDEV(avg_total_time) AS stddev_avg_time",
            " FROM (",
            "   SELECT",
            "     DATE(created_at) AS day_bucket,",
            "     AVG(CASE WHEN execution_status='SUCCESS' THEN 1.0 ELSE 0.0 END) AS success_rate,",
            "     AVG(CASE WHEN execution_status='REJECT' THEN 1.0 ELSE 0.0 END) AS reject_rate,",
            "     AVG(total_time_ms) AS avg_total_time",
            "   FROM nd_decision_flow_log",
            "   WHERE created_at &gt;= #{baselineStart} AND created_at &lt; #{currentStart}",
            "   <if test='rulePackagePath != null'> AND rule_package_path = #{rulePackagePath} </if>",
            "   GROUP BY DATE(created_at)",
            " ) daily_stats",
            "</script>"
    })
    Map<String, Object> computeAnomalyBaseline(
            @Param("baselineStart") Date baselineStart,
            @Param("currentStart") Date currentStart,
            @Param("rulePackagePath") String rulePackagePath
    );
}
