package com.ruleforge.parse;

import com.ruleforge.action.Action;
import com.ruleforge.model.rule.Rhs;
import org.dom4j.Element;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Jacky.gao
 * 2014年12月23日
 */
public class RhsParser implements Parser<Rhs>, ApplicationContextAware {
    private Collection<ActionParser> actionParsers;

    public Rhs parse(Element element) {
        Rhs rhs = new Rhs();
        rhs.setActions(parseActions(element));
        return rhs;
    }

    public List<Action> parseActions(Element element) {
        List<Action> actions = new ArrayList<>();
        for (Object obj : element.elements()) {
            if (!(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            String name = ele.getName();
            for (ActionParser actionParser : actionParsers) {
                if (actionParser.support(name)) {
                    actions.add(actionParser.parse(ele));
                    break;
                }
            }
        }
        return actions;
    }

    public boolean support(String name) {
        return name.equals("then");
    }

    public Collection<ActionParser> getActionParsers() {
        return actionParsers;
    }

    public void setApplicationContext(ApplicationContext context) throws BeansException {
        actionParsers = context.getBeansOfType(ActionParser.class).values();
    }
}
