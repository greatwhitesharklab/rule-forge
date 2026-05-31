package com.ruleforge.decision.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: 4 维度对比工具
 *
 * ComparisonUtils 提供执行状态、决策结果、输出字段、规则执行的对比逻辑。
 */
@DisplayName("ComparisonUtils - 4 维度对比")
class ComparisonUtilsTest {

    @Nested
    @DisplayName("Scenario: 执行状态对比")
    class StatusComparison {

        // Given 两个不同的执行状态
        // When nullSafeEquals 被调用
        // Then 返回 false
        @Test
        @DisplayName("不同状态 → 不匹配")
        void shouldDetectStatusMismatch() {
            assertThat(ComparisonUtils.nullSafeEquals("SUCCESS", "REJECT")).isFalse();
        }

        @Test
        @DisplayName("相同状态 → 匹配")
        void shouldMatchSameStatus() {
            assertThat(ComparisonUtils.nullSafeEquals("SUCCESS", "SUCCESS")).isTrue();
        }

        @Test
        @DisplayName("null vs 空字符串 → 匹配")
        void shouldTreatNullAsEmpty() {
            assertThat(ComparisonUtils.nullSafeEquals(null, "")).isTrue();
            assertThat(ComparisonUtils.nullSafeEquals("", null)).isTrue();
        }
    }

    @Nested
    @DisplayName("Scenario: 输出字段对比")
    class OutputComparison {

        // Given 两个不同的输出 JSON
        // When compareOutputParams 被调用
        // Then 返回差异字段列表
        @Test
        @DisplayName("检测输出字段差异")
        void shouldDetectOutputDivergence() {
            String main = "{\"score\":\"85\",\"level\":\"A\"}";
            String shadow = "{\"score\":\"92\",\"level\":\"A\"}";

            String result = ComparisonUtils.compareOutputParams(main, shadow);

            assertThat(result).contains("\"field\":\"score\"");
            assertThat(result).contains("\"main\":\"85\"");
            assertThat(result).contains("\"shadow\":\"92\"");
            assertThat(result).doesNotContain("\"field\":\"level\"");
        }

        @Test
        @DisplayName("完全相同的输出 → 空差异")
        void shouldReturnEmptyForSameOutput() {
            String json = "{\"score\":\"85\"}";
            assertThat(ComparisonUtils.compareOutputParams(json, json)).isEqualTo("[]");
        }

        @Test
        @DisplayName("null vs null → 空差异")
        void shouldHandleNulls() {
            assertThat(ComparisonUtils.compareOutputParams(null, null)).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("Scenario: 规则执行对比")
    class RuleComparison {

        // Given 两个不同的规则集合
        // When compareRules 被调用
        // Then 返回 onlyInMain 和 onlyInShadow
        @Test
        @DisplayName("检测规则执行差异")
        void shouldDetectRuleDivergence() {
            Set<String> mainRules = Set.of("ruleA", "ruleB", "ruleC");
            Set<String> shadowRules = Set.of("ruleB", "ruleC", "ruleD");

            String result = ComparisonUtils.compareRules(mainRules, shadowRules);

            assertThat(ComparisonUtils.hasRuleDivergence(result)).isTrue();
            assertThat(result).contains("\"ruleA\"");
            assertThat(result).contains("\"ruleD\"");
        }

        @Test
        @DisplayName("完全相同的规则 → 无差异")
        void shouldReturnNoDivergenceForSameRules() {
            Set<String> rules = Set.of("ruleA", "ruleB");
            String result = ComparisonUtils.compareRules(rules, rules);

            assertThat(ComparisonUtils.hasRuleDivergence(result)).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario: 严重度判定")
    class SeverityCalculation {

        @Test
        @DisplayName("状态不一致 → HIGH")
        void shouldReturnHighForStatusMismatch() {
            assertThat(ComparisonUtils.calculateSeverity(false, true, false, false)).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("结果不一致 → HIGH")
        void shouldReturnHighForResultMismatch() {
            assertThat(ComparisonUtils.calculateSeverity(true, false, false, false)).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("输出字段差异 → MEDIUM")
        void shouldReturnMediumForOutputDivergence() {
            assertThat(ComparisonUtils.calculateSeverity(true, true, true, false)).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("仅规则差异 → LOW")
        void shouldReturnLowForRuleDivergence() {
            assertThat(ComparisonUtils.calculateSeverity(true, true, false, true)).isEqualTo("LOW");
        }

        @Test
        @DisplayName("完全一致 → NONE")
        void shouldReturnNoneForNoDivergence() {
            assertThat(ComparisonUtils.calculateSeverity(true, true, false, false)).isEqualTo("NONE");
        }
    }
}
