package com.ruleforge.console.app.service.impl;

import com.ruleforge.console.app.mapper.DecisionAnalysisMapper;
import com.ruleforge.console.app.mapper.RuleCoverageMapper;
import com.ruleforge.console.app.mapper.clickhouse.ChDecisionAnalysisMapper;
import com.ruleforge.console.app.mapper.clickhouse.ChRuleCoverageMapper;
import com.ruleforge.console.app.service.IAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 决策日志聚合分析服务实现
 */
@Slf4j
@Service
public class AnalysisServiceImpl implements IAnalysisService {

    private final DecisionAnalysisMapper decisionAnalysisMapper;
    private final RuleCoverageMapper ruleCoverageMapper;

    @Autowired(required = false)
    private ChDecisionAnalysisMapper chAnalysisMapper;

    @Autowired(required = false)
    private ChRuleCoverageMapper chRuleCoverageMapper;

    @Value("${clickhouse.analytics.enabled:true}")
    private boolean chEnabled;

    private static final String DATE_FORMAT_HOURLY = "%Y-%m-%d %H:00";
    private static final String DATE_FORMAT_DAILY = "%Y-%m-%d";

    public AnalysisServiceImpl(DecisionAnalysisMapper decisionAnalysisMapper,
                                RuleCoverageMapper ruleCoverageMapper) {
        this.decisionAnalysisMapper = decisionAnalysisMapper;
        this.ruleCoverageMapper = ruleCoverageMapper;
    }

    @Override
    public Map<String, Object> getFlowLogTimeSeries(Date startTime, Date endTime,
                                                     String rulePackagePath, String flowId,
                                                     Boolean isGray, String granularity) {
        String dateFormat = "daily".equalsIgnoreCase(granularity) ? DATE_FORMAT_DAILY : DATE_FORMAT_HOURLY;
        List<Map<String, Object>> rows = queryWithFallback(
                ch -> ch.aggregateFlowLogTimeSeries(startTime, endTime, rulePackagePath, flowId, isGray, dateFormat),
                () -> decisionAnalysisMapper.aggregateFlowLogTimeSeries(startTime, endTime, rulePackagePath, flowId, isGray, dateFormat),
                "aggregateFlowLogTimeSeries");

        List<String> timestamps = new ArrayList<>();
        List<Number> volume = new ArrayList<>();
        List<Number> successRate = new ArrayList<>();
        List<Number> rejectRate = new ArrayList<>();
        List<Number> avgLatency = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            timestamps.add(String.valueOf(row.get("time_bucket")));

            long total = toLong(row.get("total_count"));
            long success = toLong(row.get("success_count"));
            long reject = toLong(row.get("reject_count"));

            volume.add(total);
            successRate.add(total > 0 ? round(success * 100.0 / total, 2) : 0);
            rejectRate.add(total > 0 ? round(reject * 100.0 / total, 2) : 0);
            avgLatency.add(toDouble(row.get("avg_total_time_ms")));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamps", timestamps);
        result.put("volume", volume);
        result.put("successRate", successRate);
        result.put("rejectRate", rejectRate);
        result.put("avgLatency", avgLatency);
        return result;
    }

    @Override
    public List<Map<String, Object>> getPackageFlowSummary(Date startTime, Date endTime) {
        List<Map<String, Object>> rows = queryWithFallback(
                ch -> ch.aggregateFlowLogByPackage(startTime, endTime),
                () -> decisionAnalysisMapper.aggregateFlowLogByPackage(startTime, endTime),
                "aggregateFlowLogByPackage");
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rulePackagePath", String.valueOf(row.get("rule_package_path")));
            entry.put("flowId", String.valueOf(row.get("flow_id")));

            long total = toLong(row.get("total_count"));
            long success = toLong(row.get("success_count"));
            long reject = toLong(row.get("reject_count"));

            entry.put("totalCount", total);
            entry.put("successCount", success);
            entry.put("rejectCount", reject);
            entry.put("successRate", total > 0 ? round(success * 100.0 / total, 2) : 0);
            entry.put("rejectRate", total > 0 ? round(reject * 100.0 / total, 2) : 0);
            entry.put("avgTotalTimeMs", toDouble(row.get("avg_total_time_ms")));
            entry.put("maxTotalTimeMs", toDouble(row.get("max_total_time_ms")));
            result.add(entry);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getRejectDistribution(Date startTime, Date endTime,
                                                            String rulePackagePath, int limit) {
        List<Map<String, Object>> rows = queryWithFallback(
                ch -> ch.aggregateRejectDistribution(startTime, endTime, rulePackagePath, limit),
                () -> decisionAnalysisMapper.aggregateRejectDistribution(startTime, endTime, rulePackagePath, limit),
                "aggregateRejectDistribution");
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rejectCode", String.valueOf(row.get("reject_code")));
            entry.put("rejectReason", String.valueOf(row.get("reject_reason")));
            entry.put("count", toLong(row.get("count")));
            result.add(entry);
        }
        return result;
    }

