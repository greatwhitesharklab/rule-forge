package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 项目级权限实体 — 对应 rf_user_project_permission 表 (V5.15)
 *
 * <p>一个 user × 一个 project = 一行,描述该用户在该项目下
 * 对各种文件类型的读/写权限。与 {@link com.ruleforge.console.servlet.permission.ProjectConfig}
 * 字段一一对应。
 */
@Data
@TableName("rf_user_project_permission")
public class UserProjectPermissionEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("project")
    private String project;

    @TableField("read_project")
    private boolean readProject;

    @TableField("read_package")
    private boolean readPackage;

    @TableField("write_package")
    private boolean writePackage;

    @TableField("read_variable_file")
    private boolean readVariableFile;

    @TableField("write_variable_file")
    private boolean writeVariableFile;

    @TableField("read_parameter_file")
    private boolean readParameterFile;

    @TableField("write_parameter_file")
    private boolean writeParameterFile;

    @TableField("read_constant_file")
    private boolean readConstantFile;

    @TableField("write_constant_file")
    private boolean writeConstantFile;

    @TableField("read_action_file")
    private boolean readActionFile;

    @TableField("write_action_file")
    private boolean writeActionFile;

    @TableField("read_rule_file")
    private boolean readRuleFile;

    @TableField("write_rule_file")
    private boolean writeRuleFile;

    @TableField("read_decision_table_file")
    private boolean readDecisionTableFile;

    @TableField("write_decision_table_file")
    private boolean writeDecisionTableFile;

    @TableField("read_decision_tree_file")
    private boolean readDecisionTreeFile;

    @TableField("write_decision_tree_file")
    private boolean writeDecisionTreeFile;

    @TableField("read_scorecard_file")
    private boolean readScorecardFile;

    @TableField("write_scorecard_file")
    private boolean writeScorecardFile;

    @TableField("read_flow_file")
    private boolean readFlowFile;

    @TableField("write_flow_file")
    private boolean writeFlowFile;

    /**
     * 将 DB entity 转为 ProjectConfig (PermissionServiceImpl 消费)
     */
    public com.ruleforge.console.servlet.permission.ProjectConfig toProjectConfig() {
        com.ruleforge.console.servlet.permission.ProjectConfig config = new com.ruleforge.console.servlet.permission.ProjectConfig();
        config.setProject(project);
        config.setReadProject(readProject);
        config.setReadPackage(readPackage);
        config.setWritePackage(writePackage);
        config.setReadVariableFile(readVariableFile);
        config.setWriteVariableFile(writeVariableFile);
        config.setReadParameterFile(readParameterFile);
        config.setWriteParameterFile(writeParameterFile);
        config.setReadConstantFile(readConstantFile);
        config.setWriteConstantFile(writeConstantFile);
        config.setReadActionFile(readActionFile);
        config.setWriteActionFile(writeActionFile);
        config.setReadRuleFile(readRuleFile);
        config.setWriteRuleFile(writeRuleFile);
        config.setReadDecisionTableFile(readDecisionTableFile);
        config.setWriteDecisionTableFile(writeDecisionTableFile);
        config.setReadDecisionTreeFile(readDecisionTreeFile);
        config.setWriteDecisionTreeFile(writeDecisionTreeFile);
        config.setReadScorecardFile(readScorecardFile);
        config.setWriteScorecardFile(writeScorecardFile);
        config.setReadFlowFile(readFlowFile);
        config.setWriteFlowFile(writeFlowFile);
        return config;
    }
}
