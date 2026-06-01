package com.ruleforge.console.app.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.SimulationRunEntity;
import com.ruleforge.console.app.mapper.SimulationResultMapper;
import com.ruleforge.console.app.mapper.SimulationRunMapper;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import com.ruleforge.console.service.BatchTestService;
import com.ruleforge.builder.KnowledgeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 规则仿真服务
 *
 * SimulationServiceImpl 提供仿真的启动、进度查询、结果列表和统计。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationServiceImpl - 规则仿真服务")
class SimulationServiceImplTest {

    @Mock private SimulationRunMapper simulationRunMapper;
    @Mock private SimulationResultMapper simulationResultMapper;
    @Mock private BatchTestSessionMapper batchTestSessionMapper;
    @Mock private BatchTestRowMapper batchTestRowMapper;
    @Mock private BatchTestService batchTestService;
    @Mock private KnowledgeBuilder knowledgeBuilder;
    @Mock private ObjectMapper objectMapper;
    @Mock private RestTemplate execRestTemplate;

    /** 同步执行器 — 让异步逻辑在测试线程中直接运行 */
    private final java.util.concurrent.Executor syncExecutor = Runnable::run;

    private SimulationServiceImpl createService() {
        return new SimulationServiceImpl(
                simulationRunMapper, simulationResultMapper,
                batchTestSessionMapper, batchTestRowMapper,
                batchTestService, knowledgeBuilder,
                objectMapper, execRestTemplate,
                "http://localhost:8082", syncExecutor);
    }

    @Nested
    @DisplayName("Scenario: 启动仿真 — 无历史日志")
    class StartSimulationNoLogs {

        // Given executor-app 返回空日志列表
        // When startSimulation 被调用
        // Then 状态直接变为 COMPLETED（无日志数据）
        @Test
        @DisplayName("无历史日志时直接完成")
        void shouldCompleteWhenNoLogs() {
            // Given — insert 成功后设置 ID
            doAnswer(inv -> {
                SimulationRunEntity run = inv.getArgument(0);
                run.setId(1L);
                return 1;
            }).when(simulationRunMapper).insert(any(SimulationRunEntity.class));

            // execRestTemplate 返回空列表
            when(execRestTemplate.getForEntity(anyString(), eq(List.class)))
                    .thenReturn(new org.springframework.http.ResponseEntity<>(List.of(), org.springframework.http.HttpStatus.OK));

            SimulationServiceImpl service = createService();

            // When
            Long runId = service.startSimulation("project", "pkg", "a.xml", null,
                    "2026-05-01", "2026-05-31", "admin");

            // Then
            assertThat(runId).isEqualTo(1L);
            // COMPLETED status（无日志数据）
            verify(simulationRunMapper).updateProgress(eq(1L), eq(SimulationRunEntity.STATUS_COMPLETED),
                    eq(0), eq(0), eq(0.0), eq(0), eq(0), eq(0), eq("无历史日志数据"));
        }
    }

    @Nested
    @DisplayName("Scenario: 查询仿真进度")
    class GetSimulationProgress {

        // Given runId 存在
        // When getSimulationProgress 被调用
        // Then 返回正确的进度信息
        @Test
        @DisplayName("返回运行进度")
        void shouldReturnProgress() {
            SimulationRunEntity run = new SimulationRunEntity();
            run.setId(1L);
            run.setStatus(SimulationRunEntity.STATUS_RUNNING);
            run.setTotalLogs(100);
            run.setTotalCompared(50);
            run.setTotalDivergent(5);
            run.setDivergenceRate(10.0);
            run.setHighSeverityCount(1);
            run.setMediumSeverityCount(2);
            run.setLowSeverityCount(2);

            when(simulationRunMapper.selectById(1L)).thenReturn(run);

            SimulationServiceImpl service = createService();
            Map<String, Object> progress = service.getSimulationProgress(1L);

            assertThat(progress.get("runId")).isEqualTo(1L);
            assertThat(progress.get("status")).isEqualTo("RUNNING");
            assertThat(progress.get("totalLogs")).isEqualTo(100);
            assertThat(progress.get("totalCompared")).isEqualTo(50);
            assertThat(progress.get("totalDivergent")).isEqualTo(5);
            assertThat(progress.get("divergenceRate")).isEqualTo(10.0);
            assertThat(progress.get("highSeverityCount")).isEqualTo(1);
        }

