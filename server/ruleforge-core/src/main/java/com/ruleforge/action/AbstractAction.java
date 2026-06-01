package com.ruleforge.action;


/**
 * @author Jacky.gao
 * 2015年4月8日
 */
public abstract class AbstractAction implements Action {
    private int priority;
    protected boolean debug;

    public AbstractAction() {
    }

    public int compareTo(Action o) {
        return o.getPriority() - this.priority;
    }

    public int getPriority() {
        return this.priority;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
