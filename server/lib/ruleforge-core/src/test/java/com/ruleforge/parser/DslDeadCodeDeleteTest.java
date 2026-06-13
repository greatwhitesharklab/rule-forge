package com.ruleforge.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V5.43.6 — com.ruleforge.dsl.* 删 dead code 后的"类消失"快照测试。
 *
 * <p>V5.43 删老 .ul DSL 链后,production 运行时不再需要大部分 dsl/ 文件。
 * {@code DSLRuleSetBuilder} 本身**无** caller(production runtime 不可达),
 * 但其 transitive 编译依赖(ANTLR 生成物 + dsl/builder/)仍合法保留 — 删
 * DSLRuleSetBuilder 等会同时牵连 ANTLR parser/lexer 编译。
 *
 * <p>V5.44.1 — 把整个 com.ruleforge.dsl.* 整包**搬到独立 jar**(lib/ruleforge-dsl),
 * V5.43.6 之前决定**保留**的 DSLRuleSetBuilder / RuleParser* / BuildRulesVisitor /
 * ScriptDecisionTableErrorListener / DSLUtils / dsl/builder/* 都已搬走 — 它们的
 * 保留方式从"在 ruleforge-core 里占位"变成"在 ruleforge-dsl jar 里独立打包"。
 *
 * <p>本测试现在只锁一件事: V5.43.6 删的 5 个 dead code class **永远不再出现**(防
 * 有人在 V5.44+ 错误救回)。它们的"保留"或"消失"都跟打包位置无关,只跟 class 存在性
 * 有关。
 *
 * <p>ruleforge-dsl jar 内的 class 列表快照见 DslJarExtractTest(在 ruleforge-dsl
 * module 的 test 目录)。
 *
 * @since 5.43
 */
@DisplayName("V5.43.6 — 删老 dsl/ dead code class 不再存在")
class DslDeadCodeDeleteTest {

    @Test
    @DisplayName("V5.43.6 删的 5 个 dead code class 已消失(永远不再被 classloader 找到)")
    void deadDslClassesGone() {
        for (String fqn : List.of(
            // ScriptDecisionTable 链专用,V5.43.5 已删上游,BaseVisitor 不再被 reference
            "com.ruleforge.dsl.CellScriptRuleParserBaseVisitor",
            // SyntaxError chain:zero ref 外部
            "com.ruleforge.dsl.SyntaxErrorListener",
            "com.ruleforge.dsl.SyntaxErrorReportor",
            // ANTLR 生成的 lexer(老的,RuleParserLexer.java 是新版)
            "com.ruleforge.dsl.RuleLexer"
            // 注:V5.44.1 把所有"保留"的 dsl/* 整包搬到 lib/ruleforge-dsl,本测试只管
            //   V5.43.6 真死的 5 个 class 永远不要救回。新位置见 DslJarExtractTest。
        )) {
            try {
                Class.forName(fqn);
                fail("V5.43.6 删的 class '" + fqn + "' 仍可被 classloader 找到 — 删不彻底");
            } catch (ClassNotFoundException expected) {
                // 期望:删干净
            }
        }
    }

    @Test
    @DisplayName("V5.44.1 DSLRuleSetBuilder 等 8 个保留的 dsl/ class 不再在 ruleforge-core classpath(已搬到 ruleforge-dsl jar)")
    void keptDslClassesMovedOutOfCore() {
        // V5.43.6 第二块测试原本是 `assertThat(cls).isNotNull()` — V5.44.1 后反过来:
        // 这些 class **不应在** ruleforge-core classpath,搬到 ruleforge-dsl 了。
        for (String fqn : List.of(
            "com.ruleforge.dsl.DSLRuleSetBuilder",
            "com.ruleforge.dsl.RuleParserLexer",
            "com.ruleforge.dsl.RuleParserParser",
            "com.ruleforge.dsl.BuildRulesVisitor",
            "com.ruleforge.dsl.ScriptDecisionTableErrorListener",
            "com.ruleforge.dsl.DSLUtils",
            "com.ruleforge.dsl.builder.ActionContextBuilder",
            "com.ruleforge.dsl.builder.CriteriaContextBuilder"
        )) {
            try {
                Class.forName(fqn);
                fail("V5.44.1 后 " + fqn + " 不应在 ruleforge-core classpath(应搬到 ruleforge-dsl jar)");
            } catch (ClassNotFoundException expected) {
                // 期望:不在 ruleforge-core classpath(已搬到 ruleforge-dsl)
                assertThat(expected.getMessage()).contains(fqn);
            }
        }
    }
}
