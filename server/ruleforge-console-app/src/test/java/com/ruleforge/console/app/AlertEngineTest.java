package com.ruleforge.console.app.monitoring;

import com.ruleforge.console.app.entity.AlertHistory;
import com.ruleforge.console.app.entity.AlertRule;
import com.ruleforge.console.app.entity.MetricsSnapshot;
import com.ruleforge.console.app.repository.data.MonitoringRepository;
import com.ruleforge.console.app.service.impl.AlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 告警引擎
 */
@DisplayName("告警引擎 - 规则评估与通知")
class AlertEngineTest {

    private MonitoringRepository monitoringRepository;
    private RestTemplate restTemplate;
    private AlertServiceImpl service;

    @BeforeEach
    void setUp() {
        monitoringRepository = mock(MonitoringRepository.class);
        restTemplate = mock(RestTemplate.class);
        service = new AlertServiceImpl(monitoringRepository, restTemplate);
    }

    private AlertRule createRule(String condition, double threshold, int durationMin, int cooldownMin) {
        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setName("Test Alert");
        rule.setEnabled(true);
        rule.setMetricName("rule.execution.latency");
        rule.setCondition(condition);
        rule.setThreshold(threshold);
        rule.setDurationMin(durationMin);
        rule.setCooldownMin(cooldownMin);
        rule.setWebhookUrl("http://example.com/hook");
        return rule;
    }

    private MetricsSnapshot createSnapshot(long p95Value) {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setMetricType("TIMER");
        snapshot.setP95Ms(p95Value);
        snapshot.setCountVal(1L);
        return snapshot;
    }

    @Nested
    @DisplayName("条件评估")
    class ConditionEvaluation {

        @Test
        @DisplayName("GT 条件：实际值大于阈值时触发")
        @SuppressWarnings("unchecked")
        void shouldFireWhenValueGreaterThanThreshold() {
            // Given
            AlertRule rule = createRule("GT", 5000, 1, 0);
            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));
            when(monitoringRepository.findLatestMetrics(anyString(), anyInt(), any())).thenReturn(List.of(createSnapshot(8000)));
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            // When
            service.evaluateAlerts();

