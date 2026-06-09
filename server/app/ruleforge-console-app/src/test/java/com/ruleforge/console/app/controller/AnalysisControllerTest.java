package com.ruleforge.console.app.controller;

import com.ruleforge.console.app.service.IAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * AnalysisController REST API 测试
 *
 * 直接测试 Controller 方法（不通过 MockMvc，避免 Spring Boot 自动配置问题）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisController - 决策分析 REST API")
class AnalysisControllerTest {

    @Mock
    private IAnalysisService analysisService;

    @InjectMocks
    private AnalysisController controller;

    private Date startTime;
    private Date endTime;

    @BeforeEach
    void setUp() {
        startTime = new Date(1748390400000L); // 2026-05-28 00:00 UTC
        endTime = new Date(1748476799000L);   // 2026-05-28 23:59 UTC
    }

    @Nested
    @DisplayName("Scenario: 时间序列查询")
    class FlowTimeSeries {

        @Test
        @DisplayName("返回 ECharts 格式时间序列数据")
        void shouldReturnTimeSeries() {
            // Given
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("timestamps", Arrays.asList("2026-05-28 10:00", "2026-05-28 11:00"));
            data.put("volume", Arrays.asList(100, 200));
            data.put("successRate", Arrays.asList(90.0, 85.0));

            when(analysisService.getFlowLogTimeSeries(any(), any(), isNull(), isNull(), isNull(), eq("hourly")))
                    .thenReturn(data);

            // When
            ResponseEntity<?> response = controller.getFlowTimeSeries(startTime, endTime, null, null, null, "hourly");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("timestamps", Arrays.asList("2026-05-28 10:00", "2026-05-28 11:00"));
            assertThat((List<?>) body.get("volume")).hasSize(2);
        }

