package com.ruleforge.model.table;

/**
 * DMN 决策表 hit policy(OASIS DMN 1.3 §8.2)。
 *
 * <p>V5.40 — 决策表 → DMN 1.3 切格式后,从 .dmn 的 {@code <decisionTable hitPolicy="...">}
 * 属性读这个字段。RuleForge 老 .xml 路径里,默认相当于 {@link #FIRST}(首行命中短路);
 * 老 .xml 的复杂 salience 行为不会被强制翻译到任何 hit policy,而是保留为
 * {@link com.ruleforge.model.table.DecisionTable#getSalience()} 字段继续走 RETE activation 路径。
 *
 * <p>FEEL hit policy 完整列表 — 我们只支持这 7 种:
 * <ul>
 *   <li>{@link #UNIQUE} — 只允许一条规则匹配,多匹配报错(UNIQUE + PRIORITY 子集)</li>
 *   <li>{@link #FIRST} — 第一条匹配规则胜出(默认)</li>
 *   <li>{@link #PRIORITY} — 多匹配时按输出优先级(需配合 {@link Aggregation} 或内置 order)</li>
 *   <li>{@link #ANY} — 所有匹配规则输出必须相同,否则报错</li>
 *   <li>{@link #COLLECT} — 收集所有匹配规则的输出(可配合 {@link Aggregation} 聚合)</li>
 *   <li>{@link #RULE_ORDER} — 按规则定义顺序(等价 FIRST,保留语义)</li>
 *   <li>{@link #OUTPUT_ORDER} — 按输出值排序后取第一个</li>
 * </ul>
 *
 * @since 5.40
 */
public enum HitPolicy {
    UNIQUE,
    FIRST,
    PRIORITY,
    ANY,
    COLLECT,
    RULE_ORDER,
    OUTPUT_ORDER
}
