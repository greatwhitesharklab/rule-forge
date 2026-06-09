package com.ruleforge.model.rete.jsondeserializer;

import com.fasterxml.jackson.databind.JsonNode;

import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;

/**
 * @author Jacky.gao
 * @since 2015年3月6日
 */
public interface ValueDeserializer {
	Value deserialize(JsonNode jsonNode);
	boolean support(ValueType type);
}
