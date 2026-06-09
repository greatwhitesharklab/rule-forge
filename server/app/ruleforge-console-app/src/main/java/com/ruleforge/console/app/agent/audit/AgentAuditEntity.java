package com.ruleforge.console.app.agent.audit;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Agent 工具调用审计 (V5.22.2) — 对应 nd_agent_audit 表
 *
 * <p>权限域:app_db (跟 nd_agent_chat_session / nd_agent_chat_message 同源)
 */
@Data
@TableName("nd_agent_audit")
public class AgentAuditEntity {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_RATE_LIMITED = "RATE_LIMITED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("message_id")
    private String messageId;

    @TableField("user_id")
    private String userId;

    @TableField("tool_name")
    private String toolName;

    @TableField("args_summary")
    private String argsSummary;

    @TableField("result_size")
    private Integer resultSize;

    @TableField("status")
    private String status;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Date createdAt;
}
