package com.ruleforge.model.rete.jsondeserializer;

import com.fasterxml.jackson.databind.JsonNode;

import com.ruleforge.model.rete.JsonUtils;
import com.ruleforge.model.rule.CommonFunctionValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;

/**
 * @author Jacky.gao
 * @since 2015年7月30日
 */
public class CommonFunctionValueDeserializer implements ValueDeserializer {
	@Override
	public Value deserialize(JsonNode jsonNode) {
		CommonFunctionValue value=new CommonFunctionValue();
		value.setArithmetic(JsonUtils.parseComplexArithmetic(jsonNode));
		value.setLabel(JsonUtils.getJsonValue(jsonNode, "label"));
		value.setName(JsonUtils.getJsonValue(jsonNode, "name"));
		value.setParameter(JsonUtils.parseCommonFunctionParameter(jsonNode));
		value.setValueType(ValueType.CommonFunction);
		return value;
	}

	@Override
	public boolean support(ValueType type) {
		return type.equals(ValueType.CommonFunction);
	}
}
