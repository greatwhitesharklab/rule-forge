package com.ruleforge.builder.resource;

import org.dom4j.Element;

import com.ruleforge.model.library.constant.ConstantLibrary;
import com.ruleforge.parse.deserializer.ConstantLibraryDeserializer;

/**
 * @author Jacky.gao
 * @since 2015年1月15日
 */
public class ConstantLibraryResourceBuilder implements ResourceBuilder<ConstantLibrary> {
	private ConstantLibraryDeserializer constantLibraryDeserializer;
	public ConstantLibrary build(Element root) {
		return constantLibraryDeserializer.deserialize(root);
	}
	public boolean support(Element root) {
		return constantLibraryDeserializer.support(root);
	}
	public ResourceType getType() {
		return ResourceType.ConstantLibrary;
	};
	public void setConstantLibraryDeserializer(
			ConstantLibraryDeserializer constantLibraryDeserializer) {
		this.constantLibraryDeserializer = constantLibraryDeserializer;
	}
}
