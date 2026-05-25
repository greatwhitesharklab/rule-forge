package com.ruleforge.console.flow.converter;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class FlowXmlConverter {

    private static final String RF_NS = "http://ruleforge.com/schema";
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI";
    private static final String DC_NS = "http://www.omg.org/spec/DD/20100524/DC";
    private static final String DI_NS = "http://www.omg.org/spec/DD/20100524/DI";
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    public String convertToBpmn(String oldXml) throws Exception {
        Document oldDoc = DocumentHelper.parseText(oldXml);
        Element root = oldDoc.getRootElement();
        if (!"rule-flow".equals(root.getName())) {
            throw new IllegalArgumentException("Not a valid rule-flow XML document");
        }

        String flowId = root.attributeValue("id", "flow-" + UUID.randomUUID().toString().substring(0, 8));

        Document bpmnDoc = DocumentHelper.createDocument();
        Element definitions = bpmnDoc.addElement("bpmn:definitions", BPMN_NS);
        definitions.addNamespace("bpmn", BPMN_NS);
        definitions.addNamespace("bpmndi", BPMNDI_NS);
        definitions.addNamespace("dc", DC_NS);
        definitions.addNamespace("di", DI_NS);
        definitions.addNamespace("flowable", FLOWABLE_NS);
        definitions.addNamespace("ruleforge", RF_NS);
        definitions.addAttribute("id", "Definitions_1");
        definitions.addAttribute("targetNamespace", RF_NS);

        Element process = definitions.addElement("bpmn:process", BPMN_NS);
        process.addAttribute("id", flowId);
        process.addAttribute("isExecutable", "true");

        Element diagram = definitions.addElement("bpmndi:BPMNDiagram", BPMNDI_NS);
        diagram.addAttribute("id", "BPMNDiagram_1");
        Element plane = diagram.addElement("bpmndi:BPMNPlane", BPMNDI_NS);
        plane.addAttribute("id", "BPMNPlane_1");
        plane.addAttribute("bpmnElement", flowId);

        Map<String, NodeInfo> nodes = new HashMap<>();
        Map<String, List<ConnectionInfo>> connections = new HashMap<>();

        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            String name = ele.getName();
            String nodeName = ele.attributeValue("name");

            if (nodeName == null) continue;

            switch (name) {
                case "start":
                    nodes.put(nodeName, new NodeInfo(nodeName, "start", ele));
                    break;
                case "end":
                    nodes.put(nodeName, new NodeInfo(nodeName, "end", ele));
                    break;
                case "rule":
                    nodes.put(nodeName, new NodeInfo(nodeName, "rule", ele));
                    break;
                case "action":
                    nodes.put(nodeName, new NodeInfo(nodeName, "action", ele));
                    break;
                case "script":
                    nodes.put(nodeName, new NodeInfo(nodeName, "script", ele));
                    break;
                case "decision":
                    nodes.put(nodeName, new NodeInfo(nodeName, "decision", ele));
                    break;
                case "fork":
                    nodes.put(nodeName, new NodeInfo(nodeName, "fork", ele));
                    break;
                case "join":
                    nodes.put(nodeName, new NodeInfo(nodeName, "join", ele));
                    break;
                case "rule-package":
                    nodes.put(nodeName, new NodeInfo(nodeName, "package", ele));
                    break;
                default:
                    break;
            }
        }

        for (NodeInfo node : nodes.values()) {
            createElement(process, plane, node);
        }

        return bpmnDoc.asXML();
    }

    private void createElement(Element process, Element plane, NodeInfo node) {
        String bpmnId = sanitizeId(node.name);
        Element el;
        String shapeKind;

        switch (node.type) {
            case "start":
                el = process.addElement("bpmn:startEvent");
                shapeKind = "StartEvent";
                break;
            case "end":
                el = process.addElement("bpmn:endEvent");
                shapeKind = "EndEvent";
                break;
            case "decision":
                el = process.addElement("bpmn:exclusiveGateway");
                shapeKind = "ExclusiveGateway";
                String decisionType = node.element.attributeValue("decision-type", "Criteria");
                addExtensionAttr(el, "decisionType", "Criteria".equals(decisionType) ? "condition" : "percent");
                break;
            case "fork":
                el = process.addElement("bpmn:parallelGateway");
                shapeKind = "ParallelGateway";
                break;
            case "join":
                el = process.addElement("bpmn:parallelGateway");
                shapeKind = "ParallelGateway";
                break;
            case "rule":
                el = process.addElement("bpmn:serviceTask");
                shapeKind = "ServiceTask";
                addExtensionAttr(el, "taskType", "rule");
                addExtensionAttr(el, "file", node.element.attributeValue("file"));
                addExtensionAttr(el, "version", node.element.attributeValue("version"));
                addExtensionAttr(el, "project", node.element.attributeValue("packageName"));
                break;
            case "action":
                el = process.addElement("bpmn:serviceTask");
                shapeKind = "ServiceTask";
                addExtensionAttr(el, "taskType", "action");
                addExtensionAttr(el, "bean", node.element.attributeValue("action-bean"));
                break;
            case "script":
                el = process.addElement("bpmn:scriptTask");
                shapeKind = "ScriptTask";
                String scriptContent = node.element.getTextTrim();
                if (scriptContent != null && !scriptContent.isEmpty()) {
                    el.addElement("bpmn:script").setText(scriptContent);
                }
                break;
            case "package":
                el = process.addElement("bpmn:serviceTask");
                shapeKind = "ServiceTask";
                addExtensionAttr(el, "taskType", "package");
                addExtensionAttr(el, "packageId", node.element.attributeValue("package-id"));
                addExtensionAttr(el, "project", node.element.attributeValue("project"));
                break;
            default:
                return;
        }

        el.addAttribute("id", bpmnId);
        el.addAttribute("name", node.name);

        String eventBean = node.element.attributeValue("event-bean");
        if (eventBean != null && !eventBean.isEmpty()) {
            addExtensionAttr(el, "eventBean", eventBean);
        }

        // Create outgoing sequence flows from connections
        for (Object obj : node.element.elements("connection")) {
            Element conn = (Element) obj;
            String toName = conn.attributeValue("to");
            if (toName == null) continue;
            String flowId = "flow_" + sanitizeId(node.name) + "_to_" + sanitizeId(toName);
            el.addElement("bpmn:outgoing").setText(flowId);

            // Add incoming reference to target (we'll find it in the process)
            String targetId = sanitizeId(toName);
            // We need to find the target element and add incoming - will do in a second pass

            Element seqFlow = process.elementByID(flowId);
            if (seqFlow == null) {
                seqFlow = process.addElement("bpmn:sequenceFlow");
                seqFlow.addAttribute("id", flowId);
                seqFlow.addAttribute("sourceRef", bpmnId);
                seqFlow.addAttribute("targetRef", targetId);

                // Percent for percent-based decision
                String percent = conn.attributeValue("percent");
                if (percent != null && !percent.isEmpty()) {
                    addExtensionAttr(seqFlow, "percent", percent);
                }

                // Condition script
                String script = conn.getTextTrim();
                if (script != null && !script.isEmpty()) {
                    addExtensionAttr(seqFlow, "conditionScript", script);
                }

                // Create diagram edge
                Element edge = plane.addElement("bpmndi:BPMNEdge");
                edge.addAttribute("id", flowId + "_di");
                edge.addAttribute("bpmnElement", flowId);
            }
        }

        // Create diagram shape
        String x = node.element.attributeValue("x", "100");
        String y = node.element.attributeValue("y", "100");
        String w = node.element.attributeValue("width", "80");
        String h = node.element.attributeValue("height", "60");
        Element shape = plane.addElement("bpmndi:BPMNShape");
        shape.addAttribute("id", bpmnId + "_di");
        shape.addAttribute("bpmnElement", bpmnId);
        Element bounds = shape.addElement("dc:Bounds");
        bounds.addAttribute("x", x);
        bounds.addAttribute("y", y);
        bounds.addAttribute("width", w);
        bounds.addAttribute("height", h);
    }

    private void addExtensionAttr(Element el, String name, String value) {
        if (value == null || value.isEmpty()) return;
        el.addAttribute("ruleforge:" + name, value);
    }

    private String sanitizeId(String name) {
        if (name == null) return "id_" + UUID.randomUUID().toString().substring(0, 8);
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static class NodeInfo {
        String name;
        String type;
        Element element;

        NodeInfo(String name, String type, Element element) {
            this.name = name;
            this.type = type;
            this.element = element;
        }
    }

    private static class ConnectionInfo {
        String toName;
        String script;
        String percent;
    }
}
