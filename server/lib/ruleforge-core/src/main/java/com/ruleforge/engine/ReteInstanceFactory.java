package com.ruleforge.engine;

import com.ruleforge.model.rete.ObjectTypeNode;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.ReteUnit;
import com.ruleforge.runtime.rete.ObjectTypeActivity;
import com.ruleforge.runtime.rete.ReteInstance;
import com.ruleforge.runtime.rete.ReteInstanceUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V6.1 TD-2 — 把 Rete.newReteInstance() 的 runtime 对象构造逻辑从 model 类抽到 engine 工厂。
 * model/Rete 不再 import runtime.rete.ReteInstance/ReteInstanceUnit(ObjectTypeActivity。
 */
public final class ReteInstanceFactory {

    private ReteInstanceFactory() {
    }

    public static ReteInstance create(Rete rete) {
        List<ObjectTypeActivity> objectTypeActivities = new ArrayList<>();
        Map<Object, Object> contextMap = new HashMap<>();

        for (ObjectTypeNode node : rete.getObjectTypeNodes()) {
            ObjectTypeActivity activity = (ObjectTypeActivity) NodeActivityFactory.create(node, contextMap);
            objectTypeActivities.add(activity);
        }

        Map<String, List<ReteInstanceUnit>> activationGroupMap = buildGroupRetesInstance(rete.getActivationGroupRetesMap());
        Map<String, List<ReteInstanceUnit>> agendaGroupMap = buildGroupRetesInstance(rete.getAgendaGroupRetesMap());
        return new ReteInstance(objectTypeActivities, activationGroupMap, agendaGroupMap);
    }

    private static Map<String, List<ReteInstanceUnit>> buildGroupRetesInstance(Map<String, List<ReteUnit>> groupRetesMap) {
        if (groupRetesMap == null) {
            return null;
        }
        Map<String, List<ReteInstanceUnit>> map = new HashMap<>();
        for (String name : groupRetesMap.keySet()) {
            List<ReteUnit> reteList = groupRetesMap.get(name);
            for (ReteUnit unit : reteList) {
                List<ReteInstanceUnit> instances = map.computeIfAbsent(name, k -> new ArrayList<>());
                Rete rete = unit.getRete();
                ReteInstance ins = create(rete);
                ReteInstanceUnit insUnit = new ReteInstanceUnit(ins, unit.getRuleName());
                insUnit.setEffectiveDate(unit.getEffectiveDate());
                insUnit.setExpiresDate(unit.getExpiresDate());
                instances.add(insUnit);
            }
        }
        return map;
    }
}
