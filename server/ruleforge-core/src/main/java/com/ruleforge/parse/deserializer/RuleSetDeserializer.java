package com.ruleforge.parse.deserializer;

import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.parse.RuleSetParser;
import lombok.Setter;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * 2014年12月23日
 */
@Setter
public class RuleSetDeserializer implements Deserializer<RuleSet> {

    public static final String BEAN_ID = "ruleforge.ruleSetDeserializer";
    private RuleSetParser ruleSetParser;

    @Override
    public RuleSet deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public RuleSet deserialize(Element root, boolean isContainSnapshot) {
        return this.ruleSetParser.parse(root, isContainSnapshot);
    }

    public boolean support(Element root) {
        return ruleSetParser.support(root.getName());
    }

}
