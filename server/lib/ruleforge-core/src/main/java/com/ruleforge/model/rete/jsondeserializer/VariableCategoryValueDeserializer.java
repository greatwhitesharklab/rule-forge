package com.ruleforge.model.rete.jsondeserializer;

import com.fasterxml.jackson.databind.JsonNode;

import com.ruleforge.model.rete.JsonUtils;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;
import com.ruleforge.model.rule.VariableCategoryValue;

/**
 * @author Jacky.gao
 * @since 2015年3月6日
 */
public class VariableCategoryValueDeserializer implements ValueDeserializer {

	@Override
	public Value deserialize(JsonNode jsonNode) {
		VariableCategoryValue value=new VariableCategoryValue();
		value.setArithmetic(JsonUtils.parseComplexArithmetic(jsonNode));
		value.setVariableCategory(JsonUtils.getJsonValue(jsonNode, "variableCategory"));
		return value;
	}

	@Override
	public boolean support(ValueType type) {
		return type.equals(ValueType.VariableCategory);
	}
}
