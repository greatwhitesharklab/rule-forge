package com.ruleforge.builder.resource;

import org.dom4j.Element;

import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.parse.deserializer.DecisionTableDeserializer;

/**
 * @author Jacky.gao
 * @since 2015年2月9日
 */
public class DecisionTableResourceBuilder implements ResourceBuilder<DecisionTable> {
	private DecisionTableDeserializer decisionTableDeserializer;
	public DecisionTable build(Element root) {
		return decisionTableDeserializer.deserialize(root);
	}
	public ResourceType getType() {
		return ResourceType.DecisionTable;
	}
	public boolean support(Element root) {
		return decisionTableDeserializer.support(root);
	}
	public void setDecisionTableDeserializer(
			DecisionTableDeserializer decisionTableDeserializer) {
		this.decisionTableDeserializer = decisionTableDeserializer;
	}
}
