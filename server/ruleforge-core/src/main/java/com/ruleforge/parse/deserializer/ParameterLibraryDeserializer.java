package com.ruleforge.parse.deserializer;

import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.parse.ParameterLibraryParser;
import org.dom4j.Element;

import java.util.List;

/**
 * @author Jacky.gao
 * @date 2015年3月10日
 */
public class ParameterLibraryDeserializer implements Deserializer<List<Variable>> {
    public static final String BEAN_ID = "ruleforge.parameterLibraryDeserializer";
    private ParameterLibraryParser parameterLibraryParser;

    @Override
    public List<Variable> deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public List<Variable> deserialize(Element root, boolean isContainSnapshot) {
        return parameterLibraryParser.parse(root);
    }

    @Override
    public boolean support(Element root) {
        return parameterLibraryParser.support(root.getName());
    }

    public void setParameterLibraryParser(
            ParameterLibraryParser parameterLibraryParser) {
        this.parameterLibraryParser = parameterLibraryParser;
    }
}