            // Then
            verify(monitoringRepository).insertAlertHistory((AlertHistory) any());
        }

        @Test
        @DisplayName("GT 条件：实际值不大于阈值时不触发")
        void shouldNotFireWhenValueNotGreaterThanThreshold() {
            // Given
            AlertRule rule = createRule("GT", 5000, 1, 0);
            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));
            when(monitoringRepository.findLatestMetrics(anyString(), anyInt(), any())).thenReturn(List.of(createSnapshot(3000)));

            // When
            service.evaluateAlerts();

            // Then
            verify(monitoringRepository, never()).insertAlertHistory((AlertHistory) any());
        }

        @Test
        @DisplayName("LT 条件正确评估")
        @SuppressWarnings("unchecked")
        void shouldEvaluateLTCondition() {
            // Given
            AlertRule rule = createRule("LT", 10, 1, 0);
            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));

            MetricsSnapshot snapshot = new MetricsSnapshot();
            snapshot.setMetricType("COUNTER");
            snapshot.setCountVal(5L);
            when(monitoringRepository.findLatestMetrics(anyString(), anyInt(), any())).thenReturn(List.of(snapshot));
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            // When
            service.evaluateAlerts();

            // Then
            verify(monitoringRepository).insertAlertHistory((AlertHistory) any());
        }
    }

    @Nested
    @DisplayName("持续时间窗口")
    class DurationWindow {

        @Test
        @DisplayName("连续 N 个窗口都超阈值才触发")
        void shouldFireOnlyAfterConsecutiveBreaches() {
            // Given 需要连续 3 个窗口，但只有 2 个快照
            AlertRule rule = createRule("GT", 5000, 3, 0);
            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));

            MetricsSnapshot s1 = createSnapshot(8000);
            MetricsSnapshot s2 = createSnapshot(9000);
            when(monitoringRepository.findLatestMetrics(anyString(), anyInt(), any())).thenReturn(List.of(s1, s2));

            // When
            service.evaluateAlerts();

            // Then 快照数 < durationMin，不应触发
            verify(monitoringRepository, never()).insertAlertHistory((AlertHistory) any());
        }
    }

    @Nested
    @DisplayName("冷却机制")
    class Cooldown {

        @Test
        @DisplayName("冷却期内不重复触发")
        void shouldNotFireDuringCooldown() {
            // Given 5 分钟前触发过，冷却 10 分钟
            AlertRule rule = createRule("GT", 5000, 1, 10);
            rule.setLastFiredAt(new Date(System.currentTimeMillis() - 5 * 60 * 1000));
            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));

            // When
            service.evaluateAlerts();

            // Then 不触发（冷却中）
            verify(monitoringRepository, never()).findLatestMetrics(anyString(), anyInt(), any());
        }

        @Test
        @DisplayName("冷却期过后可再次触发")
        @SuppressWarnings("unchecked")
        void shouldFireAfterCooldownExpires() {
            // Given 15 分钟前触发过，冷却 10 分钟
            AlertRule rule = createRule("GT", 5000, 1, 10);
            rule.setLastFiredAt(new Date(System.currentTimeMillis() - 15 * 60 * 1000));
            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));
            when(monitoringRepository.findLatestMetrics(anyString(), anyInt(), any())).thenReturn(List.of(createSnapshot(8000)));
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            // When
            service.evaluateAlerts();

            // Then 应触发
            verify(monitoringRepository).insertAlertHistory((AlertHistory) any());
        }
    }

    @Nested
    @DisplayName("Webhook 通知")
    class WebhookNotification {

        @Test
        @DisplayName("触发时发送 JSON POST 到 webhook_url")
        @SuppressWarnings("unchecked")
        void shouldSendJsonPostToWebhookUrl() {
            // Given
            AlertRule rule = createRule("GT", 5000, 1, 0);
            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));
            when(monitoringRepository.findLatestMetrics(anyString(), anyInt(), any())).thenReturn(List.of(createSnapshot(8000)));
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            // When
            service.evaluateAlerts();

            // Then
            ArgumentCaptor<AlertHistory> captor = ArgumentCaptor.forClass(AlertHistory.class);
            verify(monitoringRepository).insertAlertHistory(captor.capture());
            AlertHistory history = captor.getValue();
            assertThat(history.getActualValue()).isEqualTo(8000.0);
            assertThat(history.getThreshold()).isEqualTo(5000.0);
            assertThat(history.getWebhookStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("触发后记录告警历史")
        @SuppressWarnings("unchecked")
        void shouldRecordAlertHistory() {
            // Given
            AlertRule rule = createRule("GT", 5000, 1, 0);
            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));
            when(monitoringRepository.findLatestMetrics(anyString(), anyInt(), any())).thenReturn(List.of(createSnapshot(8000)));
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            // When
            service.evaluateAlerts();

            // Then
            verify(monitoringRepository).insertAlertHistory((AlertHistory) any());
        }

        @Test
        @DisplayName("触发后更新 last_fired_at")
        @SuppressWarnings("unchecked")
        void shouldUpdateLastFiredAt() {
            // Given
            AlertRule rule = createRule("GT", 5000, 1, 0);
            when(monitoringRepository.findEnabledAlertRules()).thenReturn(List.of(rule));
            when(monitoringRepository.findLatestMetrics(anyString(), anyInt(), any())).thenReturn(List.of(createSnapshot(8000)));
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            // When
            service.evaluateAlerts();

            // Then
            verify(monitoringRepository).updateAlertRuleLastFired(any(), any());
        }
    }

    @Nested
    @DisplayName("禁用规则")
    class DisabledRules {

        @Test
        @DisplayName("enabled=false 的规则不参与评估")
        void shouldSkipDisabledRules() {
            // Given
            AlertRule rule = createRule("GT", 5000, 1, 0);
            rule.setEnabled(false);
            when(monitoringRepository.findEnabledAlertRules()).thenReturn(Collections.emptyList());

            // When
            service.evaluateAlerts();

            // Then
            verify(monitoringRepository, never()).findLatestMetrics(anyString(), anyInt(), any());
            verify(monitoringRepository, never()).insertAlertHistory((AlertHistory) any());
        }
    }
}
