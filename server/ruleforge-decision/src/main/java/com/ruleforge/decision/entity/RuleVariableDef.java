package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 根据 nd_rule_variable_def 表生成的实体
 */
@Data
@TableName("nd_rule_variable_def")
public class RuleVariableDef {

    @TableId(value = "id")
    private Long id;

    @TableField("clazz")
    private String clazz;

    @TableField("name")
    private String name;

    @TableField("label")
    private String label;

    @TableField("datatype")
    private String datatype;

    @TableField("act")
    private String act;

    @TableField("default_value")
    private String defaultValue;

    @TableField("ds_status")
    private Integer dsStatus;

    @TableField("format_hint")
    private String formatHint;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("ext_json")
    private String extJson;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
