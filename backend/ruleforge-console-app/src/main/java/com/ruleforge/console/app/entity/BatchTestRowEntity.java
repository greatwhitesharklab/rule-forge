package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 批量测试数据行 — 对应 nd_batch_test_row 表
 *
 * 每行记录一条测试输入数据及其执行结果。
 * input_data / output_data 使用 JSON 格式存储 ApplicationAllVariableCategoryMap。
 */
@Data
@TableName("nd_batch_test_row")
public class BatchTestRowEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("row_index")
    private Integer rowIndex;

    @TableField("input_data")
    private String inputData;

    @TableField("output_data")
    private String outputData;

    @TableField("error_message")
    private String errorMessage;

    @TableField("status")
    private String status;

    /** 行状态常量 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";
}
