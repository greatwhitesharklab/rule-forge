//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.ruleforge.runtime.rete;
import com.ruleforge.engine.Activity;
import com.ruleforge.engine.EvaluationContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractActivity implements Activity {
    private List<Path> paths;

    public AbstractActivity() {
    }

    public List<Path> getPaths() {
        return this.paths;
    }

    public void addPath(Path path) {
        if (this.paths == null) {
            this.paths = new ArrayList<>();
        }

        this.paths.add(path);
    }

    protected List<FactTracker> visitPaths(EvaluationContext context, Object obj, FactTracker tracker) {
        if (this.paths != null && !this.paths.isEmpty()) {
            List<FactTracker> trackers = null;

            // V6.2 — 砍死代码 else + 删未用 size 变量 (Fernflower 反编译 artifact)。
            // line 33 int size = this.paths.size() + line 39 if (size > 0) {...} else {...}
            // 模式: else 不可达,因为 line 31 `!this.paths.isEmpty()` guard 已经保证
            // size > 0。else 分支 (传原 tracker 而非 newSubFactTracker) 是死代码 —
            // 永远是 size > 0 path,所以永远是 newSubFactTracker 路径。
            // 行为 100% 等价:在 `!paths.isEmpty()` 前提下,size > 0 永真。
            for (Path path : this.paths) {
                Collection<FactTracker> results = null;
                Activity activity = path.getTo();
                path.setPassed(true);
                results = activity.enter(context, obj, tracker.newSubFactTracker());

                if (results != null) {
                    if (trackers == null) {
                        trackers = new ArrayList<>();
                    }

                    trackers.addAll(results);
                }
            }

            return trackers;
        } else {
            return null;
        }
    }

    protected void doPassAndNode() {
        List<Path> paths = this.getPaths();
        if (paths != null) {
            // V5.96 — Iterator var123 → enhanced for
            for (Path path : paths) {
                AbstractActivity activity = (AbstractActivity) path.getTo();
                activity.passAndNode();
            }
        }
    }

    public abstract boolean joinNodeIsPassed();

    public abstract void passAndNode();

    public abstract void reset();
}
