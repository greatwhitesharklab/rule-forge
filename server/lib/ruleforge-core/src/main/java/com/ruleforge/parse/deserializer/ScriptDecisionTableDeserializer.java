package com.ruleforge.parse.deserializer;

import com.ruleforge.model.table.ScriptDecisionTable;
import com.ruleforge.parse.table.ScriptDecisionTableParser;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * @date 2015年1月19日
 */
public class ScriptDecisionTableDeserializer implements Deserializer<ScriptDecisionTable> {
    public static final String BEAN_ID = "ruleforge.scriptDecisionTableDeserializer";
    private ScriptDecisionTableParser scriptDecisionTableParser;

    @Override
    public ScriptDecisionTable deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public ScriptDecisionTable deserialize(Element root, boolean isContainSnapshot) {
        return scriptDecisionTableParser.parse(root);
    }

    @Override
    public boolean support(Element root) {
        return scriptDecisionTableParser.support(root.getName());
    }

    public void setScriptDecisionTableParser(
            ScriptDecisionTableParser scriptDecisionTableParser) {
        this.scriptDecisionTableParser = scriptDecisionTableParser;
    }
}
