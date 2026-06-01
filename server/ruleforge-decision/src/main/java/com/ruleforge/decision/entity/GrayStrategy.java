package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 灰度发布策略配置
 */
@Data
@TableName("gr_gray_strategy")
public class GrayStrategy {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("package_id")
    private String packageId;

    @TableField("strategy_name")
    private String strategyName;

    /** PERCENT_USER / PERCENT_RANDOM / WHITELIST */
    @TableField("strategy_type")
    private String strategyType;

    /** 灰度百分比 0-100, PERCENT_USER/PERCENT_RANDOM 时有效 */
    @TableField("gray_percent")
    private Integer grayPercent;

    /** 白名单用户ID, 逗号分隔, WHITELIST 时有效 */
    @TableField("whitelist")
    private String whitelist;

    /** 灰度目标版本 */
    @TableField("target_git_tag")
    private String targetGitTag;

    /** 基准生产版本 */
    @TableField("baseline_git_tag")
    private String baselineGitTag;

    /** 1=启用 0=停用 */
    @TableField("enabled")
    private Boolean enabled;

    @TableField("description")
    private String description;

    @TableField("created_by")
    private String createdBy;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
