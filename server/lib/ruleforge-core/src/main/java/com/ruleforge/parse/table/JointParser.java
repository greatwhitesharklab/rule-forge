package com.ruleforge.parse.table;

import org.dom4j.Element;

import com.ruleforge.model.rule.Op;
import com.ruleforge.model.table.Condition;
import com.ruleforge.model.table.Joint;
import com.ruleforge.model.table.JointType;
import com.ruleforge.parse.Parser;
import com.ruleforge.parse.ValueParser;

/**
 * @author Jacky.gao
 * @since 2015年1月19日
 */
public class JointParser implements Parser<Joint> {
	private ValueParser valueParser;
	public Joint parse(Element element) {
		Joint joint=new Joint();
		joint.setType(JointType.valueOf(element.attributeValue("type")));
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(ele.getName().equals("condition")){
				joint.addCondition(parseCondition(ele));
			}else if(support(ele.getName())){
				joint.addJoint(parse(ele));
			}
		}
		return joint;
	}
	public Condition parseCondition(Element element) {
		Condition condition=new Condition();
		condition.setOp(Op.valueOf(element.attributeValue("op")));
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(valueParser.support(ele.getName())){
				condition.setValue(valueParser.parse(ele));
				break;
			}
		}
		return condition;
	}
	public boolean support(String name) {
		return name.equals("joint");
	}
	public void setValueParser(ValueParser valueParser) {
		this.valueParser = valueParser;
	}
}
