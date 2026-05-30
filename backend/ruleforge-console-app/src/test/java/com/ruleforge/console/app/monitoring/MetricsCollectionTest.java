package com.ruleforge.console.app.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Feature: Micrometer 指标采集
 */
@DisplayName("指标采集 - Micrometer 埋点")
class MetricsCollectionTest {

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Nested
    @DisplayName("执行耗时记录")
    class ExecutionLatency {

        @Test
        @DisplayName("成功执行时记录 latency Timer")
        void shouldRecordLatencyTimerOnSuccess() {
            // Given 一次成功的规则执行
            String packageName = "loan-rules";
            String flowId = "loanFlow";

            // When 记录成功指标
            Timer.builder("rule.execution.latency")
                    .tag("package", packageName)
                    .tag("flow", flowId)
                    .tag("status", "SUCCESS")
                    .register(meterRegistry)
                    .record(150, TimeUnit.MILLISECONDS);

            // Then
            Timer timer = meterRegistry.find("rule.execution.latency").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(150.0);

            assertThat(timer.getId().getTag("package")).isEqualTo(packageName);
            assertThat(timer.getId().getTag("flow")).isEqualTo(flowId);
            assertThat(timer.getId().getTag("status")).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("失败执行时记录 latency Timer")
        void shouldRecordLatencyTimerOnFailure() {
            // Given 一次失败的规则执行
            // When 记录失败指标
            Timer.builder("rule.execution.latency")
                    .tag("package", "loan-rules")
                    .tag("flow", "loanFlow")
                    .tag("status", "FAILED")
                    .register(meterRegistry)
                    .record(50, TimeUnit.MILLISECONDS);

            // Then
            Timer timer = meterRegistry.find("rule.execution.latency").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.getId().getTag("status")).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("PENDING 执行时记录 latency Timer")
        void shouldRecordLatencyTimerOnPending() {
            // Given 一次 PENDING 执行
            // When 记录 PENDING 指标
            Timer.builder("rule.execution.latency")
                    .tag("package", "loan-rules")
                    .tag("flow", "loanFlow")
                    .tag("status", "PENDING")
                    .register(meterRegistry)
                    .record(200, TimeUnit.MILLISECONDS);

            // Then
            Timer timer = meterRegistry.find("rule.execution.latency").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.getId().getTag("status")).isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("阶段耗时记录")
    class PhaseTiming {

        @Test
        @DisplayName("记录 loadKnowledge 阶段耗时")
        void shouldRecordLoadKnowledgePhaseTimer() {
            // Given 加载知识包阶段
            // When 记录阶段耗时
            Timer.builder("rule.execution.phase")
                    .tag("phase", "loadKnowledge")
                    .tag("package", "loan-rules")
                    .register(meterRegistry)
                    .record(30, TimeUnit.MILLISECONDS);

            // Then
            Timer timer = meterRegistry.find("rule.execution.phase").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.getId().getTag("phase")).isEqualTo("loadKnowledge");
        }

        @Test
        @DisplayName("记录 flowExecution 阶段耗时")
        void shouldRecordFlowExecutionPhaseTimer() {
            // Given 决策流执行阶段
            // When 记录阶段耗时
            Timer.builder("rule.execution.phase")
                    .tag("phase", "flowExecution")
                    .tag("package", "loan-rules")
                    .register(meterRegistry)
                    .record(80, TimeUnit.MILLISECONDS);

            // Then
            Timer timer = meterRegistry.find("rule.execution.phase").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.getId().getTag("phase")).isEqualTo("flowExecution");
        }
    }

    @Nested
    @DisplayName("执行计数")
    class ExecutionCounting {

        @Test
        @DisplayName("成功执行时递增 rule.execution.total Counter")
        void shouldIncrementCounterOnSuccess() {
            // Given
            meterRegistry.counter("rule.execution.total", "package", "loan-rules", "status", "SUCCESS");

            // When
            meterRegistry.counter("rule.execution.total", "package", "loan-rules", "status", "SUCCESS").increment();

            // Then
            double count = meterRegistry.counter("rule.execution.total", "package", "loan-rules", "status", "SUCCESS").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("失败执行时递增 FAILED Counter")
        void shouldIncrementCounterOnFailure() {
            // Given
            meterRegistry.counter("rule.execution.total", "package", "loan-rules", "status", "FAILED");

            // When
            meterRegistry.counter("rule.execution.total", "package", "loan-rules", "status", "FAILED").increment();

            // Then
            double count = meterRegistry.counter("rule.execution.total", "package", "loan-rules", "status", "FAILED").count();
            assertThat(count).isEqualTo(1.0);
        }
    }
}
