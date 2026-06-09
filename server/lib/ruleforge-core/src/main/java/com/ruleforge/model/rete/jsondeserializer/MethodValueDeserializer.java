package com.ruleforge.model.rete.jsondeserializer;

import com.fasterxml.jackson.databind.JsonNode;

import com.ruleforge.model.rete.JsonUtils;
import com.ruleforge.model.rule.MethodValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;

/**
 * @author Jacky.gao
 * @since 2015年3月6日
 */
public class MethodValueDeserializer implements ValueDeserializer {

	@Override
	public Value deserialize(JsonNode jsonNode) {
		MethodValue value=new MethodValue();
		value.setArithmetic(JsonUtils.parseComplexArithmetic(jsonNode));
		value.setBeanId(JsonUtils.getJsonValue(jsonNode, "beanId"));
		value.setBeanLabel(JsonUtils.getJsonValue(jsonNode, "beanLabel"));
		value.setMethodLabel(JsonUtils.getJsonValue(jsonNode, "methodLabel"));
		value.setMethodName(JsonUtils.getJsonValue(jsonNode, "methodName"));
		value.setParameters(JsonUtils.parseParameters(jsonNode));
		return value;
	}

	@Override
	public boolean support(ValueType type) {
		return type.equals(ValueType.Method);
	}
}
