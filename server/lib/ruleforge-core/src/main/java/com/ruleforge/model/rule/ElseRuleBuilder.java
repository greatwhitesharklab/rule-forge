package com.ruleforge.model.rule;

/**
 * 构建"否则"规则(else rule):从原规则的 {@link Other} 子句派生一条独立 {@link Rule},
 * 继承原规则元数据(激活组 / salience / 有效期等),RHS 复用 Other 的动作列表。
 *
 * <p>被 rete 网络构建({@code model.rete.builder})与议程触发({@code runtime.agenda})
 * 共用,故放在 {@code model.rule} 而非任一具体层。
 */
public final class ElseRuleBuilder {

    private ElseRuleBuilder() {
    }

    public static Rule buildElseRule(Rule rule) {
        if (rule.getElseRule() != null) {
            return rule.getElseRule();
        } else {
            Other other = rule.getOther();
            if (other != null && other.getActions().size() != 0) {
                Rule elseRule = new Rule();
                elseRule.setFile(rule.getFile());
                elseRule.setName(rule.getName() + "else");
                elseRule.setActivationGroup(rule.getActivationGroup());
                elseRule.setAgendaGroup(rule.getAgendaGroup());
                elseRule.setAutoFocus(rule.getAutoFocus());
                elseRule.setEffectiveDate(rule.getEffectiveDate());
                elseRule.setExpiresDate(rule.getExpiresDate());
                elseRule.setEnabled(rule.getEnabled());
                elseRule.setRuleflowGroup(rule.getRuleflowGroup());
                elseRule.setSalience(rule.getSalience());
                Rhs rhs = new Rhs();
                elseRule.setRhs(rhs);
                rhs.setActions(other.getActions());
                rule.setElseRule(elseRule);
                return elseRule;
            } else {
                return null;
            }
        }
    }
}
