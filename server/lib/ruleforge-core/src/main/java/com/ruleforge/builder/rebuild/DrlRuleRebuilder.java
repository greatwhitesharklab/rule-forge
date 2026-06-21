package com.ruleforge.builder.rebuild;

import com.ruleforge.action.Action;
import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Other;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.loop.LoopEnd;
import com.ruleforge.model.rule.loop.LoopRule;
import com.ruleforge.model.rule.loop.LoopStart;
import com.ruleforge.model.rule.loop.LoopTarget;

import java.util.List;
import java.util.Map;

/**
 * V5.48 — DRL 通用规则 rebuilder(也是 fallback,supports 永远 true)。
 *
 * <p>从 {@link RulesRebuilder} L51-101 抽出来的 per-rule rebuild 逻辑:
 * LHS criterion + RHS actions + Other actions + LoopRule 特有的
 * LoopTarget/LoopStart/LoopEnd。
 *
 * <p>需要 rebuildCriterion/rebuildAction/rebuildValue 这些 utility,delegate 回
 * {@link RulesRebuilder}(公共 API,DecisionTreeParser / ComplexScorecardParser
 * 也直接调)。DrlRuleRebuilder 不持有这 3 个方法的副本 — 避免代码重复 + 单一修改点。
 */
public class DrlRuleRebuilder implements RuleTypeRebuilder {

    private final RulesRebuilder delegate;

    public DrlRuleRebuilder(RulesRebuilder delegate) {
        this.delegate = delegate;
    }

    /** 测试用,验证 facade 注入的 delegate 是同一引用。 */
    public RulesRebuilder getDelegate() {
        return delegate;
    }

    @Override
    public boolean supports(Rule rule) {
        // fallback — 所有 Rule 都能处理
        return true;
    }

    @Override
    public void rebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        // LHS
        if (rule.getLhs() != null) {
            delegate.rebuildCriterion(rule.getLhs().getCriterion(), resLibraries, namedMap, forDSL);
        }
        // RHS
        rebuildActions(rule.getRhs(), resLibraries, namedMap, forDSL);
        // Other
        rebuildActions(rule.getOther(), resLibraries, namedMap, forDSL);
        // LoopRule — V5.47 之前就在的 instanceof 路径,搬过来 1:1
        if (rule instanceof LoopRule) {
            LoopRule loopRule = (LoopRule) rule;
            LoopTarget target = loopRule.getLoopTarget();
            if (target != null) {
                Value value = target.getValue();
                delegate.rebuildValue(value, resLibraries, namedMap, forDSL);
            }
            rebuildActions(loopRule.getLoopStart(), resLibraries, namedMap, forDSL);
            rebuildActions(loopRule.getLoopEnd(), resLibraries, namedMap, forDSL);
        }
    }

    // V6.9.28 — V6.9.14 helper extract: 4 method-local copies of
    // `if (X != null && X.getActions() != null) { for (Action a : ...) delegate.rebuildAction(...); }`
    // → 1 helper supporting Rhs / Other / LoopStart / LoopEnd (all share List<Action> getActions())
    private void rebuildActions(Object actionContainer, ResourceLibrary resLibraries,
                                Map<String, String> namedMap, boolean forDSL) {
        if (actionContainer == null) {
            return;
        }
        List<Action> actions;
        if (actionContainer instanceof Rhs) {
            actions = ((Rhs) actionContainer).getActions();
        } else if (actionContainer instanceof Other) {
            actions = ((Other) actionContainer).getActions();
        } else if (actionContainer instanceof LoopStart) {
            actions = ((LoopStart) actionContainer).getActions();
        } else if (actionContainer instanceof LoopEnd) {
            actions = ((LoopEnd) actionContainer).getActions();
        } else {
            return;
        }
        if (actions == null) {
            return;
        }
        for (Action action : actions) {
            delegate.rebuildAction(action, resLibraries, namedMap, forDSL);
        }
    }
}
