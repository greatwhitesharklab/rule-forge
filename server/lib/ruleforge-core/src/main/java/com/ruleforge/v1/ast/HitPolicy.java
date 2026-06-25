package com.ruleforge.v1.ast;

/**
 * RuleSet 命中策略。
 * <ul>
 *   <li>{@link #FIRST_MATCH} — 首条命中即停(像 switch),其余不评估</li>
 *   <li>{@link #ALL_MATCH} — 所有命中规则的 actions 全执行</li>
 *   <li>{@link #PRIORITY} — 按 {@link Rule#getPriority()} 降序评估,首条命中即停</li>
 * </ul>
 */
public enum HitPolicy {
    FIRST_MATCH,
    ALL_MATCH,
    PRIORITY
}
