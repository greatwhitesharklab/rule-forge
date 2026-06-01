package com.ruleforge.console.app.controller;

import com.ruleforge.console.service.SimulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Feature: 仿真 REST API
 *
 * SimulationController 暴露仿真的启动、进度、结果、统计端点。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationController - 仿真 REST API")
class SimulationControllerTest {

    @Mock private SimulationService simulationService;
    @InjectMocks private SimulationController controller;

    @Nested
    @DisplayName("Scenario: 启动仿真")
    class StartSimulation {

        // Given 仿真参数
        // When POST /startSimulation
        // Then 返回 runId 和 STARTED
        @Test
        @DisplayName("返回 runId 和 STARTED 状态")
        void shouldReturnRunIdAndStarted() {
            when(simulationService.startSimulation(anyString(), anyString(), anyString(),
                    any(), anyString(), anyString(), anyString())).thenReturn(42L);

            Map<String, String> params = new HashMap<>();
            params.put("project", "project");
            params.put("packageId", "pkg");
            params.put("files", "a.xml");
            params.put("startTime", "2026-05-01");
            params.put("endTime", "2026-05-31");

            ResponseEntity<Map<String, Object>> response = controller.startSimulation(params);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().get("runId")).isEqualTo(42L);
            assertThat(response.getBody().get("status")).isEqualTo("STARTED");
        }
    }

    @Nested
    @DisplayName("Scenario: 查询仿真进度")
    class GetSimulationProgress {

        // Given runId 存在且正在运行
        // When GET /simulationProgress?runId=X
        // Then 返回进度信息
        @Test
        @DisplayName("返回进度 JSON")
        void shouldReturnProgress() {
            Map<String, Object> progress = new HashMap<>();
            progress.put("runId", 1L);
            progress.put("status", "RUNNING");
            progress.put("totalLogs", 100);
            when(simulationService.getSimulationProgress(1L)).thenReturn(progress);

            ResponseEntity<Map<String, Object>> response = controller.getSimulationProgress(1L);

            assertThat(response.getBody().get("status")).isEqualTo("RUNNING");
            assertThat(response.getBody().get("totalLogs")).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Scenario: 查询仿真对比结果")
    class ListSimulationResults {

        // Given runId 有结果
        // When GET /simulationResults?runId=X&page=1&size=10
        // Then 返回分页结果
        @Test
        @DisplayName("返回分页对比结果")
        void shouldReturnPagedResults() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 1L);
            row.put("divergenceSeverity", "NONE");
            when(simulationService.listSimulationResults(1L, 1, 10)).thenReturn(List.of(row));

            ResponseEntity<Map<String, Object>> response = controller.listSimulationResults(1L, 1, 10);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).get("divergenceSeverity")).isEqualTo("NONE");
            assertThat(response.getBody().get("page")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Scenario: 查询历史仿真记录")
    class ListSimulationRuns {

        // Given 包路径下有仿真记录
        // When GET /simulationRuns?rulePackagePath=X
        // Then 返回分页列表
        @Test
        @DisplayName("返回历史仿真列表")
        void shouldReturnRuns() {
            Map<String, Object> run = new HashMap<>();
            run.put("id", 1L);
            run.put("status", "COMPLETED");
            when(simulationService.listSimulationRuns("project/pkg", 1, 20)).thenReturn(List.of(run));

            ResponseEntity<Map<String, Object>> response = controller.listSimulationRuns("project/pkg", 1, 20);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> runs = (List<Map<String, Object>>) response.getBody().get("runs");
            assertThat(runs).hasSize(1);
            assertThat(runs.get(0).get("status")).isEqualTo("COMPLETED");
        }
    }

    @Nested
    @DisplayName("Scenario: 查询仿真统计")
    class GetSimulationStats {

        // Given 包路径有仿真数据
        // When GET /simulationStats?rulePackagePath=X
        // Then 返回聚合统计
        @Test
        @DisplayName("返回聚合统计")
        void shouldReturnStats() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRuns", 3);
            stats.put("totalLogs", 500);
            stats.put("averageDivergenceRate", 4.5);
            when(simulationService.getSimulationStats("project/pkg", null, null)).thenReturn(stats);

            ResponseEntity<Map<String, Object>> response = controller.getSimulationStats("project/pkg", null, null);

            assertThat(response.getBody().get("totalRuns")).isEqualTo(3);
            assertThat(response.getBody().get("totalLogs")).isEqualTo(500);
        }
    }
}
