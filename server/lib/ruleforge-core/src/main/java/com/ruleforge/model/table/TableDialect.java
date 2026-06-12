package com.ruleforge.model.table;

/**
 * 决策表 IR 来源方言 — V5.40+ 用于区分"这个 DecisionTable 实例来自哪条加载路径"。
 *
 * <p>V5.40 切 IR 之前,所有决策表都从 RuleForge 自家 .xml {@code <rule-config>} 解析。
 * V5.40+ 引入 DMN 1.3 标准 IR(Kie DMN 10.1.0)后,决策表可以从 .dmn 解析。
 * 模型层需要这个字段:
 * <ul>
 *   <li>决定 {@code RulesRebuilder} 走老 .xml 路径还是新 .dmn 路径</li>
 *   <li>决定 {@code console-ui} 加载哪个编辑器(RuleForge Native XML schema vs DMN 1.3 schema)</li>
 *   <li>决定 {@code KnowledgePackageService} 写知识包时打哪个 dialect 标签</li>
 * </ul>
 *
 * <p>默认 {@link #RULEFORGE_NATIVE} 保持 V5.39 之前 100% 向后兼容;
 * V5.40 走完后,新写的决策表走 {@link #DMN}。
 *
 * @since 5.40
 */
public enum TableDialect {
    /** RuleForge 自家 .xml schema — V5.39 及之前默认。V5.40 切完 IR 后变成"老格式"语义,反序列化路径可继续走通。 */
    RULEFORGE_NATIVE,
    /** DMN 1.3 标准 IR(Kie DMN 10.1.0 加载)— V5.40+ 首选。 */
    DMN
}
