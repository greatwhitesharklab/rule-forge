package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 5.10-C: dualWrite 失败 audit log 行.
 *
 * 每次 dualWriteToGit 抛 GitOperationException 时记一行.
 * 配套 Micrometer counter + 5.10-C admin endpoint.
 */
@Data
@TableName("gr_git_dualwrite_failure")
public class GitDualwriteFailureEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String filePath;
    private Long projectId;
    private Long fileId;
    private String errorType;
    private String errorMessage;
    private String branch;
    @TableField(fill = FieldFill.INSERT)
    private Date occurredAt;
}
