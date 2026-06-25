package com.ruleforge.v1.ast;

/**
 * DecisionTable 命中策略(对齐 DMN hit policy,方便 DMN Adapter)。
 * <ul>
 *   <li>{@link #FIRST} — 首行命中即停</li>
 *   <li>{@link #UNIQUE} — 必须恰好一行命中,否则报错</li>
 *   <li>{@link #PRIORITY} — 输出值排序后取优先级最高</li>
 *   <li>{@link #ANY} — 0 或多行命中,多行必须输出一致</li>
 *   <li>{@link #COLLECT} — 所有命中行输出聚合</li>
 * </ul>
 */
public enum TableHitPolicy {
    FIRST,
    UNIQUE,
    PRIORITY,
    ANY,
    COLLECT
}
