package com.ruleforge.decision.flow.parser;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.ir.BpmnDefinition;
import com.ruleforge.decision.flow.ir.Collaboration;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.Lane;
import com.ruleforge.decision.flow.ir.MessageFlow;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.ir.Participant;
import com.ruleforge.decision.flow.ir.SequenceFlow;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BPMN 2.0 XML 解析器。读 BPMN → BpmnDefinition。
 * <p>
 * 命名空间:
 *   bpmn      = http://www.omg.org/spec/BPMN/20100524/MODEL
 *   ruleforge = http://ruleforge.com/schema
 *   flowable  = http://flowable.org/bpmn  (V5.x 兼容,识别 flowable: 扩展属性但不执行)
 * <p>
 * V5.37 B0 — 升级:
 * <ul>
 *   <li>顶层 IR 是 {@link BpmnDefinition}(record:collaboration + processes map)</li>
 *   <li>支持 {@code <bpmn:collaboration>} 根:多 participant + 多 process + messageFlow</li>
 *   <li>单 process(老 caller)走 {@code BpmnDefinition.ofSingleProcess(parseSingleProcess(...))}</li>
 *   <li>lane 解析到 {@link FlowDefinition#getLanes()};{@link FlowNode#getLaneId()} 写回</li>
 *   <li>messageFlow 端点 → {@code FlowNode.messageFlowId}(规则:START/END 节点带
 *       {@code <ruleforge:messageFlowRef id="..."/>} 扩展元素)</li>
 * </ul>
 */
@Component
public class BpmnXmlParser {

    private static final Logger log = LoggerFactory.getLogger(BpmnXmlParser.class);

    private static final String NS_BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String NS_RULEFORGE = "http://ruleforge.com/schema";
    private static final String NS_FLOWABLE = "http://flowable.org/bpmn";

    /**
     * V5.37 B0 — 顶层解析入口。返 {@link BpmnDefinition}。
     *
     * <p>检测根:
     * <ul>
     *   <li>含 {@code <bpmn:collaboration>} → 走多池路径</li>
     *   <li>无 → 单 process 路径(向后兼容)</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public BpmnDefinition parse(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new FlowExecutionException("BPMN XML is empty");
        }
        Document doc;
        try {
            doc = DocumentHelper.parseText(bpmnXml);
        } catch (Exception e) {
            throw new FlowExecutionException("Invalid BPMN XML: " + e.getMessage(), e);
        }
        Element root = doc.getRootElement();
        Namespace bpmnNs = root.getNamespaceForURI(NS_BPMN);
        if (bpmnNs == null) {
            throw new FlowExecutionException("BPMN root missing namespace " + NS_BPMN);
        }

        // 找 collaboration 根(优先) / process 根(向后兼容)
        Element collaboration = findChild(root, NS_BPMN, "collaboration");
        if (collaboration != null) {
            return parseCollaboration(collaboration, bpmnXml);
        }

        // 单 process 路径
        Element process = findChild(root, NS_BPMN, "process");
        if (process == null) {
            throw new FlowExecutionException("BPMN has no <collaboration> or <process> element");
        }
        FlowDefinition def = parseSingleProcessFromElement(process, bpmnXml);
        return BpmnDefinition.ofSingleProcess(def);
    }

    /**
     * V5.37 B0 — 便利方法,老 caller(单 process)用。
     * 等同 {@code parse(xml).requireProcess(processId)} 的快捷写法。
     */
    public FlowDefinition parseSingleProcess(String bpmnXml) {
        BpmnDefinition bpmn = parse(bpmnXml);
        if (bpmn.collaboration() != null) {
            throw new FlowExecutionException(
                "parseSingleProcess called on a collaboration XML; use parse() to get BpmnDefinition");
        }
        // 单 process,取唯一 process
        if (bpmn.processes().size() != 1) {
            throw new FlowExecutionException(
                "Expected single process, got " + bpmn.processes().size());
        }
        return bpmn.processes().values().iterator().next();
    }

    // -------- V5.37 B0 — collaboration 路径 --------

    @SuppressWarnings("unchecked")
    private BpmnDefinition parseCollaboration(Element collabEl, String bpmnXml) {
        String collabId = collabEl.attributeValue("id");
        String collabName = collabEl.attributeValue("name");

        // 1. 解析 participants
        List<Participant> participants = new ArrayList<>();
        Map<String, Element> processElements = new LinkedHashMap<>();
        for (Element el : (List<Element>) collabEl.elements()) {
            String local = el.getName();
            String elNs = el.getNamespaceURI();
            if (!NS_BPMN.equals(elNs)) continue;
            if ("participant".equals(local)) {
                String pid = el.attributeValue("id");
                String pname = el.attributeValue("name");
                String processRef = el.attributeValue("processRef");
                if (pid == null || pid.isBlank()) {
                    throw new FlowExecutionException(
                        "BPMN <participant> missing id in collaboration " + collabId);
                }
                if (processRef == null || processRef.isBlank()) {
                    throw new FlowExecutionException(
                        "BPMN <participant> " + pid + " missing processRef");
                }
                participants.add(new Participant(pid, pname, processRef));
                // 暂存 process element reference(稍后 parse)
                processElements.put(processRef, null);
            }
        }

        // 2. 解析 messageFlow
        List<MessageFlow> messageFlows = new ArrayList<>();
        for (Element el : (List<Element>) collabEl.elements()) {
            if (!NS_BPMN.equals(el.getNamespaceURI())) continue;
            if (!"messageFlow".equals(el.getName())) continue;
            String mfId = el.attributeValue("id");
            String mfName = el.attributeValue("name");
            String src = el.attributeValue("sourceRef");
            String tgt = el.attributeValue("targetRef");
            if (src == null || src.isBlank() || tgt == null || tgt.isBlank()) {
                throw new FlowExecutionException(
                    "BPMN <messageFlow> " + mfId + " missing sourceRef or targetRef");
            }
            // sourceRef / targetRef 在 BPMN 2.0 里可以指 participant id 或 node id。
            // v0 简化:暂存字符串,运行时按 (participantId, nodeId) tuple 解析。
            // 解析时:如果 src 是某个 participant id,绑定到 participant;否则按 node id 查。
            // 这里 sourceParticipantId / targetParticipantId 暂时存 src 字符串,等下
            // 走完 process 解析后做 endpoint resolution。
            messageFlows.add(new MessageFlow(mfId, mfName, src, src, tgt, tgt));
            // 注:上面构造的 MessageFlow 4-tuple 实际是 (src=src, sourceNode=src, target=tgt, targetNode=tgt) —
            // 因为 v0 简化假设 sourceRef/targetRef 直接是 nodeId(从 bpmn-js 导出常见)或者
            // participant id(从 Camunda Modeler 导出常见)。下面 endpoint resolution 阶段
            // 会做校正。
        }

        // 3. 找所有 referenced <bpmn:process>(在 root 下,不在 collabEl 下)
        // dom4j 实际允许 process 在 collab 兄弟节点(我们的 fixture 就是这样)
        Element parent = collabEl.getParent();
        for (Participant p : participants) {
            Element processEl = findProcessElement(parent, p.getProcessRef());
            if (processEl == null) {
                throw new FlowExecutionException(
                    "BPMN <process> " + p.getProcessRef() + " not found for participant " + p.getId());
            }
            processElements.put(p.getProcessRef(), processEl);
        }

        // 4. 解析每个 process
        Map<String, FlowDefinition> processes = new LinkedHashMap<>();
        for (Participant p : participants) {
            Element processEl = processElements.get(p.getProcessRef());
            FlowDefinition def = parseSingleProcessFromElement(processEl, bpmnXml, collabId, messageFlows, p.getId());
            processes.put(p.getProcessRef(), def);
        }

        // 5. 校正 messageFlow 4-tuple(endpoint resolution)
        // bpmn-js 风格:sourceRef = node id(在 source participant 池里)
        // Camunda Modeler 风格:sourceRef = participant id(在 collab 里,需解析)
        // v0:遍历每个 messageFlow,先按 (participantId, nodeId) tuple 找;找不到
        // 就把 sourceRef/targetRef 整个当 nodeId,归属到当前池
        List<MessageFlow> resolved = new ArrayList<>();
        for (MessageFlow mf : messageFlows) {
            String src = mf.getSourceParticipantId();
            String tgt = mf.getTargetParticipantId();
            // 尝试:sourceRef = participant id?
            Participant srcP = participants.stream()
                .filter(p -> p.getId().equals(src))
                .findFirst().orElse(null);
            Participant tgtP = participants.stream()
                .filter(p -> p.getId().equals(tgt))
                .findFirst().orElse(null);
            if (srcP != null && tgtP != null) {
                // 找到 endpoint node id = <mf.id>_target / <mf.id>_source?
                // 实际:走 extensionElements 的 <ruleforge:messageFlowRef id="MF1"/> 在哪个节点上
                String srcNode = findEndpointNodeId(processes, srcP.getProcessRef(),
                    mf.getId(), NodeType.END_EVENT);
                String tgtNode = findEndpointNodeId(processes, tgtP.getProcessRef(),
                    mf.getId(), NodeType.START_EVENT);
                if (srcNode == null || tgtNode == null) {
                    throw new FlowExecutionException(
                        "BPMN <messageFlow> " + mf.getId() + " endpoints not found in processes "
                        + "(source participant=" + srcP.getId() + " target participant=" + tgtP.getId() + ")");
                }
                resolved.add(new MessageFlow(mf.getId(), mf.getName(),
                    srcP.getId(), srcNode,
                    tgtP.getId(), tgtNode));
            } else {
                // sourceRef 直接是 node id 风格 — 找这个 node 属于哪个 participant
                String srcNodeId = src;
                String tgtNodeId = tgt;
                Participant ownerSrc = findOwnerParticipant(processes, srcNodeId, participants);
                Participant ownerTgt = findOwnerParticipant(processes, tgtNodeId, participants);
                if (ownerSrc == null || ownerTgt == null) {
                    throw new FlowExecutionException(
                        "BPMN <messageFlow> " + mf.getId()
                        + " endpoints not resolvable: sourceRef=" + src + " targetRef=" + tgt);
                }
                resolved.add(new MessageFlow(mf.getId(), mf.getName(),
                    ownerSrc.getId(), srcNodeId,
                    ownerTgt.getId(), tgtNodeId));
            }
        }

        Collaboration collab = new Collaboration(collabId, collabName, participants, resolved);
        return new BpmnDefinition(collab, processes);
    }

    /** 找引用了 {@code <ruleforge:messageFlowRef id="<mfId>"/>} 的某 type 节点 id。 */
    private String findEndpointNodeId(Map<String, FlowDefinition> processes,
                                      String processRef, String mfId, NodeType type) {
        FlowDefinition def = processes.get(processRef);
        if (def == null) return null;
        for (FlowNode n : def.getNodes().values()) {
            if (n.getType() != type) continue;
            String ref = n.attr("ruleforge", "messageFlowRef");
            if (mfId.equals(ref)) return n.getNodeId();
        }
        return null;
    }

    /** 找拥有 {@code nodeId} 的 participant(node id 在 process 内的归属)。 */
    private Participant findOwnerParticipant(Map<String, FlowDefinition> processes,
                                              String nodeId, List<Participant> participants) {
        for (Participant p : participants) {
            FlowDefinition def = processes.get(p.getProcessRef());
            if (def != null && def.getNode(nodeId) != null) return p;
        }
        return null;
    }

    /** 找子元素 — dom4j child(避免 xpath)。 */
    @SuppressWarnings("unchecked")
    private Element findChild(Element parent, String ns, String localName) {
        for (Element el : (List<Element>) parent.elements()) {
            if (ns.equals(el.getNamespaceURI()) && localName.equals(el.getName())) {
                return el;
            }
        }
        return null;
    }

    /** 找 process element(在 root 或任意父节点下)。 */
    @SuppressWarnings("unchecked")
    private Element findProcessElement(Element root, String processId) {
        for (Element el : (List<Element>) root.elements()) {
            if (NS_BPMN.equals(el.getNamespaceURI())
                && "process".equals(el.getName())
                && processId.equals(el.attributeValue("id"))) {
                return el;
            }
        }
        return null;
    }

    // -------- 单 process 解析(parseSingleProcess 复用) --------

    /**
     * 单 process 解析(无 collab 上下文)。返 {@link FlowDefinition},无 messageFlow 解析。
     * {@code parseSingleProcess(String)} 公开便利方法走这里。
     */
    private FlowDefinition parseSingleProcessFromElement(Element process, String bpmnXml) {
        return parseSingleProcessFromElement(process, bpmnXml, null, List.of(), null);
    }

    /**
     * V5.37 B0 — 单 process 解析,带可选 collab 上下文。
     *
     * <p>collab 上下文存在时:
     * <ul>
     *   <li>{@code FlowDefinition.collaborationId} = collabId</li>
     *   <li>检查节点带 {@code <ruleforge:messageFlowRef id="..."/>} → 写回
     *       {@code FlowNode.messageFlowId}</li>
     *   <li>解析 {@code <bpmn:laneSet>}/{@code <bpmn:lane>} → 写回
     *       {@code FlowDefinition.lanes} + {@code FlowNode.laneId}</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private FlowDefinition parseSingleProcessFromElement(Element process, String bpmnXml,
                                                          String collabId,
                                                          List<MessageFlow> messageFlows,
                                                          String participantId) {
        String processId = process.attributeValue("id");
        if (processId == null || processId.isBlank()) {
            throw new FlowExecutionException("BPMN <process> missing id attribute");
        }
        String name = process.attributeValue("name");

        Map<String, FlowNode> nodes = new LinkedHashMap<>();
        List<SequenceFlow> edges = new ArrayList<>();
        Set<String> seenNodeIds = new java.util.HashSet<>();

        // 遍历所有 flowElement
        for (Element el : (List<Element>) process.elements()) {
            String local = el.getName();
            String nodeId = el.attributeValue("id");
            if (nodeId == null || nodeId.isBlank()) continue;

            Map<String, String> ext = extractExtensionAttrs(el);
            List<String> outgoing = new ArrayList<>();
            for (Element out : (List<Element>) el.elements()) {
                if ("outgoing".equals(out.getName())) {
                    outgoing.add(out.getTextTrim());
                }
            }

            NodeType type = mapType(local, ext);
            if (type == null) {
                log.warn("Skipping unknown BPMN element: {} at nodeId={}", local, nodeId);
                continue;
            }
            if (type == NodeType.SUB_PROCESS) {
                log.warn("SubProcess not supported in Phase 1, skipping node={}", nodeId);
                continue;
            }
            if (!seenNodeIds.add(nodeId)) {
                throw new FlowExecutionException("Duplicate node id: " + nodeId + " in process " + processId);
            }

            String scriptText = null;
            String scriptFormat = null;
            if (type == NodeType.SCRIPT_TASK) {
                Element script = el.element("script");
                if (script != null) {
                    scriptText = script.getTextTrim();
                    scriptFormat = script.attributeValue("scriptFormat", "groovy");
                }
            }

            boolean async = Boolean.parseBoolean(ext.get("ruleforge:async"));

            // V5.37 B0 — 节点级 messageFlowId 提取(只 START/END 节点需要)
            // BPMN 2.0 习惯:扩展属性放 <extensionElements><ruleforge:messageFlowRef id="..."/></extensionElements>
            // 这里从子元素里读 id 属性
            String messageFlowRef = null;
            if (collabId != null) {
                for (Element child : (List<Element>) el.elements()) {
                    if (!"extensionElements".equals(child.getName())) continue;
                    for (Element extEl : (List<Element>) child.elements()) {
                        if (!"messageFlowRef".equals(extEl.getName())) continue;
                        if (!NS_RULEFORGE.equals(extEl.getNamespaceURI())) continue;
                        String ref = extEl.attributeValue("id");
                        if (ref != null && !ref.isBlank()) {
                            messageFlowRef = ref;
                            break;
                        }
                    }
                    if (messageFlowRef != null) break;
                }
            }

            FlowNode node = new FlowNode(nodeId, type, el.attributeValue("name"),
                ext, scriptText, scriptFormat, outgoing, async,
                null, messageFlowRef);
            nodes.put(nodeId, node);
        }

        // 解析所有 sequenceFlow
        for (Element el : (List<Element>) process.elements()) {
            if (!"sequenceFlow".equals(el.getName())) continue;
            String id = el.attributeValue("id");
            String src = el.attributeValue("sourceRef");
            String tgt = el.attributeValue("targetRef");
            if (id == null || src == null || tgt == null) {
                log.warn("Skipping sequenceFlow with missing id/sourceRef/targetRef");
                continue;
            }
            Map<String, String> ext = extractExtensionAttrs(el);
            String conditionExpression = null;
            Element condExpr = el.element("conditionExpression");
            if (condExpr != null) {
                conditionExpression = condExpr.getTextTrim();
            }
            Integer percent = null;
            String p = ext.get("ruleforge:percent");
            if (p != null) {
                try { percent = Integer.parseInt(p); } catch (NumberFormatException ignore) {}
            }
            boolean isDefault = conditionExpression == null && percent == null;
            edges.add(new SequenceFlow(id, src, tgt, conditionExpression, percent, isDefault, ext));
        }

        // outgoing 兜底反推
        for (java.util.Map.Entry<String, FlowNode> entry : nodes.entrySet()) {
            FlowNode n = entry.getValue();
            if (!n.getOutgoingIds().isEmpty()) continue;
            List<String> derived = new ArrayList<>();
            for (SequenceFlow e : edges) {
                if (n.getNodeId().equals(e.getSourceId())) {
                    derived.add(e.getId());
                }
            }
            if (!derived.isEmpty()) {
                FlowNode replaced = new FlowNode(n.getNodeId(), n.getType(), n.getName(),
                    n.getExtensionAttrs(), n.getScriptText(), n.getScriptFormat(),
                    derived, n.isAsync(), n.getLaneId(), n.getMessageFlowId());
                nodes.put(entry.getKey(), replaced);
            }
        }

        // 找 start / end
        String startNodeId = null;
        List<String> endNodeIds = new ArrayList<>();
        for (FlowNode n : nodes.values()) {
            if (n.getType() == NodeType.START_EVENT && startNodeId == null) {
                startNodeId = n.getNodeId();
            } else if (n.getType() == NodeType.START_EVENT) {
                throw new FlowExecutionException("Multiple startEvent in process " + processId);
            }
            if (n.getType() == NodeType.END_EVENT) {
                endNodeIds.add(n.getNodeId());
            }
        }
        if (startNodeId == null) {
            throw new FlowExecutionException("No startEvent in process " + processId);
        }
        if (endNodeIds.isEmpty()) {
            throw new FlowExecutionException("No endEvent in process " + processId);
        }

        if (nodes.size() != seenNodeIds.size()) {
            throw new FlowExecutionException("Inconsistent node tracking in process " + processId);
        }

        // attachedCompensations
        Map<String, List<String>> attachedCompensations = new LinkedHashMap<>();
        for (FlowNode n : nodes.values()) {
            if (n.getType() != NodeType.COMPENSATION_INTERMEDIATE) continue;
            String attachedTo = n.attr("ruleforge", "attachedToRef");
            if (attachedTo == null || attachedTo.isBlank()) {
                log.warn("CompensationIntermediate node {} has no ruleforge:attachedToRef — skipping", n.getNodeId());
                continue;
            }
            attachedCompensations
                .computeIfAbsent(attachedTo, k -> new ArrayList<>())
                .add(n.getNodeId());
        }

        // linkTargets
        Map<String, String> linkTargets = new LinkedHashMap<>();
        for (FlowNode n : nodes.values()) {
            if (n.getType() != NodeType.INTERMEDIATE_EVENT) continue;
            String eventType = n.attr("ruleforge", "eventType");
            if (!"linkCatch".equals(eventType)) continue;
            String linkName = n.attr("ruleforge", "linkName");
            if (linkName == null || linkName.isBlank()) {
                log.warn("IntermediateCatch linkCatch node {} has no ruleforge:linkName — skipping", n.getNodeId());
                continue;
            }
            if (linkTargets.containsKey(linkName)) {
                throw new FlowExecutionException(
                    "Duplicate linkCatch name=" + linkName + " (nodes: "
                    + linkTargets.get(linkName) + ", " + n.getNodeId() + ")");
            }
            linkTargets.put(linkName, n.getNodeId());
        }

        // V5.37 B0 — lane 解析
        Map<String, Lane> lanes = new LinkedHashMap<>();
        for (Element el : (List<Element>) process.elements()) {
            if (!"laneSet".equals(el.getName())) continue;
            for (Element laneEl : (List<Element>) el.elements()) {
                if (!"lane".equals(laneEl.getName())) continue;
                String lid = laneEl.attributeValue("id");
                String lname = laneEl.attributeValue("name");
                String parentLane = laneEl.attributeValue("ruleforge", "parentLaneId");
                List<String> nodeRefs = new ArrayList<>();
                for (Element refEl : (List<Element>) laneEl.elements()) {
                    if ("flowNodeRef".equals(refEl.getName())) {
                        nodeRefs.add(refEl.getTextTrim());
                    }
                }
                if (lid == null || lid.isBlank()) {
                    log.warn("Lane missing id in process {}, skipping", processId);
                    continue;
                }
                if (lanes.containsKey(lid)) {
                    throw new FlowExecutionException("Duplicate lane id: " + lid + " in process " + processId);
                }
                lanes.put(lid, new Lane(lid, lname, parentLane, nodeRefs));
            }
        }
        // lane id → node.laneId 写回
        if (!lanes.isEmpty()) {
            Map<String, FlowNode> rebuilt = new LinkedHashMap<>();
            for (Map.Entry<String, FlowNode> e : nodes.entrySet()) {
                FlowNode orig = e.getValue();
                String myLaneId = null;
                for (Lane lane : lanes.values()) {
                    if (lane.getFlowNodeRefs().contains(orig.getNodeId())) {
                        myLaneId = lane.getId();
                        break;
                    }
                }
                if (myLaneId != null) {
                    rebuilt.put(e.getKey(), new FlowNode(orig.getNodeId(), orig.getType(), orig.getName(),
                        orig.getExtensionAttrs(), orig.getScriptText(), orig.getScriptFormat(),
                        orig.getOutgoingIds(), orig.isAsync(), myLaneId, orig.getMessageFlowId()));
                } else {
                    rebuilt.put(e.getKey(), orig);
                }
            }
            nodes = rebuilt;
        }

        String xmlHash = sha256(bpmnXml);

        return new FlowDefinition(processId, name, nodes, edges,
            startNodeId, endNodeIds, bpmnXml, xmlHash, Instant.now(),
            attachedCompensations, linkTargets, collabId, lanes);
    }

    private NodeType mapType(String local, Map<String, String> ext) {
        return switch (local) {
            case "startEvent"        -> NodeType.START_EVENT;
            case "endEvent"          -> NodeType.END_EVENT;
            case "serviceTask"       -> NodeType.SERVICE_TASK;
            case "scriptTask"        -> NodeType.SCRIPT_TASK;
            case "userTask"          -> NodeType.USER_TASK;
            case "exclusiveGateway"  -> NodeType.EXCLUSIVE_GATEWAY;
            case "parallelGateway"   -> NodeType.PARALLEL_GATEWAY;
            case "intermediateCatchEvent" -> NodeType.INTERMEDIATE_EVENT;
            case "subProcess"        -> NodeType.SUB_PROCESS;
            case "compensateStartEvent"      -> NodeType.COMPENSATION_START;
            case "compensateEndEvent"        -> NodeType.COMPENSATION_END;
            case "compensateIntermediateThrowEvent" -> NodeType.COMPENSATION_INTERMEDIATE;
            case "compensateThrowEvent"      -> NodeType.COMPENSATION_THROW;
            default -> null;
        };
    }

    private Map<String, String> extractExtensionAttrs(Element el) {
        Map<String, String> ext = new HashMap<>();
        for (Object attrObj : el.attributes()) {
            org.dom4j.Attribute a = (org.dom4j.Attribute) attrObj;
            String nsUri = a.getNamespaceURI();
            if (NS_RULEFORGE.equals(nsUri) || NS_FLOWABLE.equals(nsUri)) {
                ext.put(a.getQualifiedName(), a.getValue());
            }
        }
        return ext;
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "0";
        }
    }
}
