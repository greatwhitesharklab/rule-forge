package com.ruleforge.builder.resource;

import java.util.HashMap;

import org.dom4j.Element;

import com.ruleforge.model.library.variable.CategoryType;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.parse.deserializer.ParameterLibraryDeserializer;

/**
 * @author Jacky.gao
 * @since 2015年3月11日
 */
public class ParameterLibraryResourceBuilder implements ResourceBuilder<VariableCategory> {
	private ParameterLibraryDeserializer parameterLibraryDeserializer;
	@Override
	public VariableCategory build(Element root) {
		VariableCategory category=new VariableCategory();
		category.setName(VariableCategory.PARAM_CATEGORY);
		category.setClazz(HashMap.class.getName());
		category.setType(CategoryType.Clazz);
		category.setVariables(parameterLibraryDeserializer.deserialize(root));
		return category;
	}
	@Override
	public ResourceType getType() {
		return ResourceType.ParameterLibrary;
	}
	@Override
	public boolean support(Element root) {
		return parameterLibraryDeserializer.support(root);
	}
	public void setParameterLibraryDeserializer(
			ParameterLibraryDeserializer parameterLibraryDeserializer) {
		this.parameterLibraryDeserializer = parameterLibraryDeserializer;
	}
}
