package com.ruleforge.model.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.90 — {@link Rule#getDebug()} / {@link Rule#isDebug()} 默认值契约。
 *
 * <p>锁 V5.90 翻转 {@code Rule.debug} 构造默认从 {@code true} 翻到 {@code false}
 * 的契约。理由:
 * <ul>
 *   <li>V5.88 (PR #151) 给 {@code CriteriaActivity.logMessage} 加
 *       {@code if (!this.debug) return;} 早返,跟 {@link com.ruleforge.model.rete.builder.BuildContextImpl#currentRuleIsDebug()}
 *       联动从 {@link Rule#debug} 取值</li>
 *   <li>但 {@link Rule#Rule()} 旧默认 {@code this.debug = true} (Rule.java:35),
 *       让 V5.88 早返 <b>在所有 program-built Rule caller 上从未触发</b> —
 *       bench fixture、test、console-app service layer 全付 logMessage
 *       String.format 每次 evaluate 的 cost</li>
 *   <li>post-V5.89 JFR (v589.jfr) 实测: {@code Formatter.format} 402/402 全部
 *       栈回 {@code CriteriaActivity.logMessage:88} — V5.88 doc 隐含 V5.88 后
 *       bench 走 fast path,实际未触发</li>
 *   <li>audit: 所有 {@code isDebug/getDebug} consumer 都是 observability, 无
 *       control flow 依赖; XML 路径仍可走 {@code <rule debug="true">} 显式开
 *       ({@code parse/AbstractRuleParser.java:46-48}); 5 个测试 setDebug 调用
 *       全部显式, 不依赖构造默认</li>
 * </ul>
 *
 * <p>V5.90 翻默认 + bench 显式 setDebug(false) 后, V5.88 JFR 76% hot path
 * 收口会真正兑现到 bench + 所有 program-built Rule caller。
 *
 * @since 5.90
 */
@DisplayName("V5.90 — Rule.debug 默认值翻转 + 显式 setDebug 透传")
class RuleDebugDefaultTest {

    @Nested
    @DisplayName("构造默认值")
    class DefaultFalse {

        // Given 调用 new Rule() 构造一个 Rule
        // When 不显式调用 setDebug(...)
        // Then getDebug() 应为 false (V5.90 翻转后)
        @Test
        @DisplayName("new Rule() 默认 debug=false (V5.90 翻转)")
        void newRuleDefaultsDebugFalse() {
            Rule r = new Rule();
            assertThat(r.getDebug())
                .as("V5.90 契约: new Rule() 默认 debug=false, 让 V5.88 logMessage 早返生效")
                .isFalse();
        }
    }

    @Nested
    @DisplayName("显式 setDebug 透传")
    class ExplicitSet {

        // Given new Rule() 然后 setDebug(true)
        // When 读 getDebug()
        // Then 应为 true (显式开锁 observability)
        @Test
        @DisplayName("setDebug(true) 显式开 debug log")
        void explicitSetDebugTrue() {
            Rule r = new Rule();
            r.setDebug(true);
            assertThat(r.getDebug()).isTrue();
        }

        // Given new Rule() (默认 false) 然后 setDebug(false)
        // When 读 getDebug()
        // Then 应为 false (幂等)
        @Test
        @DisplayName("setDebug(false) 显式关 (跟默认 false 一致)")
        void explicitSetDebugFalse() {
            Rule r = new Rule();
            r.setDebug(false);
            assertThat(r.getDebug()).isFalse();
        }
    }
}
