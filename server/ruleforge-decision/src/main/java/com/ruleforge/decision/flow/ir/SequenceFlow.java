package com.ruleforge.decision.flow.ir;

import java.util.Collections;
import java.util.Map;

/**
 * 不可变的 IR sequenceFlow。
 *
 * 路由优先级(BpmnNodeRunner 决定下一节点):
 * 1. userTask 后的 binary 决策 — 匹配 ruleforge:decisionValue
 * 2. exclusiveGateway 后的 condition — UEL 解析
 * 3. exclusiveGateway 后的 percent — 加权随机
 * 4. 默认第一条 (isDefault = true 或唯一 outgoing)
 */
public final class SequenceFlow {
    private final String id;
    private final String sourceId;
    private final String targetId;
    private final String conditionExpression;
    private final Integer percent;
    private final boolean isDefault;
    private final Map<String, String> extensionAttrs;

    public SequenceFlow(String id, String sourceId, String targetId,
                        String conditionExpression, Integer percent,
                        boolean isDefault, Map<String, String> extensionAttrs) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.conditionExpression = conditionExpression;
        this.percent = percent;
        this.isDefault = isDefault;
        this.extensionAttrs = extensionAttrs == null ? Map.of() : Map.copyOf(extensionAttrs);
    }

    public String getId() { return id; }
    public String getSourceId() { return sourceId; }
    public String getTargetId() { return targetId; }
    public String getConditionExpression() { return conditionExpression; }
    public Integer getPercent() { return percent; }
    public boolean isDefault() { return isDefault; }
    public Map<String, String> getExtensionAttrs() { return extensionAttrs; }

    public String attr(String key) { return extensionAttrs.get(key); }
}
