package com.ruleforge.runtime.rete;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.rule.lhs.BaseCriteria;
import com.ruleforge.runtime.agenda.Activation;
import com.ruleforge.runtime.agenda.ActivationImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FactTracker {
    private Activation activation;
    private Map<Object, List<BaseCriteria>> objectCriteriaMap = new HashMap<>();

    public Activation getActivation() {
        return activation;
    }

    public void setActivation(Activation activation) {
        ActivationImpl ac = (ActivationImpl) activation;
        ac.setObjectCriteriaMap(objectCriteriaMap);
        this.activation = activation;
    }

    public Map<Object, List<BaseCriteria>> getObjectCriteriaMap() {
        return objectCriteriaMap;
    }

    public void addObjectCriteria(Object obj, BaseCriteria criteria) {
        if (obj instanceof HashMap && !(obj instanceof GeneralEntity)) {
            obj = HashMap.class.getName();
        }
        // V5.97 — 砍 containsKey + get 双 lookup,套 V5.93 getCriteriaValue 模式。
        // HashMap.get 对 missing key 返 null,等价 containsKey==false,
        // 1 call 砍 1 HashMap.containsKey + 1 String.hashCode。
        // 行为等价:本方法 put 后 list 永远非 null(null-stored 路径不可达),
        // 所以 get!=null ↔ containsKey 100% 等价 — 锁 [[v597-facttracker-double-lookup]]。
        List<BaseCriteria> list = objectCriteriaMap.get(obj);
        if (list != null) {
            if (!list.contains(criteria)) {
                list.add(criteria);
            }
        } else {
            list = new ArrayList<>();
            list.add(criteria);
            objectCriteriaMap.put(obj, list);
        }
    }

    public FactTracker newSubFactTracker() {
        FactTracker tracker = new FactTracker();
        tracker.getObjectCriteriaMap().putAll(objectCriteriaMap);
        return tracker;
    }
}
