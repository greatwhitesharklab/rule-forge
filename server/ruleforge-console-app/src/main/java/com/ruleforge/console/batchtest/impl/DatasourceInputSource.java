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
import java.util.List;
import java.util.Map;

/**
 * DATASOURCE input source(V5.8.0 占位)— 调三方数据源取输入
 *
 * 这是 FLOW+DATASOURCE 模式(用真实三方数据测决策流,不是"测数据源本身")。
 *
 * 协议:
 *   config.config 必须包含:
 *     - "datasourceId"   Long,要调的数据源
 *     - "batchInputs"    List<Map<String,Object>> 一组 input key(如 100 个 ID)
 *   或(更简单,前端常用):
 *     - "datasourceId"   Long
 *     - "valueList"      List<Object> 一组主键值(只填一个字段)
 *     - "inputField"     String 主键字段名(默认 "id")
 *
 * fetchAndInsert 把每条 input 落到 nd_batch_test_row.input_data(JSON),
 * subject 跑的时候反序列化 → 调 flow → 拿 flow 输出。
 *
 * ── V5.8.0 状态:TODO ─────────────────────────────────────────────────
 * 真正的"调三方 API 取 N 个响应"在 DatasourceRoutingProvider 里(在 executor-app
 * 模块),console-app 不能直接调。V5.8.1+ 计划:
 *   方案 A:在 executor-app 暴露 POST /api/datasource/fetch HTTP 端点,
 *          console-app 的 DatasourceInputSource.fetchAndInsert 通过 HTTP 调它
 *   方案 B:把 DatasourceRoutingProvider 拆到独立 ruleforge-decision-runtime 子模块,
 *          console 和 executor 都依赖
 * V5.8.0 阶段 controller 会拒绝 inputSourceType=DATASOURCE,返回 501 Not Implemented。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceInputSource implements InputSource {

    private static final String TYPE = BatchTestSessionEntity.INPUT_DATASOURCE;

    private final BatchTestRowMapper rowMapper;
    private final BatchTestSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public int fetchAndInsert(InputSourceConfig config, Long sessionId) {
        // V5.8.0:暂未实现 — 真正的三方 API 调用需要跨 console/executor 模块的集成
        // (DatasourceRoutingProvider 在 executor-app),在 controller 层会拒绝
        // inputSourceType=DATASOURCE。这里 stub 是为了接口和 BatchTestOrchestrator
        // 能正常 register 这个 Bean,不影响 FLOW+FILE 模式。
        throw new UnsupportedOperationException(
                "DATASOURCE input source 暂未实现(V5.8.1+ 计划通过 HTTP 调 executor-app 的" +
                        " DatasourceRoutingProvider)。当前 session=" + sessionId +
                        " 应当改用 FILE input source 上传 Excel。");
    }

    @Override
    public <T> T deserializeInput(BatchTestRowEntity rowEntity, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(rowEntity.getInputData(), typeRef);
        } catch (Exception e) {
            throw new RuntimeException("行 " + rowEntity.getId() + " input_data 反序列化失败", e);
        }
    }

    // ── V5.8.1+ 会用到的辅助方法(已写好,先不调用)─────────────────────

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
