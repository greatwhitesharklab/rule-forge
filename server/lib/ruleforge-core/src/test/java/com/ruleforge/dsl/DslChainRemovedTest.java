package com.ruleforge.dsl;

import com.ruleforge.builder.KnowledgeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.45.4 / V5.47 — DSL chain runtime 真删 BDD。
 *
 * <p>V5.45.4 锁 3 件事(本类 4 个 Nested test 前 3 个):
 * <ol>
 *   <li>{@link DslRuleSet} interface 从 ruleforge-core 删掉 — 0 caller 直接引用</li>
 *   <li>{@link KnowledgeBuilder} 删 {@code dslRuleSetBuilder} 字段 + 不再调
 *       {@code .support()} / {@code .build()} 老 DSL chain</li>
 *   <li>.ul 老格式走 0 rule fallback(V5.43 行为,删字段后 caller 不会触发
 *       老 DSL chain)</li>
 * </ol>
 *
 * <p>V5.47 锁第 4 件事(本类 DslModuleArchived Nested test):
 * <ol start="4">
 *   <li>ruleforge-dsl module 整 module 删除 + 2 app 入口不 import
 *       {@code com.ruleforge.dsl.*} — classloader 完全找不到
 *       {@code DSLRuleSetBuilder} 等</li>
 * </ol>
 *
 * <p>本测试**不**测 .ul 老格式"返 0 rule"(V5.43 行为,本 PR 不动)。
 */
@DisplayName("V5.45.4 / V5.47 — DSL chain runtime 真删 BDD")
class DslChainRemovedTest {

    @Nested
    @DisplayName("Given KnowledgeBuilder,V5.45.4 不再有 dslRuleSetBuilder 字段")
    class KnowledgeBuilderNoDslChain {

        @Test
        @DisplayName("KnowledgeBuilder.class.getDeclaredField(\"dslRuleSetBuilder\") 抛 NoSuchFieldException")
        void noField() {
            try {
                KnowledgeBuilder.class.getDeclaredField("dslRuleSetBuilder");
                // 仍存在 — V5.45.4 还没真删
                throw new AssertionError("V5.45.4:dslRuleSetBuilder 字段未删");
            } catch (NoSuchFieldException expected) {
                // V5.45.4 期望字段被删
                assertNotNull(expected.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Given DslRuleSet interface,V5.45.4 已从 ruleforge-core 删掉")
    class DslRuleSetRemoved {

        @Test
        @DisplayName("Class.forName(\"com.ruleforge.builder.DslRuleSet\") 抛 ClassNotFoundException")
        void classGone() {
            try {
                Class.forName("com.ruleforge.builder.DslRuleSet");
                throw new AssertionError("V5.45.4:DslRuleSet interface 未删");
            } catch (ClassNotFoundException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Given 老 .ul 资源,V5.45.4 production 走 0 rule 路径")
    class UlZeroRulesPath {

        @Test
        @DisplayName(".ul 后缀 + 老 DSL 文本 → KnowledgeBuilder 不再调 dslRuleSetBuilder.support()")
        void noSupportCall() {
            // V5.45.4:KnowledgeBuilder .ul 老格式走 0 rule fallback(跟 V5.43 行为一致)
            // 本测试只验证 dslRuleSetBuilder 字段已删 — 实际 .ul 处理路径 V5.43 写完,
            // V5.45.4 删字段后 caller 不会触发老 DSL chain
            try {
                java.lang.reflect.Field f = KnowledgeBuilder.class.getDeclaredField("dslRuleSetBuilder");
                f.setAccessible(true);
                // 仍存在 — 字段没删
                throw new AssertionError("V5.45.4 字段没删,.ul 老格式还会走 DSL chain");
            } catch (NoSuchFieldException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Given V5.47 删 ruleforge-dsl module,When classloader 查 DSLRuleSetBuilder,Then 找不到")
    class DslModuleArchived {

        @Test
        @DisplayName("V5.47 删 ruleforge-dsl module — DSLRuleSetBuilder / RuleParserLexer / RuleParserParser 全部 ClassNotFoundException")
        void moduleDeleted() {
            // V5.47:整 module lib/ruleforge-dsl 删,2 app 入口不 import com.ruleforge.dsl.*,
            // classloader 跑 Class.forName 应全部 CNFE(连 optional jar 都不在 classpath)
            for (String fqn : List.of(
                "com.ruleforge.dsl.DSLRuleSetBuilder",
                "com.ruleforge.dsl.RuleParserLexer",
                "com.ruleforge.dsl.RuleParserParser",
                "com.ruleforge.dsl.BuildRulesVisitor",
                "com.ruleforge.dsl.RuleForgeDslAutoConfiguration",
                "com.ruleforge.dsl.builder.ActionContextBuilder",
                "com.ruleforge.dsl.builder.CriteriaContextBuilder"
            )) {
                try {
                    Class.forName(fqn);
                    throw new AssertionError(
                        "V5.47 删 ruleforge-dsl module 后," + fqn + " 仍可被 classloader 找到 — module 没删干净");
                } catch (ClassNotFoundException expected) {
                    // 期望:整 module 删了
                }
            }
        }
    }
}
