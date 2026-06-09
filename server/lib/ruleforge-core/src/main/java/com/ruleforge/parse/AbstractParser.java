package com.ruleforge.parse;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Parameter;
import com.ruleforge.model.rule.Value;
import org.dom4j.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * @date 2015年2月28日
 */
public abstract class AbstractParser<T> implements Parser<T> {
    protected List<Parameter> parseParameters(Element element, ValueParser valueParser) {
        List<Parameter> parameters = new ArrayList<>();
        for (Object obj : element.elements()) {
            if (obj == null || !(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            if (ele.getName().equals("parameter")) {
                Parameter parameter = new Parameter();
                parameter.setName(ele.attributeValue("name"));
                parameter.setType(Datatype.valueOf(ele.attributeValue("type")));
                for (Object o : ele.elements()) {
                    if (o == null || !(o instanceof Element)) {
                        continue;
                    }
                    Element e = (Element) o;
                    if (valueParser.support(e.getName())) {
                        Value value = valueParser.parse(e);
                        parameter.setValue(value);
                        break;
                    }
                }
                parameters.add(parameter);
            }
        }
        return parameters;
    }
}
