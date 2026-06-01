package com.ruleforge.parse;

import org.dom4j.Element;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.action.Method;
import com.ruleforge.model.library.action.Parameter;
import com.ruleforge.model.library.action.SpringBean;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class SpringBeanParser implements Parser<SpringBean> {
	public SpringBean parse(Element element) {
		SpringBean bean=new SpringBean();
		bean.setId(element.attributeValue("id"));
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(ele.getName().equals("method")){
				Method method=parseMethod(ele);
				bean.addMethod(method);
			}
		}
		return bean;
	}
	private Method parseMethod(Element element){
		Method method=new Method();
		method.setName(element.attributeValue("name"));
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(ele.getName().equals("parameter")){
				Parameter parameter=new Parameter();
				parameter.setType(Datatype.valueOf(ele.attributeValue("type")));
				method.addParameter(parameter);
			}
		}
		return method;
	}
	public boolean support(String name) {
		return name.equals("spring-bean");
	}
}
