package com.ruleforge.model.rete.jsondeserializer;

import com.fasterxml.jackson.databind.JsonNode;

import com.ruleforge.model.rete.JsonUtils;
import com.ruleforge.model.rule.ParameterValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;

/**
 * @author Jacky.gao
 * @since 2015年5月26日
 */
public class ParameterValueDeserializer implements ValueDeserializer {

	@Override
	public Value deserialize(JsonNode jsonNode) {
		ParameterValue value=new ParameterValue();
		value.setArithmetic(JsonUtils.parseComplexArithmetic(jsonNode));
		value.setVariableLabel(JsonUtils.getJsonValue(jsonNode, "variableLabel"));
		value.setVariableName(JsonUtils.getJsonValue(jsonNode, "variableName"));
		return value;
	}

	@Override
	public boolean support(ValueType type) {
		return type.equals(ValueType.Parameter);
	}
}
