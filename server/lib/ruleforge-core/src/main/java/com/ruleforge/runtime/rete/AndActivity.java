package com.ruleforge.runtime.rete;

import com.ruleforge.model.rule.lhs.BaseCriteria;

import java.util.*;

/**
 * @author Jacky.gao
 * 2015年1月8日
 */
public class AndActivity extends JoinActivity {
    private boolean passed = false;
    private FactTracker currentTracker;
    private List<Path> fromPaths = new ArrayList<>();

    public AndActivity() {
    }

    public Collection<FactTracker> enter(EvaluationContext context, Object obj, FactTracker tracker) {
        if (this.currentTracker != null) {
            Map<Object, List<BaseCriteria>> map = tracker.getObjectCriteriaMap();
            Map<Object, List<BaseCriteria>> currentMap = this.currentTracker.getObjectCriteriaMap();

            for (Object key : currentMap.keySet()) {
                if (!map.containsKey(key)) {
                    map.put(key, currentMap.get(key));
                }

            }
        }

        this.currentTracker = tracker;
        if (this.isAllPassed()) {
            List<FactTracker> allTrackers = new ArrayList<>();
            List<FactTracker> trackers = this.visitPaths(context, obj, tracker);
            if (trackers != null && !trackers.isEmpty()) {
                allTrackers.addAll(trackers);
            }

            return allTrackers;
        } else {
            return null;
        }
    }

    private boolean isAllPassed() {
        // V5.96 — decompiled do-while → 早返 enhanced for,语义等价(任一 path 未 passed 返 false)
        for (Path path : this.fromPaths) {
            if (!path.isPassed()) {
                return false;
            }
        }
        return true;
    }

    public void addFromPath(Path fromPath) {
        this.fromPaths.add(fromPath);
    }

    public void passAndNode() {
        this.passed = true;
        this.doPassAndNode();
    }

    public boolean joinNodeIsPassed() {
        if (!this.passed) {
            List<Path> paths = this.getPaths();
            if (paths.size() == 1) {
                Path path = paths.get(0);
                AbstractActivity activity = (AbstractActivity) path.getTo();
                return activity.joinNodeIsPassed();
            }
        }

        return this.passed;
    }

    public void reset() {
        this.currentTracker = null;
        this.passed = false;
    }
}
