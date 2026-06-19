package com.ruleforge.engine;
import com.ruleforge.runtime.rete.AndActivity;
import com.ruleforge.engine.Activity;

/**
 * @author Jacky.gao
 * 2015年1月8日
 */
public class Path {
    private boolean passed;
    private Activity to;

    public Path(Activity to) {
        this.to = to;
        if (to instanceof AndActivity) {
            AndActivity andActivity = (AndActivity) to;
            andActivity.addFromPath(this);
        }
    }

    public Activity getTo() {
        return to;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }
}
