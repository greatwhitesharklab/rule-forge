package com.ruleforge.model.rete.jsondeserializer;

import com.fasterxml.jackson.databind.JsonNode;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rete.JsonUtils;
import com.ruleforge.model.rule.NamedReferenceValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;

/**
 * @author Jacky.gao
 * @since 2016年8月16日
 */
public class NameReferenceValueDeserializer implements ValueDeserializer {
	@Override
	public Value deserialize(JsonNode jsonNode) {
		NamedReferenceValue value=new NamedReferenceValue();
		value.setArithmetic(JsonUtils.parseComplexArithmetic(jsonNode));
		String datatypeText=JsonUtils.getJsonValue(jsonNode, "datatype");
		if(datatypeText!=null){
			value.setDatatype(Datatype.valueOf(datatypeText));
		}
		value.setReferenceName(JsonUtils.getJsonValue(jsonNode, "referenceName"));
		value.setPropertyLabel(JsonUtils.getJsonValue(jsonNode, "propertyLabel"));
		value.setPropertyName(JsonUtils.getJsonValue(jsonNode, "propertyName"));
		return value;
	}

	@Override
	public boolean support(ValueType type) {
		return type.equals(ValueType.NamedReference);
	}
}
