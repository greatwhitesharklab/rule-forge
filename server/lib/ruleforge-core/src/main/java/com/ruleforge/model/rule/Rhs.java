package com.ruleforge.model.rule;

import com.ruleforge.action.Action;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class Rhs {
    private List<Action> actions;

    public void setActions(List<Action> actions) {
        this.actions = actions;
        Collections.sort(actions);
    }

    public void addAction(Action action) {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        actions.add(action);
        Collections.sort(actions);
    }
}
