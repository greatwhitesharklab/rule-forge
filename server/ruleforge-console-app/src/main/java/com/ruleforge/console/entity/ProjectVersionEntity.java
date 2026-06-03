package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("gr_project_version")
public class ProjectVersionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String packageId;
    private String versionName;
    private Long versionNumReal;
    private Integer AuditStatus;// 0 草稿，10 测试中，20 审批中，90 通过，91 拒绝
    private Date createTime;
    private String createUser;
    private String comment;
    private String gitCommitSha;
    private String gitBranch;
}