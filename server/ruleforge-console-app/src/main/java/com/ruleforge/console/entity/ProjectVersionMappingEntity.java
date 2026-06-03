package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("gr_project_version_mapping")
public class ProjectVersionMappingEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectVersionId;
    private Long fileVersionId;
    private Long projectId; // 新增 projectId 字段
    private Long fileId;    // 新增 fileId 字段
}