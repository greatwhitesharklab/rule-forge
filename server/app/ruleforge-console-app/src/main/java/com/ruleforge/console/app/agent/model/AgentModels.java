package com.ruleforge.console.app.agent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 模块领域模型
 */
public class AgentModels {

    /**
     * 聊天会话
     */
    @Data
    @TableName("nd_agent_chat_session")
    public static class ChatSession {
        @TableId(type = IdType.ASSIGN_UUID)
        private String id;

        /** 会话标题（取首条消息摘要） */
        private String title;

        /** 所属项目 */
        private String project;

        /** 创建人 */
        private String createdBy;

        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createTime;

        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updateTime;
    }

    /**
     * 聊天消息
     */
    @Data
    @TableName("nd_agent_chat_message")
    public static class ChatMessage {
        @TableId(type = IdType.ASSIGN_UUID)
        private String id;

        /** 所属会话 ID */
        private String sessionId;

        /** 消息角色: system / user / assistant / tool */
        private String role;

        /** 消息内容 */
        private String content;

        /** 工具调用 ID（仅 role=tool 时） */
        private String toolCallId;

        /** 工具名称（仅 role=tool 时） */
        private String toolName;

        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createTime;
    }

    /**
     * LLM API 请求/响应模型（OpenAI 兼容格式）
     */
    @Data
    public static class ChatCompletionRequest {
        private String model;
        private List<MessageItem> messages;
        private List<ToolDef> tools;
        private Double temperature;
        private Integer maxTokens;
        private Boolean stream;

        @Data
        public static class MessageItem {
            private String role;
            private String content;
            private List<ToolCallItem> toolCalls;
            private String toolCallId;
            private String name;
        }

        @Data
        public static class ToolCallItem {
            private String id;
            private String type;
            private FunctionCall function;
        }

        @Data
        public static class FunctionCall {
            private String name;
            private String arguments;
        }
    }

    @Data
    public static class ChatCompletionResponse {
        private String id;
        private String object;
        private Long created;
        private String model;
        private List<ChoiceItem> choices;

        @Data
        public static class ChoiceItem {
            private Integer index;
            private ChatCompletionRequest.MessageItem message;
            private ChatCompletionRequest.MessageItem delta;
            private String finishReason;
        }
    }

    /**
     * SSE 流式响应数据块
     */
    @Data
    public static class StreamChunk {
        private String id;
        private String object;
        private Long created;
        private String model;
        private List<ChatCompletionResponse.ChoiceItem> choices;
    }

    /**
     * 工具定义
     */
    @Data
    public static class ToolDef {
        private String type = "function";
        private FunctionDef function;

        @Data
        public static class FunctionDef {
            private String name;
            private String description;
            private ParametersDef parameters;
        }

        @Data
        public static class ParametersDef {
            private String type = "object";
            private java.util.Map<String, PropertyDef> properties;
            private java.util.List<String> required;
        }

        @Data
        public static class PropertyDef {
            private String type;
            private String description;
        }
    }
}
