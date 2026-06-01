package com.ruleforge.console.app.controller;

import com.ruleforge.console.app.agent.AgentConfigService;
import com.ruleforge.console.app.agent.AgentService;
import com.ruleforge.console.app.agent.config.VendorPresets;
import com.ruleforge.console.app.agent.model.AgentModels.*;
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
@RequestMapping("/${ruleforgeV2.root.path}/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AgentConfigService configService;
    private final ToolRegistry toolRegistry;

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
