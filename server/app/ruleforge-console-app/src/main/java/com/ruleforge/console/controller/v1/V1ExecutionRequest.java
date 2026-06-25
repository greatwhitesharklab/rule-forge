package com.ruleforge.console.controller.v1;

import com.ruleforge.v1.ast.RuleAsset;

import java.util.Map;

/**
 * V1 决策流执行请求(POST /v1/execute)。
 *
 * <p>{@code asset} = 完整 RuleAsset(画布 toRuleAsset 序列化的 flow + nodes + schema);
 * {@code fact} = 输入 fact 字段(Map,GeneralEntity 模型)。执行后 fact 原地被节点改写
 * (ScoreCard 写 output、RuleSet action 写字段、setDecision 写 decisionField),随响应返回。
 */
public class V1ExecutionRequest {
    private RuleAsset asset;
    private Map<String, Object> fact;

    public RuleAsset getAsset() {
        return asset;
    }

    public void setAsset(RuleAsset asset) {
        this.asset = asset;
    }

    public Map<String, Object> getFact() {
        return fact;
    }

    public void setFact(Map<String, Object> fact) {
        this.fact = fact;
    }
}
