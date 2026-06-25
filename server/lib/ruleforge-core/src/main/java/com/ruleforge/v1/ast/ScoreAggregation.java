package com.ruleforge.v1.ast;

/**
 * ScoreCard 聚合方式(多个 card 命中 band 取分后如何合并写入 output)。
 */
public enum ScoreAggregation {
    SUM,
    AVG,
    MIN,
    MAX,
    WEIGHTED_SUM
}
