package com.ruleforge.model.rete.jsondeserializer;

import com.fasterxml.jackson.databind.JsonNode;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rete.JsonUtils;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;
import com.ruleforge.model.rule.VariableValue;

/**
 * @author Jacky.gao
 * @since 2015年3月6日
 */
public class VariableValueDeserializer implements ValueDeserializer {
	@Override
	public Value deserialize(JsonNode jsonNode) {
		VariableValue value=new VariableValue();
		value.setArithmetic(JsonUtils.parseComplexArithmetic(jsonNode));
		String datatypeText=JsonUtils.getJsonValue(jsonNode, "datatype");
		if(datatypeText!=null){
			value.setDatatype(Datatype.valueOf(datatypeText));
		}
		value.setVariableCategory(JsonUtils.getJsonValue(jsonNode, "variableCategory"));
		value.setVariableLabel(JsonUtils.getJsonValue(jsonNode, "variableLabel"));
		value.setVariableName(JsonUtils.getJsonValue(jsonNode, "variableName"));
		return value;
	}

	@Override
	public boolean support(ValueType type) {
		return type.equals(ValueType.Variable);
	}
}
