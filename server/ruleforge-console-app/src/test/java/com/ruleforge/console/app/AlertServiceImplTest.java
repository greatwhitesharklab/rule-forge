package com.ruleforge.console.app.service.impl;

import com.ruleforge.console.app.entity.AlertHistory;
import com.ruleforge.console.app.entity.AlertRule;
import com.ruleforge.console.app.entity.MetricsSnapshot;
import com.ruleforge.console.app.repository.data.MonitoringRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 告警规则引擎
 *
 * AlertServiceImpl 负责评估告警规则、触发告警并调用 Webhook 通知。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertServiceImpl - 告警规则引擎")
class AlertServiceImplTest {

    @Mock private MonitoringRepository monitoringRepository;
    @Mock private RestTemplate restTemplate;

    @InjectMocks
    private AlertServiceImpl alertService;

    private AlertRule buildRule(Long id, String name, String metricName, String condition,
                                double threshold, int durationMin, int cooldownMin,
                                String webhookUrl, Date lastFiredAt) {
        AlertRule rule = new AlertRule();
        rule.setId(id);
        rule.setName(name);
        rule.setEnabled(true);
        rule.setMetricName(metricName);
        rule.setCondition(condition);
        rule.setThreshold(threshold);
        rule.setDurationMin(durationMin);
        rule.setCooldownMin(cooldownMin);
        rule.setWebhookUrl(webhookUrl);
        rule.setLastFiredAt(lastFiredAt);
        return rule;
    }

    private MetricsSnapshot buildTimerSnapshot(String metricName, long p95Ms, long count) {
        MetricsSnapshot s = new MetricsSnapshot();
        s.setMetricName(metricName);
        s.setMetricType("TIMER");
        s.setP95Ms(p95Ms);
        s.setCountVal(count);
        s.setSnapshotTime(new Date());
        return s;
    }

    private MetricsSnapshot buildCounterSnapshot(String metricName, long count) {
        MetricsSnapshot s = new MetricsSnapshot();
        s.setMetricName(metricName);
        s.setMetricType("COUNTER");
        s.setCountVal(count);
        s.setSnapshotTime(new Date());
        return s;
    }

    @Nested
    @DisplayName("Scenario: 指标超阈值触发告警")
    class AlertFiredWhenThresholdExceeded {

        @Test
        @DisplayName("P95 延迟超阈值触发告警并调用 Webhook")
        void shouldFireAlertWhenP95ExceedsThreshold() {
            // Given
            AlertRule rule = buildRule(1L, "高延迟告警", "rule.execution.latency",
                    "GT", 1000.0, 1, 30, "https://hook.example.com/alert", null);

            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));

            MetricsSnapshot snapshot = buildTimerSnapshot("rule.execution.latency", 1500L, 10L);
            when(monitoringRepository.findLatestMetrics(eq("rule.execution.latency"), eq(1), isNull()))
                    .thenReturn(List.of(snapshot));

            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            // When
            alertService.evaluateAlerts();

            // Then — 告警历史被记录
            ArgumentCaptor<AlertHistory> historyCaptor = ArgumentCaptor.forClass(AlertHistory.class);
            verify(monitoringRepository).insertAlertHistory(historyCaptor.capture());
            AlertHistory history = historyCaptor.getValue();
            assertThat(history.getAlertRuleId()).isEqualTo(1L);
            assertThat(history.getRuleName()).isEqualTo("高延迟告警");
            assertThat(history.getActualValue()).isEqualTo(1500.0);
            assertThat(history.getThreshold()).isEqualTo(1000.0);
            assertThat(history.getWebhookStatus()).isEqualTo(200);

            // Webhook 被调用
            verify(restTemplate).exchange(contains("hook.example.com"), any(), any(), eq(String.class));

