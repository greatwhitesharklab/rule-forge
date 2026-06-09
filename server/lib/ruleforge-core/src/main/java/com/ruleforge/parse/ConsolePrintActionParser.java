package com.ruleforge.parse;

import org.dom4j.Element;

import com.ruleforge.action.Action;
import com.ruleforge.action.ConsolePrintAction;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class ConsolePrintActionParser extends ActionParser {
	public Action parse(Element element) {
		ConsolePrintAction action=new ConsolePrintAction();
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
		return name.equals("console-print");
	}
}
