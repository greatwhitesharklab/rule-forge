package com.ruleforge.console.controller.v1;

import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.NodeBase;
import com.ruleforge.v1.ast.library.Libraries;

import java.util.Map;

/**
 * V1 决策流执行请求(POST /v1/execute)。
 *
 * <p>{@code asset} = 完整 RuleAsset(画布 toRuleAsset 序列化的 flow + nodes + schema);
 * {@code fact} = 输入 fact 字段(Map,GeneralEntity 模型)。执行后 fact 原地被节点改写
 * (ScoreCard 写 output、RuleSet action 写字段、setDecision 写 decisionField),随响应返回。
 */
public class V1ExecutionRequest {
    private RuleAsset asset;
    private Map<String, Object> fact;
    /** 参数库(pl)值:规则 CEL param.xxx 引用(V7.4),V1FlowRunner fireRules 注入会话参数。 */
    private Map<String, Object> parameters;
    /** 四库(V7.4.1):vl 派生 schema,pl/cl 参数,al 动作。设了优先于 parameters。 */
    private Libraries libraries;
    /**
     * V7.5:规则独立文件(ruleRef → NodeBase 映射)。
     * 决策流节点设了 ruleRef 时,用此 Map 中的规则文件内容替代内嵌节点。
     * Key = 规则文件路径(如 "/proj/V1规则集/precheck.v1rs.json"),Value = 规则文件顶层 NodeBase。
     */
    private Map<String, NodeBase> ruleFiles;

    public RuleAsset getAsset() {
        return asset;
    }

    public void setAsset(RuleAsset asset) {
        this.asset = asset;
    }

    public Map<String, Object> getFact() {
        return fact;
    }

    public void setFact(Map<String, Object> fact) {
        this.fact = fact;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Libraries getLibraries() {
        return libraries;
    }

    public void setLibraries(Libraries libraries) {
        this.libraries = libraries;
    }

    public Map<String, NodeBase> getRuleFiles() {
        return ruleFiles;
    }

    public void setRuleFiles(Map<String, NodeBase> ruleFiles) {
        this.ruleFiles = ruleFiles;
    }
}
