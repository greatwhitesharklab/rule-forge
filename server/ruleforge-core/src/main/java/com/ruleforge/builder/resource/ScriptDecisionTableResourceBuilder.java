package com.ruleforge.builder.resource;

import org.dom4j.Element;

import com.ruleforge.model.table.ScriptDecisionTable;
import com.ruleforge.parse.deserializer.ScriptDecisionTableDeserializer;

/**
 * @author Jacky.gao
 * @since 2015年2月9日
 */
public class ScriptDecisionTableResourceBuilder implements ResourceBuilder<ScriptDecisionTable> {
	private ScriptDecisionTableDeserializer scriptDecisionTableDeserializer;
	public ScriptDecisionTable build(Element root) {
		return scriptDecisionTableDeserializer.deserialize(root);
	}
	public ResourceType getType() {
		return ResourceType.ScriptDecisionTable;
	}
	public boolean support(Element root) {
		return scriptDecisionTableDeserializer.support(root);
	}
	public void setScriptDecisionTableDeserializer(
			ScriptDecisionTableDeserializer scriptDecisionTableDeserializer) {
		this.scriptDecisionTableDeserializer = scriptDecisionTableDeserializer;
	}
}
