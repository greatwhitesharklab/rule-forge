package com.ruleforge.console.app.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.entity.SimulationResultEntity;
import com.ruleforge.console.app.entity.SimulationRunEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import com.ruleforge.console.app.mapper.SimulationResultMapper;
import com.ruleforge.console.app.mapper.SimulationRunMapper;
import com.ruleforge.console.service.BatchTestService;
import com.ruleforge.console.service.SimulationService;
import com.ruleforge.decision.util.ComparisonUtils;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.variable.VariableCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 仿真服务实现 — 加载历史日志 → 重放 → 对比
 */
@Slf4j
@Service
public class SimulationServiceImpl implements SimulationService {

    private final SimulationRunMapper simulationRunMapper;
    private final SimulationResultMapper simulationResultMapper;
    private final BatchTestSessionMapper batchTestSessionMapper;
    private final BatchTestRowMapper batchTestRowMapper;
    private final BatchTestService batchTestService;
    private final KnowledgeBuilder knowledgeBuilder;
    private final ObjectMapper objectMapper;
    private final RestTemplate execRestTemplate;
    private final String execUrl;
    private final Executor simulationExecutor;

    private static final int MAX_LOGS = 1000;

    public SimulationServiceImpl(
            SimulationRunMapper simulationRunMapper,
            SimulationResultMapper simulationResultMapper,
            BatchTestSessionMapper batchTestSessionMapper,
            BatchTestRowMapper batchTestRowMapper,
            BatchTestService batchTestService,
            KnowledgeBuilder knowledgeBuilder,
            ObjectMapper objectMapper,
            RestTemplate execRestTemplate,
            @Value("${ruleforge.exec.url}") String execUrl,
            @Qualifier("simulationExecutor") Executor simulationExecutor) {
        this.simulationRunMapper = simulationRunMapper;
        this.simulationResultMapper = simulationResultMapper;
        this.batchTestSessionMapper = batchTestSessionMapper;
        this.batchTestRowMapper = batchTestRowMapper;
        this.batchTestService = batchTestService;
        this.knowledgeBuilder = knowledgeBuilder;
        this.objectMapper = objectMapper;
        this.execRestTemplate = execRestTemplate;
        this.execUrl = execUrl;
        this.simulationExecutor = simulationExecutor;
    }

    @Override
    public Long startSimulation(String project, String packageId, String files, String flowId,
                                String startTime, String endTime, String createdBy) {
        // 1. 创建 simulation run
        SimulationRunEntity run = new SimulationRunEntity();
        run.setProject(project);
        run.setPackageId(packageId);
        run.setRulePackagePath(project + "/" + packageId);
        run.setFiles(files);
        run.setFlowId(flowId);
        run.setStartTime(startTime);
        run.setEndTime(endTime);
        run.setStatus(SimulationRunEntity.STATUS_PENDING);
        run.setCreatedBy(createdBy);
        run.setTotalLogs(0);
        run.setTotalCompared(0);
        run.setTotalDivergent(0);
        run.setDivergenceRate(0.0);
        run.setHighSeverityCount(0);
        run.setMediumSeverityCount(0);
        run.setLowSeverityCount(0);
        simulationRunMapper.insert(run);

        // 2. 异步执行
        simulationExecutor.execute(() -> {
            try {
                executeSimulation(run);
            } catch (Exception e) {
                log.error("仿真执行失败: runId={}", run.getId(), e);
                simulationRunMapper.updateProgress(run.getId(), SimulationRunEntity.STATUS_FAILED,
                        0, 0, 0, 0, 0, 0, e.getMessage().length() > 500 ? e.getMessage().substring(0, 500) : e.getMessage());
            }
        });

        return run.getId();
    }

