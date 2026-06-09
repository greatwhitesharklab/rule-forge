package com.ruleforge.console.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * V5.17 user/permission audit log 实体 — 跟 {@code rf_user_audit_log} 表对应。
 *
 * <p>写入点: {@link com.ruleforge.console.audit.service.AuditServiceImpl}
 * 在 AuthServiceImpl / LoginController / PermissionController 各个 user-mgmt
 * 操作路径上调。
 *
 * <p>密码**不**进 entity: 密码变更只走 {@code fieldName="password"} +
 * oldValue/newValue=null(参见 {@code V5.17.0__user_audit_log.sql} 注释)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("rf_user_audit_log")
public class AuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    @TableField("actor_username")
    private String actor;

    private String action;

    @TableField("target_user_id")
    private Long targetUserId;

    @TableField("target_username")
    private String targetUsername;

    @TableField("field_name")
    private String fieldName;

    @TableField("old_value")
    private String oldValue;

    @TableField("new_value")
    private String newValue;

    private String project;

    private String note;
}
