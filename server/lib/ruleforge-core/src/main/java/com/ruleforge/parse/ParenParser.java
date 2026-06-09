package com.ruleforge.parse;

import org.dom4j.Element;

import com.ruleforge.model.rule.ParenValue;

/**
 * @author Jacky.gao
 * @since 2015年6月14日
 */
public class ParenParser implements Parser<ParenValue> {
	private ValueParser valueParser;
	private ComplexArithmeticParser arithmeticParser;
	@Override
	public ParenValue parse(Element element) {
		ParenValue value=new ParenValue();
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(valueParser.support(ele.getName())){
				value.setValue(valueParser.parse(ele));
			}else if(arithmeticParser.support(ele.getName())){
				value.setArithmetic(arithmeticParser.parse(ele));
			}
		}
		return value;
	}

	@Override
	public boolean support(String name) {
		return name.equals("paren");
	}
	public void setValueParser(ValueParser valueParser) {
		this.valueParser = valueParser;
	}
	public void setArithmeticParser(ComplexArithmeticParser arithmeticParser) {
		this.arithmeticParser = arithmeticParser;
	}
}
