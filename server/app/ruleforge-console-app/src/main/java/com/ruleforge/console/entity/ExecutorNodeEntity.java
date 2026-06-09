package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("gr_executor_node")
public class ExecutorNodeEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nodeName;
    private String nodeUrl;
    private String execEnv;
    private String nodeGroup;
    private String status;
    private Date lastHeartbeat;
    private Date createTime;
    private Date updateTime;
}
