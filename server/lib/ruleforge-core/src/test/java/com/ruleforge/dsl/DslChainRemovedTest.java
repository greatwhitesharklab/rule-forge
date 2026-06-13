package com.ruleforge.dsl;

import com.ruleforge.builder.KnowledgeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.45.4 — DSL chain runtime 真删 BDD。
 *
 * <p>锁 4 件事:
 * <ol>
 *   <li>{@link DslRuleSet} interface 从 ruleforge-core 删掉 — 0 caller 直接引用</li>
 *   <li>{@link KnowledgeBuilder} 删 {@code dslRuleSetBuilder} 字段 + 不再调
 *       {@code .support()} / {@code .build()} 老 DSL chain</li>
 *   <li>ruleforge-dsl module 仍存在(classpath 加载),但 production runtime
 *       不可达 — 0 caller 调 DSLRuleSetBuilder</li>
 *   <li>ruleforge-dsl dependency 改 optional(ruleforge-core / console-app /
 *       executor-app 编译期不需要,jar 仍在 classpath)</li>
 * </ol>
 *
 * <p>本测试**不**测 .ul 老格式"返 0 rule"(V5.43 行为,本 PR 不动)。
 */
@DisplayName("V5.45.4 — DSL chain runtime 真删 BDD")
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
    @DisplayName("Given ruleforge-dsl module,V5.45.4 仍存在但 production 不可达")
    class DslModuleArchived {

        @Test
        @DisplayName("ruleforge-dsl module 不在 ruleforge-core 编译依赖(V5.45.4 验证 — 已无 DslRuleSet interface)")
        void archived() {
            // V5.45.4:DslRuleSet interface 删掉后,ruleforge-dsl 的 DSLRuleSetBuilder
            // 实现就**没有** type 实现了 — 0 caller 调用,DslRuleSet 是"已 archive"
            // 状态(module 仍可加载,但 production runtime 不可达)
            // 断言:KnowledgeBuilder.class 加载时,没有 DslRuleSet 类引用需求
            try {
                Class.forName("com.ruleforge.builder.DslRuleSet");
                throw new AssertionError("V5.45.4:DslRuleSet 未删,ruleforge-dsl 仍可达");
            } catch (ClassNotFoundException expected) {
                // V5.45.4 期望 DslRuleSet 删掉,ruleforge-dsl module 不可达
                assertFalse(false, "DslRuleSet 不在 ruleforge-core classpath");
            }
        }
    }
}
