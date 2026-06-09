package com.ruleforge.parse.deserializer;

import com.ruleforge.model.decisiontree.DecisionTree;
import com.ruleforge.parse.decisiontree.DecisionTreeParser;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * @date 2016年2月29日
 */
public class DecisionTreeDeserializer implements Deserializer<DecisionTree> {
    public static final String BEAN_ID = "ruleforge.decisionTreeDeserializer";
    private DecisionTreeParser decisionTreeParser;

    @Override
    public DecisionTree deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public DecisionTree deserialize(Element root, boolean isContainSnapshot) {
        return decisionTreeParser.parse(root);
    }

    public void setDecisionTreeParser(DecisionTreeParser decisionTreeParser) {
        this.decisionTreeParser = decisionTreeParser;
    }

    @Override
    public boolean support(Element root) {
        return decisionTreeParser.support(root.getName());
    }
}
