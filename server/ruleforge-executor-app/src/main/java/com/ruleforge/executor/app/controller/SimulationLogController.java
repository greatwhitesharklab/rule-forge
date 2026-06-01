package com.ruleforge.executor.app.controller;

import com.ruleforge.decision.entity.DecisionFlowLog;
import com.ruleforge.decision.entity.DecisionFlowParams;
import com.ruleforge.decision.entity.DecisionRuleLog;
import com.ruleforge.decision.repository.DecisionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内部 API — 为 console-app 仿真提供决策日志查询
 *
 * 这些端点不面向终端用户，仅供 console-app 通过 execRestTemplate 调用。
 */
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SimulationLogController {

    private final DecisionLogRepository decisionLogRepository;

    /**
     * 查询指定包路径和时间范围内的决策流日志
     *
     * GET /api/simulation/logs?rulePackagePath=X&startTime=X&endTime=X&limit=500
     */
    @GetMapping("/logs")
    public List<Map<String, Object>> getLogs(
            @RequestParam String rulePackagePath,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "500") int limit) {

        List<DecisionFlowLog> logs = decisionLogRepository.findFlowLogsByPackageAndTimeRange(
                rulePackagePath, startTime, endTime, limit);

        return logs.stream().map(log -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", log.getId());
            map.put("userId", log.getUserId());
            map.put("orderNo", log.getOrderNo());
            map.put("flowId", log.getFlowId());
            map.put("rulePackagePath", log.getRulePackagePath());
            map.put("executionStatus", log.getExecutionStatus());
            map.put("rejectCode", log.getRejectCode());
            map.put("rejectReason", log.getRejectReason());
            map.put("totalTimeMs", log.getTotalTimeMs());
            map.put("createdAt", log.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 获取指定决策流日志的输入/输出参数
     *
     * GET /api/simulation/logs/{flowLogId}/params
     */
    @GetMapping("/logs/{flowLogId}/params")
    public Map<String, Object> getParams(@PathVariable Long flowLogId) {
        DecisionFlowParams params = decisionLogRepository.findFlowParamsByFlowLogId(flowLogId);
        Map<String, Object> result = new HashMap<>();
        if (params != null) {
            result.put("inputParams", params.getInputParams());
            result.put("outputParams", params.getOutputParams());
        }
        return result;
    }

    /**
     * 获取指定决策流日志触发的规则列表
     *
     * GET /api/simulation/logs/{flowLogId}/rules
     */
    @GetMapping("/logs/{flowLogId}/rules")
    public List<Map<String, Object>> getRules(@PathVariable Long flowLogId) {
        List<DecisionRuleLog> rules = decisionLogRepository.findRuleLogsByFlowLogId(flowLogId);
        return rules.stream().map(rule -> {
            Map<String, Object> map = new HashMap<>();
            map.put("ruleName", rule.getRuleName());
            map.put("ruleType", rule.getRuleType());
            return map;
        }).collect(Collectors.toList());
    }
}
