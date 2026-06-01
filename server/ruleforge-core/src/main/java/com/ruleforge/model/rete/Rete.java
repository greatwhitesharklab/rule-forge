package com.ruleforge.model.rete;

import com.ruleforge.model.Node;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.runtime.rete.ObjectTypeActivity;
import com.ruleforge.runtime.rete.ReteInstance;
import com.ruleforge.runtime.rete.ReteInstanceUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jacky.gao
 * 2015年1月6日
 */
@Getter
@NoArgsConstructor
public class Rete implements Node {
    private List<ObjectTypeNode> objectTypeNodes;
    @Setter
    private Map<String, List<ReteUnit>> activationGroupRetesMap;
    @Setter
    private Map<String, List<ReteUnit>> agendaGroupRetesMap;
    @JsonIgnore
    private ResourceLibrary resourceLibrary;

    public Rete(List<ObjectTypeNode> objectTypeNodes, ResourceLibrary resourceLibrary) {
        this.objectTypeNodes = objectTypeNodes;
        this.resourceLibrary = resourceLibrary;
    }

    public ReteInstance newReteInstance() {
        List<ObjectTypeActivity> objectTypeActivities = new ArrayList<>();
        Map<Object, Object> contextMap = new HashMap<>();

        for (ObjectTypeNode node : this.objectTypeNodes) {
            objectTypeActivities.add((ObjectTypeActivity) node.newActivity(contextMap));
        }

        Map<String, List<ReteInstanceUnit>> activationGroupReteInstancesMap = this.buildGroupRetesInstance(this.activationGroupRetesMap);
        Map<String, List<ReteInstanceUnit>> agendaGroupReteInstancesMap = this.buildGroupRetesInstance(this.agendaGroupRetesMap);
        return new ReteInstance(objectTypeActivities, activationGroupReteInstancesMap, agendaGroupReteInstancesMap);
    }

    private Map<String, List<ReteInstanceUnit>> buildGroupRetesInstance(Map<String, List<ReteUnit>> groupRetesMap) {
        if (groupRetesMap == null) {
            return null;
        } else {
            Map<String, List<ReteInstanceUnit>> map = new HashMap<>();

            for (String name : groupRetesMap.keySet()) {
                List<ReteUnit> reteList = groupRetesMap.get(name);

                for (ReteUnit unit : reteList) {
                    List<ReteInstanceUnit> instances = map.computeIfAbsent(name, k -> new ArrayList<>());

                    Rete rete = unit.getRete();
                    ReteInstance ins = rete.newReteInstance();
                    ReteInstanceUnit insUnit = new ReteInstanceUnit(ins, unit.getRuleName());
                    insUnit.setEffectiveDate(unit.getEffectiveDate());
                    insUnit.setExpiresDate(unit.getExpiresDate());
                    instances.add(insUnit);
                }
            }

            return map;
        }
    }
}
