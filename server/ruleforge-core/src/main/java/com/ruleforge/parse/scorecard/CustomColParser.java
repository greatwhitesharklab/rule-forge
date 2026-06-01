package com.ruleforge.parse.scorecard;

import org.dom4j.Element;

import com.ruleforge.model.scorecard.CustomCol;
import com.ruleforge.parse.Parser;

/**
 * @author Jacky.gao
 * @since 2016年9月22日
 */
public class CustomColParser implements Parser<CustomCol> {
	@Override
	public CustomCol parse(Element element) {
		CustomCol col=new CustomCol();
		col.setColNumber(Integer.parseInt(element.attributeValue("col-number")));
		col.setName(element.attributeValue("name"));
		col.setWidth(element.attributeValue("width"));
		return col;
	}
	@Override
	public boolean support(String name) {
		return name.equals("custom-col");
	}
}
