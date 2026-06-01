package com.ruleforge.model.rete;

import com.ruleforge.model.AbstractJsonDeserializer;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ReteNodeJsonDeserializer extends AbstractJsonDeserializer<List<ReteNode>> {
    public ReteNodeJsonDeserializer() {
    }

    public List<ReteNode> deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectCodec oc = jsonParser.getCodec();
        JsonNode jsonNode = oc.readTree(jsonParser);
        List<ReteNode> reteNodes = new ArrayList<>();
        Iterator<JsonNode> childrenNodesIter = jsonNode.elements();

        while (childrenNodesIter.hasNext()) {
            JsonNode childNode = childrenNodesIter.next();
            int id = childNode.get("id").asInt();
            JsonNode nodeTypeNode = childNode.get("nodeType");
            if (nodeTypeNode != null) {
                String nodeTypeText = nodeTypeNode.asText();
                NodeType nodeType = NodeType.valueOf(nodeTypeText);
                ReteNode reteNode = NodeType.newReteNodeInstance(nodeType);
                if (reteNode instanceof ObjectTypeNode) {
                    ObjectTypeNode node = (ObjectTypeNode) reteNode;
                    node.setObjectTypeClass(childNode.get("objectTypeClass").asText());
                    node.setId(id);
                } else if (reteNode instanceof AndNode) {
                    AndNode node = (AndNode) reteNode;
                    node.setId(id);
                    node.setToLineCount(childNode.get("toLineCount").asInt());
                    node.setLines(this.parseLines(childNode));
                } else if (reteNode instanceof OrNode) {
                    OrNode node = (OrNode) reteNode;
                    node.setId(id);
                    node.setLines(this.parseLines(childNode));
                } else if (reteNode instanceof CriteriaNode) {
                    CriteriaNode node = (CriteriaNode) reteNode;
                    node.setId(id);
                    JsonNode debugNode = childNode.get("debug");
                    if (debugNode != null) {
                        node.setDebug(debugNode.asBoolean());
                    }

                    JsonNode criteriaNode = childNode.get("criteria");
                    node.setCriteria(this.parseCriteria(criteriaNode));
                    node.setLines(this.parseLines(childNode));
                } else if (reteNode instanceof TerminalNode) {
                    TerminalNode node = (TerminalNode) reteNode;
                    node.setId(id);
                    node.setRule(this.parseRule(jsonParser, childNode));
                }

                reteNodes.add(reteNode);
            }
        }

        return reteNodes;
    }

    private List<Line> parseLines(JsonNode node) {
        JsonNode lineNodes = node.get("lines");
        if (lineNodes == null) {
            return null;
        } else {
            List<Line> lines = new ArrayList<>();

            for (JsonNode jsonNode : lineNodes) {
                Line line = new Line();
                line.setFromNodeId(jsonNode.get("fromNodeId").asInt());
                line.setToNodeId(jsonNode.get("toNodeId").asInt());
                lines.add(line);
            }

            return lines;
        }
    }

    private Criteria parseCriteria(JsonNode jsonNode) {
        Criteria criteria = new Criteria();
        String opText = jsonNode.get("op").asText();
        Op op = Op.valueOf(opText);
        criteria.setOp(op);
        JsonNode leftJsonNode = jsonNode.get("left");
        Left left = new Left();
        criteria.setLeft(left);
        String type = JsonUtils.getJsonValue(leftJsonNode, "type");
        JsonNode leftPartJsonNode = leftJsonNode.get("leftPart");
        left.setType(LeftType.valueOf(type));
        switch (left.getType()) {
            case function:
                FunctionLeftPart funPart = new FunctionLeftPart();
                funPart.setName(JsonUtils.getJsonValue(leftPartJsonNode, "name"));
                funPart.setParameters(JsonUtils.parseParameters(leftPartJsonNode));
                left.setLeftPart(funPart);
                break;
            case method:
                MethodLeftPart methodPart = new MethodLeftPart();
                methodPart.setBeanId(JsonUtils.getJsonValue(leftPartJsonNode, "beanId"));
                methodPart.setBeanLabel(JsonUtils.getJsonValue(leftPartJsonNode, "beanLabel"));
                methodPart.setMethodLabel(JsonUtils.getJsonValue(leftPartJsonNode, "methodLabel"));
                methodPart.setMethodName(JsonUtils.getJsonValue(leftPartJsonNode, "methodName"));
                methodPart.setParameters(JsonUtils.parseParameters(leftPartJsonNode));
                left.setLeftPart(methodPart);
                break;
            case eval:
                EvalLeftPart evalPart = new EvalLeftPart();
                evalPart.setExpression(JsonUtils.getJsonValue(leftPartJsonNode, "expression"));
                left.setLeftPart(evalPart);
                break;
            case all:
                AllLeftPart allLeftPart = new AllLeftPart();
                String statisticTypeStr = JsonUtils.getJsonValue(leftPartJsonNode, "statisticType");
                StatisticType statisticType = StatisticType.valueOf(statisticTypeStr);
                allLeftPart.setStatisticType(statisticType);
                if (statisticType.equals(StatisticType.percent)) {
                    allLeftPart.setPercent(Integer.valueOf(JsonUtils.getJsonValue(leftPartJsonNode, "percent")));
                } else if (statisticType.equals(StatisticType.amount)) {
                    allLeftPart.setAmount(Integer.valueOf(JsonUtils.getJsonValue(leftPartJsonNode, "amount")));
                }

                allLeftPart.setVariableCategory(JsonUtils.getJsonValue(leftPartJsonNode, "variableCategory"));
                allLeftPart.setVariableLabel(JsonUtils.getJsonValue(leftPartJsonNode, "variableLabel"));
                allLeftPart.setVariableName(JsonUtils.getJsonValue(leftPartJsonNode, "variableName"));
                JsonNode multiConditionNode = leftPartJsonNode.get("multiCondition");
                allLeftPart.setMultiCondition(this.parseMultiCondition(multiConditionNode));
                left.setLeftPart(allLeftPart);
                break;
            case exist:
                ExistLeftPart existLeftPart = new ExistLeftPart();
                String existStatisticTypeStr = JsonUtils.getJsonValue(leftPartJsonNode, "statisticType");
                StatisticType existStatisticType = StatisticType.valueOf(existStatisticTypeStr);
                existLeftPart.setStatisticType(existStatisticType);
                if (existStatisticType.equals(StatisticType.percent)) {
                    existLeftPart.setPercent(Integer.valueOf(JsonUtils.getJsonValue(leftPartJsonNode, "percent")));
                } else if (existStatisticType.equals(StatisticType.amount)) {
                    existLeftPart.setAmount(Integer.valueOf(JsonUtils.getJsonValue(leftPartJsonNode, "amount")));
                }

                existLeftPart.setVariableCategory(JsonUtils.getJsonValue(leftPartJsonNode, "variableCategory"));
                existLeftPart.setVariableLabel(JsonUtils.getJsonValue(leftPartJsonNode, "variableLabel"));
                existLeftPart.setVariableName(JsonUtils.getJsonValue(leftPartJsonNode, "variableName"));
                JsonNode existMultiConditionNode = leftPartJsonNode.get("multiCondition");
                existLeftPart.setMultiCondition(this.parseMultiCondition(existMultiConditionNode));
                left.setLeftPart(existLeftPart);
                break;
            case collect:
                CollectLeftPart collectLeftPart = new CollectLeftPart();
                collectLeftPart.setVariableCategory(JsonUtils.getJsonValue(leftPartJsonNode, "variableCategory"));
                collectLeftPart.setVariableLabel(JsonUtils.getJsonValue(leftPartJsonNode, "variableLabel"));
                collectLeftPart.setVariableName(JsonUtils.getJsonValue(leftPartJsonNode, "variableName"));
                collectLeftPart.setProperty(JsonUtils.getJsonValue(leftPartJsonNode, "property"));
                collectLeftPart.setPurpose(CollectPurpose.valueOf(JsonUtils.getJsonValue(leftPartJsonNode, "purpose")));
                JsonNode collectMultiConditionNode = leftPartJsonNode.get("multiCondition");
                collectLeftPart.setMultiCondition(this.parseMultiCondition(collectMultiConditionNode));
                left.setLeftPart(collectLeftPart);
                break;
            case commonfunction:
                CommonFunctionLeftPart functionPart = new CommonFunctionLeftPart();
                functionPart.setLabel(JsonUtils.getJsonValue(leftPartJsonNode, "label"));
                functionPart.setName(JsonUtils.getJsonValue(leftPartJsonNode, "name"));
                functionPart.setParameter(JsonUtils.parseCommonFunctionParameter(leftPartJsonNode));
                left.setLeftPart(functionPart);
                break;
            default:
                VariableLeftPart varPart = new VariableLeftPart();
                varPart.setVariableCategory(JsonUtils.getJsonValue(leftPartJsonNode, "variableCategory"));
                varPart.setVariableLabel(JsonUtils.getJsonValue(leftPartJsonNode, "variableLabel"));
                varPart.setVariableName(JsonUtils.getJsonValue(leftPartJsonNode, "variableName"));
                varPart.setDatatype(Datatype.valueOf(JsonUtils.getJsonValue(leftPartJsonNode, "datatype")));
                left.setLeftPart(varPart);
        }

        left.setArithmetic(JsonUtils.parseComplexArithmetic(leftJsonNode));
        Value value = JsonUtils.parseValue(jsonNode);
        if (value != null) {
            criteria.setValue(value);
        }

        return criteria;
    }

    private MultiCondition parseMultiCondition(JsonNode multiConditionNode) {
        MultiCondition condition = new MultiCondition();
        condition.setType(JunctionType.valueOf(JsonUtils.getJsonValue(multiConditionNode, "type")));
        Iterator<JsonNode> iter = multiConditionNode.get("conditions").elements();

        while (iter.hasNext()) {
            JsonNode propertyCriteriaNode = iter.next();
            PropertyCriteria pc = new PropertyCriteria();
            pc.setOp(Op.valueOf(JsonUtils.getJsonValue(propertyCriteriaNode, "op")));
            pc.setProperty(JsonUtils.getJsonValue(propertyCriteriaNode, "property"));
            pc.setValue(JsonUtils.parseValue(propertyCriteriaNode));
            condition.addCondition(pc);
        }

        return condition;
    }
}
