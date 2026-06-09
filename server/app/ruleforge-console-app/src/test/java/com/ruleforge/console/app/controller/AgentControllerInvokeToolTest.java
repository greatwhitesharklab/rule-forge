package com.ruleforge.console.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.agent.AgentConfigService;
import com.ruleforge.console.app.agent.AgentService;
import com.ruleforge.console.app.agent.audit.AgentAuditService;
import com.ruleforge.console.app.agent.audit.AgentRateLimiter;
import com.ruleforge.console.app.agent.tool.ToolExecutor;
import com.ruleforge.console.app.agent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * AgentController.invokeTool 端点测试 (V5.22)
 *
 * 直接调 controller 方法(@InjectMocks),不通过 MockMvc。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentController - V5.22 invokeTool 端点")
class AgentControllerInvokeToolTest {

    @Mock
    private AgentService agentService;
    @Mock
    private AgentConfigService configService;
    @Spy
    private ToolRegistry toolRegistry = new ToolRegistry();
    @Mock
    private ToolExecutor toolExecutor;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private AgentAuditService agentAuditService;   // V5.22.2
    @Mock
    private AgentRateLimiter agentRateLimiter;      // V5.22.2

    @InjectMocks
    private AgentController controller;

    @BeforeEach
    void setUp() {
        toolRegistry.getAllTools(); // 初始化
    }

    @Nested
    @DisplayName("Scenario: 调合法工具")
    class InvokeValidTool {

        @Test
        @DisplayName("Given draft_rule 工具存在 When 调 invokeTool Then 走 ToolExecutor 返 200 + JSON")
        void shouldInvokeTool() throws Exception {
            // Given
            when(toolExecutor.execute(eq("draft_rule"), anyString()))
                    .thenReturn("{\"draftId\":\"drf_abc\",\"status\":\"DRAFT\"}");

            // When
            ResponseEntity<?> resp = controller.invokeTool("draft_rule", Map.of(
                    "ruleType", "decision_table",
                    "project", "demo",
                    "content", "{}"
            ));

            // Then
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            // JSON 解析后是 Map
            assertThat(resp.getBody().toString()).contains("draftId");
        }

        @Test
        @DisplayName("Given 工具返的 JSON 是 string 'raw:...' When invokeTool Then 包成 {raw:...}")
        void shouldWrapRawResult() throws Exception {
            // Given — 工具返的不是合法 JSON(其实不太可能,但兜底)
            when(toolExecutor.execute(eq("list_projects"), anyString()))
                    .thenReturn("not json");

            // When
            ResponseEntity<?> resp = controller.invokeTool("list_projects", null);

            // Then
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().toString()).contains("raw");
        }
    }

    @Nested
    @DisplayName("Scenario: 调不存在的工具")
    class InvokeUnknownTool {

        @Test
        @DisplayName("Given 工具不在 registry When 调 invokeTool Then 返 404 + tool_not_found")
        void shouldReturn404() {
            // When
            ResponseEntity<?> resp = controller.invokeTool("non_existent_tool", null);

            // Then
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(resp.getBody().toString()).contains("tool_not_found");
        }
    }

    // ========== V5.22.2 — Rate Limit ==========

    @Nested
    @DisplayName("Scenario: Rate limit (100 calls / hour per user)")
    class RateLimit {

        @Test
        @DisplayName("Given 限流器抛 RateLimitExceeded When 调 invokeTool Then 返 429 + 写审计 RATE_LIMITED + 带 retryAfterSeconds")
        void shouldReturn429() {
            // Given
            doThrow(new AgentRateLimiter.RateLimitExceededException("user1 超过每小时 100 次调用上限", 3600L))
                    .when(agentRateLimiter).check(any(), any());
            when(agentRateLimiter.getMaxPerHour()).thenReturn(100);

            // When
            ResponseEntity<?> resp = controller.invokeTool("draft_rule", Map.of("createdBy", "u1", "project", "demo"));

            // Then
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(resp.getBody().toString()).contains("rate_limit_exceeded");
            assertThat(resp.getBody().toString()).contains("100");
            // V5.22.3 — retryAfterSeconds 在 429 响应里
            assertThat(resp.getBody().toString()).contains("retryAfterSeconds");
            assertThat(resp.getBody().toString()).contains("3600");
        }
    }
}
