package com.ruleforge.console.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.agent.AgentConfigService;
import com.ruleforge.console.app.agent.AgentService;
import com.ruleforge.console.app.agent.audit.AgentAuditEntity;
import com.ruleforge.console.app.agent.audit.AgentAuditService;
import com.ruleforge.console.app.agent.audit.AgentRateLimiter;
import com.ruleforge.console.app.agent.config.VendorPresets;
import com.ruleforge.console.app.agent.model.AgentModels.*;
import com.ruleforge.console.app.agent.tool.ToolExecutor;
import com.ruleforge.console.app.agent.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Agent AI 助手 REST API
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AgentConfigService configService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final AgentAuditService agentAuditService;     // V5.22.2
    private final AgentRateLimiter agentRateLimiter;        // V5.22.2

    // ========== 对话 ==========

    /**
     * 发送消息 — SSE 流式响应
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String message = request.get("message");

        if (sessionId == null || sessionId.isEmpty()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data("sessionId 必填"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        if (message == null || message.isEmpty()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data("message 必填"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        return agentService.chat(sessionId, message);
    }

    // ========== 会话管理 ==========

    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@RequestBody Map<String, String> request) {
        String title = request.getOrDefault("title", "新对话");
        String project = request.get("project");
        String createdBy = request.getOrDefault("createdBy", "anonymous");
        ChatSession session = agentService.createSession(title, project, createdBy);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions(@RequestParam(required = false) String project) {
        return ResponseEntity.ok(agentService.listSessions(project));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<?> getSessionMessages(@PathVariable String sessionId) {
        return ResponseEntity.ok(agentService.getSessionMessages(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable String sessionId) {
        agentService.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ========== 配置管理（DB） ==========

    /**
     * 获取所有配置
     */
    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        Map<String, String> config = configService.getAll();
        // 脱敏 api_key
        String apiKey = config.getOrDefault("llm.api_key", "");
        if (apiKey != null && apiKey.length() > 8) {
            config.put("llm.api_key", apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
        }
        return ResponseEntity.ok(config);
    }

    /**
     * 更新配置项
     */
    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, String> config) {
        for (Map.Entry<String, String> entry : config.entrySet()) {
            configService.set(entry.getKey(), entry.getValue());
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 获取支持的厂商预设列表
     */
    @GetMapping("/vendors")
    public ResponseEntity<?> getVendors() {
        return ResponseEntity.ok(configService.getVendors());
    }

    /**
     * 应用厂商预设（自动填充 base_url + model）
     */
    @PostMapping("/vendors/{vendorId}/apply")
    public ResponseEntity<?> applyVendor(@PathVariable String vendorId) {
        try {
            configService.applyVendor(vendorId);
            return ResponseEntity.ok(Map.of("success", true, "vendor", vendorId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 测试 LLM 连接
     */
    @PostMapping("/config/test-connection")
    public ResponseEntity<?> testConnection() {
        Map<String, Object> result = configService.testConnection();
        return ResponseEntity.ok(result);
    }

    // ========== 工具 & 状态 ==========

    @GetMapping("/tools")
    public ResponseEntity<?> listTools() {
        return ResponseEntity.ok(toolRegistry.getAllTools());
    }

    /**
     * V5.22 — 直接调用 agent 工具(LLM agent / CLI 走这里,不走 chat 流)
     * V5.22.2 — 加 rate limit (100/小时 per user + per session) + audit
     *
     * POST /ruleforge/agent/tools/{name}
     * body: 工具参数(JSON 对象)
     * 返: 工具执行结果(JSON 字符串)
     */
    @PostMapping("/tools/{name}")
    public ResponseEntity<?> invokeTool(@PathVariable String name, @RequestBody(required = false) Map<String, Object> args) {
        if (toolRegistry.getTool(name) == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "tool_not_found",
                    "name", name
            ));
        }
        long start = System.currentTimeMillis();
        String argsJson = "{}";
        try {
            argsJson = args == null ? "{}" : objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            argsJson = "{}";
        }

        // V5.22.2 — 限流(100 calls / hour per user + per session)
        String userId = null;
        String sessionId = null;
        String messageId = null;
        if (args != null) {
            userId = (String) args.get("createdBy");
            if (userId == null || userId.isEmpty()) userId = (String) args.get("submittedBy");
            if (userId == null || userId.isEmpty()) userId = (String) args.get("reviewer");
            if (userId == null || userId.isEmpty()) userId = "anonymous";
            sessionId = (String) args.get("sessionId");
            messageId = (String) args.get("messageId");
        } else {
            userId = "anonymous";
        }
        try {
            agentRateLimiter.check(userId, sessionId);
        } catch (AgentRateLimiter.RateLimitExceededException e) {
            // 审计一次限流事件
            agentAuditService.record(sessionId, messageId, userId, name, argsJson, null,
                    AgentAuditEntity.STATUS_RATE_LIMITED, "rate_limit_exceeded", e.getMessage(), 0);
            return ResponseEntity.status(429).body(Map.of(
                    "error", "rate_limit_exceeded",
                    "message", e.getMessage(),
                    "maxPerHour", agentRateLimiter.getMaxPerHour(),
                    "retryAfterSeconds", e.getRetryAfterSeconds()  // V5.22.3
            ));
        }

        try {
            String result = toolExecutor.execute(name, argsJson);
            long durationMs = System.currentTimeMillis() - start;
            // 审计一次成功
            agentAuditService.record(sessionId, messageId, userId, name, argsJson, result,
                    AgentAuditEntity.STATUS_OK, null, null, durationMs);
            // 工具返的可能是 JSON 字符串,尝试 parse 后返;不能 parse 就当 string
            try {
                return ResponseEntity.ok(objectMapper.readTree(result));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("raw", result));
            }
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            log.error("Tool invocation failed: {}", name, e);
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // 审计一次错误
            agentAuditService.record(sessionId, messageId, userId, name, argsJson, null,
                    AgentAuditEntity.STATUS_ERROR, "tool_execution_failed", errMsg, durationMs);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "tool_execution_failed",
                    "name", name,
                    "message", errMsg
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
                "available", agentService.isAvailable(),
                "toolsCount", toolRegistry.getAllTools().size(),
                "vendor", configService.get("llm.vendor"),
                "model", configService.get("llm.model")
        ));
    }
}
