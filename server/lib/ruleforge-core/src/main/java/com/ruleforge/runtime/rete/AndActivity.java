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

            // V6.1 — 砍 containsKey + put 双 lookup,套 V5.93/V5.94/V5.97/V5.98
            // getCriteriaValue / getPartValue / addObjectCriteria 同模式。
            // HashMap.get 对 missing key 返 null,等价 containsKey==false,
            // 1 call 砍 1 HashMap.containsKey + 1 String.hashCode per iter。
            // 本方法在 rete hot path (JFR 666 sample),2-class rete 多次 join
            // per-fact 多次 iter,锁定 [[v611-andactivity-double-lookup]]。
            // 行为等价:本方法 put 的 value 永远非 null (FactTracker.addObjectCriteria
            // 保证 list 非 null — V5.97 doc),所以 get==null ↔ containsKey 100% 等价。
            for (Object key : currentMap.keySet()) {
                if (map.get(key) == null) {
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
