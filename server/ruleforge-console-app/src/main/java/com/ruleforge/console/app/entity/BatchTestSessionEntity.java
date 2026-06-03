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
 * V5.8.0 起支持 subject × input_source 二维矩阵多态:
 *   subjectType  = FLOW | DATASOURCE(测什么)
 *   inputSourceType = FILE | DATASOURCE(input 从哪来)
 * 详细看 V5.8.0__batchtest_subject_polymorphism.sql
 *
 * 旧:Excel 上传 + 批量执行的全生命周期,默认 subjectType=FLOW / inputSourceType=FILE
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

    // ── V5.8.0 多态化新增字段 ──────────────────────────────────────

    /** 被测对象类型:FLOW(决策流) / DATASOURCE(数据源 connector) */
    @TableField("subject_type")
    private String subjectType;

    /** 被测对象 id:flowId 或 datasourceId */
    @TableField("subject_id")
    private Long subjectId;

    /** input 来源类型:FILE(Excel 上传) / DATASOURCE(调三方取) */
    @TableField("input_source_type")
    private String inputSourceType;

    /** datasourceId(FLOW+DATASOURCE 或 DATASOURCE+DATASOURCE 时填) */
    @TableField("input_source_id")
    private Long inputSourceId;

    /** 批量输入 payload JSON(FLOW+DATASOURCE 时存调三方拿到的 rows;FLOW+FILE 留空从 nd_batch_test_row 读) */
    @TableField("input_payload")
    private String inputPayload;

    /** Subject type 常量 */
    public static final String SUBJECT_FLOW = "FLOW";
    public static final String SUBJECT_DATASOURCE = "DATASOURCE";

    /** Input source type 常量 */
    public static final String INPUT_FILE = "FILE";
    public static final String INPUT_DATASOURCE = "DATASOURCE";

    /** 会话状态常量 */
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
