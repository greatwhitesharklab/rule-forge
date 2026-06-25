package com.ruleforge.v1.exec;

import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.v1.ast.HitPolicy;
import com.ruleforge.v1.ast.RuleSetNode;
import com.ruleforge.v1.compile.CelCriteriaTranslator;

import java.util.ArrayList;
import java.util.List;

/**
 * V1 RuleSet 编译器(W2-1)。RuleSetNode → List&lt;Rule&gt;(RETE 规则)。
 *
 * <p>每条 V1 Rule → 一条 RETE Rule:
 * <ul>
 *   <li>Lhs:CEL condition 经 {@link CelCriteriaTranslator} 翻译</li>
 *   <li>Rhs:V1 actions 经 {@link V1ActionRhs} 翻译</li>
 *   <li>salience:hitPolicy 决定优先级映射</li>
 * </ul>
 *
 * <p>hitPolicy → RETE agenda(MVP 简化):
 * <ul>
 *   <li>{@link HitPolicy#PRIORITY} — salience = rule.priority(默认 0);高优先先评估</li>
 *   <li>{@link HitPolicy#FIRST_MATCH} — salience = rule 顺序倒序(前面 rule 高优先),
 *      实际 RETE 仍 fire 所有命中,但按 salience 顺序;严格"first-only"靠条件互斥约定
 *      (现金贷准入规则通常互斥)+ reject 终止标志</li>
 *   <li>{@link HitPolicy#ALL_MATCH} — 所有命中规则 actions 全执行(salience 不关键)</li>
 * </ul>
 *
 * <p>REJECT action 设 _rejected=true,Flow runner(W2)检查后终止后续节点。
 */
public final class RuleSetCompiler {

    private RuleSetCompiler() {
    }

    /** RuleSetNode + schema → RETE rules。schema 供 CEL 翻译器声明变量。 */
    public static List<Rule> compile(RuleSetNode node, com.ruleforge.v1.ast.Schema schema) {
        List<Rule> rules = new ArrayList<>();
        if (node.getRules() == null) {
            return rules;
        }
        HitPolicy policy = node.getHitPolicy() == null ? HitPolicy.FIRST_MATCH : node.getHitPolicy();
        int n = node.getRules().size();
        for (int i = 0; i < n; i++) {
            com.ruleforge.v1.ast.Rule v1Rule = node.getRules().get(i);
            if (v1Rule.getEnabled() != null && !v1Rule.getEnabled()) {
                continue; // 禁用规则跳过
            }
            if (v1Rule.getCondition() == null || v1Rule.getCondition().isEmpty()) {
                continue; // 无条件规则跳过(或当 always-true?MVP 跳过)
            }
            Rule reteRule = new Rule();
            String ruleName = node.getId() + "." + (v1Rule.getId() != null ? v1Rule.getId() : "rule" + i);
            reteRule.setName(ruleName);
            reteRule.setLhs(CelCriteriaTranslator.translateToLhs(v1Rule.getCondition(), schema));
            Rhs rhs = new Rhs();
            rhs.setActions(V1ActionRhs.translate(v1Rule.getActions(), schema));
            reteRule.setRhs(rhs);
            reteRule.setSalience(salienceFor(policy, v1Rule, i, n));
            rules.add(reteRule);
        }
        return rules;
    }

    /** hitPolicy → RETE salience(数字大先评估)。 */
    private static int salienceFor(HitPolicy policy, com.ruleforge.v1.ast.Rule v1Rule, int index, int total) {
        switch (policy) {
            case PRIORITY:
                return v1Rule.getPriority() != null ? v1Rule.getPriority() : 0;
            case FIRST_MATCH:
                // 前面 rule 优先:第 0 条 salience=total,递减
                return total - index;
            case ALL_MATCH:
            default:
                return 0;
        }
    }
}
