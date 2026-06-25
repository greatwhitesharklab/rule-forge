package com.ruleforge.v1.ast;

/**
 * V1 结构化 Action 类型。Action 永远不含 CEL — {@code value} 是字面量或字段引用。
 *
 * <p>5 种覆盖现金贷场景:
 * <ul>
 *   <li>{@link #SET_VARIABLE} — 写 fact 字段(定价后写 rate)</li>
 *   <li>{@link #ADD_SCORE} — 给 score 字段加分(营销分累加)</li>
 *   <li>{@link #SET_DECISION} — 设最终决策(Decision 节点读)</li>
 *   <li>{@link #REJECT} — 硬终止 + 理由(准入拒绝,终端)</li>
 *   <li>{@link #FLAG} — 加风险标记到结果 flags[](非终端)</li>
 * </ul>
 */
public enum ActionType {
    SET_VARIABLE,
    ADD_SCORE,
    SET_DECISION,
    REJECT,
    FLAG
}
