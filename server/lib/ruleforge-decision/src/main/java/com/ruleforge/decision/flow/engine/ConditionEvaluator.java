package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.exception.FlowExecutionException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UEL 条件表达式解析器。最小子集,支持:
 *   - ${variable}                  字面变量引用
 *   - ${variable op value}          op ∈ {>, <, >=, <=, ==, !=}
 *   - ${a.b.c}                      点号路径访问(嵌套 Map)
 *
 * 不支持(Phase 1 不强求):函数调用、列表字面量、三元、&& / ||。复杂表达式 fallback 留到后续。
 */
public class ConditionEvaluator {

    private static final Pattern UEL = Pattern.compile(
        "([\\w]+(?:\\.[\\w]+)*)\\s*(>=|<=|!=|==|>|<)\\s*(.+)");

    /** 解析 ${...} 表达式,返回布尔。空 / null expression 视为 true(无条件)。 */
    public boolean evaluate(String expression, Map<String, Object> vars) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        String expr = expression.trim();
        if (expr.startsWith("${") && expr.endsWith("}")) {
            expr = expr.substring(2, expr.length() - 1).trim();
        }

        Matcher m = UEL.matcher(expr);
        if (!m.matches()) {
            // 简单变量引用
            Object v = resolveVariable(expr, vars);
            return Boolean.TRUE.equals(v);
        }
        Object lhs = resolveValue(m.group(1), vars);
        Object rhs = resolveValue(m.group(3), vars);
        String op = m.group(2);
        return compare(lhs, rhs, op);
    }

    private Object resolveValue(String token, Map<String, Object> vars) {
        token = token.trim();
        // 数字
        try {
            if (token.contains(".")) return Double.parseDouble(token);
            return Long.parseLong(token);
        } catch (NumberFormatException ignore) {
        }
        // boolean
        if ("true".equalsIgnoreCase(token)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(token)) return Boolean.FALSE;
        // 引号字符串
        if ((token.startsWith("\"") && token.endsWith("\"")) ||
            (token.startsWith("'") && token.endsWith("'"))) {
            return token.substring(1, token.length() - 1);
        }
        // 变量引用
        return resolveVariable(token, vars);
    }

    private Object resolveVariable(String path, Map<String, Object> vars) {
        String[] parts = path.split("\\.");
        Object cur = vars;
        for (String p : parts) {
            if (cur == null) return null;
            if (cur instanceof Map<?, ?> m) {
                cur = m.get(p);
            } else {
                throw new FlowExecutionException("Cannot resolve path '" + path + "': not a Map at " + p);
            }
        }
        return cur;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean compare(Object lhs, Object rhs, String op) {
        if (lhs == null || rhs == null) {
            return switch (op) {
                case "==" -> lhs == rhs;
                case "!=" -> lhs != rhs;
                default -> false;
            };
        }
        if (lhs instanceof Number && rhs instanceof Number) {
            double a = ((Number) lhs).doubleValue();
            double b = ((Number) rhs).doubleValue();
            return switch (op) {
                case ">"  -> a >  b;
                case "<"  -> a <  b;
                case ">=" -> a >= b;
                case "<=" -> a <= b;
                case "==" -> a == b;
                case "!=" -> a != b;
                default -> throw new FlowExecutionException("Unknown op: " + op);
            };
        }
        if (lhs instanceof Comparable && lhs.getClass().equals(rhs.getClass())) {
            int cmp = ((Comparable) lhs).compareTo(rhs);
            return switch (op) {
                case ">"  -> cmp >  0;
                case "<"  -> cmp <  0;
                case ">=" -> cmp >= 0;
                case "<=" -> cmp <= 0;
                case "==" -> cmp == 0;
                case "!=" -> cmp != 0;
                default -> throw new FlowExecutionException("Unknown op: " + op);
            };
        }
        // fallback: toString 比较
        int cmp = String.valueOf(lhs).compareTo(String.valueOf(rhs));
        return switch (op) {
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            default -> throw new FlowExecutionException("Cannot compare " + lhs.getClass() + " with " + rhs.getClass() + " for op " + op);
        };
    }
}
