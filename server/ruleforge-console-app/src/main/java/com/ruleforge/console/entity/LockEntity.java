package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * @author Fred
 * @date 2024/3/12 11:59
 */
@Data
@TableName("gr_lock")
public class LockEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String lockResource;
    private Date createTime;
}
