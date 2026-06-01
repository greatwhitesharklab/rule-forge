package com.ruleforge.console.app.agent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 配置实体 — 对应 nd_agent_config 表
 */
@Data
@TableName("nd_agent_config")
public class AgentConfigEntity {
    @TableId(type = IdType.INPUT)
    private String configKey;

    private String configValue;

    private String description;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
