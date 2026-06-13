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
import com.ruleforge.model.scorecard.runtime.ScoreRule;

import java.util.List;
import java.util.Map;

/**
 * V5.49.8 — 评分卡规则 rebuilder(active — {@code ScoreRule} 是唯一 Rule 子类)。
 *
 * <h2>为什么能真做</h2>
 * <p>4 个 stub 排查发现:
 * <ul>
 *   <li>Wizard / DecisionTable / Tree 都不是 Rule 子类(独立 model,先 build 成
 *       {@code List<Rule>}),facade 路由无法判别</li>
 *   <li>ScoreRule <b>extends Rule</b>({@code com.ruleforge.model.scorecard.runtime}),
 *       唯一能被 {@code instanceof} 判别的子类</li>
 * </ul>
 *
 * <h2>V5.49.8 行为</h2>
 * <ul>
 *   <li>{@link #supports} 返 {@code rule instanceof ScoreRule} — 跟其他 3 个
 *       stub(Wizard/DecisionTable/Tree 永远 false)区分</li>
 *   <li>{@link #rebuild} 跟 {@link DrlRuleRebuilder} 同样的 LHS/RHS/Other/LoopRule
 *       通用路径(ScoreRule 继承 Rule 的 LHS/RHS 字段,本身不是 LoopRule —
 *       instanceof LoopRule 总是 false)— rebuild 内容跟 DrlRuleRebuilder 等价</li>
 *   <li>ScoreRule-specific 字段(scoringType / scoringBean / assignTargetType /
 *       variableCategory / variableName / datatype / libraries /
 *       knowledgePackageWrapper)由 {@code ScorecardResourceBuilder} 在 build 时
 *       设置,rebuild 阶段不处理这些 — runtime {@code ScoreRule.execute()} 走
 *       自有逻辑处理</li>
 * </ul>
 *
 * <h2>Facade 路由效果</h2>
 * <pre>
 *   Rule 实例化顺序    → dispatch 目标
 *   plain Rule          → DrlRuleRebuilder (fallback)
 *   LoopRule            → DrlRuleRebuilder (fallback,不是 ScoreRule)
 *   ScoreRule           → ScorecardRebuilder (active)
 * </pre>
 */
public class ScorecardRebuilder implements RuleTypeRebuilder {

    private final RulesRebuilder delegate;

    public ScorecardRebuilder(RulesRebuilder delegate) {
        this.delegate = delegate;
    }

    /** 测试用,验证 facade 注入的 delegate 是同一引用。 */
    public RulesRebuilder getDelegate() {
        return delegate;
    }

    @Override
    public boolean supports(Rule rule) {
        // V5.49.8: 真做 — ScoreRule 是唯一 Rule 子类
        return rule instanceof ScoreRule;
    }

    @Override
    public void rebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        // 跟 DrlRuleRebuilder 同样的 LHS/RHS/Other + LoopRule 通用路径 —
        // ScoreRule 继承 Rule 的 LHS/RHS 字段,本身不是 LoopRule(没有
        // LoopStart/LoopEnd 概念)。
        if (rule.getLhs() != null) {
            delegate.rebuildCriterion(rule.getLhs().getCriterion(), resLibraries, namedMap, forDSL);
        }
        Rhs rhs = rule.getRhs();
        if (rhs != null && rhs.getActions() != null) {
            List<Action> actions = rhs.getActions();
            for (Action action : actions) {
                delegate.rebuildAction(action, resLibraries, namedMap, forDSL);
            }
        }
        Other other = rule.getOther();
        if (other != null && other.getActions() != null) {
            List<Action> otherActions = other.getActions();
            for (Action action : otherActions) {
                delegate.rebuildAction(action, resLibraries, namedMap, forDSL);
            }
        }
        // LoopRule 路径 — ScoreRule 不会走到这里 instanceof LoopRule 总是 false,
        // 但保留跟 DrlRuleRebuilder 一致的 null-safe 模式
        if (rule instanceof LoopRule) {
            LoopRule loopRule = (LoopRule) rule;
            LoopTarget target = loopRule.getLoopTarget();
            if (target != null) {
                Value value = target.getValue();
                delegate.rebuildValue(value, resLibraries, namedMap, forDSL);
            }
            LoopStart start = loopRule.getLoopStart();
            if (start != null && start.getActions() != null) {
                for (Action action : start.getActions()) {
                    delegate.rebuildAction(action, resLibraries, namedMap, forDSL);
                }
            }
            LoopEnd end = loopRule.getLoopEnd();
            if (end != null && end.getActions() != null) {
                for (Action action : end.getActions()) {
                    delegate.rebuildAction(action, resLibraries, namedMap, forDSL);
                }
            }
        }
    }
}
