package com.ruleforge.model.rete.jsondeserializer;

import com.fasterxml.jackson.databind.JsonNode;

import com.ruleforge.model.rete.JsonUtils;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;

/**
 * @author Jacky.gao
 * @since 2015年3月6日
 */
public class InputValueDeserializer implements ValueDeserializer {

	@Override
	public Value deserialize(JsonNode jsonNode) {
		SimpleValue value=new SimpleValue();
		value.setContent(JsonUtils.getJsonValue(jsonNode, "content"));
		value.setArithmetic(JsonUtils.parseComplexArithmetic(jsonNode));
		return value;
	}

	@Override
	public boolean support(ValueType type) {
		return type.equals(ValueType.Input);
	}
}
