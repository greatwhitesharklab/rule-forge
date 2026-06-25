package com.ruleforge.v1.exec;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.v1.ast.DecisionNode;
import com.ruleforge.v1.ast.DecisionTableNode;
import com.ruleforge.v1.ast.FlowElement;
import com.ruleforge.v1.ast.NodeBase;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.RuleSetNode;
import com.ruleforge.v1.ast.ScoreCardNode;
import com.ruleforge.v1.ast.SequenceFlow;
import com.ruleforge.v1.ast.ServiceTask;
import com.ruleforge.v1.ast.StartEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V1 Flow 执行器(W2-4 + W2-5)。按 flow 编排执行节点,处理 REJECT 终止 + Decision emit。
 *
 * <p><b>不接 ruleforge-decision BPMN 引擎</b>(MVP 线性流程足够,接 BPMN 是 V1 完整版的事)。
 * 本执行器自己按 sequenceFlow 链式遍历:startEvent → serviceTask* → endEvent(Decision)。
 *
 * <p>执行语义:
 * <ol>
 *   <li>fact = GeneralEntity(schemaName),填入输入</li>
 *   <li>按顺序执行节点(MVP 线性,无 gateway):
 *     <ul>
 *       <li>RuleSetNode → RuleSetCompiler + RETE fire(actions 改 fact)</li>
 *       <li>DecisionTableNode → DecisionTableExecutor(线性 CEL)</li>
 *       <li>ScoreCardNode → ScoreCardExecutor(线性 CEL bands)</li>
 *     </ul>
 *   </li>
 *   <li>每节点后检查 fact[_rejected]==true → 终止后续节点(W2-4 reject 终止)</li>
 *   <li>到 endEvent(Decision):读 decisionField,∈ outputs 校验,emit(W2-4 decision emit)</li>
 * </ol>
 */
public final class V1FlowRunner {

    private V1FlowRunner() {
    }

    /** 执行整个 RuleAsset flow。输入 fact Map(会原地修改并加决策结果)。返回 FlowResult。 */
    public static FlowResult execute(RuleAsset asset, Map<String, Object> inputFact) {
        String schemaName = asset.getSchema() != null ? asset.getSchema().getName() : "Fact";
        Map<String, Object> fact = inputFact != null ? inputFact : new GeneralEntity(schemaName);

        // 按顺序遍历节点
        List<String> nodeOrder = orderNodes(asset);
        Map<String, NodeBase> nodes = asset.getNodes();
        boolean rejected = false;
        for (String nodeId : nodeOrder) {
            NodeBase node = nodes.get(nodeId);
            if (node == null) continue;
            executeNode(node, asset, fact);
            if (Boolean.TRUE.equals(fact.get(V1ActionRhs.REJECTED_FLAG))) {
                rejected = true;
                break;
            }
        }

        // Decision emit:找 endEvent 的 DecisionNode
        String decision = emitDecision(asset, fact);
        return new FlowResult(decision, fact, rejected,
                fact.containsKey(V1ActionRhs.REJECT_REASON) ? (String) fact.get(V1ActionRhs.REJECT_REASON) : null,
                fact.containsKey(V1ActionRhs.DEFAULT_FLAGS_FIELD) ? (List<Object>) fact.get(V1ActionRhs.DEFAULT_FLAGS_FIELD) : new ArrayList<>());
    }

    /** 按 sequenceFlow 链推节点执行顺序(startEvent 起始)。MVP 线性,取首条链。 */
    private static List<String> orderNodes(RuleAsset asset) {
        List<String> order = new ArrayList<>();
        if (asset.getFlow() == null || asset.getFlow().getFlowElements() == null) {
            return order;
        }
        // 索引 sequenceFlow by sourceRef
        Map<String, String> flowNext = new LinkedHashMap<>();
        String startId = null;
        for (FlowElement e : asset.getFlow().getFlowElements()) {
            if (e instanceof StartEvent) {
                startId = parseNodeId(((StartEvent) e).getImplementation());
            } else if (e instanceof SequenceFlow) {
                SequenceFlow sf = (SequenceFlow) e;
                flowNext.put(sf.getSourceRef(), sf.getTargetRef());
            }
        }
        // 从 startEvent 元素开始遍历 flow element id 链,解析每步的 nodeId
        // startEvent id → first serviceTask ...
        if (startId == null && asset.getFlow().getFlowElements().stream().noneMatch(e -> e instanceof StartEvent)) {
            return order;
        }
        // 遍历 flow element 链(按 element id)
        String curElementId = firstStartElementId(asset);
        while (curElementId != null) {
            FlowElement el = findElement(asset, curElementId);
            if (el instanceof ServiceTask) {
                order.add(parseNodeId(((ServiceTask) el).getImplementation()));
            }
            curElementId = flowNext.get(curElementId);
        }
        return order;
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
    private static void executeNode(NodeBase node, RuleAsset asset, Map<String, Object> fact) {
        if (node instanceof RuleSetNode) {
            RuleSetNode rs = (RuleSetNode) node;
            List<Rule> rules = RuleSetCompiler.compile(rs, asset.getSchema());
            if (rules.isEmpty()) return;
            KnowledgePackage kp = V1KnowledgeBuilder.build(asset.getSchema(), rules);
            KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
            session.insert(fact);
            session.fireRules();
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
