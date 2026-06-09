package com.ruleforge.parse;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;

import com.ruleforge.action.Action;
import com.ruleforge.action.VariableAssignAction;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.lhs.LeftType;
/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class VariableAssignActionParser extends ActionParser {
	public Action parse(Element element) {
		VariableAssignAction action=new VariableAssignAction();
		String referenceName=element.attributeValue("reference-name");
		if(StringUtils.isNotEmpty(referenceName)){
			action.setReferenceName(referenceName);
		}
		String variable=element.attributeValue("var");
		if(StringUtils.isEmpty(variable)){
			variable=element.attributeValue("property-name");
		}
		action.setVariableName(variable);
		String variableLabel=element.attributeValue("var-label");
		if(StringUtils.isEmpty(variableLabel)){
			variableLabel=element.attributeValue("property-label");
		}
		action.setVariableLabel(variableLabel);
		String variableCategory=element.attributeValue("var-category");
		action.setVariableCategory(variableCategory);
		String datatype=element.attributeValue("datatype");
		if(StringUtils.isNotEmpty(datatype)){
			action.setDatatype(Datatype.valueOf(datatype));
		}
		String type=element.attributeValue("type");
		if(StringUtils.isNotEmpty(type)){
			action.setType(LeftType.valueOf(type));
		}
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(valueParser.support(ele.getName())){
				action.setValue(valueParser.parse(ele));
				break;
			}
		}
		return action;
	}
	public boolean support(String name) {
		return name.equals("var-assign");
	}
}
