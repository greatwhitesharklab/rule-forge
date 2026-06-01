package com.ruleforge.model.rete;

import com.ruleforge.exception.RuleException;

/**
 * @author Jacky.gao
 * 2015年3月6日
 */
public enum NodeType {
    and,
    or,
    criteria,
    namedCriteria,
    objectType,
    terminal;

    private NodeType() {
    }

    public static ReteNode newReteNodeInstance(NodeType type) {
        switch (type) {
            case and:
                return new AndNode();
            case or:
                return new OrNode();
            case criteria:
                return new CriteriaNode();
            case namedCriteria:
                throw new RuleException("Unsupport Node.");
            case objectType:
                return new ObjectTypeNode();
            case terminal:
                return new TerminalNode();
            default:
                return null;
        }
    }
}
