package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 决策陪跑配置表
 */
@Data
@TableName("nd_decision_shadow_config")
public class ShadowConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("main_rule_package_path")
    private String mainRulePackagePath;

    @TableField("shadow_rule_package_path")
    private String shadowRulePackagePath;

    @TableField("shadow_flow_id")
    private String shadowFlowId;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("sample_rate")
    private Integer sampleRate;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;
}
