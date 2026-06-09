package com.ruleforge.decision.lazy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风控数据字段查询请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDataFieldQueryRequest {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 数据源名称（使用 clazz）
     */
    private String dataSource;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 贷款区域，透传自决策流输入参数
     * 可选值: "APEX" | "ORBIT" | null
     */
    private String loanZone;

    /**
     * ORBIT 编码，透传自决策流输入参数
     */
    private String orbitCode;
}
