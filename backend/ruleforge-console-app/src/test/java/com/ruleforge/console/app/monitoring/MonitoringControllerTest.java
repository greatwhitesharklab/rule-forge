package com.ruleforge.console.app.monitoring;

import com.ruleforge.console.app.controller.MonitoringController;
import com.ruleforge.console.app.entity.AlertRule;
import com.ruleforge.console.app.entity.MetricsSnapshot;
import com.ruleforge.console.app.repository.data.MonitoringRepository;
import com.ruleforge.console.app.service.IAlertService;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 监控 Dashboard REST API
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("监控 API - MonitoringController")
class MonitoringControllerTest {

    @Mock
    private IAlertService alertService;

    @Mock
    private MonitoringRepository monitoringRepository;

    @InjectMocks
    private MonitoringController controller;

    private MetricsSnapshot createTestSnapshot(String metricName, String tags, long p95, long count, Date time) {
        MetricsSnapshot s = new MetricsSnapshot();
        s.setMetricName(metricName);
        s.setTags(tags);
        s.setMetricType("TIMER");
        s.setP95Ms(p95);
        s.setP50Ms(p95 / 2);
        s.setP99Ms(p95 * 2);
        s.setCountVal(count);
        s.setSnapshotTime(time);
        return s;
    }

    @Nested
    @DisplayName("GET /api/monitoring/metrics - 查询指标")
    class QueryMetrics {

        @Test
        @DisplayName("按 metricName 和时间范围查询，返回 ECharts 格式")
        void shouldQueryByMetricNameAndTimeRange() {
            // Given
            Date now = new Date();
            List<MetricsSnapshot> snapshots = List.of(
                    createTestSnapshot("rule.execution.latency", "{\"package\":\"loan-rules\"}", 200, 10, now)
            );
            when(monitoringRepository.findMetricsByMetricName("rule.execution.latency", now, now, null))
                    .thenReturn(snapshots);

            // When
            ResponseEntity<?> response = controller.queryMetrics(
                    "rule.execution.latency", now, now, null
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("timestamps");
            assertThat(body).containsKey("series");
        }

        @Test
        @DisplayName("按 tags 过滤查询")
        void shouldFilterByTags() {
            // Given
            Date now = new Date();
            when(monitoringRepository.findMetricsByMetricName("rule.execution.latency", now, now, "{\"package\":\"loan-rules\"}"))
                    .thenReturn(Collections.emptyList());

            // When
            ResponseEntity<?> response = controller.queryMetrics(
                    "rule.execution.latency", now, now, "{\"package\":\"loan-rules\"}"
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("GET /api/monitoring/metrics/packages - 规则包列表")
    class ListPackages {

        @Test
        @DisplayName("返回所有唯一的规则包路径")
        void shouldReturnDistinctPackagePaths() {
            // Given
            MetricsSnapshot s1 = new MetricsSnapshot();
            s1.setTags("{\"package\":\"loan-rules\"}");
            MetricsSnapshot s2 = new MetricsSnapshot();
            s2.setTags("{\"package\":\"credit-score\"}");
            when(monitoringRepository.findDistinctTags()).thenReturn(List.of(s1, s2));

            // When
            ResponseEntity<?> response = controller.listPackages();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<String> packages = (List<String>) response.getBody();
            assertThat(packages).containsExactlyInAnyOrder("loan-rules", "credit-score");
        }
    }

    @Nested
    @DisplayName("POST /api/monitoring/alerts - 创建告警规则")
    class CreateAlertRule {

        @Test
        @DisplayName("创建有效的告警规则")
        void shouldCreateAlertRule() {
            // Given
            AlertRule rule = new AlertRule();
            rule.setName("High Latency");
            rule.setMetricName("rule.execution.latency");
            rule.setCondition("GT");
            rule.setThreshold(5000.0);
            rule.setWebhookUrl("http://example.com/hook");
            when(alertService.saveAlertRule(any())).thenAnswer(invocation -> {
                AlertRule r = invocation.getArgument(0);
                r.setId(1L);
                return r;
            });

            // When
            ResponseEntity<?> response = controller.createAlertRule(rule);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            AlertRule created = (AlertRule) response.getBody();
            assertThat(created.getId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("DELETE /api/monitoring/alerts/{id} - 删除告警规则")
    class DeleteAlertRule {

        @Test
        @DisplayName("删除存在的规则")
        void shouldDeleteExistingRule() {
            // Given
            doNothing().when(alertService).deleteAlertRule(1L);

            // When
            ResponseEntity<?> response = controller.deleteAlertRule(1L);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(alertService).deleteAlertRule(1L);
        }
    }

    @Nested
    @DisplayName("GET /api/monitoring/alerts/history - 告警历史")
    class AlertHistoryQuery {

        @Test
        @DisplayName("按时间范围查询告警历史")
        void shouldQueryHistoryByTimeRange() {
            // Given
            Date start = new Date(System.currentTimeMillis() - 3600000);
            Date end = new Date();
            when(alertService.listAlertHistory(isNull(), eq(start), eq(end)))
                    .thenReturn(Collections.emptyList());

            // When
            ResponseEntity<?> response = controller.listAlertHistory(null, start, end);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
