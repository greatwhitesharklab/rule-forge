package com.ruleforge.builder.resource;

import org.dom4j.Element;

import com.ruleforge.model.library.action.ActionLibrary;
import com.ruleforge.parse.deserializer.ActionLibraryDeserializer;


/**
 * @author Jacky.gao
 * @since 2014年11月22日
 */
public class ActionLibraryResourceBuilder implements ResourceBuilder<ActionLibrary> {
	private ActionLibraryDeserializer actionLibraryDeserializer;
	public ActionLibrary build(Element root) {
		return actionLibraryDeserializer.deserialize(root);
	}

	public void setActionLibraryDeserializer(ActionLibraryDeserializer actionLibraryDeserializer) {
		this.actionLibraryDeserializer = actionLibraryDeserializer;
	}
	public boolean support(Element root) {
		return actionLibraryDeserializer.support(root);
	}
	public ResourceType getType() {
		return ResourceType.ActionLibrary;
	}
}
