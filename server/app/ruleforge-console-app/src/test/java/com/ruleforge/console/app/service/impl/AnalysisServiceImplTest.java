package com.ruleforge.console.app.service.impl;

import com.ruleforge.console.app.mapper.DecisionAnalysisMapper;
import com.ruleforge.console.app.mapper.RuleCoverageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * AnalysisServiceImpl BDD 测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisServiceImpl - 决策日志聚合分析")
class AnalysisServiceImplTest {

    @Mock
    private DecisionAnalysisMapper decisionAnalysisMapper;

    @Mock
    private RuleCoverageMapper ruleCoverageMapper;

    @InjectMocks
    private AnalysisServiceImpl analysisService;

    @Nested
    @DisplayName("Scenario: 时间序列聚合查询")
    class FlowTimeSeriesAggregation {

        @Test
        @DisplayName("按小时粒度返回时间序列数据")
        void shouldReturnHourlyTimeSeries() {
            // Given: flow_log 数据横跨 3 小时
            Date start = new Date();
            Date end = new Date();
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(buildTimeSeriesRow("2026-05-31 10:00", 100L, 80L, 15L, 50.0));
            rows.add(buildTimeSeriesRow("2026-05-31 11:00", 200L, 170L, 20L, 60.0));
            rows.add(buildTimeSeriesRow("2026-05-31 12:00", 150L, 130L, 10L, 55.0));

            when(decisionAnalysisMapper.aggregateFlowLogTimeSeries(
                    any(Date.class), any(Date.class), isNull(), isNull(), isNull(), eq("%Y-%m-%d %H:00")))
                    .thenReturn(rows);

            // When
            Map<String, Object> result = analysisService.getFlowLogTimeSeries(
                    start, end, null, null, null, "hourly");

            // Then
            assertThat(result).containsEntry("timestamps", Arrays.asList(
                    "2026-05-31 10:00", "2026-05-31 11:00", "2026-05-31 12:00"));
            assertThat((List<Number>) result.get("volume")).hasSize(3);
            assertThat((List<Number>) result.get("successRate")).hasSize(3);
            assertThat((List<Number>) result.get("rejectRate")).hasSize(3);
            assertThat((List<Number>) result.get("avgLatency")).hasSize(3);

            // 第一个时间点：100 total, 80 success → 80%
            List<Number> successRate = (List<Number>) result.get("successRate");
            assertThat(successRate.get(0).doubleValue()).isCloseTo(80.0, org.assertj.core.data.Offset.offset(0.1));
        }

        @Test
        @DisplayName("按天粒度返回时间序列数据")
        void shouldReturnDailyTimeSeries() {
            // Given
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(buildTimeSeriesRow("2026-05-29", 1000L, 900L, 50L, 45.0));
            when(decisionAnalysisMapper.aggregateFlowLogTimeSeries(
                    any(Date.class), any(Date.class), isNull(), isNull(), isNull(), eq("%Y-%m-%d")))
                    .thenReturn(rows);

            // When
            Map<String, Object> result = analysisService.getFlowLogTimeSeries(
                    new Date(), new Date(), null, null, null, "daily");

            // Then
            assertThat((List<String>) result.get("timestamps")).containsExactly("2026-05-29");
        }

        @Test
        @DisplayName("按规则包路径过滤")
        void shouldFilterByPackagePath() {
            // Given
            when(decisionAnalysisMapper.aggregateFlowLogTimeSeries(
                    any(Date.class), any(Date.class), eq("loan-rules"), isNull(), isNull(), anyString()))
                    .thenReturn(Collections.emptyList());

            // When
            Map<String, Object> result = analysisService.getFlowLogTimeSeries(
                    new Date(), new Date(), "loan-rules", null, null, "hourly");

            // Then
            assertThat((List<?>) result.get("timestamps")).isEmpty();
        }

