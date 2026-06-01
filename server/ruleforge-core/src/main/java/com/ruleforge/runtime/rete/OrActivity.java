package com.ruleforge.runtime.rete;

import java.util.List;
import java.util.Map;

/**
 * @author Jacky.gao
 * 2015年1月8日
 */
public class OrActivity extends JoinActivity {
    private boolean passed;

    public OrActivity() {
    }

    public List<FactTracker> enter(EvaluationContext context, Object obj, FactTracker tracker) {
        if (this.passed) {
            return null;
        } else {
            this.passed = true;
            return this.visitPaths(context, obj, tracker);
        }
    }

    public void passAndNode() {
    }

    public boolean joinNodeIsPassed() {
        if (!this.passed) {
            List<Path> paths = this.getPaths();
            if (paths.size() == 1) {
                Path path = (Path) paths.get(0);
                AbstractActivity activity = (AbstractActivity) path.getTo();
                return activity.joinNodeIsPassed();
            }
        }

        return this.passed;
    }

    public void reset() {
        this.passed = false;
    }
}
