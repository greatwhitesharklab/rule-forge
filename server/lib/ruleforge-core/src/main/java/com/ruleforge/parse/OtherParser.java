package com.ruleforge.parse;

import java.util.Collection;

import org.dom4j.Element;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.ruleforge.model.rule.Other;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class OtherParser implements Parser<Other>,ApplicationContextAware {
	private Collection<ActionParser> actionParsers;
	public Other parse(Element element) {
		Other other=new Other();
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			String name=ele.getName();
			for(ActionParser actionParser:actionParsers){
				if(actionParser.support(name)){
					other.addAction(actionParser.parse(ele));
					break;
				}
			}
		}
		return other;
	}
	public boolean support(String name) {
		return name.equals("else");
	}
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		actionParsers=context.getBeansOfType(ActionParser.class).values();
	}
}
