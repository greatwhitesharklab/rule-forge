package com.ruleforge.parse;

import com.ruleforge.action.Action;


/**
 * @author Jacky.gao
 * 2014年12月23日
 */
public abstract class ActionParser extends AbstractParser<Action> {
    protected ValueParser valueParser;

    public void setValueParser(ValueParser valueParser) {
        this.valueParser = valueParser;
    }
}
