package com.ruleforge.runtime;

import com.ruleforge.model.GeneralEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V6.2 — 从 KnowledgeSessionImpl 抽取的 fact 存储管理(god class 拆分)。
 * 管 allFactsList + factMaps。insert/retract/update/assertFact/addToFactsMap/getClassName。
 */
public class FactStore {
    private final List<Object> allFactsList = new ArrayList<>();
    private final List<Map<?, ?>> factMaps = new ArrayList<>();

    /** V5.82:累加到 List<Object> allFactsList,不按 className 覆盖。 */
    public void add(Object fact) {
        allFactsList.add(fact);
    }

    public void addAll(List<Object> facts) {
        allFactsList.addAll(facts);
    }

    public boolean remove(Object fact) {
        return allFactsList.remove(fact);
    }

    /** Map 型 fact(非 GeneralEntity)单独存 factMaps,不进 allFactsList。 */
    public boolean insert(Object fact) {
        if (!(fact instanceof GeneralEntity) && fact instanceof Map) {
            factMaps.add((Map<?, ?>) fact);
            return false;
        }
        add(fact);
        return true;
    }

    public List<Object> getAllFactsList() {
        return allFactsList;
    }

    public List<Map<?, ?>> getFactMaps() {
        return factMaps;
    }

    /** last-wins 视图(向后兼容 ValueCompute.findObject / LoopRule)。 */
    public Map<String, Object> getAllFactsMap() {
        Map<String, Object> map = new HashMap<>();
        for (Object fact : allFactsList) {
            String cls = getClassName(fact);
            if (cls != null) {
                map.put(cls, fact);
            }
        }
        return map;
    }

    public static String getClassName(Object fact) {
        if (fact instanceof GeneralEntity) {
            return ((GeneralEntity) fact).getTargetClass();
        } else if (fact != null) {
            return fact.getClass().getName();
        }
        return null;
    }

    public void clear() {
        allFactsList.clear();
        factMaps.clear();
    }
}
