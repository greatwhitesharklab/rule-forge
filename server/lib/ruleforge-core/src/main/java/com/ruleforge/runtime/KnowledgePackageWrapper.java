package com.ruleforge.runtime;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.rete.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Jacky.gao
 * 2015年3月6日
 */
@Getter
@NoArgsConstructor
public class KnowledgePackageWrapper {
    @JsonDeserialize(as = KnowledgePackageImpl.class)
    private KnowledgePackage knowledgePackage;
    @JsonDeserialize(using = ReteNodeJsonDeserializer.class)
    private List<ReteNode> allNodes = new ArrayList<>();
    @Getter
    private String id;
    @Setter
    private String version;

    public KnowledgePackageWrapper(KnowledgePackage knowledgePackage) {
        this.knowledgePackage = knowledgePackage;
        initNodes();
    }

    private void initNodes() {
        Rete rete = knowledgePackage.getRete();
        List<ObjectTypeNode> typeNodes = rete.getObjectTypeNodes();
        List<ReteNode> childrenNodes = new ArrayList<>(typeNodes);
        queryReteNodes(childrenNodes);
    }

    private void queryReteNodes(List<ReteNode> reteNodes) {
        if (reteNodes == null) {
            return;
        }
        for (ReteNode reteNode : reteNodes) {
            if (!allNodes.contains(reteNode) && !(reteNode instanceof ObjectTypeNode)) {
                allNodes.add(reteNode);
            }
            if (reteNode instanceof BaseReteNode) {
                BaseReteNode abstractReteNode = (BaseReteNode) reteNode;
                queryReteNodes(abstractReteNode.getChildrenNodes());
            }
        }
    }

    public void buildDeserialize() {
        Rete rete = knowledgePackage.getRete();
        List<ObjectTypeNode> typeNodes = rete.getObjectTypeNodes();
        for (ObjectTypeNode typeNode : typeNodes) {
            List<Line> lines = typeNode.getLines();
            for (Line line : lines) {
                line.setFrom(typeNode);
            }
            rebuildLine(lines, allNodes);
        }
        ((KnowledgePackageImpl) knowledgePackage).buildWithElseRules();
    }

    private void rebuildLine(List<Line> lines, List<ReteNode> reteNodes) {
        if (lines == null) {
            return;
        }
        for (Line line : lines) {
            if (line.getFrom() == null) {
                int fromId = line.getFromNodeId();
                ReteNode fromNode = findTargetNode(reteNodes, fromId);
                line.setFrom(fromNode);
                if (fromNode instanceof BaseReteNode) {
                    BaseReteNode node = (BaseReteNode) fromNode;
                    rebuildLine(node.getLines(), reteNodes);
                }
            }
            if (line.getTo() == null) {
                int toId = line.getToNodeId();
                ReteNode toNode = findTargetNode(reteNodes, toId);
                line.setTo(toNode);
                if (toNode instanceof BaseReteNode) {
                    BaseReteNode node = (BaseReteNode) toNode;
                    rebuildLine(node.getLines(), reteNodes);
                }
            }
        }
    }

    private ReteNode findTargetNode(List<ReteNode> reteNodes, int id) {
        for (ReteNode node : reteNodes) {
            if (node.getId() == id) {
                return node;
            }
        }
        throw new RuleException("Node[" + id + "] not exist.");
    }

}
