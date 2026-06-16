package com.ruleforge.parse.crosstab;

import com.ruleforge.model.crosstab.ConditionCrossCell;
import com.ruleforge.parse.Parser;
import com.ruleforge.parse.table.JointParser;
import org.dom4j.Element;

/**
 * @author fred
 * 2018-11-05 6:49 PM
 */
public class ConditionCrossCellParser extends CrossCellParser implements Parser<ConditionCrossCell> {
    private JointParser jointParser;

    public ConditionCrossCellParser() {
    }

    public ConditionCrossCell parse(Element element) {
        ConditionCrossCell cell = new ConditionCrossCell();
        this.parseCrossCell(cell, element);
        // V5.96 — Iterator var123 → enhanced for
        for (Object obj : element.elements()) {
            if (obj != null && obj instanceof Element) {
                Element ele = (Element) obj;
                if (this.jointParser.support(ele.getName())) {
                    cell.setJoint(this.jointParser.parse(ele));
                }
            }
        }

        return cell;
    }

    public boolean support(String name) {
        return "condition-cell".equals(name);
    }

    public void setJointParser(JointParser jointParser) {
        this.jointParser = jointParser;
    }
}
