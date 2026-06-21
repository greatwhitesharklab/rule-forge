package com.ruleforge.model.rete.builder;

import java.util.ArrayList;
import java.util.List;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.rete.AndNode;
import com.ruleforge.model.rete.BaseReteNode;
import com.ruleforge.model.rete.ConditionNode;
import com.ruleforge.model.rete.CriteriaNode;
import com.ruleforge.model.rete.JunctionNode;
import com.ruleforge.model.rete.ReteNode;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.BaseCriterion;
import com.ruleforge.model.rule.lhs.Criterion;

/**
 * @author Jacky.gao
 * 2016年9月9日
 */
public class AndBuilder extends JunctionBuilder {
    public AndBuilder() {
    }

    public List<BaseReteNode> buildCriterion(BaseCriterion c, BuildContext context) {
        And and = (And) c;
        AndNode andNode = null;
        List<Criterion> criterions = and.getCriterions();
        if (criterions != null && !criterions.isEmpty()) {
            ConditionNode currentCriteriaNode = null;
            // V5.100.5 — 外层 Iterator var7 + while(true){do{...}while(nodes==null)}
            // find-non-null 状态机 → enhanced for + continue. 套 V6.3/V6.4 模式
            // (do-while-find-non-null → for + null-check-continue). 行为: 遍历每个
            // criterion, build 它; 若 build 返 null 则 skip (continue); 否则处理 nodes
            // (V6.0 内层 for, 不动). 全部 criterion 跑完后走 terminal block (原 do-while
            // 顶部 !hasNext() 分支, 移到 for 之后 — 两种写法都只在 iterator 耗尽时到达
            // terminal, 等价). 砍 Fernflower 反编译的 while(true)+do-while+label 状态机
            // artifact, 关 V5.96 立的 skip (最后一个 decompiled outer state machine).
            for (Criterion criterion : criterions) {
                List<ConditionNode> prevNodes = new ArrayList();
                if (currentCriteriaNode != null) {
                    prevNodes.add(currentCriteriaNode);
                }

                List<BaseReteNode> nodes = this.buildCriterion(criterion, context, prevNodes);
                if (nodes == null) {
                    continue;
                }

                for (BaseReteNode node : nodes) {
                    if (node instanceof CriteriaNode) {
                        if (currentCriteriaNode != null) {
                            List<ReteNode> childrenNodes = currentCriteriaNode.getChildrenNodes();
                            if (!childrenNodes.contains(node)) {
                                if (andNode == null) {
                                    andNode = new AndNode(context.nextId());
                                }

                                currentCriteriaNode.addLine(andNode);
                            }
                        }

                        currentCriteriaNode = (ConditionNode) node;
                    } else if (node instanceof JunctionNode) {
                        if (andNode == null) {
                            andNode = new AndNode(context.nextId());
                        }

                        ((JunctionNode) node).addLine(andNode);
                    }
                }
            }

            // terminal block (原 !var7.hasNext() 分支): 全部 criterion 跑完后, 根据
            // currentCriteriaNode / andNode 累积状态构造 result.
            // V6.9.3 — 收口 Fernflower 3-level if/return state machine (V6.2-V6.4 同档):
            //   if (size==1 && currentCriteriaNode != null) return [currentCriteriaNode];  // passthrough
            //   if (andNode == null) return [currentCriteriaNode];  // chain
            //   if (andNode != null && currentCriteriaNode != null) currentCriteriaNode.addLine(andNode);
            //   return [andNode];  // cross-type join
            // 简化成 early return + 简化 boolean (andNode != null 已被前一 early return 兜底),
            // 行为 100% 等价。
            List<BaseReteNode> result = new ArrayList();
            if (criterions.size() == 1 && currentCriteriaNode != null) {
                result.add((BaseReteNode) currentCriteriaNode);
                return result;
            }

            if (andNode == null) {
                result.add((BaseReteNode) currentCriteriaNode);
                return result;
            }

            if (currentCriteriaNode != null) {
                currentCriteriaNode.addLine(andNode);
            }

            result.add(andNode);
            return result;
        } else {
            throw new RuleException("Condition join node[and] need one child at least.");
        }
    }

    public boolean support(Criterion criterion) {
        return criterion instanceof And;
    }
}
