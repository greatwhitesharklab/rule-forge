package com.ruleforge.v1.ast;

/**
 * V1 节点类型。MVP 5 种:Start / RuleSet / DecisionTable / ScoreCard / Decision。
 * 作为 {@link NodeBase} 的 Jackson 多态 discriminator({@code "type"} 字段)。
 *
 * <p>V2 扩 Script / Switch / MLModel / SubFlow 时加枚举值 + {@code @JsonSubTypes} 注册。
 */
public enum NodeType {
    Start,
    RuleSet,
    DecisionTable,
    ScoreCard,
    Decision
}
