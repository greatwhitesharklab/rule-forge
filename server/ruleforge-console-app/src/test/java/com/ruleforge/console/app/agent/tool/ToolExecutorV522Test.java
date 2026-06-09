package com.ruleforge.console.app.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruleforge.console.app.draft.DraftApplyService;
import com.ruleforge.console.app.draft.DraftEntity;
import com.ruleforge.console.app.draft.DraftHistoryService;
import com.ruleforge.console.app.draft.DraftService;
import com.ruleforge.console.app.draft.TestCaseEntity;
import com.ruleforge.console.app.draft.TestCaseService;
import com.ruleforge.console.app.agent.audit.AgentAuditService;
import com.ruleforge.console.app.service.IAnalysisService;
import com.ruleforge.console.service.impl.RuleForgeRepositoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ToolExecutor V5.22 AI 工具测试
 *
 * 不走 Spring,Mock 所有 service。验证:
 * - draft_rule 校验 + 调 DraftService.createDraft
 * - list_drafts 按 project/status 过滤
 * - get_draft 返 DTO 或 404
 * - submit/approve/reject/apply 状态机
 * - generate_test_cases + run_test 走 LLM agent 流程
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolExecutor - V5.22 AI Rule Authoring tools")
class ToolExecutorV522Test {

    @Mock
    private IAnalysisService analysisService;
    @Mock
    private RuleForgeRepositoryServiceImpl repoService;
    @Mock
    private DraftService draftService;
    @Mock
    private DraftApplyService draftApplyService;
    @Mock
    private TestCaseService testCaseService;
    @Mock
    private DraftHistoryService historyService;  // V5.22.3
    @Mock
    private AgentAuditService auditService;       // V5.22.3

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ToolExecutor(analysisService, repoService, objectMapper, draftService, draftApplyService, testCaseService, historyService, auditService);
    }

    // ========== draft_rule ==========

    @Nested
    @DisplayName("Scenario: draft_rule 工具")
    class DraftRule {

        @Test
        @DisplayName("Given 合法 content When 调 draft_rule Then 调 DraftService.createDraft 并返 draftId")
        void shouldCreateDraft() throws Exception {
            // Given
            String content = "{\"type\":\"decision_table\",\"rows\":[],\"columns\":[],\"cellMap\":{}}";
            DraftEntity d = newDraft("drf_abc", DraftEntity.STATUS_DRAFT, content);
            when(draftService.createDraft(eq("decision_table"), eq("demo"), eq(content),
                    eq("user1"), any(), any(), any(), any()))
                    .thenReturn(d);
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            // When
            String args = "{\"ruleType\":\"decision_table\",\"project\":\"demo\",\"content\":" +
                    objectMapper.writeValueAsString(content) + ",\"createdBy\":\"user1\"}";
            String result = executor.execute(ToolRegistry.DRAFT_RULE, args);

            // Then
            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("draftId").asText()).isEqualTo("drf_abc");
            assertThat(r.get("status").asText()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("Given content 不合法 When 调 draft_rule Then 返 error + 不写 DB")
        void shouldRejectInvalidContent() throws Exception {
            // Given — content 缺 cellMap,validateContent 抛
            doThrow(new IllegalArgumentException("content.cellMap 必填"))
                    .when(draftService).validateContent(eq("decision_table"), anyString());

            // When
            String args = "{\"ruleType\":\"decision_table\",\"project\":\"demo\",\"content\":\"{\\\"type\\\":\\\"decision_table\\\",\\\"rows\\\":[],\\\"columns\\\":[]}\",\"createdBy\":\"u\"}";
            String result = executor.execute(ToolRegistry.DRAFT_RULE, args);

            // Then
            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("error").asText()).isEqualTo("content_validation_failed");
            assertThat(r.get("message").asText()).contains("cellMap");
        }

        @Test
        @DisplayName("Given 缺必填参数 When 调 draft_rule Then 返 error")
        void shouldRequireMandatoryArgs() {
            String result = executor.execute(ToolRegistry.DRAFT_RULE, "{\"ruleType\":\"decision_table\"}");
            assertThat(result).contains("error").contains("必填");
        }
    }

    // ========== list_drafts ==========

    @Nested
    @DisplayName("Scenario: list_drafts 工具")
    class ListDrafts {

        @Test
        @DisplayName("按 project 列草稿")
        void shouldListByProject() throws Exception {
            when(draftService.listByProject("demo", 50)).thenReturn(java.util.List.of(
                    newDraft("d1", "DRAFT", "x"), newDraft("d2", "PENDING_REVIEW", "x")
            ));
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.LIST_DRAFTS,
                    "{\"project\":\"demo\",\"limit\":50}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("count").asInt()).isEqualTo(2);
            assertThat(r.get("drafts").isArray()).isTrue();
        }
    }

    // ========== get_draft ==========

    @Nested
    @DisplayName("Scenario: get_draft 工具")
    class GetDraft {

        @Test
        @DisplayName("给定存在的 draftId 返 DTO")
        void shouldReturnDto() throws Exception {
            DraftEntity d = newDraft("drf_abc", "DRAFT",
                    "{\"type\":\"decision_table\",\"rows\":[],\"columns\":[],\"cellMap\":{}}");
            when(draftService.get("drf_abc")).thenReturn(Optional.of(d));
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.GET_DRAFT, "{\"draftId\":\"drf_abc\"}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("draftId").asText()).isEqualTo("drf_abc");
        }

        @Test
        @DisplayName("给定不存在的 draftId 返 error")
        void shouldReturnNotFound() {
            when(draftService.get("none")).thenReturn(Optional.empty());
            String result = executor.execute(ToolRegistry.GET_DRAFT, "{\"draftId\":\"none\"}");
            assertThat(result).contains("draft_not_found");
        }
    }

    // ========== 状态机 tools ==========

    @Nested
    @DisplayName("Scenario: 审批状态机")
    class StateMachineTools {

        @Test
        @DisplayName("submit_draft 走 DraftService.submitForReview")
        void shouldSubmit() throws Exception {
            DraftEntity d = newDraft("d1", "PENDING_REVIEW", "x");
            when(draftService.submitForReview("d1", "u")).thenReturn(d);
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.SUBMIT_DRAFT,
                    "{\"draftId\":\"d1\",\"submittedBy\":\"u\"}");
            assertThat(objectMapper.readTree(result).get("status").asText()).isEqualTo("PENDING_REVIEW");
        }

        @Test
        @DisplayName("approve_draft 走 DraftService.approve")
        void shouldApprove() throws Exception {
            DraftEntity d = newDraft("d1", "APPROVED", "x");
            when(draftService.approve("d1", "r", "ok")).thenReturn(d);
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.APPROVE_DRAFT,
                    "{\"draftId\":\"d1\",\"reviewer\":\"r\",\"comment\":\"ok\"}");
            assertThat(objectMapper.readTree(result).get("status").asText()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("reject_draft 走 DraftService.reject")
        void shouldReject() throws Exception {
            DraftEntity d = newDraft("d1", "REJECTED", "x");
            when(draftService.reject("d1", "r", "no")).thenReturn(d);
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.REJECT_DRAFT,
                    "{\"draftId\":\"d1\",\"reviewer\":\"r\",\"reason\":\"no\"}");
            assertThat(objectMapper.readTree(result).get("status").asText()).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("apply_draft 走 DraftApplyService.applyToPackage")
        void shouldApply() throws Exception {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("draftId", "d1");
            out.put("newVersion", "v1.0.5");
            when(draftApplyService.applyToPackage(eq("d1"), eq("/pkg"), any(), any(), any())).thenReturn(out);

            String result = executor.execute(ToolRegistry.APPLY_DRAFT,
                    "{\"draftId\":\"d1\",\"packagePath\":\"/pkg\",\"reviewer\":\"r\"}");
            assertThat(objectMapper.readTree(result).get("newVersion").asText()).isEqualTo("v1.0.5");
        }
    }

    // ========== generate_test_cases ==========

    @Nested
    @DisplayName("Scenario: generate_test_cases 工具")
    class GenerateTestCases {

        @Test
        @DisplayName("从 decision_table 的 cellMap 反推测试用例")
        void shouldGenerateFromCellMap() throws Exception {
            String content = """
                    {
                      "type": "decision_table",
                      "rows": [
                        {"rowId": "r1", "remark": "age<18 reject"},
                        {"rowId": "r2", "remark": "income<3000 reject"}
                      ],
                      "columns": [
                        {"colId": "c1", "type": "condition", "variable": "customer.age", "operator": "lt", "datatype": "number"},
                        {"colId": "c2", "type": "condition", "variable": "customer.monthlyIncome", "operator": "lt", "datatype": "number"},
                        {"colId": "c3", "type": "action", "variable": "decision.status", "operator": "assign", "datatype": "string"}
                      ],
                      "cellMap": {
                        "r1,c1": "18",
                        "r1,c3": "'REJECTED'",
                        "r2,c2": "3000",
                        "r2,c3": "'REJECTED'"
                      }
                    }
                    """;
            DraftEntity d = newDraft("d1", "DRAFT", content);
            when(draftService.get("d1")).thenReturn(Optional.of(d));

            String result = executor.execute(ToolRegistry.GENERATE_TEST_CASES,
                    "{\"draftId\":\"d1\",\"count\":5}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("count").asInt()).isEqualTo(2); // 2 个 row
            JsonNode tests = r.get("testCases");
            // 第一个测试用例
            JsonNode t1 = tests.get(0);
            assertThat(t1.get("rowId").asText()).isEqualTo("r1");
            assertThat(t1.get("inputs").get("customer.age").asInt()).isEqualTo(18);
            // cellMap value 保留单引号(schema 设计:字符串要带引号)
            assertThat(t1.get("expectedAction").get("decision.status").asText()).isEqualTo("'REJECTED'");
        }

        @Test
        @DisplayName("草稿不存在返 error")
        void shouldReturnErrorForMissingDraft() {
            when(draftService.get("none")).thenReturn(Optional.empty());
            String result = executor.execute(ToolRegistry.GENERATE_TEST_CASES, "{\"draftId\":\"none\"}");
            assertThat(result).contains("draft_not_found");
        }
    }

    // ========== run_test ==========

    @Nested
    @DisplayName("Scenario: run_test 工具")
    class RunTest {

        @Test
        @DisplayName("测试用例全 PASS — 命中 row r1")
        void shouldMatchRow() throws Exception {
            String content = """
                    {
                      "type": "decision_table",
                      "rows": [{"rowId": "r1", "remark": "age<18 reject"}],
                      "columns": [
                        {"colId": "c1", "type": "condition", "variable": "customer.age", "operator": "lt", "datatype": "number"},
                        {"colId": "c2", "type": "action", "variable": "decision.status", "operator": "assign", "datatype": "string"}
                      ],
                      "cellMap": {
                        "r1,c1": "18",
                        "r1,c2": "'REJECTED'"
                      }
                    }
                    """;
            DraftEntity d = newDraft("d1", "DRAFT", content);
            when(draftService.get("d1")).thenReturn(Optional.of(d));

            // 直接 JSON 内嵌,不再二次编码
            String args = """
                    {
                      "draftId": "d1",
                      "testCases": [
                        {"name": "under18", "rowId": "r1", "inputs": {"customer.age": 17}, "expectedAction": {"decision.status": "REJECTED"}}
                      ]
                    }
                    """;
            String result = executor.execute(ToolRegistry.RUN_TEST, args);

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("passed").asInt()).isEqualTo(1);
            assertThat(r.get("failed").asInt()).isEqualTo(0);
            assertThat(r.get("results").get(0).get("matchedRowId").asText()).isEqualTo("r1");
            assertThat(r.get("results").get(0).get("status").asText()).isEqualTo("PASS");
        }
    }

    // ========== V5.22.1 草稿测试用例持久化 ==========

    @Nested
    @DisplayName("Scenario: list_test_cases / add_test_case / delete_test_case / run_saved_tests")
    class TestCasePersistence {

        @Test
        @DisplayName("list_test_cases 返 testCases 数组 + count")
        void shouldListTestCases() throws Exception {
            when(testCaseService.listByDraftId("d1")).thenReturn(java.util.List.of(
                    newTestCase("tc_1", "d1", "{\"age\":17}", "r1"),
                    newTestCase("tc_2", "d1", "{\"age\":25}", null)
            ));
            lenient().when(testCaseService.toDto(any(TestCaseEntity.class))).thenAnswer(inv -> {
                TestCaseEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("testCaseId", e.getTestCaseId());
                n.put("draftId", e.getDraftId());
                n.put("name", e.getName());
                n.put("expectedRowId", e.getExpectedRowId());
                n.put("source", e.getSource());
                try {
                    n.set("inputs", objectMapper.readTree(e.getInputs()));
                } catch (Exception ex) {
                    n.put("inputs", e.getInputs());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.LIST_TEST_CASES, "{\"draftId\":\"d1\"}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("count").asInt()).isEqualTo(2);
            assertThat(r.get("testCases").isArray()).isTrue();
            assertThat(r.get("testCases").get(0).get("testCaseId").asText()).isEqualTo("tc_1");
        }

        @Test
        @DisplayName("add_test_case 调 service + 返 DTO")
        void shouldAddTestCase() throws Exception {
            String inputs = "{\"age\":17}";
            TestCaseEntity tc = newTestCase("tc_new", "d1", inputs, "r1");
            tc.setName("under18");  // 跟 addTestCase 调用时传的 name 一致
            when(testCaseService.addTestCase(eq("d1"), eq("under18"), any(), eq(inputs),
                    eq("r1"), eq("BA"), eq("MANUAL"))).thenReturn(tc);
            lenient().when(testCaseService.toDto(any(TestCaseEntity.class))).thenAnswer(inv -> {
                TestCaseEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("testCaseId", e.getTestCaseId());
                n.put("draftId", e.getDraftId());
                n.put("name", e.getName());
                n.put("expectedRowId", e.getExpectedRowId());
                try {
                    n.set("inputs", objectMapper.readTree(e.getInputs()));
                } catch (Exception ex) {
                    n.put("inputs", e.getInputs());
                }
                return n;
            });

            String args = "{\"draftId\":\"d1\",\"name\":\"under18\",\"inputs\":" +
                    objectMapper.writeValueAsString(inputs) + ",\"expectedRowId\":\"r1\",\"createdBy\":\"BA\"}";
            String result = executor.execute(ToolRegistry.ADD_TEST_CASE, args);

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("testCaseId").asText()).isEqualTo("tc_new");
            assertThat(r.get("name").asText()).isEqualTo("under18");
        }

        @Test
        @DisplayName("add_test_case inputs 非法时返 error")
        void shouldRejectInvalidInputs() throws Exception {
            doThrow(new IllegalArgumentException("inputs 必须是 JSON object"))
                    .when(testCaseService).addTestCase(anyString(), anyString(), any(), anyString(), any(), anyString(), anyString());

            String result = executor.execute(ToolRegistry.ADD_TEST_CASE,
                    "{\"draftId\":\"d1\",\"name\":\"x\",\"inputs\":\"[]\",\"createdBy\":\"BA\"}");
            assertThat(result).contains("add_test_case_failed");
        }

        @Test
        @DisplayName("delete_test_case 返 deleted=true/false")
        void shouldDeleteTestCase() throws Exception {
            when(testCaseService.deleteTestCase("tc_1")).thenReturn(true);
            String r1 = executor.execute(ToolRegistry.DELETE_TEST_CASE, "{\"testCaseId\":\"tc_1\"}");
            assertThat(objectMapper.readTree(r1).get("deleted").asBoolean()).isTrue();

            when(testCaseService.deleteTestCase("tc_missing")).thenReturn(false);
            String r2 = executor.execute(ToolRegistry.DELETE_TEST_CASE, "{\"testCaseId\":\"tc_missing\"}");
            assertThat(objectMapper.readTree(r2).get("deleted").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("run_saved_tests 全 PASS 当 expectedRowId == matchedRowId")
        void shouldRunSavedTestsPass() throws Exception {
            String content = """
                    {
                      "type": "decision_table",
                      "rows": [{"rowId": "r1", "remark": "age<18 reject"}],
                      "columns": [
                        {"colId": "c1", "type": "condition", "variable": "customer.age", "operator": "lt", "datatype": "number"}
                      ],
                      "cellMap": {"r1,c1": "18"}
                    }
                    """;
            DraftEntity d = newDraft("d1", "DRAFT", content);
            when(draftService.get("d1")).thenReturn(Optional.of(d));
            // tc_1: inputs age=17, expectedRowId=r1 — 走 matchRow 命中 r1 → PASS
            when(testCaseService.listByDraftId("d1")).thenReturn(java.util.List.of(
                    newTestCase("tc_1", "d1", "{\"customer.age\":17}", "r1")
            ));

            String result = executor.execute(ToolRegistry.RUN_SAVED_TESTS, "{\"draftId\":\"d1\"}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("passed").asInt()).isEqualTo(1);
            assertThat(r.get("failed").asInt()).isEqualTo(0);
            assertThat(r.get("results").get(0).get("status").asText()).isEqualTo("PASS");
        }

        @Test
        @DisplayName("run_saved_tests 期望 r2 但只匹配到 r1 → FAIL")
        void shouldRunSavedTestsFail() throws Exception {
            String content = """
                    {
                      "type": "decision_table",
                      "rows": [{"rowId": "r1", "remark": "reject"}],
                      "columns": [
                        {"colId": "c1", "type": "condition", "variable": "customer.age", "operator": "lt", "datatype": "number"}
                      ],
                      "cellMap": {"r1,c1": "18"}
                    }
                    """;
            DraftEntity d = newDraft("d1", "DRAFT", content);
            when(draftService.get("d1")).thenReturn(Optional.of(d));
            // 期望 r2 但实际命中 r1
            when(testCaseService.listByDraftId("d1")).thenReturn(java.util.List.of(
                    newTestCase("tc_1", "d1", "{\"customer.age\":17}", "r2")
            ));

            String result = executor.execute(ToolRegistry.RUN_SAVED_TESTS, "{\"draftId\":\"d1\"}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("passed").asInt()).isEqualTo(0);
            assertThat(r.get("failed").asInt()).isEqualTo(1);
            assertThat(r.get("results").get(0).get("status").asText()).isEqualTo("FAIL");
        }

        @Test
        @DisplayName("run_saved_tests 草稿下无测试用例返 passed/failed=0")
        void shouldReturnEmptyWhenNoSavedTests() throws Exception {
            String content = "{\"type\":\"decision_table\",\"rows\":[],\"columns\":[],\"cellMap\":{}}";
            DraftEntity d = newDraft("d1", "DRAFT", content);
            when(draftService.get("d1")).thenReturn(Optional.of(d));
            when(testCaseService.listByDraftId("d1")).thenReturn(java.util.List.of());

            String result = executor.execute(ToolRegistry.RUN_SAVED_TESTS, "{\"draftId\":\"d1\"}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("passed").asInt()).isEqualTo(0);
            assertThat(r.get("failed").asInt()).isEqualTo(0);
            assertThat(r.get("message").asText()).contains("没有保存的测试用例");
        }
    }

    // ========== V5.22.2 规则健康仪表盘 ==========

    @Nested
    @DisplayName("Scenario: get_rule_health 工具")
    class RuleHealth {

        @Test
        @DisplayName("聚合 coverage / hotRules / anomalies / reject / staleDrafts")
        void shouldAggregateAll() throws Exception {
            // coverage
            when(analysisService.getRuleCoverageAnalysis(eq("demo"), any(), any()))
                    .thenReturn(Map.of("totalRules", 50, "activeRules", 35, "deadRules", 15));
            // hot
            when(analysisService.getRuleFireFrequency(any(), any(), eq("demo")))
                    .thenReturn(java.util.List.of(
                            Map.of("ruleId", "r_hot_1", "fireCount", 1000),
                            Map.of("ruleId", "r_hot_2", "fireCount", 800)
                    ));
            // anomalies
            when(analysisService.detectAnomalies(any(), eq(30), eq(2.0), eq("demo")))
                    .thenReturn(java.util.List.of(
                            Map.of("type", "reject_rate_spike", "severity", "high", "message", "拒绝率飙升")
                    ));
            // reject dist
            when(analysisService.getRejectDistribution(any(), any(), eq("demo"), eq(5)))
                    .thenReturn(java.util.List.of(
                            Map.of("reason", "AGE_TOO_LOW", "count", 100)
                    ));
            // stale drafts (none)
            when(draftService.listByStatus(eq(DraftEntity.STATUS_DRAFT), eq(200)))
                    .thenReturn(java.util.List.of());
            when(draftService.listByStatus(eq(DraftEntity.STATUS_PENDING_REVIEW), eq(200)))
                    .thenReturn(java.util.List.of());

            String result = executor.execute(ToolRegistry.GET_RULE_HEALTH,
                    "{\"project\":\"demo\",\"days\":30}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("project").asText()).isEqualTo("demo");
            assertThat(r.get("days").asInt()).isEqualTo(30);
            assertThat(r.get("coverage").get("totalRules").asInt()).isEqualTo(50);
            assertThat(r.get("hotRules").isArray()).isTrue();
            assertThat(r.get("hotRules").size()).isEqualTo(2);
            assertThat(r.get("recentAnomalies").get(0).get("severity").asText()).isEqualTo("high");
            assertThat(r.get("topRejectReasons").get(0).get("reason").asText()).isEqualTo("AGE_TOO_LOW");
            assertThat(r.get("staleDrafts").isArray()).isTrue();
            assertThat(r.get("staleDraftCount").asInt()).isEqualTo(0);
        }

        @Test
        @DisplayName("DRAFT 滞留 > 3 天进入 staleDrafts")
        void shouldIncludeStaleDrafts() throws Exception {
            when(analysisService.getRuleCoverageAnalysis(any(), any(), any()))
                    .thenReturn(Map.of());
            when(analysisService.getRuleFireFrequency(any(), any(), any()))
                    .thenReturn(java.util.List.of());
            when(analysisService.detectAnomalies(any(), anyInt(), anyDouble(), any()))
                    .thenReturn(java.util.List.of());
            when(analysisService.getRejectDistribution(any(), any(), any(), anyInt()))
                    .thenReturn(java.util.List.of());

            // 一个 5 天前的 DRAFT
            DraftEntity oldDraft = newDraft("drf_old", DraftEntity.STATUS_DRAFT, "{}");
            Date old = new Date(System.currentTimeMillis() - 5L * 24 * 3600 * 1000);
            oldDraft.setCreatedAt(old);
            oldDraft.setProject("demo");
            oldDraft.setTitle("滞留草稿");
            when(draftService.listByStatus(eq(DraftEntity.STATUS_DRAFT), eq(200)))
                    .thenReturn(java.util.List.of(oldDraft));
            when(draftService.listByStatus(eq(DraftEntity.STATUS_PENDING_REVIEW), eq(200)))
                    .thenReturn(java.util.List.of());

            String result = executor.execute(ToolRegistry.GET_RULE_HEALTH, "{\"days\":30}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("staleDraftCount").asInt()).isEqualTo(1);
            JsonNode stale = r.get("staleDrafts").get(0);
            assertThat(stale.get("draftId").asText()).isEqualTo("drf_old");
            assertThat(stale.get("status").asText()).isEqualTo("DRAFT");
            assertThat(stale.get("daysOld").asLong()).isGreaterThanOrEqualTo(5L);
        }

        @Test
        @DisplayName("project 过滤生效 — 不同项目的草稿不计入")
        void shouldFilterByProject() throws Exception {
            when(analysisService.getRuleCoverageAnalysis(any(), any(), any()))
                    .thenReturn(Map.of());
            when(analysisService.getRuleFireFrequency(any(), any(), any()))
                    .thenReturn(java.util.List.of());
            when(analysisService.detectAnomalies(any(), anyInt(), anyDouble(), any()))
                    .thenReturn(java.util.List.of());
            when(analysisService.getRejectDistribution(any(), any(), any(), anyInt()))
                    .thenReturn(java.util.List.of());

            DraftEntity otherProject = newDraft("drf_other", DraftEntity.STATUS_DRAFT, "{}");
            otherProject.setCreatedAt(new Date(System.currentTimeMillis() - 5L * 24 * 3600 * 1000));
            otherProject.setProject("other_project");
            otherProject.setTitle("其他项目");
            when(draftService.listByStatus(eq(DraftEntity.STATUS_DRAFT), eq(200)))
                    .thenReturn(java.util.List.of(otherProject));
            when(draftService.listByStatus(eq(DraftEntity.STATUS_PENDING_REVIEW), eq(200)))
                    .thenReturn(java.util.List.of());

            String result = executor.execute(ToolRegistry.GET_RULE_HEALTH,
                    "{\"project\":\"demo\",\"days\":30}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("staleDraftCount").asInt()).isEqualTo(0);
        }
    }

    // ========== helper ==========

    private TestCaseEntity newTestCase(String testCaseId, String draftId, String inputs, String expectedRowId) {
        TestCaseEntity tc = new TestCaseEntity();
        tc.setTestCaseId(testCaseId);
        tc.setDraftId(draftId);
        tc.setName("auto_" + testCaseId);
        tc.setInputs(inputs);
        tc.setExpectedRowId(expectedRowId);
        tc.setCreatedBy("BA1");
        tc.setSource(TestCaseEntity.SOURCE_MANUAL);
        return tc;
    }

    private DraftEntity newDraft(String id, String status, String content) {
        DraftEntity d = new DraftEntity();
        d.setDraftId(id);
        d.setRuleType("decision_table");
        d.setProject("demo");
        d.setContent(content);
        d.setStatus(status);
        d.setCreatedBy("user1");
        d.setSource("LLM");
        return d;
    }
}
