package com.ruleforge.model.rete;

import com.ruleforge.model.Node;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.engine.ReteInstanceFactory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;

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

    /**
     * V6.1 TD-2 — 构造逻辑委托给 {@link ReteInstanceFactory}(engine 包),
     * 本类(model)不再 import runtime.rete.* 类型。
     */
    public Object newReteInstance() {
        return ReteInstanceFactory.create(this);
    }
}
