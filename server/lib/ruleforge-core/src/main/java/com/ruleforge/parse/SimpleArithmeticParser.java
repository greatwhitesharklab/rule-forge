package com.ruleforge.parse;

import org.dom4j.Element;

import com.ruleforge.model.rule.ArithmeticType;
import com.ruleforge.model.rule.SimpleArithmetic;
import com.ruleforge.model.rule.SimpleArithmeticValue;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class SimpleArithmeticParser implements Parser<SimpleArithmetic> {
	public SimpleArithmetic parse(Element element) {
		SimpleArithmetic arithmetic=new SimpleArithmetic();
		ArithmeticType arithmeticType=ArithmeticType.valueOf(element.attributeValue("type"));
		arithmetic.setType(arithmeticType);
		SimpleArithmeticValue value=new SimpleArithmeticValue();
		value.setContent(element.attributeValue("value"));
		arithmetic.setValue(value);
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(this.support(ele.getName())){
				value.setArithmetic(this.parse(ele));
				break;
			}
		}
		return arithmetic;
	}
	public boolean support(String name) {
		return name.equals("simple-arith");
	}
}
