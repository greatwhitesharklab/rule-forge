package com.ruleforge.model.rete.builder;

import com.ruleforge.model.rete.BaseReteNode;
import com.ruleforge.model.rete.ConditionNode;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Junction;
import com.ruleforge.model.rule.lhs.NamedCriteria;

import java.util.List;

public abstract class JunctionBuilder extends CriterionBuilder {
    public JunctionBuilder() {
    }

    protected List<BaseReteNode> buildCriterion(Criterion criterion, BuildContext context, List<ConditionNode> prevCriteriaNodes) {
        if (criterion instanceof Junction) {
            Junction junction = (Junction) criterion;
            return ReteBuilder.buildCriterion(context, junction);
        } else if (criterion instanceof Criteria) {
            Criteria criteria = (Criteria) criterion;
            return this.buildCriteria(criteria, prevCriteriaNodes, context);
        } else {
            return null;
        }
    }
}
