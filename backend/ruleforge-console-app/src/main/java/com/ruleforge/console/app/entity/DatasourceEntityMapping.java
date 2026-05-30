package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 实体类与数据源映射
 */
@Data
@TableName("nd_datasource_entity_mapping")
public class DatasourceEntityMapping {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("clazz")
    private String clazz;

    @TableField("datasource_id")
    private Long datasourceId;

    @TableField("created_at")
    private Date createdAt;
}
