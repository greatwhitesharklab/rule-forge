package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("gr_file_relation")
public class FileRelationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private long projectId;
    private long ancestor;
    private long descendant;
    private int distance;
}