            // last_fired_at 被更新
            verify(monitoringRepository).updateAlertRuleLastFired(eq(1L), any(Date.class));
        }
    }

    @Nested
    @DisplayName("Scenario: 冷却期内不重复告警")
    class CooldownPreventsRepeatAlert {

        @Test
        @DisplayName("冷却期内跳过告警评估")
        void shouldSkipAlertDuringCooldown() {
            // Given — 5 秒前刚触发过，冷却期 30 分钟
            AlertRule rule = buildRule(2L, "冷却中规则", "rule.execution.latency",
                    "GT", 100.0, 1, 30, "https://hook.example.com", new Date(System.currentTimeMillis() - 5000));

            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));

            // When
            alertService.evaluateAlerts();

            // Then — 不查快照、不记录历史、不调 Webhook
            verify(monitoringRepository, never()).findLatestMetrics(anyString(), anyInt(), any());
            verify(monitoringRepository, never()).insertAlertHistory(any(AlertHistory.class));
            verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        }
    }

    @Nested
    @DisplayName("Scenario: 快照数据不足不触发告警")
    class InsufficientSnapshotsNoAlert {

        @Test
        @DisplayName("快照数量不足时不触发告警")
        void shouldNotFireWhenSnapshotsInsufficient() {
            // Given
            AlertRule rule = buildRule(3L, "持续超阈值", "rule.execution.latency",
                    "GT", 500.0, 3, 10, "https://hook.example.com", null);

            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));

            // 只有 2 个快照，不够 3 个
            MetricsSnapshot s1 = buildTimerSnapshot("rule.execution.latency", 800L, 5L);
            MetricsSnapshot s2 = buildTimerSnapshot("rule.execution.latency", 900L, 3L);
            when(monitoringRepository.findLatestMetrics(eq("rule.execution.latency"), eq(3), isNull()))
                    .thenReturn(List.of(s1, s2));

            // When
            alertService.evaluateAlerts();

            // Then — 不触发告警
            verify(monitoringRepository, never()).insertAlertHistory(any(AlertHistory.class));
            verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        }
    }

    @Nested
    @DisplayName("Scenario: 指标未超阈值不触发告警")
    class BelowThresholdNoAlert {

        @Test
        @DisplayName("P95 低于阈值时不触发告警")
        void shouldNotFireWhenBelowThreshold() {
            // Given
            AlertRule rule = buildRule(4L, "高延迟告警", "rule.execution.latency",
                    "GT", 1000.0, 1, 30, "https://hook.example.com", null);

            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));

            MetricsSnapshot snapshot = buildTimerSnapshot("rule.execution.latency", 500L, 10L);
            when(monitoringRepository.findLatestMetrics(eq("rule.execution.latency"), eq(1), isNull()))
                    .thenReturn(List.of(snapshot));

            // When
            alertService.evaluateAlerts();

            // Then
            verify(monitoringRepository, never()).insertAlertHistory(any(AlertHistory.class));
            verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        }
    }

    @Nested
    @DisplayName("Scenario: Webhook 调用失败仍记录告警历史")
    class WebhookFailureStillRecords {

        @Test
        @DisplayName("Webhook 失败时记录 webhookStatus=-1")
        void shouldRecordAlertEvenWhenWebhookFails() {
            // Given
            AlertRule rule = buildRule(5L, "Webhook 故障", "rule.execution.latency",
                    "GT", 100.0, 1, 10, "https://broken-hook.example.com", null);

            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));

            MetricsSnapshot snapshot = buildTimerSnapshot("rule.execution.latency", 500L, 5L);
            when(monitoringRepository.findLatestMetrics(eq("rule.execution.latency"), eq(1), isNull()))
                    .thenReturn(List.of(snapshot));

            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            // When
            alertService.evaluateAlerts();

            // Then — 告警历史仍然记录
            ArgumentCaptor<AlertHistory> historyCaptor = ArgumentCaptor.forClass(AlertHistory.class);
            verify(monitoringRepository).insertAlertHistory(historyCaptor.capture());
            AlertHistory history = historyCaptor.getValue();
            assertThat(history.getWebhookStatus()).isEqualTo(-1);
            assertThat(history.getWebhookResponse()).contains("Connection refused");

            // last_fired_at 仍更新
            verify(monitoringRepository).updateAlertRuleLastFired(eq(5L), any(Date.class));
        }
    }

    @Nested
    @DisplayName("Scenario: 告警规则支持多种条件运算符")
    class MultipleConditionOperators {

        @Test
        @DisplayName("GT / LT / EQ 条件正确判断")
        void shouldEvaluateMultipleConditionsCorrectly() {
            // Given — GT 规则（value=500 > threshold=100，应该触发）
            AlertRule gtRule = buildRule(10L, "GT 告警", "rule.execution.latency",
                    "GT", 100.0, 1, 10, "https://hook.example.com/gt", null);
            // LT 规则（value=500 < threshold=100，不应该触发）
            AlertRule ltRule = buildRule(11L, "LT 告警", "rule.execution.counter",
                    "LT", 100.0, 1, 10, "https://hook.example.com/lt", null);
            // EQ 规则（value=500 == threshold=500，应该触发）
            AlertRule eqRule = buildRule(12L, "EQ 告警", "rule.execution.gauge",
                    "EQ", 500.0, 1, 10, "https://hook.example.com/eq", null);

            when(monitoringRepository.findEnabledAlertRules())
                    .thenReturn(List.of(gtRule, ltRule, eqRule));

            MetricsSnapshot timerSnap = buildTimerSnapshot("rule.execution.latency", 500L, 5L);
            MetricsSnapshot counterSnap = buildCounterSnapshot("rule.execution.counter", 500L);
            MetricsSnapshot gaugeSnap = new MetricsSnapshot();
            gaugeSnap.setMetricName("rule.execution.gauge");
            gaugeSnap.setMetricType("GAUGE");
            gaugeSnap.setGaugeVal(500.0);

            when(monitoringRepository.findLatestMetrics(eq("rule.execution.latency"), eq(1), isNull()))
                    .thenReturn(List.of(timerSnap));
            when(monitoringRepository.findLatestMetrics(eq("rule.execution.counter"), eq(1), isNull()))
                    .thenReturn(List.of(counterSnap));
            when(monitoringRepository.findLatestMetrics(eq("rule.execution.gauge"), eq(1), isNull()))
                    .thenReturn(List.of(gaugeSnap));

            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            // When
            alertService.evaluateAlerts();

            // Then — 只有 GT 和 EQ 触发，2 条告警历史
            verify(monitoringRepository, times(2)).insertAlertHistory(any(AlertHistory.class));
            verify(restTemplate, times(2)).exchange(anyString(), any(), any(), eq(String.class));
        }
    }

    @Nested
    @DisplayName("Scenario: 告警规则 CRUD")
    class AlertRuleCrud {

        @Test
        @DisplayName("创建告警规则默认启用")
        void shouldCreateRuleWithEnabledTrue() {
            // Given
            AlertRule newRule = new AlertRule();
            newRule.setName("新规则");
            newRule.setMetricName("rule.execution.latency");
            newRule.setCondition("GT");
            newRule.setThreshold(500.0);

            // When
            AlertRule result = alertService.saveAlertRule(newRule);

            // Then
            assertThat(result.getEnabled()).isTrue();
            assertThat(result.getCreatedAt()).isNotNull();
            verify(monitoringRepository).insertAlertRule(newRule);
        }

        @Test
        @DisplayName("删除告警规则")
        void shouldDeleteRule() {
            // When
            alertService.deleteAlertRule(42L);

            // Then
            verify(monitoringRepository).deleteAlertRule(42L);
        }
    }

    @Nested
    @DisplayName("Scenario: 告警历史查询")
    class AlertHistoryQuery {

        @Test
        @DisplayName("按规则 ID 查询告警历史")
        void shouldQueryHistoryByRuleId() {
            // Given
            AlertHistory h1 = new AlertHistory();
            h1.setAlertRuleId(1L);
            h1.setRuleName("规则A");
            AlertHistory h2 = new AlertHistory();
            h2.setAlertRuleId(1L);
            h2.setRuleName("规则A");

            when(monitoringRepository.findAlertHistory(eq(1L), isNull(), isNull())).thenReturn(List.of(h1, h2));

            // When
            List<AlertHistory> result = alertService.listAlertHistory(1L, null, null);

            // Then
            assertThat(result).hasSize(2);
            verify(monitoringRepository).findAlertHistory(1L, null, null);
        }

        @Test
        @DisplayName("无过滤条件返回所有历史")
        void shouldReturnAllHistoryWhenNoFilter() {
            // Given
            when(monitoringRepository.findAlertHistory(isNull(), isNull(), isNull())).thenReturn(List.of(new AlertHistory()));

            // When
            List<AlertHistory> result = alertService.listAlertHistory(null, null, null);

            // Then
            assertThat(result).hasSize(1);
        }
    }
}
