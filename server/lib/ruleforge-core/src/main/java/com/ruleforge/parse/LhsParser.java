package com.ruleforge.parse;

import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Lhs;
import lombok.Getter;
import org.dom4j.Element;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collection;

/**
 * @author Jacky.gao
 * 2014年12月23日
 */
@Getter
public class LhsParser implements Parser<Lhs>, ApplicationContextAware {
    private Collection<CriterionParser> criterionParsers;

    public Lhs parse(Element element) {
        Lhs lhs = new Lhs();
        lhs.setCriterion(parseCriterion(element));
        return lhs;
    }

    public Criterion parseCriterion(Element element) {
        Criterion criterion = null;
        for (Object obj : element.elements()) {
            if (obj == null || !(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            String name = ele.getName();
            for (CriterionParser parser : criterionParsers) {
                if (parser.support(name)) {
                    criterion = parser.parse(ele);
                    if (criterion != null) {
                        break;
                    }
                }
            }
        }
        return criterion;
    }


    public boolean support(String name) {
        return name.equals("if");
    }

    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        criterionParsers = applicationContext.getBeansOfType(CriterionParser.class).values();
    }
}
