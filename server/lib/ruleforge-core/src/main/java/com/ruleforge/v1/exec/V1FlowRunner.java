package com.ruleforge.v1.exec;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.v1.ast.DecisionNode;
import com.ruleforge.v1.ast.DecisionTableNode;
import com.ruleforge.v1.ast.EndEvent;
import com.ruleforge.v1.ast.ExclusiveGateway;
import com.ruleforge.v1.ast.FlowElement;
import com.ruleforge.v1.ast.NodeBase;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.RuleSetNode;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.ScoreCardNode;
import com.ruleforge.v1.ast.SequenceFlow;
import com.ruleforge.v1.ast.ServiceTask;
import com.ruleforge.v1.ast.StartEvent;
import com.ruleforge.v1.cel.CelEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V1 Flow 执行器(W2-4 + W2-5 + V7.1 gateway 分流)。按 flow 编排执行节点,处理 REJECT 终止 +
 * Decision emit + <b>exclusiveGateway 分流</b>。
 *
 * <p><b>不接 ruleforge-decision BPMN 引擎</b>(自建图遍历足够覆盖 V1 子集:线性 + 排他网关;
 * 接完整 BPMN 引擎是更后期的事)。本执行器按 sequenceFlow 遍历:startEvent → serviceTask*
 * →(exclusiveGateway)*→ endEvent(Decision)。
 *
 * <p>执行语义(图遍历,非预排序线性链):
 * <ol>
 *   <li>fact = GeneralEntity(schemaName),填入输入</li>
 *   <li>从 startEvent 起沿出边遍历,每个元素:
 *     <ul>
 *       <li>ServiceTask → 执行对应节点(RuleSet/DecisionTable 走 RETE,ScoreCard 走独立执行器)</li>
 *       <li>ExclusiveGateway → 评估出边 {@code conditionExpression}(CEL)<b>首个命中</b>选边;
 *           全不命中走 {@code defaultFlow} 兜底</li>
 *       <li>EndEvent → 停止</li>
 *     </ul>
 *   </li>
 *   <li>每 ServiceTask 后检查 fact[_rejected]==true → 终止后续(W2-4 reject 终止)</li>
 *   <li>到 endEvent(Decision):读 decisionField,∈ outputs 校验,emit(W2-4 decision emit)</li>
 * </ol>
 *
 * <p>防环:visited 集合记录已访问元素 id,重复访问即停(支持网关分支汇合到同一 endEvent,
 * 但 endEvent 在首次到达时 break,不会二次执行)。
 */
public final class V1FlowRunner {

    private V1FlowRunner() {
    }

    /** 执行整个 RuleAsset flow。输入 fact Map(会原地修改并加决策结果)。返回 FlowResult。 */
    public static FlowResult execute(RuleAsset asset, Map<String, Object> inputFact) {
        return execute(asset, inputFact, null);
    }

    /**
     * 执行 flow + 参数库(pl)parameters。规则 CEL {@code param.xxx} 引用参数(ParameterValue
     * 走会话参数通道),{@link V1FlowRunner} 把 parameters 喂 {@code KnowledgeSession.fireRules}。
     */
    public static FlowResult execute(RuleAsset asset, Map<String, Object> inputFact, Map<String, Object> parameters) {
        String schemaName = asset.getSchema() != null ? asset.getSchema().getName() : "Fact";
        // fact 必须是 GeneralEntity(schemaName) —— RETE ObjectTypeNode 按 fact className 匹配
        // category clazz(= schemaName)。REST/Jackson 传入的 LinkedHashMap 不匹配 → RuleSet 不 fire。
        // 包成 GeneralEntity 再灌入字段(BDD 直接传 GeneralEntity,putAll 等价)。
        Map<String, Object> fact = new GeneralEntity(schemaName);
        if (inputFact != null) {
            fact.putAll(inputFact);
        }

        boolean rejected = traverse(asset, fact, parameters);

        // Decision emit:找 endEvent 的 DecisionNode
        String decision = emitDecision(asset, fact);
        return new FlowResult(decision, fact, rejected,
                fact.containsKey(V1ActionRhs.REJECT_REASON) ? (String) fact.get(V1ActionRhs.REJECT_REASON) : null,
                fact.containsKey(V1ActionRhs.DEFAULT_FLAGS_FIELD) ? (List<Object>) fact.get(V1ActionRhs.DEFAULT_FLAGS_FIELD) : new ArrayList<>());
    }

    /**
     * 图遍历执行:startEvent 起,沿出边走。遇 ServiceTask 执行 + reject 检查;遇 ExclusiveGateway
     * 评估出边条件选边;遇 EndEvent 停。返回是否因 reject 终止。
     */
    private static boolean traverse(RuleAsset asset, Map<String, Object> fact, Map<String, Object> parameters) {
        if (asset.getFlow() == null || asset.getFlow().getFlowElements() == null) {
            return false;
        }
        Schema schema = asset.getSchema();
        Set<String> visited = new HashSet<>();
        String curId = firstStartElementId(asset);
        while (curId != null && !visited.contains(curId)) {
            visited.add(curId);
            FlowElement el = findElement(asset, curId);
            if (el == null) {
                break;
            }
            if (el instanceof ServiceTask) {
                String nodeId = parseNodeId(((ServiceTask) el).getImplementation());
                NodeBase node = asset.getNodes() == null ? null : asset.getNodes().get(nodeId);
                if (node != null) {
                    executeNode(node, asset, fact, parameters);
                }
                if (Boolean.TRUE.equals(fact.get(V1ActionRhs.REJECTED_FLAG))) {
                    return true;
                }
            } else if (el instanceof EndEvent) {
                break;
            }
            curId = chooseNext(asset, el, fact, schema);
        }
        return false;
    }

