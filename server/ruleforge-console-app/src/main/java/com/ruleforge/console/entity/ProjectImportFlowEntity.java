package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("gr_project_import_flow")
public class ProjectImportFlowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String filePath;
    @TableField(fill = FieldFill.INSERT)
    private String createUser;
    private Date createTime;
    @TableField(update = "now()")
    private Date updateTime;

}
