package com.ruleforge.parse;

import org.dom4j.Element;

import com.ruleforge.model.rule.ArithmeticType;
import com.ruleforge.model.rule.ComplexArithmetic;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class ComplexArithmeticParser implements Parser<ComplexArithmetic> {
	private ValueParser valueParser;
	private ParenParser parenParser;
	public ComplexArithmetic parse(Element element) {
		ComplexArithmetic arithmetic=new ComplexArithmetic();
		ArithmeticType arithmeticType=ArithmeticType.valueOf(element.attributeValue("type"));
		arithmetic.setType(arithmeticType);
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(valueParser.support(ele.getName())){
				arithmetic.setValue(valueParser.parse(ele));
			}else if(parenParser.support(ele.getName())){
				arithmetic.setValue(parenParser.parse(ele));
			}
		}
		return arithmetic;
	}
	public void setValueParser(ValueParser valueParser) {
		this.valueParser = valueParser;
	}
	public void setParenParser(ParenParser parenParser) {
		this.parenParser = parenParser;
	}
	public boolean support(String name) {
		return name.equals("complex-arith");
	}
}
