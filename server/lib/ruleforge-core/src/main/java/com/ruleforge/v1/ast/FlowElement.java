package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * V1 Flow 元素基类。BPMN 子集 5 元素(词汇用 BPMN,序列化 JSON,不暴露 BPMN 2.0 全集)。
 *
 * <p>Jackson 多态:JSON 的 {@code "type"} 字段决定反序列化子类。
 * 跟 V1 节点 1:1 映射:startEvent→Start / serviceTask→RuleSet|DecisionTable|ScoreCard /
 * exclusiveGateway→Gateway / endEvent→Decision / sequenceFlow→边。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StartEvent.class, name = "startEvent"),
        @JsonSubTypes.Type(value = ServiceTask.class, name = "serviceTask"),
        @JsonSubTypes.Type(value = ExclusiveGateway.class, name = "exclusiveGateway"),
        @JsonSubTypes.Type(value = EndEvent.class, name = "endEvent"),
        @JsonSubTypes.Type(value = SequenceFlow.class, name = "sequenceFlow"),
})
public abstract class FlowElement {
    private String id;
    private String name;
    /** 画布坐标,presentation-only,运行时忽略。 */
    private Position position;

    /** BPMN 元素类型常量(startEvent/serviceTask/...),Jackson discriminator。 */
    public abstract String getType();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }
}
