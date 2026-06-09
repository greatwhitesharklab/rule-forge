package com.ruleforge.parse.deserializer;

import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.parse.table.DecisionTableParser;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * @date 2015年1月19日
 */
public class DecisionTableDeserializer implements Deserializer<DecisionTable> {
    public static final String BEAN_ID = "ruleforge.decisionTableDeserializer";
    private DecisionTableParser decisionTableParser;

    @Override
    public DecisionTable deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public DecisionTable deserialize(Element root, boolean isContainSnapshot) {
        return this.decisionTableParser.parse(root);
    }

    @Override
    public boolean support(Element root) {
        return decisionTableParser.support(root.getName());
    }

    public void setDecisionTableParser(DecisionTableParser decisionTableParser) {
        this.decisionTableParser = decisionTableParser;
    }
}
