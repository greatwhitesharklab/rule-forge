package com.ruleforge.builder.resource;

import org.dom4j.Element;

import com.ruleforge.model.library.variable.VariableLibrary;
import com.ruleforge.parse.deserializer.VariableLibraryDeserializer;

/**
 * @author Jacky.gao
 * @since 2014年12月22日
 */
public class VariableLibraryResourceBuilder implements ResourceBuilder<VariableLibrary> {
	private VariableLibraryDeserializer variableLibraryDeserializer;
	public VariableLibrary build(Element root) {
		VariableLibrary lib=new VariableLibrary();
		lib.setVariableCategories(variableLibraryDeserializer.deserialize(root));
		return lib;
	}

	public boolean support(Element root) {
		return variableLibraryDeserializer.support(root);
	}
	public ResourceType getType() {
		return ResourceType.VariableLibrary;
	}
	public void setVariableLibraryDeserializer(VariableLibraryDeserializer variableLibraryDeserializer) {
		this.variableLibraryDeserializer = variableLibraryDeserializer;
	}
}