    /**
     * 选下一条出边的 targetRef。
     * <ul>
     *   <li>ExclusiveGateway:遍历出边(按 flowElements 顺序),首个带 conditionExpression 且 CEL 命中
     *       的出边即返回其 target;全不命中 → {@link ExclusiveGateway#getDefaultFlow()} 指向的出边 target;
     *       无 default → null(终止)</li>
     *   <li>其他元素(普通单出边):取第一条出边 target</li>
     * </ul>
     */
    private static String chooseNext(RuleAsset asset, FlowElement el, Map<String, Object> fact, Schema schema) {
        boolean isGateway = el instanceof ExclusiveGateway;
        for (SequenceFlow sf : flowsFrom(asset, el.getId())) {
            if (!isGateway) {
                return sf.getTargetRef();
            }
            String cond = sf.getConditionExpression();
            if (cond != null && !cond.isBlank() && CelEngine.evalBoolean(cond, fact, schema)) {
                return sf.getTargetRef();
            }
        }
        if (isGateway) {
            ExclusiveGateway gw = (ExclusiveGateway) el;
            if (gw.getDefaultFlow() != null) {
                FlowElement defaultEl = findElement(asset, gw.getDefaultFlow());
                if (defaultEl instanceof SequenceFlow) {
                    return ((SequenceFlow) defaultEl).getTargetRef();
                }
            }
        }
        return null;
    }

    /** 列出 sourceRef == sourceId 的所有 sequenceFlow(按 flowElements 顺序)。 */
    private static List<SequenceFlow> flowsFrom(RuleAsset asset, String sourceId) {
        List<SequenceFlow> out = new ArrayList<>();
        if (asset.getFlow() == null || sourceId == null) {
            return out;
        }
        for (FlowElement e : asset.getFlow().getFlowElements()) {
            if (e instanceof SequenceFlow) {
                SequenceFlow sf = (SequenceFlow) e;
                if (sourceId.equals(sf.getSourceRef())) {
                    out.add(sf);
                }
            }
        }
        return out;
    }

    private static String firstStartElementId(RuleAsset asset) {
        for (FlowElement e : asset.getFlow().getFlowElements()) {
            if (e instanceof StartEvent) {
                return e.getId();
            }
        }
        return null;
    }

    private static FlowElement findElement(RuleAsset asset, String id) {
        for (FlowElement e : asset.getFlow().getFlowElements()) {
            if (e.getId() != null && e.getId().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** implementation "NodeType:nodeId" → nodeId。 */
    private static String parseNodeId(String implementation) {
        if (implementation == null) return null;
        int colon = implementation.indexOf(':');
        return colon > 0 ? implementation.substring(colon + 1) : implementation;
    }

    /** 分发执行单个节点。 */
    private static void executeNode(NodeBase node, RuleAsset asset, Map<String, Object> fact, Map<String, Object> parameters) {
        if (node instanceof RuleSetNode) {
            RuleSetNode rs = (RuleSetNode) node;
            List<Rule> rules = RuleSetCompiler.compile(rs, asset.getSchema());
            if (rules.isEmpty()) return;
            KnowledgePackage kp = V1KnowledgeBuilder.build(asset.getSchema(), rules);
            KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
            session.insert(fact);
            session.fireRules(parameters);
        } else if (node instanceof DecisionTableNode) {
            DecisionTableExecutor.execute((DecisionTableNode) node, asset.getSchema(), fact);
        } else if (node instanceof ScoreCardNode) {
            ScoreCardExecutor.execute((ScoreCardNode) node, asset.getSchema(), fact);
        }
        // StartNode/DecisionNode 不在这里执行(Start 只是 schema,Decision 在 emitDecision)
    }

    /** Decision emit:找 endEvent 的 DecisionNode,读 decisionField,校验 ∈ outputs。 */
    private static String emitDecision(RuleAsset asset, Map<String, Object> fact) {
        DecisionNode decision = findDecisionNode(asset);
        if (decision == null) {
            return null;
        }
        String field = decision.getDecisionField() != null ? decision.getDecisionField() : V1ActionRhs.DEFAULT_DECISION_FIELD;
        Object val = fact.get(field);
        String decisionValue = val == null ? decision.getDefaultOutput() : String.valueOf(val);
        // 校验 ∈ outputs(若 outputs 非空)
        if (decision.getOutputs() != null && !decision.getOutputs().isEmpty()
                && decisionValue != null && !decision.getOutputs().contains(decisionValue)) {
            // 不在允许集合 → 用 defaultOutput 兜底
            String fallback = decision.getDefaultOutput();
            if (fallback != null) {
                return fallback;
            }
        }
        return decisionValue;
    }

    private static DecisionNode findDecisionNode(RuleAsset asset) {
        if (asset.getNodes() == null) return null;
        for (NodeBase n : asset.getNodes().values()) {
            if (n instanceof DecisionNode) {
                return (DecisionNode) n;
            }
        }
        return null;
    }

    /** Flow 执行结果。 */
    public static final class FlowResult {
        public final String decision;
        public final Map<String, Object> fact;
        public final boolean rejected;
        public final String rejectReason;
        public final List<Object> flags;

        public FlowResult(String decision, Map<String, Object> fact, boolean rejected,
                          String rejectReason, List<Object> flags) {
            this.decision = decision;
            this.fact = fact;
            this.rejected = rejected;
            this.rejectReason = rejectReason;
            this.flags = flags;
        }
    }
}
