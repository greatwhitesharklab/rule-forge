package com.ruleforge.v1.ast;

import java.util.List;

/**
 * ScoreCard 节点 — 风险分 / 营销分 / 额度分 / 行为分。
 * cards(每个对一个字段打分)→ bands 命中取分 → {@link ScoreAggregation} 聚合写入 output 字段。
 * 不进 RETE,独立执行器(分段求值 + 聚合不是 RETE 强项)。
 */
public class ScoreCardNode extends NodeBase {
    /** 结果写入的 fact 字段名。 */
    private String output;
    private ScoreAggregation aggregation = ScoreAggregation.SUM;
    private List<Card> cards;

    @Override
    public String getType() {
        return "ScoreCard";
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public ScoreAggregation getAggregation() {
        return aggregation;
    }

    public void setAggregation(ScoreAggregation aggregation) {
        this.aggregation = aggregation;
    }

    public List<Card> getCards() {
        return cards;
    }

    public void setCards(List<Card> cards) {
        this.cards = cards;
    }
}
