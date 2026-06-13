package com.ruleforge.builder.rebuild;

import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Rule;

import java.util.Map;

/**
 * V5.49.6 — 决策表规则 rebuilder(active inactive)。
 *
 * <h2>架构约束(V5.49 确认)</h2>
 * <ul>
 *   <li>{@code DecisionTable} 走 {@code decisionTableRulesBuilder.buildRules(table)}
 *       → 产 {@code List<Rule>},Rule 是普通基类不带 table 元数据,然后进
 *       {@code RulesRebuilder.rebuildRules}。</li>
 *   <li>{@link RuleTypeRebuilder#supports} 签名是 {@code Rule},DecisionTable
 *       本身不是 Rule 子类 — facade 路由无法基于 model type 判别。</li>
 *   <li>要做"真"实现有 2 条路:
 *       <ol>
 *         <li>改 {@code decisionTableRulesBuilder} 产 {@code DecisionTableRule
 *             extends Rule} 带 column header 引用,本 stub instanceof 判别 +
 *             优化"只改 column header 不动 rules"路径</li>
 *         <li>改 {@code RuleTypeRebuilder} 接口接 {@code Object}/新顶层
 *             model 类型,facade 在更高层路由(重,跟 6 caller 兼容性强耦合)</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <h2>V5.49.6 决定</h2>
 * <p>维持 {@code supports() = false}。JavaDoc 化清约束,后续 V5.50+ 改
 * decisionTableRulesBuilder 路径时再 active。
 */
public class DecisionTableRebuilder implements RuleTypeRebuilder {

    @Override
    public boolean supports(Rule rule) {
        // V5.49.6: 维持 false — DecisionTable 不是 Rule 子类
        return false;
    }

    @Override
    public void rebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        // should never be called when supports() == false
        throw new UnsupportedOperationException("DecisionTableRebuilder V5.49.6 inactive — table rules fall through to DrlRuleRebuilder. "
            + "改 decisionTableRulesBuilder 产 DecisionTableRule class 后这里加 column header 增量 rebuild 优化。");
    }
}
