package com.ruleforge.builder.resource;

import org.dom4j.Element;

import com.ruleforge.model.decisiontree.DecisionTree;
import com.ruleforge.parse.deserializer.DecisionTreeDeserializer;

/**
 * @author Jacky.gao
 * @since 2016年2月29日
 */
public class DecisionTreeResourceBuilder implements ResourceBuilder<DecisionTree> {
	private DecisionTreeDeserializer decisionTreeDeserializer;
	@Override
	public DecisionTree build(Element root) {
		return decisionTreeDeserializer.deserialize(root);
	}
	@Override
	public ResourceType getType() {
		return ResourceType.DecisionTree;
	}
	@Override
	public boolean support(Element root) {
		return decisionTreeDeserializer.support(root);
	}
	public void setDecisionTreeDeserializer(
			DecisionTreeDeserializer decisionTreeDeserializer) {
		this.decisionTreeDeserializer = decisionTreeDeserializer;
	}
}
