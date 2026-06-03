package com.ruleforge.console.controller;

import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.console.servlet.diagram.Box;
import com.ruleforge.console.servlet.diagram.Diagram;
import com.ruleforge.console.servlet.diagram.DiagramContext;
import com.ruleforge.console.servlet.diagram.Edge;
import com.ruleforge.console.servlet.diagram.NodeInfo;
import com.ruleforge.console.servlet.diagram.ReteNodeLayout;
import com.ruleforge.console.servlet.respackage.HttpSessionKnowledgeCache;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.Node;
import com.ruleforge.model.rete.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/${ruleforge.root.path}/retediagram")
@RequiredArgsConstructor
public class ReteDiagramController {

    private final KnowledgeBuilder knowledgeBuilder;
    private final HttpSessionKnowledgeCache httpSessionKnowledgeCache;
    private final ReteNodeLayout nodeLayout = new ReteNodeLayout();

    @PostMapping("/loadReteDiagramData")
    public Diagram loadReteDiagramData(HttpServletRequest req, @RequestParam String files) throws RuleException {
        files = Utils.decodeURL(files);
        KnowledgeBase knowledgeBase = (KnowledgeBase) httpSessionKnowledgeCache.get(req, "_kb");
        if (knowledgeBase == null) {
            ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
            String[] paths = files.split(";");
            for (String s : paths) {
                String path = s;
                String[] subpaths = path.split(",");
                path = subpaths[0];
                String version = subpaths.length > 1 ? subpaths[1] : null;
                path = Utils.toUTF8(path);
                resourceBase.addResource(path, version);
            }
            knowledgeBase = knowledgeBuilder.buildKnowledgeBase(resourceBase);
            httpSessionKnowledgeCache.put(req, "_kb", knowledgeBase);
        }
        Rete rete = knowledgeBase.getRete();
        return buildReteDiagram(rete);
    }

    private Diagram buildReteDiagram(Rete rete) {
        Map<Node, NodeInfo> nodeMap = new HashMap<>();
        List<Edge> edges = new ArrayList<>();
        DiagramContext context = new DiagramContext(edges, nodeMap);

        NodeInfo root = new NodeInfo();
        root.setId(context.nextId());
        root.setLabel("Enter");
        root.setColor("#98AFC7");
        root.setWidth(30);
        root.setHeight(30);
        root.setRoundCorner(10);

        List<ObjectTypeNode> typeNodes = rete.getObjectTypeNodes();
        int level = 1;
        for (ObjectTypeNode typeNode : typeNodes) {
            NodeInfo node = new NodeInfo();
            node.setId(context.nextId());
            node.setLabel("T");
            node.setTitle(typeNode.getObjectTypeClass());
            node.setColor("#97CBFF");
            node.setLevel(level);
            node.setWidth(30);
            node.setHeight(30);
            node.setRoundCorner(5);
            root.addChild(node);
            List<Line> lines = typeNode.getLines();
            if (lines == null) continue;
            int nextLevel = level + 1;
            for (Object o : lines) {
                Line line = (Line) o;
                edges.add(new Edge(root.getId(), node.getId()));
                buildLine(line, context, node, nextLevel);
            }
        }

        Box box = nodeLayout.layout(root);
        Diagram diagram = new Diagram(edges, root);
        if (box != null) {
            diagram.setWidth(box.getWidth() + 500);
            diagram.setHeight(box.getHeight() + 300);
        }
        return diagram;
    }

    private void buildLine(Line line, DiagramContext context, NodeInfo parentNode, int level) {
        Node toNode = line.getTo();
        if (toNode == null) return;

        Map<Node, NodeInfo> nodeMap = context.getNodeMap();
        if (nodeMap.containsKey(toNode)) {
            NodeInfo existing = nodeMap.get(toNode);
            context.addEdge(new Edge(parentNode.getId(), existing.getId()));
            return;
        }

        List<Line> lines = null;
        NodeInfo newNodeInfo = new NodeInfo();
        newNodeInfo.setLevel(level);
        newNodeInfo.setId(context.nextId());
        newNodeInfo.setWidth(30);
        newNodeInfo.setHeight(30);

        if (toNode instanceof CriteriaNode) {
            CriteriaNode cnode = (CriteriaNode) toNode;
            newNodeInfo.setColor("#B3D9D9");
            newNodeInfo.setLabel("C");
            newNodeInfo.setTitle(cnode.getCriteriaInfo());
            newNodeInfo.setRoundCorner(30);
            lines = cnode.getLines();
        } else if (toNode instanceof AndNode) {
            AndNode andNode = (AndNode) toNode;
            lines = andNode.getLines();
            newNodeInfo.setColor("#DAB1D5");
            newNodeInfo.setLabel("AND");
            newNodeInfo.setRoundCorner(15);
        } else if (toNode instanceof OrNode) {
            OrNode orNode = (OrNode) toNode;
            lines = orNode.getLines();
            newNodeInfo.setColor("#82D900");
            newNodeInfo.setLabel("OR");
            newNodeInfo.setRoundCorner(15);
        } else if (toNode instanceof TerminalNode) {
            TerminalNode terminalNode = (TerminalNode) toNode;
            newNodeInfo.setColor("orange");
            newNodeInfo.setLabel(terminalNode.getRule().getName());
            newNodeInfo.setTitle(terminalNode.getRule().getName());
            newNodeInfo.setRoundCorner(0);
        }

        nodeMap.put(toNode, newNodeInfo);
        parentNode.addChild(newNodeInfo);
        context.addEdge(new Edge(parentNode.getId(), newNodeInfo.getId()));

        if (lines != null) {
            int nextLevel = level + 1;
            for (Line nextLine : lines) {
                buildLine(nextLine, context, newNodeInfo, nextLevel);
            }
        }
    }
}