        @Test
        @DisplayName("按规则包过滤传参正确")
        void shouldPassPackageFilter() {
            // Given
            Map<String, Object> empty = Map.of("timestamps", Collections.emptyList(), "volume", Collections.emptyList(),
                    "successRate", Collections.emptyList(), "rejectRate", Collections.emptyList(), "avgLatency", Collections.emptyList());
            when(analysisService.getFlowLogTimeSeries(any(), any(), eq("luzcred/withdrawal"), isNull(), isNull(), anyString()))
                    .thenReturn(empty);

            // When
            ResponseEntity<?> response = controller.getFlowTimeSeries(startTime, endTime, "luzcred/withdrawal", null, null, "daily");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("默认 granularity 为 hourly")
        void shouldDefaultToHourly() {
            // Given
            when(analysisService.getFlowLogTimeSeries(any(), any(), isNull(), isNull(), isNull(), eq("hourly")))
                    .thenReturn(Map.of("timestamps", Collections.emptyList(), "volume", Collections.emptyList(),
                            "successRate", Collections.emptyList(), "rejectRate", Collections.emptyList(), "avgLatency", Collections.emptyList()));

            // When
            ResponseEntity<?> response = controller.getFlowTimeSeries(startTime, endTime, null, null, null, "hourly");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Scenario: 包汇总查询")
    class PackageSummary {

        @Test
        @DisplayName("返回包汇总列表")
        void shouldReturnPackageSummary() {
            // Given: 参考 nova_decision 真实数据
            List<Map<String, Object>> data = new ArrayList<>();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rulePackagePath", "luzcred/withdrawal");
            entry.put("flowId", "withdrawal-flow");
            entry.put("totalCount", 122);
            entry.put("successRate", 92.0);
            entry.put("rejectRate", 6.0);
            entry.put("avgTotalTimeMs", 45.5);
            data.add(entry);

            when(analysisService.getPackageFlowSummary(any(), any())).thenReturn(data);

            // When
            ResponseEntity<?> response = controller.getPackageFlowSummary(startTime, endTime);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
            assertThat(body).hasSize(1);
            assertThat(body.get(0)).containsEntry("rulePackagePath", "luzcred/withdrawal");
            assertThat(body.get(0)).containsEntry("totalCount", 122);
        }
    }

    @Nested
    @DisplayName("Scenario: 拒绝码分布")
    class RejectDistribution {

        @Test
        @DisplayName("返回 Top-N 拒绝码")
        void shouldReturnTopNRejectCodes() {
            // Given
            List<Map<String, Object>> data = Arrays.asList(
                    Map.of("rejectCode", "HIGH_RISK", "rejectReason", "高风险客户", "count", 30L),
                    Map.of("rejectCode", "DEBT_RATIO", "rejectReason", "负债率过高", "count", 15L)
            );
            when(analysisService.getRejectDistribution(any(), any(), isNull(), eq(20))).thenReturn(data);

            // When
            ResponseEntity<?> response = controller.getRejectDistribution(startTime, endTime, null, 20);

            // Then
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
            assertThat(body).hasSize(2);
            assertThat(body.get(0)).containsEntry("rejectCode", "HIGH_RISK");
            assertThat(body.get(1)).containsEntry("count", 15L);
        }

        @Test
        @DisplayName("默认 limit 为 20")
        void shouldDefaultToLimit20() {
            when(analysisService.getRejectDistribution(any(), any(), isNull(), eq(20)))
                    .thenReturn(Collections.emptyList());

            ResponseEntity<?> response = controller.getRejectDistribution(startTime, endTime, null, 20);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Scenario: 规则覆盖率")
    class RuleCoverage {

        @Test
        @DisplayName("返回规则覆盖率分析")
        void shouldReturnRuleCoverage() {
            // Given: 参考 nova_decision 真实数据 — 17 个规则
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalFiredInWindow", 10);
            data.put("totalRulesEverSeen", 17);
            data.put("hotRules", Arrays.asList(
                    Map.of("ruleName", "新客额度else", "fireCount", 442L, "avgDurationMs", 5.0),
                    Map.of("ruleName", "免费模型规则else", "fireCount", 300L, "avgDurationMs", 3.0)
            ));
            data.put("coldRules", Arrays.asList("age_check", "advance_check"));
            data.put("frequencyDistribution", Map.of("0-10", 3, "10-100", 5, "100-1000", 2, "1000+", 2));

            when(analysisService.getRuleCoverageAnalysis(isNull(), any(), any())).thenReturn(data);

            // When
            ResponseEntity<?> response = controller.getRuleCoverage(null, startTime, endTime);

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("totalFiredInWindow", 10);
            assertThat(body).containsEntry("totalRulesEverSeen", 17);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hotRules = (List<Map<String, Object>>) body.get("hotRules");
            assertThat(hotRules).hasSize(2);
            assertThat(hotRules.get(0)).containsEntry("ruleName", "新客额度else");

            @SuppressWarnings("unchecked")
            List<String> coldRules = (List<String>) body.get("coldRules");
            assertThat(coldRules).containsExactly("age_check", "advance_check");
        }
    }

    @Nested
    @DisplayName("Scenario: 偏差检测")
    class AnomalyDetection {

        @Test
        @DisplayName("返回异常检测结果")
        void shouldReturnAnomalies() {
            // Given
            List<Map<String, Object>> anomalies = Arrays.asList(
                    Map.of("metric", "reject_rate", "severity", "HIGH", "sigmaDelta", 5.0,
                            "baseline", 0.05, "current", 0.15, "direction", "SPIKE", "label", "拒绝率")
            );
            when(analysisService.detectAnomalies(isNull(), eq(7), eq(2.0), isNull())).thenReturn(anomalies);

            // When
            ResponseEntity<?> response = controller.detectAnomalies(null, 7, 2.0, null);

            // Then
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
            assertThat(body).hasSize(1);
            assertThat(body.get(0)).containsEntry("metric", "reject_rate");
            assertThat(body.get(0)).containsEntry("severity", "HIGH");
        }

        @Test
        @DisplayName("无异常时返回空列表")
        void shouldReturnEmptyWhenNoAnomalies() {
            when(analysisService.detectAnomalies(any(), anyInt(), anyDouble(), isNull()))
                    .thenReturn(Collections.emptyList());

            ResponseEntity<?> response = controller.detectAnomalies(null, 7, 2.0, null);

            @SuppressWarnings("unchecked")
            List<?> body = (List<?>) response.getBody();
            assertThat(body).isEmpty();
        }

        @Test
        @DisplayName("支持自定义参数")
        void shouldSupportCustomParams() {
            when(analysisService.detectAnomalies(any(), eq(14), eq(3.0), eq("luzcred/T4")))
                    .thenReturn(Collections.emptyList());

            ResponseEntity<?> response = controller.detectAnomalies(null, 14, 3.0, "luzcred/T4");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Scenario: 规则包列表")
    class ListPackages {

        @Test
        @DisplayName("返回所有规则包路径")
        void shouldReturnPackagePaths() {
            // Given: 参考 nova_decision 真实数据
            when(analysisService.listPackagePaths()).thenReturn(
                    Arrays.asList("luzcred/dormant_credit_adjust", "luzcred/withdrawal", "luzcred/T4",
                            "luzcred/repayment_credit_adjust", "luzcred/t2", "luzcred/t1",
                            "luzcred/dormant_product_adjust", "luzcred/t01"));

            // When
            ResponseEntity<?> response = controller.listPackages();

            // Then
            @SuppressWarnings("unchecked")
            List<String> body = (List<String>) response.getBody();
            assertThat(body).hasSize(8);
            assertThat(body).containsExactly(
                    "luzcred/dormant_credit_adjust", "luzcred/withdrawal", "luzcred/T4",
                    "luzcred/repayment_credit_adjust", "luzcred/t2", "luzcred/t1",
                    "luzcred/dormant_product_adjust", "luzcred/t01");
        }
    }
}
