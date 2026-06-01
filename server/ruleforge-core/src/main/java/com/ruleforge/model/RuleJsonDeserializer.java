package com.ruleforge.model;

import com.ruleforge.model.rule.Rule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
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
        Iterator<JsonNode> childrenNodesIter = jsonNode.elements();
        List<Rule> rules = new ArrayList<>();
        while (childrenNodesIter.hasNext()) {
            JsonNode childNode = childrenNodesIter.next();
            rules.add(parseRule(jp, childNode));
        }
        return rules;
    }
}