        @Test
        @DisplayName("空数据集返回空的 ECharts 格式")
        void shouldReturnEmptyEChartsFormatForNoData() {
            // Given: 数据库无记录
            when(decisionAnalysisMapper.aggregateFlowLogTimeSeries(
                    any(Date.class), any(Date.class), isNull(), isNull(), isNull(), anyString()))
                    .thenReturn(Collections.emptyList());

            // When
            Map<String, Object> result = analysisService.getFlowLogTimeSeries(
                    new Date(), new Date(), null, null, null, "hourly");

            // Then: 返回空的 ECharts 格式
            assertThat(result).containsEntry("timestamps", Collections.emptyList());
            assertThat(result).containsEntry("volume", Collections.emptyList());
            assertThat(result).containsEntry("successRate", Collections.emptyList());
            assertThat(result).containsEntry("rejectRate", Collections.emptyList());
            assertThat(result).containsEntry("avgLatency", Collections.emptyList());
        }

        private Map<String, Object> buildTimeSeriesRow(String timeBucket, long total,
                                                        long success, long reject, double avgTime) {
            Map<String, Object> row = new HashMap<>();
            row.put("time_bucket", timeBucket);
            row.put("total_count", total);
            row.put("success_count", success);
            row.put("reject_count", reject);
            row.put("error_count", total - success - reject);
            row.put("avg_total_time_ms", avgTime);
            row.put("avg_execution_time_ms", avgTime * 0.8);
            row.put("avg_load_time_ms", avgTime * 0.2);
            return row;
        }
    }

    @Nested
    @DisplayName("Scenario: 规则包/决策流汇总")
    class PackageFlowSummary {

        @Test
        @DisplayName("返回每个规则包的调用统计")
        void shouldReturnPackageStatistics() {
            // Given: 2 个规则包
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row1 = new HashMap<>();
            row1.put("rule_package_path", "loan-rules");
            row1.put("flow_id", "loan-approval");
            row1.put("total_count", 1000L);
            row1.put("success_count", 900L);
            row1.put("reject_count", 80L);
            row1.put("avg_total_time_ms", 45.5);
            row1.put("max_total_time_ms", 200.0);
            rows.add(row1);

            Map<String, Object> row2 = new HashMap<>();
            row2.put("rule_package_path", "fraud-rules");
            row2.put("flow_id", "fraud-check");
            row2.put("total_count", 500L);
            row2.put("success_count", 400L);
            row2.put("reject_count", 80L);
            row2.put("avg_total_time_ms", 30.0);
            row2.put("max_total_time_ms", 150.0);
            rows.add(row2);

            when(decisionAnalysisMapper.aggregateFlowLogByPackage(any(Date.class), any(Date.class)))
                    .thenReturn(rows);

            // When
            List<Map<String, Object>> result = analysisService.getPackageFlowSummary(new Date(), new Date());

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsEntry("rulePackagePath", "loan-rules");
            assertThat(result.get(0)).containsEntry("successRate", 90.0);
            assertThat(result.get(0)).containsEntry("rejectRate", 8.0);
            assertThat(result.get(1)).containsEntry("rulePackagePath", "fraud-rules");
        }
    }

    @Nested
    @DisplayName("Scenario: 拒绝码分布")
    class RejectDistribution {

        @Test
        @DisplayName("返回 Top-N 拒绝码及其数量")
        void shouldReturnTopNRejectCodes() {
            // Given
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> r1 = new HashMap<>();
            r1.put("reject_code", "R001");
            r1.put("reject_reason", "高风险客户");
            r1.put("count", 50L);
            rows.add(r1);

            Map<String, Object> r2 = new HashMap<>();
            r2.put("reject_code", "R002");
            r2.put("reject_reason", "负债率过高");
            r2.put("count", 30L);
            rows.add(r2);

            when(decisionAnalysisMapper.aggregateRejectDistribution(
                    any(Date.class), any(Date.class), isNull(), eq(20)))
                    .thenReturn(rows);

            // When
            List<Map<String, Object>> result = analysisService.getRejectDistribution(
                    new Date(), new Date(), null, 20);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsEntry("rejectCode", "R001");
            assertThat(result.get(0)).containsEntry("count", 50L);
            assertThat(result.get(1)).containsEntry("rejectCode", "R002");
        }

