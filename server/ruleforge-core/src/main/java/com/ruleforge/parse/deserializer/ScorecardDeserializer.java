package com.ruleforge.parse.deserializer;

import com.ruleforge.model.scorecard.ScorecardDefinition;
import com.ruleforge.parse.scorecard.ScorecardParser;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * @author fred
 * 2016年9月22日
 */
public class ScorecardDeserializer implements Deserializer<ScorecardDefinition> {
    public static final String BEAN_ID = "ruleforge.scorecardDeserializer";
    private ScorecardParser scorecardParser;

    @Override
    public ScorecardDefinition deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public ScorecardDefinition deserialize(Element root, boolean isContainSnapshot) {
        return scorecardParser.parse(root);
    }

    public void setScorecardParser(ScorecardParser scorecardParser) {
        this.scorecardParser = scorecardParser;
    }

    @Override
    public boolean support(Element root) {
        return scorecardParser.support(root.getName());
    }
}
