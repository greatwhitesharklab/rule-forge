package com.ruleforge.console.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import com.ruleforge.console.service.BatchTestService;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.BatchTestFlowMap;
import com.ruleforge.console.model.SaveProcessItemDto;
import com.ruleforge.console.model.TestRuntimeErrorDto;
import com.ruleforge.console.service.TestService;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.runtime.KnowledgePackage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 批量测试异步执行服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTestServiceImpl implements BatchTestService {

    private final BatchTestSessionMapper sessionMapper;
    private final BatchTestRowMapper rowMapper;
    private final TestService testService;
    private final ObjectMapper objectMapper;
    @Qualifier("batchTestExecutor")
    private final Executor batchTestExecutor;

    @Override
    public void executeBatchAsync(Long sessionId, KnowledgePackage knowledgePackage,
                                  String flowId, List<VariableCategory> variableCategories) {
        batchTestExecutor.execute(() -> {
            try {
                executeBatch(sessionId, knowledgePackage, flowId, variableCategories);
            } catch (Exception e) {
                log.error("批量测试执行异常, sessionId={}", sessionId, e);
                updateSessionFailed(sessionId, e.getMessage());
            }
        });
    }

    @Override
    public Map<String, Object> getSessionProgress(Long sessionId) {
        BatchTestSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return Map.of("status", "NOT_FOUND");
        }

        List<Map<String, Object>> statusCounts = rowMapper.countByStatus(sessionId);
        Map<String, Object> progress = new HashMap<>();
        progress.put("sessionId", sessionId);
        progress.put("status", session.getStatus());
        progress.put("totalRows", session.getTotalRows());
        progress.put("progress", session.getProgress());
        progress.put("errorCount", session.getErrorCount());

        // 各状态计数
        for (Map<String, Object> count : statusCounts) {
            String status = (String) count.get("status");
            Long cnt = ((Number) count.get("cnt")).longValue();
            progress.put(status.toLowerCase() + "Count", cnt);
        }

        return progress;
    }

    /**
     * 获取会话实体（内部使用，不在接口中暴露）
     */
    private BatchTestSessionEntity getSession(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    /**
     * 同步执行批量测试（在异步线程中调用）
     */
    private void executeBatch(Long sessionId, KnowledgePackage knowledgePackage,
                              String flowId, List<VariableCategory> variableCategories) throws Exception {
        // 更新会话状态为 RUNNING
        sessionMapper.updateStatus(sessionId, BatchTestSessionEntity.STATUS_RUNNING, 0, 0);

        // 查询所有待处理的行
        List<Map<String, Object>> rowMaps = rowMapper.selectBySessionId(sessionId);
        int totalRows = rowMaps.size();
        int errorCount = 0;
        int processedCount = 0;

        BatchTestFlowMap flowMap = new BatchTestFlowMap();

        for (Map<String, Object> rowMap : rowMaps) {
            Long rowId = ((Number) rowMap.get("id")).longValue();
            String inputData = (String) rowMap.get("input_data");

            try {
                // 反序列化输入数据
                ApplicationAllVariableCategoryMap row = deserializeRow(inputData, variableCategories);

                // 执行测试
                SaveProcessItemDto result = testService.doFlowTest(knowledgePackage, flowId, row, flowMap);

                // 序列化输出并更新行
                String outputJson = serializeRow(row);
                rowMapper.updateResult(rowId, BatchTestRowEntity.STATUS_SUCCESS, outputJson, null);

            } catch (RuleException e) {
                errorCount++;
                log.error("批量测试行 {} 执行失败: {}", rowId, e.getTipMsg());
                rowMapper.updateResult(rowId, BatchTestRowEntity.STATUS_ERROR, null, e.getTipMsg());
            } catch (Exception e) {
                errorCount++;
                log.error("批量测试行 {} 执行异常", rowId, e);
                rowMapper.updateResult(rowId, BatchTestRowEntity.STATUS_ERROR, null, e.getMessage());
            }

            processedCount++;

            // 每处理 50 行更新一次进度
            if (processedCount % 50 == 0 || processedCount == totalRows) {
                double progress = (double) processedCount / totalRows;
                sessionMapper.updateStatus(sessionId, BatchTestSessionEntity.STATUS_RUNNING,
                        progress, errorCount);
            }
        }

        // 更新会话状态为 COMPLETED
        sessionMapper.updateStatus(sessionId, BatchTestSessionEntity.STATUS_COMPLETED, 1.0, errorCount);
        log.info("批量测试完成, sessionId={}, total={}, errors={}", sessionId, totalRows, errorCount);
    }

    private void updateSessionFailed(Long sessionId, String errorMessage) {
        try {
            sessionMapper.updateStatus(sessionId, BatchTestSessionEntity.STATUS_FAILED, 0, 0);
        } catch (Exception e) {
            log.error("更新会话失败状态异常, sessionId={}", sessionId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private ApplicationAllVariableCategoryMap deserializeRow(String json, List<VariableCategory> variableCategories) {
        try {
            Map<String, Object> rawMap = objectMapper.readValue(json, Map.class);
            ApplicationAllVariableCategoryMap result = new ApplicationAllVariableCategoryMap();

            for (VariableCategory vc : variableCategories) {
                Object rawValue = rawMap.get(vc.getName());
                if (rawValue == null) continue;

                if (VariableCategory.PARAM_CATEGORY.equals(vc.getName())) {
                    result.put(vc.getName(), rawValue);
                } else {
                    GeneralEntity entity = new GeneralEntity(vc.getClazz());
                    if (rawValue instanceof Map) {
                        entity.putAll((Map<String, Object>) rawValue);
                    }
                    result.put(vc.getName(), entity);
                }
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化测试数据行失败", e);
        }
    }

    private String serializeRow(ApplicationAllVariableCategoryMap row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (JsonProcessingException e) {
            log.error("序列化测试结果失败", e);
            return "{}";
        }
    }
}
