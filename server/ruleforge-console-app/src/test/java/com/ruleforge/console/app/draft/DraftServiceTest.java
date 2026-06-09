package com.ruleforge.console.app.draft;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.agent.schema.RuleSchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

/**
 * DraftService 单元测试
 *
 * V5.22 — 状态机 + 校验
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DraftService - AI 规则草稿生命周期")
class DraftServiceTest {

    @Mock
    private DraftMapper draftMapper;
    @Mock
    private DraftHistoryService historyService;  // V5.22.3

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RuleSchemaService schemaService;
    private DraftService service;

    @BeforeEach
    void setUp() {
        schemaService = mock(RuleSchemaService.class);
        lenient().when(schemaService.supportedV522Types()).thenReturn(List.of("decision_table", "ul", "decision_tree", "scorecard", "decision_flow", "script_decision_table"));
        service = new DraftService(draftMapper, schemaService, objectMapper, historyService);
    }

    // ========== createDraft ==========

    @Nested
    @DisplayName("Scenario: 创建草稿")
    class CreateDraft {

        @Test
        @DisplayName("Given 合法 content When 创建 Then 写入 DRAFT 状态 + 自动生成 draftId")
        void shouldCreateDraft() {
            // Given
            String content = "{\"type\":\"decision_table\",\"rows\":[{\"rowId\":\"r1\"}],\"columns\":[],\"cellMap\":{}}";

            // When
            DraftEntity d = service.createDraft("decision_table", "demo", content, "user1", "title1", "LLM", "sess1", "msg1");

            // Then
            ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
            verify(draftMapper).insert(captor.capture());
            DraftEntity saved = captor.getValue();
            assertThat(saved.getDraftId()).startsWith("drf_").hasSize(20); // drf_ + 16 hex
            assertThat(saved.getStatus()).isEqualTo(DraftEntity.STATUS_DRAFT);
            assertThat(saved.getRuleType()).isEqualTo("decision_table");
            assertThat(saved.getProject()).isEqualTo("demo");
            assertThat(saved.getCreatedBy()).isEqualTo("user1");
            assertThat(saved.getSource()).isEqualTo("LLM");
            assertThat(saved.getSessionId()).isEqualTo("sess1");
            assertThat(saved.getMessageId()).isEqualTo("msg1");
            assertThat(saved.getExpiresAt()).isNotNull(); // 7 天后过期
        }

        @Test
        @DisplayName("Given content 为 null When 创建 Then 抛 IllegalArgumentException")
        void shouldRejectNullContent() {
            assertThatThrownBy(() -> service.createDraft("decision_table", "demo", null, "u", null, "LLM", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("content");
        }

        @Test
        @DisplayName("Given content 不是合法 JSON When 创建 Then 抛")
        void shouldRejectInvalidJson() {
            assertThatThrownBy(() -> service.createDraft("decision_table", "demo", "not json", "u", null, "LLM", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("JSON");
        }
    }

    // ========== validateContent ==========

    @Nested
    @DisplayName("Scenario: 校验 content")
    class ValidateContent {

        @Test
        @DisplayName("Given decision_table content 缺 rows When 校验 Then 抛")
        void shouldRequireRows() {
            String content = "{\"type\":\"decision_table\",\"columns\":[],\"cellMap\":{}}";
            assertThatThrownBy(() -> service.validateContent("decision_table", content))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rows");
        }

        @Test
        @DisplayName("Given decision_table content 缺 cellMap When 校验 Then 抛")
        void shouldRequireCellMap() {
            String content = "{\"type\":\"decision_table\",\"rows\":[],\"columns\":[]}";
            assertThatThrownBy(() -> service.validateContent("decision_table", content))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cellMap");
        }

        @Test
        @DisplayName("Given ul content 缺 rules When 校验 Then 抛")
        void shouldRequireRulesForUl() {
            String content = "{\"type\":\"ul\"}";
            assertThatThrownBy(() -> service.validateContent("ul", content))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rules");
        }

        @Test
        @DisplayName("Given decision_tree content 缺 rootNodeId When 校验 Then 抛")
        void shouldRequireRootNodeId() {
            String content = "{\"type\":\"decision_tree\",\"nodes\":{}}";
            assertThatThrownBy(() -> service.validateContent("decision_tree", content))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rootNodeId");
        }

        @Test
        @DisplayName("Given scorecard content 缺 threshold When 校验 Then 抛")
        void shouldRequireThreshold() {
            String content = "{\"type\":\"scorecard\",\"conditions\":[]}";
            assertThatThrownBy(() -> service.validateContent("scorecard", content))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("threshold");
        }

        @Test
        @DisplayName("Given 合法 decision_table When 校验 Then 通过")
        void shouldAcceptValidDecisionTable() {
            String content = "{\"type\":\"decision_table\",\"rows\":[],\"columns\":[],\"cellMap\":{}}";
            service.validateContent("decision_table", content); // 不抛
        }

        @Test
        @DisplayName("Given content.type 跟 ruleType 不一致 When 校验 Then 抛")
        void shouldRejectTypeMismatch() {
            String content = "{\"type\":\"ul\",\"rules\":[]}";
            assertThatThrownBy(() -> service.validateContent("decision_table", content))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不一致");
        }
    }

    // ========== 状态机 ==========

    @Nested
    @DisplayName("Scenario: 状态机")
    class StateMachine {

        @Test
        @DisplayName("DRAFT → submitForReview → PENDING_REVIEW")
        void shouldTransitionDraftToPendingReview() {
            // Given
            DraftEntity d = newDraft(DraftEntity.STATUS_DRAFT);
            when(draftMapper.selectByDraftId("d1")).thenReturn(d);
            lenient().when(draftMapper.updateById(any(DraftEntity.class))).thenReturn(1);

            // When
            DraftEntity result = service.submitForReview("d1", "user1");

            // Then
            assertThat(result.getStatus()).isEqualTo(DraftEntity.STATUS_PENDING_REVIEW);
        }

        @Test
        @DisplayName("DRAFT 状态直接 reject 应抛(必须先 PENDING_REVIEW)")
        void shouldNotAllowRejectFromDraft() {
            DraftEntity d = newDraft(DraftEntity.STATUS_DRAFT);
            when(draftMapper.selectByDraftId("d1")).thenReturn(d);
            assertThatThrownBy(() -> service.reject("d1", "reviewer", "reason"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("PENDING_REVIEW → reject → REJECTED + 记录原因")
        void shouldRejectFromPendingReview() {
            DraftEntity d = newDraft(DraftEntity.STATUS_PENDING_REVIEW);
            when(draftMapper.selectByDraftId("d1")).thenReturn(d);
            lenient().when(draftMapper.updateById(any(DraftEntity.class))).thenReturn(1);

            DraftEntity result = service.reject("d1", "reviewer1", "cell r1,c2 字段对不上");
            assertThat(result.getStatus()).isEqualTo(DraftEntity.STATUS_REJECTED);
            assertThat(result.getReviewedBy()).isEqualTo("reviewer1");
            assertThat(result.getReviewComment()).isEqualTo("cell r1,c2 字段对不上");
            assertThat(result.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("PENDING_REVIEW → approve → APPROVED")
        void shouldApprove() {
            DraftEntity d = newDraft(DraftEntity.STATUS_PENDING_REVIEW);
            when(draftMapper.selectByDraftId("d1")).thenReturn(d);
            lenient().when(draftMapper.updateById(any(DraftEntity.class))).thenReturn(1);

            DraftEntity result = service.approve("d1", "reviewer1", "OK");
            assertThat(result.getStatus()).isEqualTo(DraftEntity.STATUS_APPROVED);
        }

        @Test
        @DisplayName("APPROVED → markApplied → 设置 appliedVersion")
        void shouldMarkApplied() {
            DraftEntity d = newDraft(DraftEntity.STATUS_APPROVED);
            when(draftMapper.selectByDraftId("d1")).thenReturn(d);
            lenient().when(draftMapper.updateById(any(DraftEntity.class))).thenReturn(1);

            DraftEntity result = service.markApplied("d1", "v1.0.5");
            assertThat(result.getAppliedVersion()).isEqualTo("v1.0.5");
            assertThat(result.getAppliedAt()).isNotNull();
        }

        @Test
        @DisplayName("DRAFT 状态 markApplied 应抛(必须先 APPROVED)")
        void shouldNotAllowMarkAppliedFromDraft() {
            DraftEntity d = newDraft(DraftEntity.STATUS_DRAFT);
            when(draftMapper.selectByDraftId("d1")).thenReturn(d);
            assertThatThrownBy(() -> service.markApplied("d1", "v1"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Given 草稿不存在 When 操作 Then 抛 IllegalArgumentException")
        void shouldThrowOnMissingDraft() {
            when(draftMapper.selectByDraftId("nonexistent")).thenReturn(null);
            assertThatThrownBy(() -> service.submitForReview("nonexistent", "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不存在");
        }
    }

    // ========== sweepExpired ==========

    @Nested
    @DisplayName("Scenario: 过期清理")
    class SweepExpired {

        @Test
        @DisplayName("sweepExpired 调 mapper.markExpiredDrafts(DRAFT, EXPIRED)")
        void shouldSweepExpiredDrafts() {
            when(draftMapper.markExpiredDrafts(DraftEntity.STATUS_DRAFT, DraftEntity.STATUS_EXPIRED)).thenReturn(3);
            int n = service.sweepExpired();
            assertThat(n).isEqualTo(3);
        }
    }

    // ========== get / list ==========

    @Nested
    @DisplayName("Scenario: 查询")
    class Query {

        @Test
        @DisplayName("get(draftId) 返 Optional<DraftEntity>")
        void shouldReturnOptional() {
            when(draftMapper.selectByDraftId("d1")).thenReturn(newDraft(DraftEntity.STATUS_DRAFT));
            Optional<DraftEntity> result = service.get("d1");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("get(draftId) 不存在返 Optional.empty")
        void shouldReturnEmpty() {
            when(draftMapper.selectByDraftId("none")).thenReturn(null);
            assertThat(service.get("none")).isEmpty();
        }

        @Test
        @DisplayName("limit > 200 应被钳到 50")
        void shouldClampLimit() {
            when(draftMapper.listByProject("demo", 50)).thenReturn(List.of());
            service.listByProject("demo", 9999);
            verify(draftMapper).listByProject("demo", 50);
        }
    }

    // ========== toDto ==========

    @Nested
    @DisplayName("Scenario: DTO 序列化")
    class ToDto {

        @Test
        @DisplayName("toDto 把 entity 转为 JSON 友好的 ObjectNode")
        void shouldConvertToDto() throws Exception {
            DraftEntity d = newDraft(DraftEntity.STATUS_DRAFT);
            d.setContent("{\"type\":\"decision_table\",\"rows\":[]}");
            var node = service.toDto(d);
            assertThat(node.get("draftId").asText()).isEqualTo(d.getDraftId());
            assertThat(node.get("status").asText()).isEqualTo("DRAFT");
            assertThat(node.get("content").get("type").asText()).isEqualTo("decision_table");
        }
    }

    // ========== helper ==========

    private DraftEntity newDraft(String status) {
        DraftEntity d = new DraftEntity();
        d.setDraftId("d1");
        d.setRuleType("decision_table");
        d.setProject("demo");
        d.setContent("{\"type\":\"decision_table\",\"rows\":[],\"columns\":[],\"cellMap\":{}}");
        d.setStatus(status);
        d.setCreatedBy("user1");
        d.setSource("LLM");
        return d;
    }
}
