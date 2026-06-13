package com.ruleforge.builder.rebuild;

import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Rule;

import java.util.Map;

/**
 * V5.49.5 — 向导式规则 rebuilder(active inactive)。
 *
 * <h2>架构约束(V5.49 确认)</h2>
 * <ul>
 *   <li>当前 V5.47-5.49 还没有独立 {@code WizardRule} 实体 class。向导式编辑器
 *       生成的规则最终走 DecisionTable / DecisionTree,经各自 builder 转成
 *       {@code List<Rule>}(普通 Rule,无 wizard 元数据),然后进
 *       {@code RulesRebuilder.rebuildRules}。</li>
 *   <li>{@link RuleTypeRebuilder#supports} 签名是 {@code Rule},只能用
 *       {@code instanceof Rule} 子类判别。Wizard / DecisionTable / DecisionTree
 *       都不是 Rule 子类 — 是独立 model,先 build 成 Rule 后再 rebuild,
 *       所以这个 facade 永远不会被它们 dispatch 到。</li>
 *   <li>要做"真"实现有 2 条路:
 *       <ol>
 *         <li>引入 {@code WizardRule extends Rule} class + 标记字段(轻,但需要
 *             改所有 wizard builder 路径)</li>
 *         <li>改 {@code RuleTypeRebuilder} 接口接 {@code Object}/新顶层 model
 *             类型,facade 在更高层路由(重,跟 6 caller + Spring bean 注入
 *             兼容性强耦合)</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <h2>V5.49.5 决定</h2>
 * <p>维持 {@code supports() = false}。JavaDoc 化清约束,后续 V5.50+ 引入
 * {@code WizardRule} class 时再 active。BDD test 锁当前行为(锁 facade 路由
 * 不会 dispatch 到本 stub,wizard rule 走 DrlRuleRebuilder fallback)。
 */
public class WizardRuleRebuilder implements RuleTypeRebuilder {

    @Override
    public boolean supports(Rule rule) {
        // V5.49.5: 维持 false — 无独立 WizardRule class,无法 instanceof 判别
        return false;
    }

    @Override
    public void rebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        // should never be called when supports() == false
        // (facade 路由在 supports() == false 时跳过本 rebuilder)
        throw new UnsupportedOperationException("WizardRuleRebuilder V5.49.5 inactive — wizard rules fall through to DrlRuleRebuilder. "
            + "引入 WizardRule class 后这里加 wizard-specific 处理(wizard-variable binding / wizard-only action)。");
    }
}
