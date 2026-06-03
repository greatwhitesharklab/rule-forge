package com.ruleforge.console.batchtest.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.batchtest.BatchTestSubject;
import com.ruleforge.console.batchtest.SubjectExecutionContext;
import com.ruleforge.console.batchtest.SubjectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DATASOURCE subject(V5.8.2+ Phase 9)— 直接调三方数据源 connector
 *
 * 适用场景:验证数据源本身的 SLA / 字段映射 / 延迟,不经过决策流。
 * 比 FLOW+DATASOURCE 少一层(没有 KnowledgeSession),所以:
 *   - 更快(每个 row 只调数据源,不再跑整个流)
 *   - 输出更原始(直接是数据源 HTTP 响应,不是流输出)
 *
 * 协议(每行):
 *   row = {
 *     "entityId": "123",          // 主键值
 *     "fieldName": "score",       // 要拉的字段(必填,跟 connector 字段名)
 *     "clazz": "TestEntity"       // 实体类(可选,跟 executor 路由匹配)
 *   }
 *
 * ctx.params:
 *   - datasourceId  Long,要从 params 拿(controller 注入)
 *
 * 调 ExecutorDatasourceClient.fetchFields(...) 走 HTTP,跟 DatasourceInputSource
 * 走同一条链路,只调一次拿到该行所需的 1 个字段值。
 *
 * SubjectResult.output = { response, httpStatus, latencyMs, errorCode }
 *   - response  数据源返回的字段值(原始)
 *   - httpStatus DatasourceRoutingProvider.fetchFieldValue 内部返 -999 表示
 *                "未找到 entity",不返真 HTTP 状态(因为有 cache / 异常处理)
 *   - latencyMs  从进入 execute() 到拿到 response 的耗时
 *   - errorCode  "ENTITY_NOT_FOUND" / "FETCH_ERROR" / null
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceOnlyBatchTestSubject implements BatchTestSubject {

    private static final String TYPE = BatchTestSessionEntity.SUBJECT_DATASOURCE;

    private final ExecutorDatasourceClient executorClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public SubjectResult execute(SubjectExecutionContext ctx) {
        long start = System.currentTimeMillis();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) ctx.input();
            Long datasourceId = (Long) ctx.params().get("datasourceId");
            String clazz = (String) row.getOrDefault("clazz", "");
            String fieldName = (String) row.get("fieldName");
            Object entityIdRaw = row.get("entityId");

            if (datasourceId == null) {
                return SubjectResult.failure("INVALID_CONFIG",
                        "params.datasourceId 缺失", System.currentTimeMillis() - start);
            }
            if (fieldName == null || entityIdRaw == null) {
                return SubjectResult.failure("INVALID_ROW",
                        "row 必须含 entityId + fieldName", System.currentTimeMillis() - start);
            }

            String entityId = String.valueOf(entityIdRaw);

            // 调 executor HTTP 拉 1 个字段
            Map<String, Map<String, Object>> responses = executorClient.fetchFields(
                    datasourceId, clazz, List.of(entityId), List.of(fieldName));

            Map<String, Object> fieldValue = responses.getOrDefault(entityId, Map.of());
            Object value = fieldValue.get(fieldName);

            // 内部约定:value == -999 表示"未找到 entity"
            boolean entityNotFound = value != null && "-999".equals(String.valueOf(value));
            long latency = System.currentTimeMillis() - start;

            Map<String, Object> output = new HashMap<>();
            output.put("response", value);
            output.put("entityNotFound", entityNotFound);
            output.put("clazz", clazz);
            output.put("fieldName", fieldName);
            output.put("datasourceId", datasourceId);

            if (entityNotFound) {
                return SubjectResult.failure("ENTITY_NOT_FOUND",
                        "数据源未找到 clazz=" + clazz + " entityId=" + entityId, latency);
            }
            return SubjectResult.successWithStatus(output, latency, 200);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("DATASOURCE subject 异常 rowId={}", ctx.rowId(), e);
            return SubjectResult.failure("FETCH_ERROR", e.getMessage(), latency);
        }
    }
}
