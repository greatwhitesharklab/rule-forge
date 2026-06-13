package com.ruleforge.builder.rebuild;

import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Rule;

import java.util.Map;

/**
 * V5.49.7 — 决策树规则 rebuilder(active inactive)。
 *
 * <h2>架构约束(V5.49 确认)</h2>
 * <ul>
 *   <li>{@code DecisionTree} 走 {@code decisionTreeRulesBuilder.buildRules(tree)}
 *       → 产 {@code Rule}(普通 Rule,不带 tree 元数据),然后进
 *       {@code RulesRebuilder.rebuildRules}。</li>
 *   <li>{@link RuleTypeRebuilder#supports} 签名是 {@code Rule},DecisionTree
 *       本身不是 Rule 子类 — facade 路由无法基于 model type 判别。</li>
 *   <li>要做"真"实现有 2 条路:
 *       <ol>
 *         <li>改 {@code decisionTreeRulesBuilder} 产 {@code DecisionTreeRule
 *             extends Rule} 带 tree node 引用,本 stub instanceof 判别 +
 *             优化"树节点局部更新"路径</li>
 *         <li>改 {@code RuleTypeRebuilder} 接口接 {@code Object}/新顶层
 *             model 类型,facade 在更高层路由(重,跟 6 caller 兼容性强耦合)</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <h2>V5.49.7 决定</h2>
 * <p>维持 {@code supports() = false}。JavaDoc 化清约束,后续 V5.50+ 改
 * decisionTreeRulesBuilder 路径时再 active。跟 V5.49.5 Wizard / V5.49.6
 * DecisionTable 同样架构约束(三者都是独立 model,非 Rule 子类)。
 */
public class TreeRebuilder implements RuleTypeRebuilder {

    @Override
    public boolean supports(Rule rule) {
        // V5.49.7: 维持 false — DecisionTree 不是 Rule 子类
        return false;
    }

    @Override
    public void rebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        // should never be called when supports() == false
        throw new UnsupportedOperationException("TreeRebuilder V5.49.7 inactive — tree rules fall through to DrlRuleRebuilder. "
            + "改 decisionTreeRulesBuilder 产 DecisionTreeRule class 后这里加树节点局部更新优化。");
    }
}