    /**
     * 异步仿真执行主流程
     */
    private void executeSimulation(SimulationRunEntity run) {
        String rulePackagePath = run.getRulePackagePath();

        // ========== Phase 1: LOADING — 加载历史日志 ==========
        simulationRunMapper.updateProgress(run.getId(), SimulationRunEntity.STATUS_LOADING,
                0, 0, 0.0, 0, 0, 0, null);

        List<Map<String, Object>> logs = fetchHistoricalLogs(rulePackagePath, run.getStartTime(), run.getEndTime());
        if (logs.isEmpty()) {
            simulationRunMapper.updateProgress(run.getId(), SimulationRunEntity.STATUS_COMPLETED,
                    0, 0, 0.0, 0, 0, 0, "无历史日志数据");
            return;
        }

        // 创建 batch test session
        BatchTestSessionEntity session = new BatchTestSessionEntity();
        session.setProject(run.getProject());
        session.setPackageId(run.getPackageId());
        session.setFiles(run.getFiles());
        session.setStatus(BatchTestSessionEntity.STATUS_UPLOADED);
        session.setTotalRows(logs.size());
        session.setProgress(0.0);
        batchTestSessionMapper.insert(session);

        // 插入 batch test rows（每条日志对应一行）
        for (int i = 0; i < logs.size(); i++) {
            Map<String, Object> logEntry = logs.get(i);
            Long flowLogId = ((Number) logEntry.get("id")).longValue();

            // 获取输入参数
            Map<String, Object> params = fetchLogParams(flowLogId);
            String inputParams = (String) params.getOrDefault("inputParams", "{}");

            BatchTestRowEntity row = new BatchTestRowEntity();
            row.setSessionId(session.getId());
            row.setRowIndex(i + 1);
            row.setInputData(inputParams != null ? inputParams : "{}");
            row.setStatus(BatchTestRowEntity.STATUS_PENDING);
            batchTestRowMapper.insert(row);
        }

        // 更新 run 关联
        run.setBatchTestSessionId(session.getId());
        run.setTotalLogs(logs.size());
        simulationRunMapper.updateById(run);

        // ========== Phase 2: RUNNING — 执行批量测试 ==========
        simulationRunMapper.updateProgress(run.getId(), SimulationRunEntity.STATUS_RUNNING,
                0, 0, 0.0, 0, 0, 0, null);

        KnowledgeBase knowledgeBase = buildKnowledgeBase(run.getFiles());
        List<VariableCategory> variableCategories = knowledgeBase.getResourceLibrary().getVariableCategories();
        batchTestService.executeBatchAsync(session.getId(), knowledgeBase.getKnowledgePackage(),
                run.getFlowId(), variableCategories);

        // 等待批量测试完成（轮询）
        waitForBatchTestCompletion(session.getId());

        // ========== Phase 3: COMPARING — 对比结果 ==========
        simulationRunMapper.updateProgress(run.getId(), SimulationRunEntity.STATUS_COMPARING,
                0, 0, 0.0, 0, 0, 0, null);

        List<BatchTestRowEntity> testRows = fetchTestRows(session.getId());
        int totalDivergent = 0;
        int highCount = 0;
        int mediumCount = 0;
        int lowCount = 0;

        for (int i = 0; i < logs.size() && i < testRows.size(); i++) {
            Map<String, Object> originalLog = logs.get(i);
            BatchTestRowEntity testRow = testRows.get(i);
            Long originalFlowLogId = ((Number) originalLog.get("id")).longValue();

            SimulationResultEntity result = compareSingle(originalLog, originalFlowLogId, testRow);
            result.setSimulationRunId(run.getId());
            result.setOriginalFlowLogId(originalFlowLogId);
            simulationResultMapper.insert(result);

            if (Boolean.TRUE.equals(result.getHasDivergence())) {
                totalDivergent++;
                switch (result.getDivergenceSeverity()) {
                    case "HIGH" -> highCount++;
                    case "MEDIUM" -> mediumCount++;
                    case "LOW" -> lowCount++;
                }
            }

            // 每 50 行更新一次进度
            if ((i + 1) % 50 == 0) {
                double rate = logs.size() > 0 ? Math.round((double) totalDivergent / (i + 1) * 10000.0) / 100.0 : 0;
                simulationRunMapper.updateProgress(run.getId(), SimulationRunEntity.STATUS_COMPARING,
                        i + 1, totalDivergent, rate, highCount, mediumCount, lowCount, null);
            }
        }

        // ========== Phase 4: COMPLETED ==========
        double divergenceRate = logs.size() > 0
                ? Math.round((double) totalDivergent / logs.size() * 10000.0) / 100.0 : 0;
        simulationRunMapper.updateProgress(run.getId(), SimulationRunEntity.STATUS_COMPLETED,
                logs.size(), totalDivergent, divergenceRate, highCount, mediumCount, lowCount, null);

        log.info("仿真完成: runId={}, totalLogs={}, divergent={}, rate={}%",
                run.getId(), logs.size(), totalDivergent, divergenceRate);
    }

