package com.ruleforge.v1.ast;

import java.util.List;

/**
 * V1 Flow 模型(BPMN 子集)。只管编排(谁连谁),业务逻辑在 {@link RuleAsset#getNodes()}。
 *
 * <p>不存 ReactFlow JSON — {@link FlowElement#getPosition()} 是可选 presentation-only 坐标,
 * 运行时忽略;ReactFlow 的 node/edge/data/selected 等不持久化,由 RuleAsset 派生。
 * 后端执行复用 ruleforge-decision BPMN 引擎(JSON 转 BPMN 2.0 喂入)。
 */
public class Flow {
    private String id;
    private String name;
    private String version = "1.0";
    private List<FlowElement> flowElements;

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<FlowElement> getFlowElements() {
        return flowElements;
    }

    public void setFlowElements(List<FlowElement> flowElements) {
        this.flowElements = flowElements;
    }
}
