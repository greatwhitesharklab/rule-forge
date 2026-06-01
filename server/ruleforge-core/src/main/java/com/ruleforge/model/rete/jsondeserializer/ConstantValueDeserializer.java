package com.ruleforge.model.rete.jsondeserializer;

import com.fasterxml.jackson.databind.JsonNode;

import com.ruleforge.model.rete.JsonUtils;
import com.ruleforge.model.rule.ConstantValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;

/**
 * @author Jacky.gao
 * @since 2015年3月6日
 */
public class ConstantValueDeserializer implements ValueDeserializer {
	@Override
	public Value deserialize(JsonNode jsonNode) {
		ConstantValue value=new ConstantValue();
		value.setConstantCategory(JsonUtils.getJsonValue(jsonNode, "constantCategory"));
		value.setConstantLabel(JsonUtils.getJsonValue(jsonNode, "constantLabel"));
		value.setConstantName(JsonUtils.getJsonValue(jsonNode, "constantName"));
		value.setArithmetic(JsonUtils.parseComplexArithmetic(jsonNode));
		return value;
	}

	@Override
	public boolean support(ValueType type) {
		return type.equals(ValueType.Constant);
	}
}