        @Test
        @DisplayName("无拒绝记录时返回空列表")
        void shouldReturnEmptyForNoRejects() {
            // Given
            when(decisionAnalysisMapper.aggregateRejectDistribution(
                    any(Date.class), any(Date.class), isNull(), anyInt()))
                    .thenReturn(Collections.emptyList());

            // When
            List<Map<String, Object>> result = analysisService.getRejectDistribution(
                    new Date(), new Date(), null, 20);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Scenario: 规则覆盖率分析")
    class RuleCoverageAnalysis {

        @Test
        @DisplayName("区分热规则、冷规则和从未触发规则")
        void shouldCategorizeHotColdAndDeadRules() {
            // Given: 时间窗口内 3 个规则触发，历史曾触发 5 个
            List<Map<String, Object>> firedRows = new ArrayList<>();
            firedRows.add(buildRuleRow("R001", "向导式规则集", 100L, 5.0, 20.0));
            firedRows.add(buildRuleRow("R002", "决策表", 50L, 3.0, 10.0));
            firedRows.add(buildRuleRow("R003", "脚本式规则集", 10L, 2.0, 5.0));

            when(ruleCoverageMapper.aggregateRuleFireFrequency(
                    any(Date.class), any(Date.class), isNull())).thenReturn(firedRows);
            when(ruleCoverageMapper.findAllFiredRuleNames())
                    .thenReturn(Arrays.asList("R001", "R002", "R003", "R004", "R005"));

            // When
            Map<String, Object> result = analysisService.getRuleCoverageAnalysis(
                    null, new Date(), new Date());

            // Then
            assertThat(result).containsEntry("totalFiredInWindow", 3);
            assertThat(result).containsEntry("totalRulesEverSeen", 5);

            List<Map<String, Object>> hotRules = (List<Map<String, Object>>) result.get("hotRules");
            assertThat(hotRules).hasSize(3);
            assertThat(hotRules.get(0)).containsEntry("ruleName", "R001");

            List<String> coldRules = (List<String>) result.get("coldRules");
            assertThat(coldRules).containsExactly("R004", "R005");
        }

        @Test
        @DisplayName("规则触发频率分布正确计算")
        void shouldComputeFireFrequencyDistribution() {
            // Given: 规则触发频率：1, 50, 500, 5000
            List<Map<String, Object>> firedRows = new ArrayList<>();
            firedRows.add(buildRuleRow("R001", "向导式规则集", 1L, 1.0, 1.0));
            firedRows.add(buildRuleRow("R002", "决策表", 50L, 2.0, 5.0));
            firedRows.add(buildRuleRow("R003", "脚本式规则集", 500L, 3.0, 10.0));
            firedRows.add(buildRuleRow("R004", "决策树", 5000L, 4.0, 20.0));

            when(ruleCoverageMapper.aggregateRuleFireFrequency(
                    any(Date.class), any(Date.class), isNull())).thenReturn(firedRows);
            when(ruleCoverageMapper.findAllFiredRuleNames())
                    .thenReturn(Arrays.asList("R001", "R002", "R003", "R004"));

            // When
            Map<String, Object> result = analysisService.getRuleCoverageAnalysis(
                    null, new Date(), new Date());

            // Then
            Map<String, Integer> dist = (Map<String, Integer>) result.get("frequencyDistribution");
            assertThat(dist).containsEntry("0-10", 1);    // R001: 1
            assertThat(dist).containsEntry("10-100", 1);   // R002: 50
            assertThat(dist).containsEntry("100-1000", 1);  // R003: 500
            assertThat(dist).containsEntry("1000+", 1);     // R004: 5000
        }

        private Map<String, Object> buildRuleRow(String name, String type,
                                                  long fireCount, double avgDuration, double maxDuration) {
            Map<String, Object> row = new HashMap<>();
            row.put("rule_name", name);
            row.put("rule_type", type);
            row.put("rule_package_path", "loan-rules");
            row.put("flow_id", "loan-approval");
            row.put("fire_count", fireCount);
            row.put("avg_duration_ms", avgDuration);
            row.put("max_duration_ms", maxDuration);
            return row;
        }
    }

    @Nested
    @DisplayName("Scenario: 偏差检测")
    class AnomalyDetection {

        @Test
        @DisplayName("拒绝率超 2-sigma 阈值检测为异常")
        void shouldDetectRejectRateSpike() {
            // Given: 基线 reject_rate=0.05, stddev=0.02; 当前 reject_rate=0.15
            Date now = new Date();
            Map<String, Object> baseline = new HashMap<>();
            baseline.put("avg_success_rate", 0.90);
            baseline.put("avg_reject_rate", 0.05);
            baseline.put("baseline_avg_time", 50.0);
            baseline.put("stddev_success_rate", 0.03);
            baseline.put("stddev_reject_rate", 0.02);
            baseline.put("stddev_avg_time", 10.0);

            Map<String, Object> current = new HashMap<>();
            current.put("success_rate", 0.80);
            current.put("reject_rate", 0.15);
            current.put("avg_total_time", 55.0);
            current.put("total_count", 1000L);

            when(decisionAnalysisMapper.computeAnomalyBaseline(any(Date.class), any(Date.class), isNull()))
                    .thenReturn(baseline);
            when(decisionAnalysisMapper.computeCurrentWindowStats(any(Date.class), any(Date.class), isNull()))
                    .thenReturn(current);

            // When
            List<Map<String, Object>> anomalies = analysisService.detectAnomalies(now, 7, 2.0, null);

            // Then: reject_rate 偏差 (0.15-0.05)/0.02 = 5.0 sigma → HIGH
            assertThat(anomalies).isNotEmpty();
            Map<String, Object> rejectAnomaly = anomalies.stream()
                    .filter(a -> "reject_rate".equals(a.get("metric")))
                    .findFirst().orElse(null);
            assertThat(rejectAnomaly).isNotNull();
            assertThat(rejectAnomaly).containsEntry("severity", "HIGH");
            assertThat(rejectAnomaly).containsEntry("direction", "SPIKE");
            assertThat(((Number) rejectAnomaly.get("sigmaDelta")).doubleValue()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.1));
        }

        @Test
        @DisplayName("延迟退化检测为异常")
        void shouldDetectLatencyDegradation() {
            // Given: 基线 avg_time=50ms, stddev=10ms; 当前 avg_time=120ms
            Date now = new Date();
            Map<String, Object> baseline = new HashMap<>();
            baseline.put("avg_success_rate", 0.90);
            baseline.put("avg_reject_rate", 0.05);
            baseline.put("baseline_avg_time", 50.0);
            baseline.put("stddev_success_rate", 0.03);
            baseline.put("stddev_reject_rate", 0.02);
            baseline.put("stddev_avg_time", 10.0);

            Map<String, Object> current = new HashMap<>();
            current.put("success_rate", 0.89);
            current.put("reject_rate", 0.06);
            current.put("avg_total_time", 120.0);
            current.put("total_count", 500L);

            when(decisionAnalysisMapper.computeAnomalyBaseline(any(Date.class), any(Date.class), isNull()))
                    .thenReturn(baseline);
            when(decisionAnalysisMapper.computeCurrentWindowStats(any(Date.class), any(Date.class), isNull()))
                    .thenReturn(current);

            // When
            List<Map<String, Object>> anomalies = analysisService.detectAnomalies(now, 7, 2.0, null);

            // Then: avg_total_time 偏差 (120-50)/10 = 7.0 sigma → HIGH
            Map<String, Object> latencyAnomaly = anomalies.stream()
                    .filter(a -> "avg_total_time".equals(a.get("metric")))
                    .findFirst().orElse(null);
            assertThat(latencyAnomaly).isNotNull();
            assertThat(latencyAnomaly).containsEntry("severity", "HIGH");
            assertThat(((Number) latencyAnomaly.get("sigmaDelta")).doubleValue()).isCloseTo(7.0, org.assertj.core.data.Offset.offset(0.1));
        }

        @Test
        @DisplayName("当前值在正常范围内不报告异常")
        void shouldNotReportAnomalyWhenWithinRange() {
            // Given: 基线 reject_rate=0.05, stddev=0.02; 当前 reject_rate=0.06
            Date now = new Date();
            Map<String, Object> baseline = new HashMap<>();
            baseline.put("avg_success_rate", 0.90);
            baseline.put("avg_reject_rate", 0.05);
            baseline.put("baseline_avg_time", 50.0);
            baseline.put("stddev_success_rate", 0.03);
            baseline.put("stddev_reject_rate", 0.02);
            baseline.put("stddev_avg_time", 10.0);

            Map<String, Object> current = new HashMap<>();
            current.put("success_rate", 0.89);
            current.put("reject_rate", 0.06);
            current.put("avg_total_time", 55.0);
            current.put("total_count", 1000L);

            when(decisionAnalysisMapper.computeAnomalyBaseline(any(Date.class), any(Date.class), isNull()))
                    .thenReturn(baseline);
            when(decisionAnalysisMapper.computeCurrentWindowStats(any(Date.class), any(Date.class), isNull()))
                    .thenReturn(current);

            // When
            List<Map<String, Object>> anomalies = analysisService.detectAnomalies(now, 7, 2.0, null);

            // Then: 所有值在 2-sigma 范围内
            // reject_rate: (0.06-0.05)/0.02 = 0.5 < 2.0 → 不报告
            // success_rate: (0.89-0.90)/0.03 = 0.33 < 2.0 → 不报告
            // avg_total_time: (55-50)/10 = 0.5 < 2.0 → 不报告
            assertThat(anomalies).isEmpty();
        }

        @Test
        @DisplayName("历史数据不足时不做检测")
        void shouldSkipWhenInsufficientBaselineData() {
            // Given: 基线返回 null
            Date now = new Date();
            when(decisionAnalysisMapper.computeAnomalyBaseline(any(Date.class), any(Date.class), isNull()))
                    .thenReturn(null);

            // When
            List<Map<String, Object>> anomalies = analysisService.detectAnomalies(now, 7, 2.0, null);

            // Then: 返回空列表
            assertThat(anomalies).isEmpty();
        }

        @Test
        @DisplayName("基线 stddev 为 0 时不崩溃")
        void shouldHandleZeroStddev() {
            // Given: stddev 全为 0，基线值为非零
            Date now = new Date();
            Map<String, Object> baseline = new HashMap<>();
            baseline.put("avg_success_rate", 0.90);
            baseline.put("avg_reject_rate", 0.05);
            baseline.put("baseline_avg_time", 50.0);
            baseline.put("stddev_success_rate", 0.0);
            baseline.put("stddev_reject_rate", 0.0);
            baseline.put("stddev_avg_time", 0.0);

            Map<String, Object> current = new HashMap<>();
            current.put("success_rate", 0.80);
            current.put("reject_rate", 0.15);
            current.put("avg_total_time", 120.0);
            current.put("total_count", 1000L);

            when(decisionAnalysisMapper.computeAnomalyBaseline(any(Date.class), any(Date.class), isNull()))
                    .thenReturn(baseline);
            when(decisionAnalysisMapper.computeCurrentWindowStats(any(Date.class), any(Date.class), isNull()))
                    .thenReturn(current);

            // When: 不应抛异常
            List<Map<String, Object>> anomalies = analysisService.detectAnomalies(now, 7, 2.0, null);

            // Then: 使用 fallback stddev (baseline * 0.1)，偏差可能超阈值
            assertThat(anomalies).isNotNull();
        }
    }
}
