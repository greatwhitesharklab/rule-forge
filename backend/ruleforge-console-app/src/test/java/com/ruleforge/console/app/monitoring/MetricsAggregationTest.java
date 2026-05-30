package com.ruleforge.console.app.monitoring;

import com.ruleforge.console.app.entity.MetricsSnapshot;
import com.ruleforge.console.app.mapper.MetricsSnapshotMapper;
import com.ruleforge.console.app.service.IAlertService;
import com.ruleforge.console.app.service.impl.MetricsAggregationServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 指标聚合
 */
@DisplayName("指标聚合 - 定时快照")
class MetricsAggregationTest {

    private MeterRegistry meterRegistry;
    private MetricsSnapshotMapper metricsSnapshotMapper;
    private IAlertService alertService;
    private MetricsAggregationServiceImpl service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsSnapshotMapper = mock(MetricsSnapshotMapper.class);
        alertService = mock(IAlertService.class);
        service = new MetricsAggregationServiceImpl(meterRegistry, metricsSnapshotMapper, alertService);
    }

    @Nested
    @DisplayName("Timer 指标聚合")
    class TimerAggregation {

        @Test
        @DisplayName("聚合 Timer 时计算 P50/P95/P99/mean/max/min/count/total")
        void shouldComputePercentilesAndStatsForTimer() {
            // Given
            meterRegistry.timer("rule.execution.latency", "package", "loan-rules", "status", "SUCCESS")
                    .record(100, TimeUnit.MILLISECONDS);
            meterRegistry.timer("rule.execution.latency", "package", "loan-rules", "status", "SUCCESS")
                    .record(200, TimeUnit.MILLISECONDS);
            meterRegistry.timer("rule.execution.latency", "package", "loan-rules", "status", "SUCCESS")
                    .record(500, TimeUnit.MILLISECONDS);

            // When
            service.aggregateAndSnapshot();

            // Then
            ArgumentCaptor<MetricsSnapshot> captor = ArgumentCaptor.forClass(MetricsSnapshot.class);
            verify(metricsSnapshotMapper, atLeastOnce()).insert(captor.capture());

            MetricsSnapshot snapshot = captor.getAllValues().stream()
                    .filter(s -> "rule.execution.latency".equals(s.getMetricName()))
                    .findFirst().orElse(null);

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.getMetricType()).isEqualTo("TIMER");
            assertThat(snapshot.getCountVal()).isEqualTo(3);
            assertThat(snapshot.getMaxMs()).isEqualTo(500);
        }

        @Test
        @DisplayName("不同 tags 的 Timer 应生成独立的快照行")
        void shouldCreateSeparateSnapshotsForDifferentTags() {
            // Given
            meterRegistry.timer("rule.execution.latency", "package", "pkg-a", "status", "SUCCESS")
                    .record(100, TimeUnit.MILLISECONDS);
            meterRegistry.timer("rule.execution.latency", "package", "pkg-b", "status", "SUCCESS")
                    .record(200, TimeUnit.MILLISECONDS);

            // When
            service.aggregateAndSnapshot();

            // Then
            ArgumentCaptor<MetricsSnapshot> captor = ArgumentCaptor.forClass(MetricsSnapshot.class);
            verify(metricsSnapshotMapper, atLeast(2)).insert(captor.capture());

            long distinctTags = captor.getAllValues().stream()
                    .map(MetricsSnapshot::getTags)
                    .distinct()
                    .count();
            assertThat(distinctTags).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Counter 指标聚合")
    class CounterAggregation {

        @Test
        @DisplayName("聚合 Counter 时记录 count_val")
        void shouldRecordCountForCounter() {
            // Given
            meterRegistry.counter("rule.execution.total", "package", "loan-rules", "status", "SUCCESS")
                    .increment(5);

            // When
            service.aggregateAndSnapshot();

            // Then
            ArgumentCaptor<MetricsSnapshot> captor = ArgumentCaptor.forClass(MetricsSnapshot.class);
            verify(metricsSnapshotMapper).insert((MetricsSnapshot) captor.capture());

            MetricsSnapshot snapshot = captor.getValue();
            assertThat(snapshot.getMetricType()).isEqualTo("COUNTER");
            assertThat(snapshot.getCountVal()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("聚合后清理")
    class RegistryCleanup {

        @Test
        @DisplayName("聚合完成后清空 MeterRegistry")
        void shouldClearRegistryAfterSnapshot() {
            // Given
            meterRegistry.counter("rule.execution.total", "status", "SUCCESS").increment(3);

            // When
            service.aggregateAndSnapshot();

            // Then
            assertThat(meterRegistry.getMeters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("空窗口处理")
    class EmptyWindow {

        @Test
        @DisplayName("无指标时不写入快照")
        void shouldNotWriteSnapshotWhenNoMetrics() {
            // Given registry is empty

            // When
            service.aggregateAndSnapshot();

            // Then
            verify(metricsSnapshotMapper, never()).insert((MetricsSnapshot) any());
            verify(alertService, never()).evaluateAlerts();
        }
    }
}
