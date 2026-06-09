package com.ruleforge.action;

import java.util.List;
import java.util.Map;

import com.ruleforge.model.rule.Value;
import com.ruleforge.model.scorecard.runtime.ScoreRuntimeValue;
import com.ruleforge.runtime.rete.Context;
import com.ruleforge.runtime.rete.ValueCompute;

/**
 * @author Jacky.gao
 * 2016年9月26日
 */
public class ScoringAction extends AbstractAction {
    private Value value;
    private int rowNumber;
    private String name;
    private String weight;
    private ActionType actionType = ActionType.Scoring;

    public ScoringAction(int rowNumber, String name, String weight) {
        this.rowNumber = rowNumber;
        this.name = name;
        this.weight = weight;
    }

    @Override
    public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
        ValueCompute valueCompute = (ValueCompute) context.getApplicationContext().getBean(ValueCompute.BEAN_ID);
        Object content = valueCompute.complexValueCompute(value, matchedObject, context, allMatchedObjects);
        ScoreRuntimeValue scoreRuntimeValue = new ScoreRuntimeValue(this.rowNumber, this.name, this.weight, content);
        return new ActionValueImpl(scoreRuntimeValue.getName(), scoreRuntimeValue);
    }

    public Value getValue() {
        return value;
    }

    public void setValue(Value value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getWeight() {
        return weight;
    }

    @Override
    public ActionType getActionType() {
        return actionType;
    }

    public int getRowNumber() {
        return rowNumber;
    }
}
