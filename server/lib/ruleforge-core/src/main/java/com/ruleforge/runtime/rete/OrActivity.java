package com.ruleforge.runtime.rete;
import com.ruleforge.engine.Path;
import com.ruleforge.engine.EvaluationContext;

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
        // V6.9.2 — 收口 Fernflower state machine (V6.2 同档), 跟 AndActivity 完全同构。 旧实现
        // 缺 paths==null guard, 简化: passed=true → true early return; paths.size()==1 →
        // 递归 child; 否则 false。 顺带 null safety。
        if (this.passed) {
            return true;
        }
        List<Path> paths = this.getPaths();
        if (paths == null || paths.size() != 1) {
            return false;
        }
        Path path = paths.get(0);
        AbstractActivity activity = (AbstractActivity) path.getTo();
        return activity.joinNodeIsPassed();
    }

    public void reset() {
        this.passed = false;
    }
}
