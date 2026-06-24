package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * ScoreCard 内单项。field(评估的字段)+ bands(分段,按顺序首个命中取分)+
 * 可选 weight(WEIGHTED_SUM 聚合下的权重,默认 1)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Card {
    private String id;
    private String field;
    private Double weight;
    private List<Band> bands;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public List<Band> getBands() {
        return bands;
    }

    public void setBands(List<Band> bands) {
        this.bands = bands;
    }
}
