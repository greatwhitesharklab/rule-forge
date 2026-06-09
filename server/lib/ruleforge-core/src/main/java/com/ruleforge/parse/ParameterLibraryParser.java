package com.ruleforge.parse;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;

import com.ruleforge.model.library.variable.Variable;

/**
 * @author Jacky.gao
 * @since 2015年3月10日
 */
public class ParameterLibraryParser implements Parser<List<Variable>> {
	private VariableParser variableParser;
	@Override
	public List<Variable> parse(Element element) {
		List<Variable> variables=new ArrayList<Variable>();
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			String name=ele.getName();
			if(name.equals("parameter")){
				variables.add(variableParser.parse(ele));
			}
		}
		return variables;
	}
	@Override
	public boolean support(String name) {
		return name.equals("parameter-library");
	}
	public void setVariableParser(VariableParser variableParser) {
		this.variableParser = variableParser;
	}
}
