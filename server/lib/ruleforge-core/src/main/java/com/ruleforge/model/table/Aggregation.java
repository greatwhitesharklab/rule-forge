package com.ruleforge.model.table;

/**
 * DMN 决策表 aggregation(OASIS DMN 1.3 §8.3)—
 * 用于 {@link HitPolicy#COLLECT} / {@link HitPolicy#PRIORITY} 时,聚合多个匹配规则输出。
 *
 * <p>V5.40 — 决策表 → DMN 1.3 切格式后,从 .dmn 的 {@code <decisionTable aggregation="...">}
 * 属性读这个字段。RuleForge 老 .xml 路径里没有 aggregation 概念(多输出由 RETE 多 activation
 * 自行处理),所以老 .xml 反序列化到此字段为 {@code null}。
 *
 * <p>支持 5 种:
 * <ul>
 *   <li>{@link #SUM} — 求和</li>
 *   <li>{@link #COUNT} — 计数</li>
 *   <li>{@link #MIN} — 最小</li>
 *   <li>{@link #MAX} — 最大</li>
 *   <li>{@link #NONE} — 不聚合,输出 list(只对 COLLECT 合法)</li>
 * </ul>
 *
 * @since 5.40
 */
public enum Aggregation {
    SUM,
    COUNT,
    MIN,
    MAX,
    NONE
}
