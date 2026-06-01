package com.ruleforge.parse.deserializer;

import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.parse.VariableLibraryParser;
import org.dom4j.Element;

import java.util.List;


/**
 * @author Jacky.gao
 * @date 2014年12月23日
 */
public class VariableLibraryDeserializer implements Deserializer<List<VariableCategory>> {
    public static final String BEAN_ID = "ruleforge.variableLibraryDeserializer";
    private VariableLibraryParser variableLibraryParser;

    @Override
    public List<VariableCategory> deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public List<VariableCategory> deserialize(Element root, boolean isContainSnapshot) {
        return this.variableLibraryParser.parse(root);
    }

    @Override
    public boolean support(Element root) {
        if (variableLibraryParser.support(root.getName())) {
            return true;
        } else {
            return false;
        }
    }

    public void setVariableLibraryParser(VariableLibraryParser variableLibraryParser) {
        this.variableLibraryParser = variableLibraryParser;
    }
}
