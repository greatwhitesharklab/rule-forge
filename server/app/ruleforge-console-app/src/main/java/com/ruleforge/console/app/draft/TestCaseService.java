package com.ruleforge.console.app.draft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 草稿测试用例服务 (V5.22.1)
 *
 * <p>BA 视角:在 AI 助手面板 → 草稿详情 → "测试用例" tab 增删改跑
 * <p>LLM 视角:generate_test_cases 自动生成一组,然后 LLM 调 add_test_case 落库
 * <p>执行:run_saved_tests 拿某 draft 下所有 saved test case,逐个跑 matchRow
 *
 * <p>权限域:ruleforge_db (跟 rf_draft 同源)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final TestCaseMapper testCaseMapper;
    private final ObjectMapper objectMapper;

    /**
     * 列草稿下所有测试用例
     */
    public List<TestCaseEntity> listByDraftId(String draftId) {
        return testCaseMapper.listByDraftId(draftId);
    }

    /**
     * 加测试用例
     *
     * @param draftId        关联草稿
     * @param name           用例名
     * @param description    描述
     * @param inputs         入参 JSON 字符串
     * @param expectedRowId  期望命中的行 ID(可空)
     * @param createdBy      创建人
     * @param source         来源:MANUAL / LLM
     */
    @Transactional
    public TestCaseEntity addTestCase(String draftId, String name, String description,
                                      String inputs, String expectedRowId,
                                      String createdBy, String source) {
        validateInputsJson(inputs);

        TestCaseEntity tc = new TestCaseEntity();
        tc.setTestCaseId(generateTestCaseId());
        tc.setDraftId(draftId);
        tc.setName(name);
        tc.setDescription(description);
        tc.setInputs(inputs);
        tc.setExpectedRowId(expectedRowId);
        tc.setCreatedBy(createdBy);
        tc.setSource(source != null ? source : TestCaseEntity.SOURCE_MANUAL);

        testCaseMapper.insert(tc);
        log.info("[TestCase] {} 添用例 draftId={} name='{}' by={}",
                tc.getTestCaseId(), draftId, name, createdBy);
        return tc;
    }

    /**
     * 删测试用例
     */
    @Transactional
    public boolean deleteTestCase(String testCaseId) {
        int n = testCaseMapper.deleteByTestCaseId(testCaseId);
        if (n > 0) {
            log.info("[TestCase] 删 testCaseId={}", testCaseId);
            return true;
        }
        return false;
    }

    /**
     * 草稿下用例总数
     */
    public int countByDraftId(String draftId) {
        return testCaseMapper.listByDraftId(draftId).size();
    }

    /**
     * 导出 DTO
     */
    public ObjectNode toDto(TestCaseEntity tc) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("testCaseId", tc.getTestCaseId());
        out.put("draftId", tc.getDraftId());
        out.put("name", tc.getName());
        out.put("description", tc.getDescription());
        out.put("expectedRowId", tc.getExpectedRowId());
        out.put("createdBy", tc.getCreatedBy());
        out.put("source", tc.getSource());
        out.put("createdAt", tc.getCreatedAt() != null ? tc.getCreatedAt().toInstant().toString() : null);
        out.put("updatedAt", tc.getUpdatedAt() != null ? tc.getUpdatedAt().toInstant().toString() : null);
        try {
            out.set("inputs", objectMapper.readTree(tc.getInputs()));
        } catch (JsonProcessingException e) {
            out.put("inputs", tc.getInputs());
        }
        return out;
    }

    // ========== 校验 / 工具 ==========

    private void validateInputsJson(String inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs 不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(inputs);
            if (!root.isObject()) {
                throw new IllegalArgumentException("inputs 必须是 JSON object (例: {\"age\":17})");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("inputs 不是合法 JSON: " + e.getMessage());
        }
    }

    private static String generateTestCaseId() {
        return "tc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
