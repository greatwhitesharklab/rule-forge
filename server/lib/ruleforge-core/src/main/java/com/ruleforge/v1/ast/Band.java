package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ScoreCard 分段。condition(CEL,返回 boolean,首个命中取分)+ score + 可选 reasonCode。
 * 对齐 PMML Scorecard 特征段,方便 PMML Adapter。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Band {
    private String id;
    private String condition;
    private double score;
    private String reasonCode;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }
}
