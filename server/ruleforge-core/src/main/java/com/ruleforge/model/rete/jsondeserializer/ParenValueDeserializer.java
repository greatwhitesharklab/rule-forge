package com.ruleforge.model.rete.jsondeserializer;

import com.fasterxml.jackson.databind.JsonNode;

import com.ruleforge.model.rete.JsonUtils;
import com.ruleforge.model.rule.ParenValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;

/**
 * @author Jacky.gao
 * @since 2015年6月15日
 */
public class ParenValueDeserializer implements ValueDeserializer {

	@Override
	public Value deserialize(JsonNode jsonNode) {
		ParenValue value=new ParenValue();
		value.setArithmetic(JsonUtils.parseComplexArithmetic(jsonNode));
		value.setValue(JsonUtils.parseValue(jsonNode));
		return value;
	}

	@Override
	public boolean support(ValueType type) {
		return type.equals(ValueType.Paren);
	}

}
