package com.ruleforge.builder.resource;

import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.parse.deserializer.RuleSetDeserializer;
import lombok.Setter;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * 2014年12月22日
 */
@Setter
public class RuleSetResourceBuilder implements ResourceBuilder<RuleSet> {

    private RuleSetDeserializer ruleSetDeserializer;

    public RuleSet build(Element root) {
        return ruleSetDeserializer.deserialize(root);
    }

    public boolean support(Element root) {
        return ruleSetDeserializer.support(root);
    }

    public ResourceType getType() {
        return ResourceType.RuleSet;
    }

}