    /**
     * 对比单条日志
     */
    private SimulationResultEntity compareSingle(Map<String, Object> originalLog,
                                                  Long originalFlowLogId,
                                                  BatchTestRowEntity testRow) {
        SimulationResultEntity result = new SimulationResultEntity();

        // 原始决策数据
        String originalStatus = (String) originalLog.get("executionStatus");
        String originalRejectCode = (String) originalLog.get("rejectCode");
        Long originalTimeMs = originalLog.get("totalTimeMs") != null
                ? ((Number) originalLog.get("totalTimeMs")).longValue() : null;

        result.setOriginalExecutionStatus(originalStatus);
        result.setOriginalRejectCode(originalRejectCode);
        result.setOriginalTotalTimeMs(originalTimeMs);

        // 获取原始输出参数和规则
        Map<String, Object> params = fetchLogParams(originalFlowLogId);
        String originalOutput = (String) params.getOrDefault("outputParams", null);
        result.setOriginalOutputParams(originalOutput);

        List<Map<String, Object>> originalRules = fetchLogRules(originalFlowLogId);
        Set<String> originalRuleNames = originalRules.stream()
                .map(r -> (String) r.get("ruleName"))
                .collect(Collectors.toSet());
        try {
            result.setOriginalRuleNames(objectMapper.writeValueAsString(originalRuleNames));
        } catch (Exception e) {
            result.setOriginalRuleNames("[]");
        }

        // 模拟决策数据
        String simulatedOutput = testRow.getOutputData();
        result.setSimulatedOutputParams(simulatedOutput);
        result.setSimulatedExecutionStatus(BatchTestRowEntity.STATUS_ERROR.equals(testRow.getStatus()) ? "ERROR" : "SUCCESS");
        result.setSimulatedRejectCode(extractRejectCode(simulatedOutput));
        result.setSimulatedRuleNames(serializeRuleNames(extractRuleNames(simulatedOutput)));
        result.setErrorMessage(testRow.getErrorMessage());

        // 如果测试行出错，直接标记为 HIGH
        if (BatchTestRowEntity.STATUS_ERROR.equals(testRow.getStatus())) {
            result.setStatusMatch(false);
            result.setResultMatch(false);
            result.setHasDivergence(true);
            result.setDivergenceSeverity("HIGH");
            return result;
        }

        // 4 维度对比
        boolean statusMatch = ComparisonUtils.nullSafeEquals(originalStatus, result.getSimulatedExecutionStatus());
        boolean resultMatch = ComparisonUtils.nullSafeEquals(originalRejectCode, result.getSimulatedRejectCode());
        result.setStatusMatch(statusMatch);
        result.setResultMatch(resultMatch);

        // 输出字段对比
        String outputDivergence = ComparisonUtils.compareOutputParams(originalOutput, simulatedOutput);
        result.setOutputDivergence(outputDivergence);
        boolean hasOutputDiv = ComparisonUtils.hasOutputDivergence(outputDivergence);

        // 规则执行对比
        Set<String> simulatedRuleNames = parseRuleNames(result.getSimulatedRuleNames());
        String ruleDivergence = ComparisonUtils.compareRules(originalRuleNames, simulatedRuleNames);
        result.setRuleDivergence(ruleDivergence);
        boolean hasRuleDiv = ComparisonUtils.hasRuleDivergence(ruleDivergence);

        // 严重度判定
        boolean hasDivergence = !statusMatch || !resultMatch || hasOutputDiv || hasRuleDiv;
        result.setHasDivergence(hasDivergence);
        result.setDivergenceSeverity(ComparisonUtils.calculateSeverity(statusMatch, resultMatch, hasOutputDiv, hasRuleDiv));

        return result;
    }

