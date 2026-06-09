package com.ruleforge.model.rete.builder;

import com.ruleforge.model.rete.BaseReteNode;
import com.ruleforge.model.rete.builder.BuildContext;
import com.ruleforge.model.rule.lhs.BaseCriterion;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;

import java.util.List;

/**
 * @author Jacky.gao
 * 2016年9月9日
 */
public class CriteriaBuilder extends CriterionBuilder {
    public CriteriaBuilder() {
    }

    public List<BaseReteNode> buildCriterion(BaseCriterion c, BuildContext context) {
        Criteria criteria = (Criteria) c;
        return this.buildCriteria(criteria, (List) null, context);
    }

    public boolean support(Criterion criterion) {
        return criterion instanceof Criteria;
    }
}
