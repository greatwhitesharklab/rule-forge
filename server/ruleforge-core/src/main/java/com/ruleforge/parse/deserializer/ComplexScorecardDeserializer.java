package com.ruleforge.parse.deserializer;

import com.ruleforge.model.scorecard.ComplexScorecardDefinition;
import com.ruleforge.parse.scorecard.ComplexScorecardParser;
import org.dom4j.Element;

/**
 * @author fred
 * 2018-12-12 2:05 PM
 */
public class ComplexScorecardDeserializer implements Deserializer<ComplexScorecardDefinition> {
    public static final String BEAN_ID = "ruleforge.complexScorecardDeserializer";
    private ComplexScorecardParser complexScorecardParser;

    public ComplexScorecardDeserializer() {
    }

    @Override
    public ComplexScorecardDefinition deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public ComplexScorecardDefinition deserialize(Element root, boolean isContainSnapshot) {
        return this.complexScorecardParser.parse(root);
    }

    public void setComplexScorecardParser(ComplexScorecardParser complexScorecardParser) {
        this.complexScorecardParser = complexScorecardParser;
    }

    @Override
    public boolean support(Element root) {
        return this.complexScorecardParser.support(root.getName());
    }
}
