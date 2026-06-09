package com.ruleforge.console.app.draft;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Data;

import java.util.Date;

/**
 * AI 规则草稿 (V5.22) — 对应 rf_draft 表
 *
 * <p>LLM/CLI/BA 生成的规则写到这张表,审批通过后由 DraftService 转写到主存储。
 *
 * <p>权限域:ruleforge_db (跟 rf_user / rf_user_audit_log 同源)
 */
@Data
@TableName("rf_draft")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
                getterVisibility = JsonAutoDetect.Visibility.NONE,
                isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class DraftEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("draft_id")
    private String draftId;

    @TableField("session_id")
    private String sessionId;

    @TableField("message_id")
    private String messageId;

    @TableField("project")
    private String project;

    @TableField("package_path")
    private String packagePath;

    @TableField("rule_type")
    private String ruleType;

    @TableField("status")
    private String status;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("source")
    private String source;

    @TableField("source_meta")
    private String sourceMeta;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Date createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;

    @TableField("reviewed_by")
    private String reviewedBy;

    @TableField("reviewed_at")
    private Date reviewedAt;

    @TableField("review_comment")
    private String reviewComment;

    @TableField("applied_version")
    private String appliedVersion;

    @TableField("applied_at")
    private Date appliedAt;

    @TableField("expires_at")
    private Date expiresAt;
}
