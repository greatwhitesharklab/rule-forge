package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * V1 资产顶层容器。一个 RuleForge 资产 = 一个 JSON 文件(.json,内容靠 {@link #version} 自识别)。
 *
 * <p>结构:
 * <ul>
 *   <li>{@link #flow} — 画布编排(BPMN 子集),只管谁连谁</li>
 *   <li>{@link #nodes} — 节点定义(平铺,id→NodeBase),flow 按 id 引用。RuleSet 节点可在多 flow 复用</li>
 *   <li>{@link #schema} — 输入 fact 结构(替代 vl 库)</li>
 * </ul>
 *
 * <p>不存 ReactFlow JSON;position 是可选 presentation 字段,运行时忽略。
 * DRL/DMN/PMML Adapter 读老格式 → 产出 RuleAsset JSON。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleAsset {
    /** 资产格式版本。内容自识别 key。 */
    private String version = "1.0";
    private String id;
    private String name;
    private Flow flow;
    private Map<String, NodeBase> nodes;
    private Schema schema;
    private AssetMetadata metadata;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

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

    public Flow getFlow() {
        return flow;
    }

    public void setFlow(Flow flow) {
        this.flow = flow;
    }

    public Map<String, NodeBase> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, NodeBase> nodes) {
        this.nodes = nodes;
    }

    public Schema getSchema() {
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    public AssetMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(AssetMetadata metadata) {
        this.metadata = metadata;
    }
}
