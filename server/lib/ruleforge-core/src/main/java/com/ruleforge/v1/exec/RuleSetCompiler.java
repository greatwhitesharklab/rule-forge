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

    /** RuleSetNode + schema → RETE rules(仅条件规则)。schema 供 CEL 翻译器声明变量。
     *  <p>无条件规则(空 / "true" condition)不进 RETE —— {@code __*__} ObjectTypeNode 不匹配
     *  GeneralEntity fact(见 {@code ObjectTypeActivity.support}),由 {@link RuleSetExecutor}
     *  经 {@link #extractUnconditionalRules} 取回并按 hitPolicy 应用。 */
    public static List<Rule> compile(RuleSetNode node, com.ruleforge.v1.ast.Schema schema) {
        List<Rule> rules = new ArrayList<>();
        List<com.ruleforge.v1.ast.Rule> enabled = enabledRules(node);
        HitPolicy policy = node.getHitPolicy() == null ? HitPolicy.FIRST_MATCH : node.getHitPolicy();
        int n = enabled.size();
        for (int i = 0; i < n; i++) {
            com.ruleforge.v1.ast.Rule v1Rule = enabled.get(i);
            if (isUnconditional(v1Rule.getCondition())) {
                continue; // 无条件规则不进 RETE,由 RuleSetExecutor 应用
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

    /** 启用规则(排除 disabled),原序。compile 与 extractUnconditionalRules 共用遍历。 */
    private static List<com.ruleforge.v1.ast.Rule> enabledRules(RuleSetNode node) {
        List<com.ruleforge.v1.ast.Rule> out = new ArrayList<>();
        if (node.getRules() == null) {
            return out;
        }
        for (com.ruleforge.v1.ast.Rule r : node.getRules()) {
            if (r.getEnabled() != null && !r.getEnabled()) {
                continue; // 禁用规则跳过
            }
            out.add(r);
        }
        return out;
    }

    /** 无条件规则(空 condition / "true"),原序,排除 disabled。不进 RETE,由 {@link RuleSetExecutor}
     *  按 hitPolicy 应用:ALL_MATCH 始终应用(base/setup),FIRST_MATCH/PRIORITY 作 catch-all(else)。 */
    public static List<com.ruleforge.v1.ast.Rule> extractUnconditionalRules(RuleSetNode node) {
        List<com.ruleforge.v1.ast.Rule> out = new ArrayList<>();
        for (com.ruleforge.v1.ast.Rule r : enabledRules(node)) {
            if (isUnconditional(r.getCondition())) {
                out.add(r);
            }
        }
        return out;
    }

    /** condition 为 null / 空白 / "true"(忽略大小写)→ 无条件(always-true)。 */
    private static boolean isUnconditional(String condition) {
        if (condition == null) {
            return true;
        }
        String t = condition.trim();
        return t.isEmpty() || t.equalsIgnoreCase("true");
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
