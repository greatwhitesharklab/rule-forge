package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 批量测试会话 — 对应 nd_batch_test_session 表
 *
 * 记录一次 Excel 上传 + 批量执行的全生命周期。
 * 替代原来 HttpSession 存储方式，支持重启恢复和历史追溯。
 */
@Data
@TableName("nd_batch_test_session")
public class BatchTestSessionEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("project")
    private String project;

    @TableField("package_id")
    private String packageId;

    @TableField("files")
    private String files;

    @TableField("flow_id")
    private String flowId;

    @TableField("status")
    private String status;

    @TableField("total_rows")
    private Integer totalRows;

    @TableField("error_count")
    private Integer errorCount;

    @TableField("progress")
    private Double progress;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;

    /** 会话状态常量 */
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