    // ===== REST 调用 executor-app =====

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchHistoricalLogs(String rulePackagePath, String startTime, String endTime) {
        try {
            String url = execUrl + "/api/simulation/logs?rulePackagePath=" + rulePackagePath
                    + "&startTime=" + startTime + "&endTime=" + endTime + "&limit=" + MAX_LOGS;
            ResponseEntity<List> response = execRestTemplate.getForEntity(url, List.class);
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("获取历史日志失败: rulePackagePath={}", rulePackagePath, e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchLogParams(Long flowLogId) {
        try {
            String url = execUrl + "/api/simulation/logs/" + flowLogId + "/params";
            ResponseEntity<Map> response = execRestTemplate.getForEntity(url, Map.class);
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("获取日志参数失败: flowLogId={}", flowLogId, e);
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchLogRules(Long flowLogId) {
        try {
            String url = execUrl + "/api/simulation/logs/" + flowLogId + "/rules";
            ResponseEntity<List> response = execRestTemplate.getForEntity(url, List.class);
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("获取日志规则失败: flowLogId={}", flowLogId);
            return Collections.emptyList();
        }
    }

    // ===== 辅助方法 =====

    private KnowledgeBase buildKnowledgeBase(String files) throws RuleException {
        ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
        String[] paths = files.split(";");
        for (String path : paths) {
            String[] subPaths = path.split(",");
            String p = subPaths[0];
            String version = subPaths.length > 1 ? subPaths[1] : null;
            resourceBase.addResource(p, version, true);
        }
        return knowledgeBuilder.buildKnowledgeBase(resourceBase);
    }

    private void waitForBatchTestCompletion(Long sessionId) {
        int maxAttempts = 600; // 最多等 10 分钟（每秒轮询一次）
        for (int i = 0; i < maxAttempts; i++) {
            Map<String, Object> progress = batchTestService.getSessionProgress(sessionId);
            String status = (String) progress.get("status");
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("批量测试轮询超时: sessionId={}", sessionId);
    }

    @SuppressWarnings("unchecked")
    private List<BatchTestRowEntity> fetchTestRows(Long sessionId) {
        List<Map<String, Object>> maps = batchTestRowMapper.selectBySessionId(sessionId);
        List<BatchTestRowEntity> rows = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            BatchTestRowEntity row = new BatchTestRowEntity();
            row.setId(((Number) map.get("id")).longValue());
            row.setSessionId(sessionId);
            row.setRowIndex(((Number) map.getOrDefault("row_index", 0)).intValue());
            row.setInputData((String) map.get("input_data"));
            row.setOutputData((String) map.get("output_data"));
            row.setErrorMessage((String) map.get("error_message"));
            row.setStatus((String) map.get("status"));
            rows.add(row);
        }
        return rows;
    }

    private String extractRejectCode(String outputJson) {
        if (outputJson == null || outputJson.isBlank()) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(outputJson, new TypeReference<>() {});
            // 尝试从常见位置获取 rejectCode
            for (Object value : map.values()) {
                if (value instanceof Map) {
                    Map<?, ?> inner = (Map<?, ?>) value;
                    if (inner.containsKey("rejectCode")) {
                        return String.valueOf(inner.get("rejectCode"));
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private Set<String> extractRuleNames(String outputJson) {
        if (outputJson == null || outputJson.isBlank()) return Collections.emptySet();
        try {
            Map<String, Object> map = objectMapper.readValue(outputJson, new TypeReference<>() {});
            Set<String> names = new HashSet<>();
            for (Object value : map.values()) {
                if (value instanceof Map) {
                    Map<?, ?> inner = (Map<?, ?>) value;
                    if (inner.containsKey("_firedRules")) {
                        Object fired = inner.get("_firedRules");
                        if (fired instanceof List) {
                            ((List<?>) fired).forEach(r -> names.add(String.valueOf(r)));
                        }
                    }
                }
            }
            return names;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private String serializeRuleNames(Set<String> names) {
        try {
            return objectMapper.writeValueAsString(names);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Set<String> parseRuleNames(String json) {
        if (json == null || json.isBlank()) return Collections.emptySet();
        try {
            return objectMapper.readValue(json, new TypeReference<Set<String>>() {});
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    // ===== 查询接口 =====

    @Override
    public Map<String, Object> getSimulationProgress(Long runId) {
        SimulationRunEntity run = simulationRunMapper.selectById(runId);
        if (run == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "NOT_FOUND");
            return result;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("runId", run.getId());
        result.put("status", run.getStatus());
        result.put("totalLogs", run.getTotalLogs());
        result.put("totalCompared", run.getTotalCompared());
        result.put("totalDivergent", run.getTotalDivergent());
        result.put("divergenceRate", run.getDivergenceRate());
        result.put("highSeverityCount", run.getHighSeverityCount());
        result.put("mediumSeverityCount", run.getMediumSeverityCount());
        result.put("lowSeverityCount", run.getLowSeverityCount());
        result.put("errorMessage", run.getErrorMessage());
        return result;
    }

    @Override
    public List<Map<String, Object>> listSimulationResults(Long runId, int page, int size) {
        int offset = (page - 1) * size;
        List<SimulationResultEntity> results = simulationResultMapper.selectByRunId(runId, size, offset);
        List<Map<String, Object>> list = new ArrayList<>();
        for (SimulationResultEntity r : results) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("originalFlowLogId", r.getOriginalFlowLogId());
            map.put("originalExecutionStatus", r.getOriginalExecutionStatus());
            map.put("originalRejectCode", r.getOriginalRejectCode());
            map.put("simulatedExecutionStatus", r.getSimulatedExecutionStatus());
            map.put("simulatedRejectCode", r.getSimulatedRejectCode());
            map.put("statusMatch", r.getStatusMatch());
            map.put("resultMatch", r.getResultMatch());
            map.put("hasDivergence", r.getHasDivergence());
            map.put("divergenceSeverity", r.getDivergenceSeverity());
            map.put("outputDivergence", r.getOutputDivergence());
            map.put("ruleDivergence", r.getRuleDivergence());
            map.put("errorMessage", r.getErrorMessage());
            list.add(map);
        }
        return list;
    }

    @Override
    public Map<String, Object> getSimulationStats(String rulePackagePath, String startTime, String endTime) {
        // 聚合该包路径下所有仿真 run 的统计
        List<SimulationRunEntity> runs = simulationRunMapper.selectByPackagePath(rulePackagePath, 100, 0);
        // TODO: 过滤时间范围

        int totalRuns = runs.size();
        int totalLogs = runs.stream().mapToInt(r -> r.getTotalLogs() != null ? r.getTotalLogs() : 0).sum();
        int totalDivergent = runs.stream().mapToInt(r -> r.getTotalDivergent() != null ? r.getTotalDivergent() : 0).sum();
        int totalCompared = runs.stream().mapToInt(r -> r.getTotalCompared() != null ? r.getTotalCompared() : 0).sum();
        double avgRate = totalCompared > 0 ? Math.round((double) totalDivergent / totalCompared * 10000.0) / 100.0 : 0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRuns", totalRuns);
        stats.put("totalLogs", totalLogs);
        stats.put("totalCompared", totalCompared);
        stats.put("totalDivergent", totalDivergent);
        stats.put("averageDivergenceRate", avgRate);
        return stats;
    }

    @Override
    public List<Map<String, Object>> listSimulationRuns(String rulePackagePath, int page, int size) {
        int offset = (page - 1) * size;
        List<SimulationRunEntity> runs = simulationRunMapper.selectByPackagePath(rulePackagePath, size, offset);
        List<Map<String, Object>> list = new ArrayList<>();
        for (SimulationRunEntity run : runs) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", run.getId());
            map.put("rulePackagePath", run.getRulePackagePath());
            map.put("startTime", run.getStartTime());
            map.put("endTime", run.getEndTime());
            map.put("status", run.getStatus());
            map.put("totalLogs", run.getTotalLogs());
            map.put("totalCompared", run.getTotalCompared());
            map.put("totalDivergent", run.getTotalDivergent());
            map.put("divergenceRate", run.getDivergenceRate());
            map.put("highSeverityCount", run.getHighSeverityCount());
            map.put("mediumSeverityCount", run.getMediumSeverityCount());
            map.put("lowSeverityCount", run.getLowSeverityCount());
            map.put("createdAt", run.getCreatedAt());
            list.add(map);
        }
        return list;
    }
}
