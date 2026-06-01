package com.ruleforge.parse;

import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;
import lombok.Setter;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * 2014年12月23日
 */
@Setter
public class CriteriaParser extends CriterionParser {
    private ValueParser valueParser;
    private LeftParser leftParser;

    public Criterion parse(Element element) {
        Criteria criteria = new Criteria();
        Op op = Op.valueOf(element.attributeValue("op"));
        criteria.setOp(op);
        for (Object obj : element.elements()) {
            if (obj == null || !(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            String name = ele.getName();
            if (name.equals("value")) {
                criteria.setValue(valueParser.parse(ele));
            } else if (name.equals("left")) {
                criteria.setLeft(leftParser.parse(ele));
            }
        }
        return criteria;
    }


    public boolean support(String name) {
        return name.equals("atom");
    }

}