        // Given runId 不存在
        // When getSimulationProgress 被调用
        // Then 返回 NOT_FOUND
        @Test
        @DisplayName("runId 不存在时返回 NOT_FOUND")
        void shouldReturnNotFound() {
            when(simulationRunMapper.selectById(999L)).thenReturn(null);

            SimulationServiceImpl service = createService();
            Map<String, Object> progress = service.getSimulationProgress(999L);

            assertThat(progress.get("status")).isEqualTo("NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("Scenario: 查询仿真对比结果")
    class ListSimulationResults {

        // Given runId 有对比结果
        // When listSimulationResults 被调用
        // Then 返回分页结果
        @Test
        @DisplayName("返回分页对比结果")
        void shouldReturnPagedResults() {
            com.ruleforge.console.app.entity.SimulationResultEntity r =
                    new com.ruleforge.console.app.entity.SimulationResultEntity();
            r.setId(1L);
            r.setOriginalFlowLogId(100L);
            r.setOriginalExecutionStatus("SUCCESS");
            r.setOriginalRejectCode("PASS");
            r.setSimulatedExecutionStatus("SUCCESS");
            r.setSimulatedRejectCode("PASS");
            r.setStatusMatch(true);
            r.setResultMatch(true);
            r.setHasDivergence(false);
            r.setDivergenceSeverity("NONE");

            when(simulationResultMapper.selectByRunId(1L, 20, 0)).thenReturn(List.of(r));

            SimulationServiceImpl service = createService();
            List<Map<String, Object>> results = service.listSimulationResults(1L, 1, 20);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).get("originalFlowLogId")).isEqualTo(100L);
            assertThat(results.get(0).get("divergenceSeverity")).isEqualTo("NONE");
        }
    }

    @Nested
    @DisplayName("Scenario: 查询仿真统计")
    class GetSimulationStats {

        // Given 包路径下有多个仿真 run
        // When getSimulationStats 被调用
        // Then 返回聚合统计
        @Test
        @DisplayName("返回聚合统计")
        void shouldReturnAggregatedStats() {
            SimulationRunEntity run1 = new SimulationRunEntity();
            run1.setTotalLogs(100);
            run1.setTotalCompared(100);
            run1.setTotalDivergent(5);

            SimulationRunEntity run2 = new SimulationRunEntity();
            run2.setTotalLogs(50);
            run2.setTotalCompared(50);
            run2.setTotalDivergent(3);

            when(simulationRunMapper.selectByPackagePath("project/pkg", 100, 0))
                    .thenReturn(List.of(run1, run2));

            SimulationServiceImpl service = createService();
            Map<String, Object> stats = service.getSimulationStats("project/pkg", null, null);

            assertThat(stats.get("totalRuns")).isEqualTo(2);
            assertThat(stats.get("totalLogs")).isEqualTo(150);
            assertThat(stats.get("totalCompared")).isEqualTo(150);
            assertThat(stats.get("totalDivergent")).isEqualTo(8);
            assertThat(stats.get("averageDivergenceRate")).isEqualTo(5.33);
        }
    }

    @Nested
    @DisplayName("Scenario: 查询历史仿真记录")
    class ListSimulationRuns {

        // Given 包路径下有历史仿真
        // When listSimulationRuns 被调用
        // Then 返回分页列表
        @Test
        @DisplayName("返回分页历史仿真列表")
        void shouldReturnPagedRuns() {
            SimulationRunEntity run = new SimulationRunEntity();
            run.setId(1L);
            run.setRulePackagePath("project/pkg");
            run.setStartTime("2026-05-01");
            run.setEndTime("2026-05-31");
            run.setStatus(SimulationRunEntity.STATUS_COMPLETED);
            run.setTotalLogs(100);
            run.setTotalDivergent(5);
            run.setDivergenceRate(5.0);

            when(simulationRunMapper.selectByPackagePath("project/pkg", 10, 0))
                    .thenReturn(List.of(run));

            SimulationServiceImpl service = createService();
            List<Map<String, Object>> runs = service.listSimulationRuns("project/pkg", 1, 10);

            assertThat(runs).hasSize(1);
            assertThat(runs.get(0).get("id")).isEqualTo(1L);
            assertThat(runs.get(0).get("status")).isEqualTo("COMPLETED");
            assertThat(runs.get(0).get("totalLogs")).isEqualTo(100);
        }
    }
}
