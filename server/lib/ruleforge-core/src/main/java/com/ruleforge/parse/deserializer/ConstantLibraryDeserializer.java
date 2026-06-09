package com.ruleforge.parse.deserializer;

import com.ruleforge.model.library.constant.ConstantLibrary;
import com.ruleforge.parse.ConstantLibraryParser;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * @date 2014年12月23日
 */
public class ConstantLibraryDeserializer implements Deserializer<ConstantLibrary> {
    public static final String BEAN_ID = "ruleforge.constantLibraryDeserializer";
    private ConstantLibraryParser constantLibraryParser;

    @Override
    public ConstantLibrary deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public ConstantLibrary deserialize(Element root, boolean isContainSnapshot) {
        return constantLibraryParser.parse(root);
    }

    @Override
    public boolean support(Element root) {
        return constantLibraryParser.support(root.getName());
    }

    public void setConstantLibraryParser(
            ConstantLibraryParser constantLibraryParser) {
        this.constantLibraryParser = constantLibraryParser;
    }
}
