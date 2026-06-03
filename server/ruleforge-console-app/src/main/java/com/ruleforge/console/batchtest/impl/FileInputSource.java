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
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * FILE input source(V5.8.0)— 从用户上传的 Excel / JSON 拿输入
 *
 * 这是 FLOW+FILE 模式(原 BatchTestService 的默认路径,保持兼容)。
 *
 * 协议:
 *   startBatchTest 之前,前端 / 老路径已经往 nd_batch_test_session 写了 session,
 *   往 nd_batch_test_row 写了 rows(每行 input_data = JSON of
 *   ApplicationAllVariableCategoryMap)。本 InputSource 不再重新读 Excel,
 *   只是验证行已存在 + 提供反序列化。
 *
 * 如果是新的 inline JSON 路径(JSON 请求体直接带 rows),本类在 startBatchTest
 * 流程里被 BatchTestService 提前 insert 到 nd_batch_test_row(见 service 实现)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileInputSource implements InputSource {

    private static final String TYPE = BatchTestSessionEntity.INPUT_FILE;

    private final BatchTestRowMapper rowMapper;
    private final BatchTestSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * FILE 模式下,rows 已经在 startBatchTest 之前导入(沿用老路径)或由 controller
     * inline 写入(新路径)。这里只校验行数,不再重新 fetch。
     */
    @Override
    public int fetchAndInsert(InputSourceConfig config, Long sessionId) {
        BatchTestSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalStateException("Session 不存在: " + sessionId);
        }

        // 统计已有行数
        Long count = rowMapper.selectCount(
                new QueryWrapper<BatchTestRowEntity>().eq("session_id", sessionId));
        if (count == 0) {
            throw new IllegalStateException(
                    "FILE input source: session " + sessionId + " 没有 rows(应该已经先导入或 inline 写入)");
        }
        log.debug("FILE input source: session={} rows={}", sessionId, count);
        return count.intValue();
    }

    @Override
    public <T> T deserializeInput(BatchTestRowEntity rowEntity, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(rowEntity.getInputData(), typeRef);
        } catch (Exception e) {
            throw new RuntimeException("行 " + rowEntity.getId() + " input_data 反序列化失败", e);
        }
    }

    /** 便捷方法:FLOW 模式专用,反序列化为 ApplicationAllVariableCategoryMap */
    public ApplicationAllVariableCategoryMap deserializeForFlow(BatchTestRowEntity rowEntity) {
        return deserializeInput(rowEntity, new TypeReference<ApplicationAllVariableCategoryMap>() {});
    }
}
