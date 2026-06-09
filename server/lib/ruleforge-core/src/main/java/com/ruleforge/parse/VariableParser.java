package com.ruleforge.parse;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.variable.Act;
import com.ruleforge.model.library.variable.Variable;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class VariableParser implements Parser<Variable> {
    public Variable parse(Element element) {
        Variable variable = new Variable();
        variable.setName(element.attributeValue("name"));
        variable.setLabel(element.attributeValue("label"));
        variable.setDefaultValue(element.attributeValue("default-value"));
        variable.setType(Datatype.valueOf(element.attributeValue("type")));
        variable.setAct(Act.valueOf(element.attributeValue("act")));
        return variable;
    }

    public boolean support(String name) {
        return name.equals("var");
    }
}
