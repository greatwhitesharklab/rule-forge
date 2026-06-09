package com.ruleforge.console.batchtest.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import com.ruleforge.console.batchtest.InputSource;
import com.ruleforge.console.batchtest.InputSourceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DATASOURCE input source(V5.8.0)— 调三方数据源取输入
 *
 * 这是 FLOW+DATASOURCE 模式(用真实三方数据测决策流,不是"测数据源本身")。
 *
 * 协议:
 *   config.config 必须包含:
 *     - "datasourceId"   Long,要调的数据源
 *     - "clazz"          String,实体类名(给 connector 走字段映射)
 *     - "batchInputs"    List<Map<String,Object>> 一组 input key(如 100 个 ID)
 *   或(更简单,前端常用):
 *     - "valueList"      List<Object> 一组主键值(只填一个字段)
 *     - "inputField"     String 主键字段名(默认 "id")
 *
 * fetchAndInsert:
 *   1. 解析 config → (datasourceId, clazz, entityIds, fieldNames)
 *   2. 调 ExecutorDatasourceClient HTTP 拉数据(executor-app 内部路由 + TTL cache)
 *   3. 把响应落到 nd_batch_test_row.input_data(JSON)
 *   4. Flow 跑的时候反序列化 → 拿 flow 输出
 *
 * V5.8.0 状态:已实现,通过 HTTP 跨模块调 executor-app 的 /test/datasource/fetch
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceInputSource implements InputSource {

    private static final String TYPE = BatchTestSessionEntity.INPUT_DATASOURCE;

    private final BatchTestRowMapper rowMapper;
    private final BatchTestSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;
    private final ExecutorDatasourceClient executorClient;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public int fetchAndInsert(InputSourceConfig config, Long sessionId) {
        Map<String, Object> cfg = config.config();
        Long datasourceId = ((Number) cfg.get("datasourceId")).longValue();
        String clazz = (String) cfg.getOrDefault("clazz", "");
        List<Map<String, Object>> batchInputs = extractInputs(cfg);

        if (batchInputs.isEmpty()) {
            throw new IllegalArgumentException("DATASOURCE input source: batchInputs 不能为空");
        }

        return fetchAndInsertRows(datasourceId, clazz, batchInputs, cfg, sessionId);
    }

    /**
     * v5.8.4 refactor:把"调 executor + 插行"核心抽出来,让 orchestrator 在
     * FLOW+DATASOURCE Excel 路径里复用(FLOW+DATASOURCE 的 batchInputs 是从 Excel
     * 解出来的,跟 JSON 路径同形)。
     */
    public int fetchAndInsertRows(Long datasourceId, String clazz,
                                  List<Map<String, Object>> batchInputs,
                                  Map<String, Object> cfg, Long sessionId) {
        if (batchInputs.isEmpty()) {
            throw new IllegalArgumentException("DATASOURCE input source: batchInputs 不能为空");
        }
        // 决定 fieldNames:从 batchInputs 第一行拿所有 key(排除 entityId 字段)
        // 注:理想是从 datasource 的 entityMapping 拿,这里简化为"全部 input 字段"
        String idField = (String) cfg.getOrDefault("inputField", "id");

        List<String> entityIds = new ArrayList<>(batchInputs.size());
        for (Map<String, Object> in : batchInputs) {
            Object idVal = in.get(idField);
            if (idVal == null) {
                throw new IllegalArgumentException(
                        "每条 input 必须有字段 '" + idField + "'(作为 entityId)");
            }
            entityIds.add(String.valueOf(idVal));
        }

        List<String> fieldNames = new ArrayList<>(batchInputs.get(0).keySet());
        fieldNames.remove(idField);  // entityId 不当作字段拉

        log.info("DATASOURCE input source 调 executor 拉取: datasourceId={} clazz={} entities={} fields={}",
                datasourceId, clazz, entityIds.size(), fieldNames.size());

        // 调 executor HTTP
        Map<String, Map<String, Object>> responses = executorClient.fetchFields(
                datasourceId, clazz, entityIds, fieldNames);

        // 把每行落库
        List<BatchTestRowEntity> rows = new ArrayList<>(entityIds.size());
        for (int i = 0; i < entityIds.size(); i++) {
            String entityId = entityIds.get(i);
            Map<String, Object> response = responses.getOrDefault(entityId, Map.of());

            BatchTestRowEntity row = new BatchTestRowEntity();
            row.setSessionId(sessionId);
            row.setRowIndex(i);
            row.setStatus(BatchTestRowEntity.STATUS_PENDING);
            try {
                // input_data 存"原 input + 数据源响应",subject 跑时反序列化
                Map<String, Object> inputData = new HashMap<>();
                inputData.put("request", batchInputs.get(i));
                inputData.put("response", response);
                row.setInputData(objectMapper.writeValueAsString(inputData));
            } catch (Exception e) {
                throw new RuntimeException("input 序列化失败", e);
            }
            rows.add(row);
        }
        for (BatchTestRowEntity row : rows) {
            rowMapper.insert(row);
        }

        // 更新 session.input_source_id(冗余存一下,方便重放)
        BatchTestSessionEntity session = sessionMapper.selectById(sessionId);
        session.setInputSourceId(datasourceId);
        try {
            session.setInputPayload(objectMapper.writeValueAsString(batchInputs));
        } catch (Exception e) {
            log.warn("session.input_payload 序列化失败,忽略", e);
        }
        sessionMapper.updateById(session);

        log.info("DATASOURCE input source 完成: session={} rows={}", sessionId, rows.size());
        return rows.size();
    }

    @Override
    public <T> T deserializeInput(BatchTestRowEntity rowEntity, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(rowEntity.getInputData(), typeRef);
        } catch (Exception e) {
            throw new RuntimeException("行 " + rowEntity.getId() + " input_data 反序列化失败", e);
        }
    }

    /**
     * 从 config 提取 batchInputs,支持两种格式:
     *   1. 完整 inputs: { batchInputs: [ {...}, {...} ] }
     *   2. 简化: { valueList: [v1, v2], inputField: "id" } → 自动包成 [{id: v1}, {id: v2}, ...]
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> extractInputs(Map<String, Object> cfg) {
        if (cfg.containsKey("batchInputs")) {
            return (List<Map<String, Object>>) cfg.get("batchInputs");
        }
        if (cfg.containsKey("valueList")) {
            String field = (String) cfg.getOrDefault("inputField", "id");
            List<Object> values = (List<Object>) cfg.get("valueList");
            List<Map<String, Object>> result = new ArrayList<>(values.size());
            for (Object v : values) {
                result.add(Map.of(field, v));
            }
            return result;
        }
        throw new IllegalArgumentException(
                "DATASOURCE input source 缺少 batchInputs 或 valueList 之一");
    }
}
