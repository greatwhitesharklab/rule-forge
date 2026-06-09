package com.ruleforge.action;

import com.ruleforge.runtime.rete.Context;

import java.util.List;

public interface Action extends Comparable<Action> {
    ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects);

    ActionType getActionType();

    int getPriority();

    void setDebug(boolean debug);
}
