package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 变量字段映射（规则变量名 → 外部字段名）
 */
@Data
@TableName("nd_datasource_field_mapping")
public class DatasourceFieldMapping {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("datasource_id")
    private Long datasourceId;

    @TableField("clazz")
    private String clazz;

    @TableField("variable_name")
    private String variableName;

    @TableField("remote_field")
    private String remoteField;

    @TableField("created_at")
    private Date createdAt;
}
