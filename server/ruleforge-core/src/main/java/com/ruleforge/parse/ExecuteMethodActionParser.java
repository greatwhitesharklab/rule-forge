package com.ruleforge.parse;

import java.util.List;

import org.dom4j.Element;

import com.ruleforge.action.Action;
import com.ruleforge.action.ExecuteMethodAction;
import com.ruleforge.model.rule.Parameter;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class ExecuteMethodActionParser extends ActionParser {
	public Action parse(Element element) {
		ExecuteMethodAction action=new ExecuteMethodAction();
		action.setBeanId(element.attributeValue("bean"));
		action.setBeanLabel(element.attributeValue("bean-label"));
		action.setMethodLabel(element.attributeValue("method-label"));
		action.setMethodName(element.attributeValue("method-name"));
		List<Parameter> parameters = parseParameters(element,valueParser);
		action.setParameters(parameters);
		return action;
	}
	public boolean support(String name) {
		return name.equals("execute-method");
	}
}
