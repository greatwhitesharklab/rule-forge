package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("gr_approval_task")
public class ApprovalTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String packageId;
    private String projectVersion;
    private String execEnv;
    private String approvalType;
    private String title;
    private String status;
    private String remark;
    private String explainText;
    private String requester;
    private String approver;
    private Date approveTime;
    private String approveRemark;
    private String processId;
    private Date createTime;
    private Date updateTime;
}
