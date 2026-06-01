package com.ruleforge.console.flow.delegate;

import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@Slf4j
public class DecisionGatewayDelegate implements JavaDelegate {

    private static final String RF_NS = "http://ruleforge.com/schema";
    private static final String DECISION_TYPE_PERCENT = "percent";
    private static final String DECISION_TYPE_CONDITION = "condition";

    private final Random random = new Random();

    @Override
    public void execute(DelegateExecution execution) {
        String selectedTarget = decide(execution);
        execution.setVariable("_decisionResult_" + execution.getCurrentActivityId(), selectedTarget);
    }

    public String decide(DelegateExecution execution) {
        FlowNode flowNode = (FlowNode) execution.getCurrentFlowElement();
        List<SequenceFlow> outgoingFlows = flowNode.getOutgoingFlows();

        if (outgoingFlows == null || outgoingFlows.isEmpty()) {
            throw new RuntimeException("No outgoing flows for gateway: " + execution.getCurrentActivityId());
        }

        String decisionType = getDecisionType(flowNode);

        if (DECISION_TYPE_PERCENT.equals(decisionType)) {
            return decideByPercent(execution, outgoingFlows);
        } else {
            // Default to condition mode
            return decideByCondition(execution, outgoingFlows);
        }
    }

    private String getDecisionType(FlowNode flowNode) {
        String type = flowNode.getAttributeValue(RF_NS, "decisionType");
        if (type != null) {
            return type.toLowerCase();
        }
        return DECISION_TYPE_CONDITION;
    }

    private String decideByPercent(DelegateExecution execution, List<SequenceFlow> outgoingFlows) {
        // Collect percentages from outgoing flows
        int totalPercent = 0;
        int[] percents = new int[outgoingFlows.size()];
        boolean hasAnyPercent = false;

        for (int i = 0; i < outgoingFlows.size(); i++) {
            String percentStr = getExtensionAttr(outgoingFlows.get(i), "percent");
            if (percentStr != null && !percentStr.isEmpty()) {
                percents[i] = Integer.parseInt(percentStr);
                hasAnyPercent = true;
            } else {
                percents[i] = 0;
            }
            totalPercent += percents[i];
        }

        if (!hasAnyPercent) {
            // No percent attributes, return first flow as default
            log.warn("No percent attributes on outgoing flows, using first flow as default");
            return outgoingFlows.get(0).getTargetRef();
        }

        // Random selection based on weighted percentages
        int roll = random.nextInt(totalPercent);
        int cumulative = 0;
        for (int i = 0; i < percents.length; i++) {
            cumulative += percents[i];
            if (roll < cumulative) {
                return outgoingFlows.get(i).getTargetRef();
            }
        }

        // Fallback to last flow
        return outgoingFlows.get(outgoingFlows.size() - 1).getTargetRef();
    }

    private String decideByCondition(DelegateExecution execution, List<SequenceFlow> outgoingFlows) {
        Map<String, Object> variables = execution.getVariables();
        SequenceFlow defaultFlow = null;

        for (SequenceFlow flow : outgoingFlows) {
            String condition = flow.getConditionExpression();
            if (condition == null || condition.isEmpty()) {
                // This is the default flow
                defaultFlow = flow;
                continue;
            }

            if (evaluateCondition(condition, variables)) {
                return flow.getTargetRef();
            }
        }

        if (defaultFlow != null) {
            return defaultFlow.getTargetRef();
        }

        throw new RuntimeException("No matching condition and no default flow for gateway: "
            + execution.getCurrentActivityId());
    }

    private boolean evaluateCondition(String condition, Map<String, Object> variables) {
        try {
            // Strip ${ } wrapper if present
            String expr = condition.trim();
            if (expr.startsWith("${") && expr.endsWith("}")) {
                expr = expr.substring(2, expr.length() - 1).trim();
            }

            // Simple UEL-style condition evaluation
            return evaluateUelExpression(expr, variables);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}", condition, e);
            return false;
        }
    }

    private boolean evaluateUelExpression(String expr, Map<String, Object> variables) {
        // Parse simple comparisons: variable op value
        // Supports: >, <, >=, <=, ==, !=
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(\\w+(?:\\.\\w+)*)\\s*(>=|<=|!=|>|<|==)\\s*(.+)");
        java.util.regex.Matcher matcher = pattern.matcher(expr);

        if (!matcher.matches()) {
            log.warn("Cannot parse condition expression: {}", expr);
            return false;
        }

        String leftExpr = matcher.group(1);
        String operator = matcher.group(2);
        String rightExpr = matcher.group(3).trim();

        Object leftValue = resolveVariable(leftExpr, variables);
        Object rightValue = resolveValue(rightExpr, variables);

        return compareValues(leftValue, rightValue, operator);
    }

    @SuppressWarnings("unchecked")
    private Object resolveVariable(String expr, Map<String, Object> variables) {
        String[] parts = expr.split("\\.");
        Object current = variables;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private Object resolveValue(String expr, Map<String, Object> variables) {
        // Try as number
        try {
            if (expr.contains(".")) {
                return Double.parseDouble(expr);
            } else {
                return Integer.parseInt(expr);
            }
        } catch (NumberFormatException e) {
            // Not a number
        }

        // Try as string literal (quoted)
        if ((expr.startsWith("\"") && expr.endsWith("\""))
            || (expr.startsWith("'") && expr.endsWith("'"))) {
            return expr.substring(1, expr.length() - 1);
        }

        // Try as boolean
        if ("true".equalsIgnoreCase(expr)) return true;
        if ("false".equalsIgnoreCase(expr)) return false;

        // Try as variable reference
        Object val = resolveVariable(expr, variables);
        if (val != null) return val;

        // Return as string
        return expr;
    }

    @SuppressWarnings("unchecked")
    private boolean compareValues(Object left, Object right, String operator) {
        if (left == null || right == null) return false;

        // Try numeric comparison
        if (left instanceof Number && right instanceof Number) {
            double l = ((Number) left).doubleValue();
            double r = ((Number) right).doubleValue();
            return switch (operator) {
                case ">" -> l > r;
                case "<" -> l < r;
                case ">=" -> l >= r;
                case "<=" -> l <= r;
                case "==" -> l == r;
                case "!=" -> l != r;
                default -> false;
            };
        }

        // String comparison
        String lStr = left.toString();
        String rStr = right.toString();
        return switch (operator) {
            case "==" -> lStr.equals(rStr);
            case "!=" -> !lStr.equals(rStr);
            case ">" -> lStr.compareTo(rStr) > 0;
            case "<" -> lStr.compareTo(rStr) < 0;
            case ">=" -> lStr.compareTo(rStr) >= 0;
            case "<=" -> lStr.compareTo(rStr) <= 0;
            default -> false;
        };
    }

    private String getExtensionAttr(SequenceFlow flow, String name) {
        return flow.getAttributeValue(RF_NS, name);
    }
}
