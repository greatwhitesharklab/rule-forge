package com.ruleforge.executor.app.controller;

import com.ruleforge.decision.entity.DecisionFlowLog;
import com.ruleforge.decision.entity.DecisionFlowParams;
import com.ruleforge.decision.entity.DecisionRuleLog;
import com.ruleforge.decision.repository.DecisionLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Feature: 仿真日志查询 API
 *
 * SimulationLogController 为 console-app 仿真提供决策日志查询。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationLogController - 仿真日志查询")
class SimulationLogControllerTest {

    @Mock private DecisionLogRepository decisionLogRepository;
    @InjectMocks private SimulationLogController controller;

    @Nested
    @DisplayName("Scenario: 按包路径和时间范围查询日志")
    class QueryLogs {

        // Given 包路径和时间范围
        // When getLogs 被调用
        // Then 返回日志列表
        @Test
        @DisplayName("查询返回日志摘要列表")
        void shouldReturnLogSummaries() {
            DecisionFlowLog log = new DecisionFlowLog();
            log.setId(1L);
            log.setRulePackagePath("luzcred/withdrawal");
            log.setExecutionStatus("SUCCESS");
            log.setRejectCode("PASS");
            log.setTotalTimeMs(120L);

            when(decisionLogRepository.findFlowLogsByPackageAndTimeRange(
                    "luzcred/withdrawal", "2026-05-01", "2026-05-31", 500))
                    .thenReturn(List.of(log));

            List<Map<String, Object>> result = controller.getLogs(
                    "luzcred/withdrawal", "2026-05-01", "2026-05-31", 500);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("id")).isEqualTo(1L);
            assertThat(result.get(0).get("executionStatus")).isEqualTo("SUCCESS");
            assertThat(result.get(0).get("rejectCode")).isEqualTo("PASS");
            assertThat(result.get(0).get("totalTimeMs")).isEqualTo(120L);
        }
    }

    @Nested
    @DisplayName("Scenario: 查询日志参数")
    class QueryParams {

        @Test
        @DisplayName("返回 inputParams 和 outputParams")
        void shouldReturnParams() {
            DecisionFlowParams params = new DecisionFlowParams();
            params.setInputParams("{\"score\":85}");
            params.setOutputParams("{\"decision\":\"APPROVE\"}");

            when(decisionLogRepository.findFlowParamsByFlowLogId(1L)).thenReturn(params);

            Map<String, Object> result = controller.getParams(1L);

            assertThat(result.get("inputParams")).isEqualTo("{\"score\":85}");
            assertThat(result.get("outputParams")).isEqualTo("{\"decision\":\"APPROVE\"}");
        }

        @Test
        @DisplayName("日志不存在时返回空 map")
        void shouldReturnEmptyWhenNotFound() {
            when(decisionLogRepository.findFlowParamsByFlowLogId(999L)).thenReturn(null);

            Map<String, Object> result = controller.getParams(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Scenario: 查询日志规则")
    class QueryRules {

        @Test
        @DisplayName("返回规则名称列表")
        void shouldReturnRuleNames() {
            DecisionRuleLog rule = new DecisionRuleLog();
            rule.setRuleName("checkCreditScore");
            rule.setRuleType("condition");

            when(decisionLogRepository.findRuleLogsByFlowLogId(1L)).thenReturn(List.of(rule));

            List<Map<String, Object>> result = controller.getRules(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("ruleName")).isEqualTo("checkCreditScore");
        }
    }
}
