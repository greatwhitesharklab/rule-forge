package com.ruleforge.decision.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 对比工具类 — 4 维度对比逻辑
 *
 * 从 ShadowComparisonServiceImpl 提取，供陪跑对比和仿真对比复用。
 *
 * 4 个维度：
 * 1. 执行状态对比（executionStatus）
 * 2. 决策结果对比（rejectCode）
 * 3. 输出字段对比（outputParams JSON）
 * 4. 规则执行对比（ruleName 集合）
 */
public final class ComparisonUtils {

    private ComparisonUtils() {}

    /**
     * 对比输出参数 JSON
     *
     * @return 差异字段列表 JSON: [{"field":"xxx","main":"xxx","shadow":"xxx"}]
     */
    public static String compareOutputParams(String mainOutputJson, String shadowOutputJson) {
        Map<String, Object> mainMap = parseJsonToMap(mainOutputJson);
        Map<String, Object> shadowMap = parseJsonToMap(shadowOutputJson);

        if (mainMap.isEmpty() && shadowMap.isEmpty()) {
            return "[]";
        }

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(mainMap.keySet());
        allKeys.addAll(shadowMap.keySet());

        List<Map<String, String>> divergences = new ArrayList<>();
        for (String key : allKeys) {
            String mainVal = String.valueOf(mainMap.getOrDefault(key, ""));
            String shadowVal = String.valueOf(shadowMap.getOrDefault(key, ""));
            if (!mainVal.equals(shadowVal)) {
                Map<String, String> diff = new LinkedHashMap<>();
                diff.put("field", key);
                diff.put("main", mainVal);
                diff.put("shadow", shadowVal);
                divergences.add(diff);
            }
        }

        return JSON.toJSONString(divergences);
    }

    /**
     * 对比规则执行
     *
     * @return JSON: {"onlyInMain":["ruleA"],"onlyInShadow":["ruleB"],"countDiff":{"mainMatched":5,"shadowMatched":6}}
     */
    public static String compareRules(Set<String> mainRuleNames, Set<String> shadowRuleNames) {
        Set<String> onlyInMain = new TreeSet<>(mainRuleNames);
        onlyInMain.removeAll(shadowRuleNames);

        Set<String> onlyInShadow = new TreeSet<>(shadowRuleNames);
        onlyInShadow.removeAll(mainRuleNames);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("onlyInMain", onlyInMain);
        result.put("onlyInShadow", onlyInShadow);
        result.put("countDiff", Map.of(
                "mainMatched", mainRuleNames.size(),
                "shadowMatched", shadowRuleNames.size()
        ));

        return JSON.toJSONString(result);
    }

    /**
     * 判定差异严重度
     *
     * HIGH: 状态不一致 或 决策结果不同
     * MEDIUM: 同状态同结果，但输出字段不同
     * LOW: 仅规则差异
     * NONE: 完全一致
     */
    public static String calculateSeverity(boolean statusMatch, boolean resultMatch,
                                           boolean hasOutputDivergence, boolean hasRuleDivergence) {
        if (!statusMatch || !resultMatch) {
            return "HIGH";
        }
        if (hasOutputDivergence) {
            return "MEDIUM";
        }
        if (hasRuleDivergence) {
            return "LOW";
        }
        return "NONE";
    }

    /**
     * 判断输出差异 JSON 是否有实际差异（非空列表）
     */
    public static boolean hasOutputDivergence(String outputDivergence) {
        return outputDivergence != null && !outputDivergence.equals("[]");
    }

    /**
     * 判断规则差异 JSON 是否有实际差异
     */
    public static boolean hasRuleDivergence(String ruleDivergence) {
        if (ruleDivergence == null) return false;
        // 如果 onlyInMain 和 onlyInShadow 都为空，则无差异
        return !ruleDivergence.contains("\"onlyInMain\":[]") ||
               !ruleDivergence.contains("\"onlyInShadow\":[]");
    }

    /**
     * 解析 JSON 为 Map
     */
    public static Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            JSONObject obj = JSON.parseObject(json);
            return obj != null ? new HashMap<>(obj) : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * null-safe 字符串比较
     */
    public static boolean nullSafeEquals(String a, String b) {
        return Objects.equals(nullSafe(a), nullSafe(b));
    }

    public static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
