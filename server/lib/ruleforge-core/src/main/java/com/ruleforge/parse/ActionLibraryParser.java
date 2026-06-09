package com.ruleforge.parse;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.action.ActionLibrary;
import com.ruleforge.model.library.action.Method;
import com.ruleforge.model.library.action.Parameter;
import com.ruleforge.model.library.action.SpringBean;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * @date 2014年12月23日
 */
public class ActionLibraryParser implements Parser<ActionLibrary> {

    @Override
    public ActionLibrary parse(Element element) {
        ActionLibrary lib = new ActionLibrary();
        for (Object obj : element.elements()) {
            if (obj == null) {
                continue;
            }
            if (!(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            if (ele.getName().equals("spring-bean")) {
                lib.addSpringBean(parseSpringBean(ele));
            }
        }
        return lib;
    }

    private SpringBean parseSpringBean(Element element) {
        SpringBean bean = new SpringBean();
        bean.setId(element.attributeValue("id"));
        bean.setName(element.attributeValue("name"));
        for (Object obj : element.elements()) {
            if (obj == null) {
                continue;
            }
            if (!(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            Method method = new Method();
            method.setMethodName(ele.attributeValue("method-name"));
            method.setName(ele.attributeValue("name"));
            for (Object o : ele.elements()) {
                if (o == null) {
                    continue;
                }
                if (!(o instanceof Element)) {
                    continue;
                }
                Element e = (Element) o;
                if (e.getName().equals("parameter")) {
                    Parameter parameter = new Parameter();
                    parameter.setName(e.attributeValue("name"));
                    parameter.setType(Datatype.valueOf(e.attributeValue("type")));
                    method.addParameter(parameter);
                }
            }
            bean.addMethod(method);
        }
        return bean;
    }

    public boolean support(String name) {
        return name.equals("action-library");
    }
}
