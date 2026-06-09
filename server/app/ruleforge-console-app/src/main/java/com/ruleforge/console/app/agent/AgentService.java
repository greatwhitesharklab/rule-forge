package com.ruleforge.console.app.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.agent.config.AgentConfig;
import com.ruleforge.console.app.agent.model.AgentModels.*;
import com.ruleforge.console.app.agent.model.AgentModels.ChatCompletionRequest.*;
import com.ruleforge.console.app.agent.tool.ToolExecutor;
import com.ruleforge.console.app.agent.tool.ToolRegistry;
import com.ruleforge.console.app.mapper.AgentChatMessageMapper;
import com.ruleforge.console.app.mapper.AgentChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 核心服务 — 管理对话上下文、编排 LLM 调用与工具执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentConfigService configService;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final AgentChatSessionMapper sessionMapper;
    private final AgentChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    // ========== 会话管理 ==========

    /**
     * 创建新会话
     */
    public ChatSession createSession(String title, String project, String createdBy) {
        ChatSession session = new ChatSession();
        session.setTitle(title);
        session.setProject(project);
        session.setCreatedBy(createdBy);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    /**
     * 列出所有会话
     */
    public List<ChatSession> listSessions(String project) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        if (project != null && !project.isEmpty()) {
            wrapper.eq(ChatSession::getProject, project);
        }
        wrapper.orderByDesc(ChatSession::getUpdateTime);
        return sessionMapper.selectList(wrapper);
    }

    /**
     * 获取会话的所有消息
     */
    public List<ChatMessage> getSessionMessages(String sessionId) {
        return messageMapper.findBySessionId(sessionId);
    }

    /**
     * 删除会话及其消息
     */
    public void deleteSession(String sessionId) {
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    // ========== 对话 ==========

    /**
     * 发送消息并获取流式 SSE 响应。
     *
     * <p>流程：
     * 1. 保存用户消息到 DB
     * 2. 构建消息上下文（system prompt + 历史消息 + 工具定义）
     * 3. 调用 LLM，SSE 逐 token 推送给前端
     * 4. 如果 LLM 返回 tool_calls → 执行工具 → 再调 LLM → 循环
     * 5. 保存 assistant 消息到 DB
     */
    public SseEmitter chat(String sessionId, String userMessage) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        sseExecutor.execute(() -> {
            try {
                // 1. 保存用户消息
                ChatSession session = sessionMapper.selectById(sessionId);
                if (session == null) {
                    emitter.send(SseEmitter.event().name("error").data("会话不存在"));
                    emitter.complete();
                    return;
                }

                saveMessage(sessionId, "user", userMessage, null, null);

                // 2. 加载历史消息
                List<ChatMessage> history = messageMapper.findBySessionId(sessionId);
                List<MessageItem> context = buildContext(history);

                // 3. 工具定义
                List<ToolDef> tools = toolRegistry.getAllTools();

                // 4. 多轮工具调用循环
                String assistantContent = runAgenticLoop(context, tools, emitter);

                // 5. 保存 assistant 消息
                saveMessage(sessionId, "assistant", assistantContent, null, null);

                // 6. 如果是首条消息，更新会话标题
                if (history.size() <= 1) {
                    String title = userMessage.length() > 50
                            ? userMessage.substring(0, 50) + "..."
                            : userMessage;
                    session.setTitle(title);
                    session.setUpdateTime(LocalDateTime.now());
                    sessionMapper.updateById(session);
                }

                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();

            } catch (Exception e) {
                log.error("Agent chat error for session {}", sessionId, e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("内部错误: " + e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Agentic 循环：调用 LLM → 如果有 tool_calls → 执行 → 再调 LLM → 直到文本响应
     */
    private String runAgenticLoop(List<MessageItem> context, List<ToolDef> tools,
                                  SseEmitter emitter) throws Exception {
        int maxRounds = 10; // 防止无限循环

        for (int round = 0; round < maxRounds; round++) {
            // 用于收集本轮完整响应
            final StringBuilder contentBuilder = new StringBuilder();
            final ChatCompletionResponse[] responseHolder = new ChatCompletionResponse[1];

            // 调用 LLM（流式）
            llmClient.chatStream(context, tools,
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().name("token").data(token));
                            contentBuilder.append(token);
                        } catch (Exception e) {
                            log.warn("SSE send token error", e);
                        }
                    },
                    response -> responseHolder[0] = response
            );

            ChatCompletionResponse response = responseHolder[0];
            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                return contentBuilder.toString();
            }

            ChatCompletionResponse.ChoiceItem choice = response.getChoices().get(0);
            MessageItem assistantMsg = choice.getMessage();

            // 检查是否有 tool_calls
            if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                // 把 assistant 消息（含 tool_calls）加入上下文
                context.add(assistantMsg);

                // 执行每个工具调用
                for (ToolCallItem tc : assistantMsg.getToolCalls()) {
                    String toolName = tc.getFunction().getName();
                    String toolArgs = tc.getFunction().getArguments();

                    log.info("Agent executing tool: {} args: {}", toolName, toolArgs);

                    // 通知前端正在执行工具
                    emitter.send(SseEmitter.event().name("tool_start")
                            .data(objectMapper.writeValueAsString(
                                    Map.of("tool", toolName, "args", toolArgs))));

                    // 执行工具
                    String toolResult = toolExecutor.execute(toolName, toolArgs);

                    // 通知前端工具执行完毕
                    emitter.send(SseEmitter.event().name("tool_end")
                            .data(objectMapper.writeValueAsString(
                                    Map.of("tool", toolName,
                                            "result", truncate(toolResult, 200)))));

                    // 把工具结果加入上下文
                    MessageItem toolMsg = new MessageItem();
                    toolMsg.setRole("tool");
                    toolMsg.setContent(truncate(toolResult, 8000));
                    toolMsg.setToolCallId(tc.getId());
                    toolMsg.setName(toolName);
                    context.add(toolMsg);
                }

                // 继续循环，让 LLM 根据工具结果生成回复
                continue;
            }

            // 没有工具调用，返回文本内容
            return contentBuilder.toString();
        }

        return "抱歉，工具调用轮次已达上限。";
    }

    // ========== 内部方法 ==========

    private List<MessageItem> buildContext(List<ChatMessage> history) {
        List<MessageItem> context = new ArrayList<>();

        // System prompt
        MessageItem systemMsg = new MessageItem();
        systemMsg.setRole("system");
        systemMsg.setContent(configService.get("system_prompt"));
        context.add(systemMsg);

        // 历史消息
        for (ChatMessage msg : history) {
            MessageItem item = new MessageItem();
            item.setRole(msg.getRole());
            item.setContent(msg.getContent());

            if ("tool".equals(msg.getRole())) {
                item.setToolCallId(msg.getToolCallId());
                item.setName(msg.getToolName());
            }

            context.add(item);
        }

        return context;
    }

    private void saveMessage(String sessionId, String role, String content,
                             String toolCallId, String toolName) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolCallId(toolCallId);
        msg.setToolName(toolName);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "...(truncated)" : s;
    }

    /**
     * 检查 Agent 是否可用
     */
    public boolean isAvailable() {
        return Boolean.parseBoolean(configService.get("enabled"))
                && configService.get("llm.api_key") != null
                && !configService.get("llm.api_key").isEmpty();
    }
}