    @Override
    public Map<String, Object> getRuleCoverageAnalysis(String rulePackagePath,
                                                        Date startTime, Date endTime) {
        // 时间窗口内触发的规则
        List<Map<String, Object>> firedRows = queryRuleCoverageWithFallback(
                ch -> ch.aggregateRuleFireFrequency(startTime, endTime, rulePackagePath),
                () -> ruleCoverageMapper.aggregateRuleFireFrequency(startTime, endTime, rulePackagePath),
                "aggregateRuleFireFrequency");
        Set<String> firedInWindow = firedRows.stream()
                .map(r -> String.valueOf(r.get("rule_name")))
                .collect(Collectors.toSet());

        // 全量曾触发的规则名
        List<String> allFiredNames = queryRuleCoverageWithFallback(
                ch -> ch.findAllFiredRuleNames(),
                () -> ruleCoverageMapper.findAllFiredRuleNames(),
                "findAllFiredRuleNames");
        Set<String> allFiredSet = new HashSet<>(allFiredNames);

        // 分类
        List<Map<String, Object>> hotRules = new ArrayList<>();
        for (Map<String, Object> row : firedRows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ruleName", String.valueOf(row.get("rule_name")));
            entry.put("ruleType", String.valueOf(row.get("rule_type")));
            entry.put("fireCount", toLong(row.get("fire_count")));
            entry.put("avgDurationMs", toDouble(row.get("avg_duration_ms")));
            entry.put("maxDurationMs", toDouble(row.get("max_duration_ms")));
            hotRules.add(entry);
        }

        // 冷规则：曾触发但窗口内未触发
        List<String> coldRules = allFiredSet.stream()
                .filter(name -> !firedInWindow.contains(name))
                .sorted()
                .collect(Collectors.toList());

        // 频率分布
        Map<String, Integer> frequencyDistribution = new LinkedHashMap<>();
        frequencyDistribution.put("0-10", 0);
        frequencyDistribution.put("10-100", 0);
        frequencyDistribution.put("100-1000", 0);
        frequencyDistribution.put("1000+", 0);
        for (Map<String, Object> row : firedRows) {
            long count = toLong(row.get("fire_count"));
            if (count <= 10) frequencyDistribution.merge("0-10", 1, Integer::sum);
            else if (count <= 100) frequencyDistribution.merge("10-100", 1, Integer::sum);
            else if (count <= 1000) frequencyDistribution.merge("100-1000", 1, Integer::sum);
            else frequencyDistribution.merge("1000+", 1, Integer::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalFiredInWindow", firedInWindow.size());
        result.put("totalRulesEverSeen", allFiredSet.size());
        result.put("hotRules", hotRules);
        result.put("coldRules", coldRules);
        result.put("frequencyDistribution", frequencyDistribution);
        return result;
    }

    @Override
    public List<Map<String, Object>> getRuleFireFrequency(Date startTime, Date endTime,
                                                           String rulePackagePath) {
        List<Map<String, Object>> rows = queryRuleCoverageWithFallback(
                ch -> ch.aggregateRuleFireFrequency(startTime, endTime, rulePackagePath),
                () -> ruleCoverageMapper.aggregateRuleFireFrequency(startTime, endTime, rulePackagePath),
                "aggregateRuleFireFrequency");
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ruleName", String.valueOf(row.get("rule_name")));
            entry.put("ruleType", String.valueOf(row.get("rule_type")));
            entry.put("rulePackagePath", String.valueOf(row.get("rule_package_path")));
            entry.put("flowId", String.valueOf(row.get("flow_id")));
            entry.put("fireCount", toLong(row.get("fire_count")));
            entry.put("avgDurationMs", toDouble(row.get("avg_duration_ms")));
            entry.put("maxDurationMs", toDouble(row.get("max_duration_ms")));
            result.add(entry);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> detectAnomalies(Date currentTimeParam, int baselineDays,
                                                      double sigmaThreshold, String rulePackagePath) {
        Date currentTime = currentTimeParam != null ? currentTimeParam : new Date();

        // 当前窗口：过去 1 小时
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentTime);
        cal.add(Calendar.HOUR_OF_DAY, -1);
        final Date currentStart = cal.getTime();

        // 基线窗口：baselineDays 天前到当前窗口开始
        cal.setTime(currentStart);
        cal.add(Calendar.DAY_OF_MONTH, -baselineDays);
        final Date baselineStart = cal.getTime();

        // 获取基线统计
        Map<String, Object> baseline = queryWithFallback(
                ch -> ch.computeAnomalyBaseline(baselineStart, currentStart, rulePackagePath),
                () -> decisionAnalysisMapper.computeAnomalyBaseline(baselineStart, currentStart, rulePackagePath),
                "computeAnomalyBaseline");
        if (baseline == null || baseline.get("avg_reject_rate") == null) {
            log.info("历史数据不足，跳过偏差检测");
            return Collections.emptyList();
        }

        // 获取当前窗口统计
        Map<String, Object> current = queryWithFallback(
                ch -> ch.computeCurrentWindowStats(currentStart, currentTime, rulePackagePath),
                () -> decisionAnalysisMapper.computeCurrentWindowStats(currentStart, currentTime, rulePackagePath),
                "computeCurrentWindowStats");
        if (current == null || current.get("total_count") == null) {
            return Collections.emptyList();
        }
        long totalCount = toLong(current.get("total_count"));
        if (totalCount == 0) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> anomalies = new ArrayList<>();

        // 检测成功率偏差
        checkAnomaly(anomalies, "success_rate", "成功率",
                baseline.get("avg_success_rate"), baseline.get("stddev_success_rate"),
                current.get("success_rate"), sigmaThreshold, "DROP");

        // 检测拒绝率偏差
        checkAnomaly(anomalies, "reject_rate", "拒绝率",
                baseline.get("avg_reject_rate"), baseline.get("stddev_reject_rate"),
                current.get("reject_rate"), sigmaThreshold, "SPIKE");

        // 检测延迟偏差
        checkAnomaly(anomalies, "avg_total_time", "平均延迟",
                baseline.get("baseline_avg_time"), baseline.get("stddev_avg_time"),
                current.get("avg_total_time"), sigmaThreshold, "SPIKE");

        return anomalies;
    }

    private void checkAnomaly(List<Map<String, Object>> anomalies, String metric, String label,
                               Object baselineObj, Object stddevObj, Object currentObj,
                               double sigmaThreshold, String direction) {
        double baseline = toDouble(baselineObj);
        double stddev = toDouble(stddevObj);
        double current = toDouble(currentObj);

        // 标准差为 0 或过小时使用最小标准差避免除零
        if (stddev < 1e-10) {
            stddev = baseline * 0.1;
            if (stddev < 1e-10) {
                return; // 无法检测
            }
        }

        double sigmaDelta = Math.abs(current - baseline) / stddev;
        if (sigmaDelta >= sigmaThreshold) {
            String severity;
            if (sigmaDelta >= 3) severity = "HIGH";
            else if (sigmaDelta >= 2) severity = "MEDIUM";
            else severity = "LOW";

            Map<String, Object> anomaly = new LinkedHashMap<>();
            anomaly.put("metric", metric);
            anomaly.put("label", label);
            anomaly.put("baseline", round(baseline, 4));
            anomaly.put("current", round(current, 4));
            anomaly.put("sigmaDelta", round(sigmaDelta, 2));
            anomaly.put("severity", severity);
            anomaly.put("direction", direction);
            anomalies.add(anomaly);
        }
    }

    @Override
    public List<String> listPackagePaths() {
        return queryWithFallback(
                ch -> ch.findAllPackagePaths(),
                () -> decisionAnalysisMapper.findAllPackagePaths(),
                "findAllPackagePaths");
    }

    // === ClickHouse 查询路由 + fallback ===

    @FunctionalInterface
    private interface ChAnalysisQuery<T> {
        T execute(ChDecisionAnalysisMapper mapper);
    }

    @FunctionalInterface
    private interface ChRuleCoverageQuery<T> {
        T execute(ChRuleCoverageMapper mapper);
    }

    private <T> T queryWithFallback(ChAnalysisQuery<T> chQuery, java.util.function.Supplier<T> mysqlQuery, String opName) {
        if (chEnabled && chAnalysisMapper != null) {
            try {
                return chQuery.execute(chAnalysisMapper);
            } catch (Exception e) {
                log.warn("ClickHouse query [{}] failed, fallback to MySQL: {}", opName, e.getMessage());
            }
        }
        return mysqlQuery.get();
    }

    private <T> T queryRuleCoverageWithFallback(ChRuleCoverageQuery<T> chQuery, java.util.function.Supplier<T> mysqlQuery, String opName) {
        if (chEnabled && chRuleCoverageMapper != null) {
            try {
                return chQuery.execute(chRuleCoverageMapper);
            } catch (Exception e) {
                log.warn("ClickHouse query [{}] failed, fallback to MySQL: {}", opName, e.getMessage());
            }
        }
        return mysqlQuery.get();
    }

    // === 工具方法 ===

    private static long toLong(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double toDouble(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}
