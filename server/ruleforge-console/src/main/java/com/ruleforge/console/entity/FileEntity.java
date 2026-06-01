package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("gr_file")
public class FileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private int fileType;
    private String filePath;
    private long projectId;
    private long latestVersionId;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(update = "now()")
    private Date updateTime;
}
