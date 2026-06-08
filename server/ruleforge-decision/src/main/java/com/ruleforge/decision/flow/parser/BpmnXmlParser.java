package com.ruleforge.decision.flow.parser;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
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
 * BPMN 2.0 XML 解析器。读 BPMN → FlowDefinition。
 * <p>
 * 命名空间:
 *   bpmn      = http://www.omg.org/spec/BPMN/20100524/MODEL
 *   ruleforge = http://ruleforge.com/schema
 *   flowable  = http://flowable.org/bpmn
 * <p>
 * 关键提取:
 * - process.id (key)
 * - startEvent / endEvent / serviceTask / scriptTask / userTask / exclusiveGateway / parallelGateway
 * - extensionAttrs: ruleforge:taskType / file / project / version / bean / method / packageId / decisionType / decisionField / rulesList / percent / decisionValue / async
 * - sequenceFlow: conditionExpression / ruleforge:percent / ruleforge:decisionValue
 */
@Component
public class BpmnXmlParser {

    private static final Logger log = LoggerFactory.getLogger(BpmnXmlParser.class);

    private static final String NS_BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String NS_RULEFORGE = "http://ruleforge.com/schema";
    private static final String NS_FLOWABLE = "http://flowable.org/bpmn";

    @SuppressWarnings("unchecked")
    public FlowDefinition parse(String bpmnXml) {
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

        // 找 process 元素(plain 遍历,避免 dom4j XPath 触发 jaxen 依赖)
        Element process = null;
        for (Element e : (List<Element>) root.elements()) {
            if ("process".equals(e.getName()) && NS_BPMN.equals(e.getNamespaceURI())) {
                process = e;
                break;
            }
        }
        if (process == null) {
            throw new FlowExecutionException("BPMN has no <process> element");
        }

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
            String elQName = el.getQualifiedName();
            String nodeId = el.attributeValue("id");
            if (nodeId == null || nodeId.isBlank()) continue;

            Map<String, String> ext = extractExtensionAttrs(el);
            List<String> outgoing = new ArrayList<>();
            // 收集所有 outgoing 引用
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

            FlowNode node = new FlowNode(nodeId, type, el.attributeValue("name"),
                ext, scriptText, scriptFormat, outgoing, async);
            nodes.put(nodeId, node);
        }

        // 解析所有 sequenceFlow 元素
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

        // 找 startNode / endNodes
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

        // 校验 id 唯一(seenNodeIds 只记录真实节点,跟 nodes.size() 应一致)
        if (nodes.size() != seenNodeIds.size()) {
            throw new FlowExecutionException("Inconsistent node tracking in process " + processId);
        }

        String xmlHash = sha256(bpmnXml);

        return new FlowDefinition(processId, name, nodes, edges,
            startNodeId, endNodeIds, bpmnXml, xmlHash, Instant.now());
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
            default -> null;
        };
    }

    /** 提取 BPMN 元素上 ruleforge:* 和 flowable:* 扩展属性。 */
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
