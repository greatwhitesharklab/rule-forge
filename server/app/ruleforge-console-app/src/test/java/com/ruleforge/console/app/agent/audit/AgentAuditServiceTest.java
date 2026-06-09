package com.ruleforge.console.app.agent.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * AgentAuditService 单元测试 (V5.22.2)
 *
 * 测:写 OK / 写 ERROR / 写 RATE_LIMITED / 截断 args 和 error_message
 */
@DisplayName("AgentAuditService - 异步审计")
class AgentAuditServiceTest {

    private AgentAuditMapper mapper;
    private AgentAuditService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AgentAuditMapper.class);
        service = new AgentAuditService(mapper);
    }

    @Test
    @DisplayName("写 OK 状态 + 完整字段")
    void shouldWriteOk() {
        service.record("sess1", "msg1", "BA1", "draft_rule", "{\"x\":1}", "{\"draftId\":\"d1\"}",
                AgentAuditEntity.STATUS_OK, null, null, 42);

        ArgumentCaptor<AgentAuditEntity> captor = ArgumentCaptor.forClass(AgentAuditEntity.class);
        verify(mapper).insert(captor.capture());
        AgentAuditEntity a = captor.getValue();
        assertThat(a.getSessionId()).isEqualTo("sess1");
        assertThat(a.getMessageId()).isEqualTo("msg1");
        assertThat(a.getUserId()).isEqualTo("BA1");
        assertThat(a.getToolName()).isEqualTo("draft_rule");
        assertThat(a.getStatus()).isEqualTo("OK");
        assertThat(a.getErrorCode()).isNull();
        assertThat(a.getResultSize()).isEqualTo(16);  // {"draftId":"d1"} length
        assertThat(a.getDurationMs()).isEqualTo(42L);
    }

    @Test
    @DisplayName("写 ERROR 状态 + 错误码 + 错误信息")
    void shouldWriteError() {
        service.record("s", "m", "u", "list_drafts", "{}", null,
                AgentAuditEntity.STATUS_ERROR, "tool_execution_failed", "boom", 10);

        ArgumentCaptor<AgentAuditEntity> captor = ArgumentCaptor.forClass(AgentAuditEntity.class);
        verify(mapper).insert(captor.capture());
        AgentAuditEntity a = captor.getValue();
        assertThat(a.getStatus()).isEqualTo("ERROR");
        assertThat(a.getErrorCode()).isEqualTo("tool_execution_failed");
        assertThat(a.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("argsSummary / errorMessage 超长时截断到 500 字符")
    void shouldTruncateLongFields() {
        String longJson = "x".repeat(800);
        service.record(null, null, "u", "tool", longJson, null,
                AgentAuditEntity.STATUS_ERROR, "code", longJson, 0);

        ArgumentCaptor<AgentAuditEntity> captor = ArgumentCaptor.forClass(AgentAuditEntity.class);
        verify(mapper).insert(captor.capture());
        AgentAuditEntity a = captor.getValue();
        assertThat(a.getArgsSummary()).hasSize(500);
        assertThat(a.getErrorMessage()).hasSize(500);
    }

    @Test
    @DisplayName("userId 为 null 时默认 'anonymous'")
    void shouldDefaultAnonymous() {
        service.record(null, null, null, "tool", null, null,
                AgentAuditEntity.STATUS_OK, null, null, 0);

        ArgumentCaptor<AgentAuditEntity> captor = ArgumentCaptor.forClass(AgentAuditEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("resultJson 为 null 时 resultSize = 0")
    void shouldHandleNullResult() {
        service.record(null, null, "u", "tool", null, null,
                AgentAuditEntity.STATUS_OK, null, null, 0);

        ArgumentCaptor<AgentAuditEntity> captor = ArgumentCaptor.forClass(AgentAuditEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getResultSize()).isEqualTo(0);
    }
}
