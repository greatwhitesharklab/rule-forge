package com.ruleforge.parse.deserializer;

import com.ruleforge.model.library.action.ActionLibrary;
import com.ruleforge.parse.ActionLibraryParser;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * 2014年12月23日
 */
public class ActionLibraryDeserializer implements Deserializer<ActionLibrary> {
    public static final String BEAN_ID = "ruleforge.actionLibraryDeserializer";
    private ActionLibraryParser actionLibraryParser;

    @Override
    public ActionLibrary deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public ActionLibrary deserialize(Element root, boolean isContainSnapshot) {
        return actionLibraryParser.parse(root);
    }

    @Override
    public boolean support(Element root) {
        return actionLibraryParser.support(root.getName());
    }

    public void setActionLibraryParser(ActionLibraryParser actionLibraryParser) {
        this.actionLibraryParser = actionLibraryParser;
    }
}
