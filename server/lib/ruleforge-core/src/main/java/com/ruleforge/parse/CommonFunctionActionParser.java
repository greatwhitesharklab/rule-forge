package com.ruleforge.parse;

import org.dom4j.Element;

import com.ruleforge.action.Action;
import com.ruleforge.action.ExecuteCommonFunctionAction;
import com.ruleforge.model.rule.lhs.CommonFunctionParameter;

/**
 * @author Jacky.gao
 * @since 2015年7月31日
 */
public class CommonFunctionActionParser extends ActionParser {
	@Override
	public Action parse(Element element) {
		ExecuteCommonFunctionAction action=new ExecuteCommonFunctionAction();
		action.setLabel(element.attributeValue("function-label"));
		action.setName(element.attributeValue("function-name"));
		for(Object obj:element.elements()){
			if(!(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(!ele.getName().equals("function-parameter")){
				continue;
			}
			CommonFunctionParameter p=new CommonFunctionParameter();
			p.setName(ele.attributeValue("name"));
			p.setProperty(ele.attributeValue("property-name"));
			p.setPropertyLabel(ele.attributeValue("property-label"));
			for(Object object:ele.elements()){
				if(!(object instanceof Element)){
					continue;
				}
				Element e=(Element)object;
				if(!e.getName().equals("value")){
					continue;
				}
				p.setObjectParameter(valueParser.parse(e));
			}
			action.setParameter(p);
		}
		return action;
	}
	@Override
	public boolean support(String name) {
		return name.equals("execute-function");
	}
}
