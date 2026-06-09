package com.ruleforge.parse.deserializer;

import com.ruleforge.model.crosstab.CrosstabDefinition;
import com.ruleforge.parse.crosstab.CrosstabParser;
import org.dom4j.Element;

/**
 * @author fred
 * 2018-11-05 6:42 PM
 */
public class CrosstableDeserializer implements Deserializer<CrosstabDefinition> {
    public static final String BEAN_ID = "ruleforge.crosstableDeserializer";
    private CrosstabParser crosstabParser;

    public CrosstableDeserializer() {
    }

    @Override
    public CrosstabDefinition deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public CrosstabDefinition deserialize(Element root, boolean isContainSnapshot) {
        return this.crosstabParser.parse(root);
    }

    @Override
    public boolean support(Element root) {
        return "crosstab".equals(root.getName());
    }

    public void setCrosstabParser(CrosstabParser crosstabParser) {
        this.crosstabParser = crosstabParser;
    }
}
