package com.ruleforge.parse;

import org.dom4j.Element;

import com.ruleforge.model.library.variable.CategoryType;
import com.ruleforge.model.library.variable.VariableCategory;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class VariableCategoryParser implements Parser<VariableCategory> {
	private VariableParser variableParser;
	public VariableCategory parse(Element element) {
		VariableCategory category=new VariableCategory();
		category.setName(element.attributeValue("name"));
		category.setClazz(element.attributeValue("clazz"));
		category.setType(CategoryType.valueOf(element.attributeValue("type")));
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			String name=ele.getName();
			if(variableParser.support(name)){
				category.addVariable(variableParser.parse(ele));
			}
		}
		return category;
	}
	public boolean support(String name) {
		return name.equals("category");
	}
	public void setVariableParser(VariableParser variableParser) {
		this.variableParser = variableParser;
	}
}
