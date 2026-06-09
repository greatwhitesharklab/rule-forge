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
 * 草稿测试用例 (V5.22.1) — 对应 rf_draft_test_case 表
 *
 * <p>一个 draft 可以挂 N 个测试用例,BA 可手动维护、LLM 可自动生成。
 * <p>权限域:ruleforge_db (跟 rf_draft 同源)
 */
@Data
@TableName("rf_draft_test_case")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
                getterVisibility = JsonAutoDetect.Visibility.NONE,
                isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class TestCaseEntity {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_LLM = "LLM";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("test_case_id")
    private String testCaseId;

    @TableField("draft_id")
    private String draftId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("inputs")
    private String inputs;

    @TableField("expected_row_id")
    private String expectedRowId;

    @TableField("created_by")
    private String createdBy;

    @TableField("source")
    private String source;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Date createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
