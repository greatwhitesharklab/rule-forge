package com.ruleforge.builder.rebuild;

import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V5.48 — RulesRebuilder 的 5+1 facade 路由层。
 *
 * <p>持有 5 个 {@link RuleTypeRebuilder}(向导式/表/树/评分卡/DRL),
 * 按 {@link RuleTypeRebuilder#supports} 判别 + 顺序匹配规则(DrlRuleRebuilder
 * 放最后,fallback,supports 永远 true)。
 *
 * <p>{@link RulesRebuilder} 调 facade.dispatchRebuild(rule, ...) 一次只处理
 * 单条 rule,per-rule try/catch 在 RulesRebuilder.rebuildRules 外层包(L96-104
 * 的 root cause 修复保留在外层,plan 风险 R4)。
 */
public class RulesRebuilderFacade {

    private final List<RuleTypeRebuilder> rebuilders = new ArrayList<>();

    public RulesRebuilderFacade(RulesRebuilder delegate) {
        // 顺序:stub 在前(Wizard/DecisionTable/Tree inactive supports=false;
        // Scorecard active 判别 instanceof ScoreRule),DrlRuleRebuilder 最后
        // (fallback,supports 永远 true)。
        this.rebuilders.add(new WizardRuleRebuilder());
        this.rebuilders.add(new DecisionTableRebuilder());
        this.rebuilders.add(new TreeRebuilder());
        this.rebuilders.add(new ScorecardRebuilder(delegate));  // V5.49.8 active
        this.rebuilders.add(new DrlRuleRebuilder(delegate));
    }

    /**
     * 派发单条 rule 给第一个 supports 的 rebuilder。
     *
     * <p>如果所有 rebuilder 都不 supports(理论上不可能 — DrlRuleRebuilder 永远
     * 返 true),抛 IllegalStateException(facade 防御性检查,plan 风险 R7)。
     */
    public void dispatchRebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        for (RuleTypeRebuilder r : rebuilders) {
            if (r.supports(rule)) {
                r.rebuild(rule, resLibraries, namedMap, forDSL);
                return;
            }
        }
        throw new IllegalStateException(
            "No RuleTypeRebuilder supports rule: " + rule.getClass().getName() + " (DrlRuleRebuilder fallback missing?)");
    }
}
