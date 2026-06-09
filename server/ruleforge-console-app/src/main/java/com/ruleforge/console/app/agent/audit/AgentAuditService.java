package com.ruleforge.console.app.agent.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 审计服务 (V5.22.2)
 *
 * <p>异步写 nd_agent_audit,失败仅 log 不阻断主流程。
 * <p>权限域:app_db
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuditService {

    private final AgentAuditMapper agentAuditMapper;

    /**
     * 异步记录一次工具调用
     *
     * @param sessionId    LLM 会话 ID
     * @param messageId    LLM 消息 ID
     * @param userId       调用人
     * @param toolName     工具名
     * @param argsJson     完整 args JSON(会被截前 500 字符)
     * @param resultJson   工具返回结果 JSON
     * @param status       OK / ERROR / RATE_LIMITED
     * @param errorCode    可空
     * @param errorMessage 可空(截前 500 字符)
     * @param durationMs   执行耗时
     */
    @Async
    public void record(String sessionId, String messageId, String userId,
                       String toolName, String argsJson, String resultJson,
                       String status, String errorCode, String errorMessage,
                       long durationMs) {
        try {
            AgentAuditEntity a = new AgentAuditEntity();
            a.setSessionId(sessionId);
            a.setMessageId(messageId);
            a.setUserId(userId != null ? userId : "anonymous");
            a.setToolName(toolName);
            a.setArgsSummary(truncate(argsJson, 500));
            a.setResultSize(resultJson != null ? resultJson.length() : 0);
            a.setStatus(status);
            a.setErrorCode(errorCode);
            a.setErrorMessage(truncate(errorMessage, 500));
            a.setDurationMs(durationMs);
            agentAuditMapper.insert(a);
        } catch (Exception e) {
            log.warn("[AgentAudit] 写审计失败 tool={} user={}: {}", toolName, userId, e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * V5.22.3 — 按过滤条件列审计(给 BA 看自己调过的工具)。
     * <p>每个参数都可空,空不过滤。limit 默认 50,max 200。
     */
    public List<AgentAuditEntity> listByFilter(String userId, String sessionId, String status, int limit) {
        return agentAuditMapper.listByFilter(userId, sessionId, status, limit);
    }
}
