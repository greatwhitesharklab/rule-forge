package com.ruleforge.v1.ast;

import java.util.List;

/**
 * RuleSet 节点 — 准入 / 反欺诈 / 拒绝 / 运营规则。
 * rules 的 condition(CEL)+ actions(结构化),按 {@link HitPolicy} 决定命中后如何执行。
 */
public class RuleSetNode extends NodeBase {
    private HitPolicy hitPolicy = HitPolicy.FIRST_MATCH;
    private List<Rule> rules;

    @Override
    public String getType() {
        return "RuleSet";
    }

    public HitPolicy getHitPolicy() {
        return hitPolicy;
    }

    public void setHitPolicy(HitPolicy hitPolicy) {
        this.hitPolicy = hitPolicy;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }
}
