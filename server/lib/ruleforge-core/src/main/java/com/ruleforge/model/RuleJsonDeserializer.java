package com.ruleforge.model;

import com.ruleforge.model.rule.Rule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * 2015年10月21日
 */
public class RuleJsonDeserializer extends AbstractJsonDeserializer<List<Rule>> {
    @Override
    public List<Rule> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectCodec oc = jp.getCodec();
        JsonNode jsonNode = oc.readTree(jp);
        // V6.9.13 — V5.96 skip: Iterator + while 状态机 → enhanced for。 跟 V6.9.12
        // JsonUtils.parseParameters 同模式 (反编译 artifact 收口)。 Build-time per-JSON-parse
        // 调用, JFR 0 sample 预期。
        List<Rule> rules = new ArrayList<>();
        for (JsonNode childNode : jsonNode) {
            rules.add(parseRule(jp, childNode));
        }
        return rules;
    }
}
