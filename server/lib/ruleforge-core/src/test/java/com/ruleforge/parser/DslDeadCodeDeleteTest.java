package com.ruleforge.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.43.6 — com.ruleforge.dsl.* 删 dead code 后的"类消失"快照测试。
 *
 * <p>V5.43 删老 .ul DSL 链后,production 运行时不再需要大部分 dsl/ 文件。
 * {@code DSLRuleSetBuilder} 本身**无** caller(production runtime 不可达),
 * 但其 transitive 编译依赖(ANTLR 生成物 + dsl/builder/)仍合法保留 — 删
 * DSLRuleSetBuilder 等会同时牵连 ANTLR parser/lexer 编译,得 V5.44 单独
 * PR 拆出 production 独立 jar。
 *
 * <p>本测试锁**只**删真死代码(无任何 caller 引用,且不是 ANTLR transitive 编译依赖):
 * <ul>
 *   <li>CellScriptRuleParserBaseVisitor(ScriptDecisionTable 链专用,V5.43.5 已删上游)</li>
 *   <li>SyntaxErrorListener / SyntaxErrorReportor(zero ref 外部,只 SyntaxErrorReportor 自引用)</li>
 *   <li>RuleLexer.java + 3 个 .tokens 文件(ANTLR 生成物,RuleParserLexer.java 替代)</li>
 *   <li>2 个 .g4 grammar 源文件(ANTLR 输入,RuleParser.g4/Lexer.g4 是新版的源)</li>
 * </ul>
 *
 * <p>本测试**不**测功能,只测 class 物理消失(防"删了又救回")。
 *
 * @since 5.43
 */
@DisplayName("V5.43.6 — 删老 dsl/ dead code class 不再存在")
class DslDeadCodeDeleteTest {

    @Test
    @DisplayName("V5.43.6 删的 5 个 dead code class 已消失")
    void deadDslClassesGone() {
        for (String fqn : List.of(
            // ScriptDecisionTable 链专用,V5.43.5 已删上游,BaseVisitor 不再被 reference
            "com.ruleforge.dsl.CellScriptRuleParserBaseVisitor",
            // SyntaxError chain:zero ref 外部
            "com.ruleforge.dsl.SyntaxErrorListener",
            "com.ruleforge.dsl.SyntaxErrorReportor",
            // ANTLR 生成的 lexer(老的,RuleParserLexer.java 是新版)
            "com.ruleforge.dsl.RuleLexer"
            // 注:DSLRuleSetBuilder 还引用 BuildRulesVisitor / ScriptDecisionTableErrorListener
            // 注:RuleParserParser 还引用 RuleParserVisitor / RuleParserBaseVisitor
            // 全部是 transitive 编译依赖,**保留**
        )) {
            try {
                Class.forName(fqn);
                throw new AssertionError(
                    "V5.43.6 删的 class '" + fqn + "' 仍可被 classloader 找到 — 删不彻底");
            } catch (ClassNotFoundException expected) {
                // 期望:删干净
            }
        }
    }

    @Test
    @DisplayName("DSLRuleSetBuilder + RuleParser* ANTLR 生成物 + BuildRulesVisitor + 2 listener 仍保留(transitive 编译依赖,V5.44 拆出独立 jar)")
    void keptDslClassesStillExist() {
        for (String fqn : List.of(
            "com.ruleforge.dsl.DSLRuleSetBuilder",
            "com.ruleforge.dsl.RuleParserLexer",
            "com.ruleforge.dsl.RuleParserParser",
            "com.ruleforge.dsl.RuleParserVisitor",
            "com.ruleforge.dsl.RuleParserBaseVisitor",
            "com.ruleforge.dsl.BuildRulesVisitor",
            "com.ruleforge.dsl.ScriptDecisionTableErrorListener",
            "com.ruleforge.dsl.DSLUtils"
        )) {
            try {
                Class<?> cls = Class.forName(fqn);
                assertThat(cls).as("保留的 dsl/ class 应存在:" + fqn).isNotNull();
            } catch (ClassNotFoundException e) {
                throw new AssertionError(
                    "V5.43.6 误删保留的 dsl/ class: " + fqn, e);
            }
        }
    }

    @Test
    @DisplayName("dsl/builder/ 7 文件仍保留(DSLRuleSetBuilder 依赖)")
    void builderSubdirKept() {
        for (String fqn : List.of(
            "com.ruleforge.dsl.builder.AbstractContextBuilder",
            "com.ruleforge.dsl.builder.ActionContextBuilder",
            "com.ruleforge.dsl.builder.BuildUtils",
            "com.ruleforge.dsl.builder.ContextBuilder",
            "com.ruleforge.dsl.builder.CriteriaContextBuilder",
            "com.ruleforge.dsl.builder.LibraryContextBuilder",
            "com.ruleforge.dsl.builder.NamedConditionBuilder"
        )) {
            try {
                Class<?> cls = Class.forName(fqn);
                assertThat(cls).as("dsl/builder/ 应保留:" + fqn).isNotNull();
            } catch (ClassNotFoundException e) {
                throw new AssertionError(
                    "V5.43.6 误删 dsl/builder/ class: " + fqn, e);
            }
        }
    }
}
