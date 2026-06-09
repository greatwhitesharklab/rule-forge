package com.ruleforge.parse;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.constant.Constant;
import com.ruleforge.model.library.constant.ConstantCategory;
import com.ruleforge.model.library.constant.ConstantLibrary;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class ConstantLibraryParser implements Parser<ConstantLibrary> {
	public static final String BEAN_ID="ruleforge.constantLibraryParser";
	public ConstantLibrary parse(Element element) {
		List<ConstantCategory> categories=new ArrayList<ConstantCategory>();
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(ele.getName().equals("category")){
				categories.add(parseConstantCategory(ele));
			}
		}
		ConstantLibrary library=new ConstantLibrary();
		library.setCategories(categories);
		return library;
	}
	private ConstantCategory parseConstantCategory(Element element){
		ConstantCategory category=new ConstantCategory();
		category.setName(element.attributeValue("name"));
		category.setLabel(element.attributeValue("label"));
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(ele.getName().equals("constant")){
				category.addConstant(parseConstant(ele));
			}
		}
		return category;
	}
	private Constant parseConstant(Element element){
		Constant constant=new Constant();
		constant.setName(element.attributeValue("name"));
		constant.setLabel(element.attributeValue("label"));
		constant.setType(Datatype.valueOf(element.attributeValue("type")));
		return constant;
	}
	public boolean support(String name) {
		return name.equals("constant-library");
	}
}
