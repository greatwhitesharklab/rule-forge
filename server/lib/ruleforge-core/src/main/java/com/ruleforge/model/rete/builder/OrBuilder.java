package com.ruleforge.model.rete.builder;

import java.util.ArrayList;
import java.util.List;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.rete.BaseReteNode;
import com.ruleforge.model.rete.OrNode;
import com.ruleforge.model.rete.builder.BuildContext;
import com.ruleforge.model.rule.lhs.BaseCriterion;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Or;

/**
 * @author Jacky.gao
 * 2016年9月9日
 */
public class OrBuilder extends JunctionBuilder {
    public OrBuilder() {
    }

    public List<BaseReteNode> buildCriterion(BaseCriterion c, BuildContext context) {
        Or or = (Or) c;
        List<Criterion> criterions = or.getCriterions();
        if (criterions != null && !criterions.isEmpty()) {
            List<BaseReteNode> childNodes = new ArrayList();
            // V5.96 — Iterator var123 → enhanced for
            for (Criterion criterion : criterions) {
                List<BaseReteNode> nodes = this.buildCriterion(criterion, context, (List) null);
                if (nodes != null) {
                    childNodes.addAll(nodes);
                }
            }

            // V6.9.3 — 收口 Fernflower 反编译 if/else if/else state machine (V6.2-V6.4 同档):
            //   if (size==0) return null;
            //   else if (size==1) return childNodes;
            //   else { build OrNode + addLines + return [orNode]; }
            // 简化成 early return + 单层 if, 行为 100% 等价。
            if (childNodes.isEmpty()) {
                return null;
            }
            if (childNodes.size() == 1) {
                return childNodes;
            }
            OrNode orNode = new OrNode(context.nextId());
            // V5.96 — Iterator var123 → enhanced for
            for (BaseReteNode node : childNodes) {
                node.addLine(orNode);
            }

            List<BaseReteNode> list = new ArrayList();
            list.add(orNode);
            return list;
        } else {
            throw new RuleException("Condition join node[or] need one child at least.");
        }
    }

    public boolean support(Criterion criterion) {
        return criterion instanceof Or;
    }
}
