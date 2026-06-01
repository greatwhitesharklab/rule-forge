package com.ruleforge.decision.dto;

import lombok.Data;

/**
 * 贷款决策评估请求
 */
@Data
public class DecisionRequest {

    /**
     * 用户ID，必需
     */
    private String userId;

    /**
     * 订单号/申请单号，可选
     */
    private String orderNo;

    /**
     * 规则包路径，必需
     * 例如: "loan/approval"
     */
    private String rulePackagePath;

    /**
     * 决策流ID，必需
     * 例如: "loan-approval-flow"
     */
    private String flowId;

    /**
     * 贷款区域，可选
     * 决定 risk-datasource 数据查询路由：APEX 走本地数据库，ORBIT 走远程 loan-provider
     * 不传时默认走 APEX，行为不变
     */
    private String loanZone;

    /**
     * ORBIT 编码，可选
     * loanZone 为 ORBIT 时使用，标识具体的 orbit 路由
     */
    private String orbitCode;
}
