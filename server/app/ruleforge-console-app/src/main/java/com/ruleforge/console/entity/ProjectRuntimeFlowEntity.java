package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * @author Xie FanFan
 * 2025-07-16 13:51
 */
@Data
@TableName("gr_project_runtime_flow")
public class ProjectRuntimeFlowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String packageId;
    private String projectVersion;
    private Integer auditStatus;// 0 草稿，20 审批中，90 通过，91 拒绝
    private String execEnv;
    private Integer proportion;
    private Date startTime;
    private Date endTime;
    private Date createTime;
    private String createUser;
    private Date updateTime;
    private String updateUser;
}
