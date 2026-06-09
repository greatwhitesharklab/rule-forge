package com.ruleforge.action;

import java.util.List;
import java.util.Map;

import com.ruleforge.runtime.rete.Context;

/**
 * @author Jacky.gao
 * @since 2014年12月22日
 */
public class SimpleAction extends AbstractAction {
    public SimpleAction(Object value) {
    }

    public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
        return null;
    }

    public ActionType getActionType() {
        return ActionType.ConsolePrint;
    }
}
