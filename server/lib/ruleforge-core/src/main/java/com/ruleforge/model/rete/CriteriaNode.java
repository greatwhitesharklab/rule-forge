package com.ruleforge.model.rete;

import com.ruleforge.model.rule.lhs.Criteria;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * RETE 条件节点:挂一个 {@link Criteria}。V5.76.6 后不再持有 {@code newActivity}(改由
 * {@code NodeActivityFactory} 创建 CriteriaActivity)。
 */
public class CriteriaNode extends BaseReteNode implements ConditionNode {
    @JsonIgnore
    private String criteriaInfo;
    private Criteria criteria;
    private boolean debug;
    private NodeType nodeType;

    public CriteriaNode() {
        super(0);
        this.nodeType = NodeType.criteria;
    }

    public CriteriaNode(Criteria criteria, int id, boolean debug) {
        super(id);
        this.nodeType = NodeType.criteria;
        this.criteria = criteria;
        this.setCriteriaInfo(criteria.getId());
        this.debug = debug;
    }

    public NodeType getNodeType() {
        return this.nodeType;
    }

    public Criteria getCriteria() {
        return this.criteria;
    }

    public void setCriteria(Criteria criteria) {
        this.criteria = criteria;
    }

    public String getCriteriaInfo() {
        return this.criteriaInfo;
    }

    public void setCriteriaInfo(String criteriaInfo) {
        this.criteriaInfo = criteriaInfo;
    }

    public boolean isDebug() {
        return this.debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
