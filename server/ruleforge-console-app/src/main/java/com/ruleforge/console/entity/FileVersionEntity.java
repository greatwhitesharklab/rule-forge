package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("gr_file_version")
public class FileVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fileId;
    private String filePath;
    private String fileName;
    private String fileContent;
    private String gitCommitSha;
    private String fileComment;
    private String versionNum;
    private Long versionNumReal;
    private Long projectVersionNumReal;
    private String beforeComment;
    private String afterComment;
    private String auditStatus;
    private long projectId;
    private String createUser;
    private Date createDate;
    @TableField(update = "now()")
    private Date updateTime;

}
