package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("gr_project")
public class ProjectEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    @TableField(value = "is_lock")
    private boolean projectLock;
    private Long lockVersion;
    private String lockUser;
    private Boolean gitInitialized;
    private Date createTime;
    private Date updateTime;
}
