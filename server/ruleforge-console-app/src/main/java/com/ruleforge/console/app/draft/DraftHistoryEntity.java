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
 * 草稿状态历史 (V5.22.3) — 对应 rf_draft_history 表
 *
 * <p>append-only 审计:每次状态转换插一行,不删不改。
 * <p>权限域:ruleforge_db (跟 rf_draft 同源)
 */
@Data
@TableName("rf_draft_history")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
                getterVisibility = JsonAutoDetect.Visibility.NONE,
                isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class DraftHistoryEntity {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_APPLY = "APPLY";
    public static final String ACTION_EDIT = "EDIT";
    public static final String ACTION_EXPIRE = "EXPIRE";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("draft_id")
    private String draftId;

    @TableField("action")
    private String action;

    @TableField("from_status")
    private String fromStatus;

    @TableField("to_status")
    private String toStatus;

    @TableField("actor")
    private String actor;

    @TableField("comment")
    private String comment;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Date createdAt;
}
