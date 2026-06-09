package com.ruleforge.model.rete;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.rule.Value;
import com.ruleforge.runtime.rete.Context;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.beanutils.BeanUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @author Jacky.gao
 * 2015年1月6日
 */
@Setter
@Getter
public abstract class BaseReteNode extends ReteNode {
    @JsonIgnore
    private List<ReteNode> childrenNodes = new ArrayList<ReteNode>();
    protected List<Line> lines;

    public BaseReteNode(int id) {
        super(id);
    }

    protected boolean buildVariables(Context context, Value value, Map<String, Object> variableMap) {
        return true;
    }

    protected Object fetchData(Object object, String property) {
        try {
            return BeanUtils.getProperty(object, property);
        } catch (Exception e) {
            throw new RuleException(e);
        }
    }

    public Line addLine(ReteNode toNode) {
        if (childrenNodes == null) {
            childrenNodes = new ArrayList<>();
        }
        childrenNodes.add(toNode);
        Line line = new Line(this, toNode);
        if (lines == null) {
            lines = new ArrayList<>();
        }
        lines.add(line);
        if (toNode instanceof JunctionNode) {
            JunctionNode junctionNode = (JunctionNode) toNode;
            junctionNode.addToConnection(line);
        }
        return line;
    }

}
