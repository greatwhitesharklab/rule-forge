package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * @author Fred Gu
 * 2025-05-08 16:03
 */
@Data
@TableName("gr_project_runtime_config")
public class ProjectRuntimeConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String packageId;
    private String projectVersion;
    private String execEnv;
    private Date createTime;
    private String createUser;
    private Date updateTime;
    private String updateUser;
}
