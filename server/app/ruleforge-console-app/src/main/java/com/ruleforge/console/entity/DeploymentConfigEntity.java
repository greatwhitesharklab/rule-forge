package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("gr_deployment_config")
public class DeploymentConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String packageId;
    private Long executorNodeId;
    private String gitTag;
    private String projectVersion;
    private String execEnv;
    private String deployStatus;
    private Date deployTime;
    private String deployUser;
    private Date createTime;
}
