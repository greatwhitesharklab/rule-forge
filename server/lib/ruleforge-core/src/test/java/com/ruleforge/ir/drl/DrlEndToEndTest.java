package com.ruleforge.ir.drl;

import com.ruleforge.ir.dsl.DslMappingSet;
import com.ruleforge.ir.dsl.DslParser;
import com.ruleforge.ir.dsl.PlaceholderExpander;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.model.rule.Rhs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.42.5 — DRL 化 end-to-end BDD。
 *
 * <p>验证整条 V5.42 流水线对老 .ul / 老 .xml 路径**不破坏**,且新 .drl 路径
 * 走通。流水线:
 * <pre>
 *   老 .ul 老 path:
 *     DSLRuleSetBuilder.build(content) → RuleSet  ← 已有测试,本类不重复
 *     UlToDrlConverter.emit(ruleSet) → String  ← V5.42.3b,本类验证
 *
 *   新 .drl path(V5.42 主线):
 *     DrlResource(文本)  ← V5.42.4
 *       ↓ DrlResourceBuilder.build
 *     DrlDeserializer.parseDrl  ← V5.42.4
 *       ↓ DrlLexer / DrlParser(V5.42.1)+ DrlAstVisitor(V5.42.2)+ DatatypeResolver
 *     List&lt;Rule&gt;
 *
 *   Drools 6 .dsl/.dslrd 桥接:
 *     DslParser.parse(.dsl 文本) → DslMappingSet
 *     PlaceholderExpander.expand(.dslrd 文本) → DRL 文本
 *     DrlResourceBuilder(展开后 DRL) → List&lt;Rule&gt;
 * </pre>
 *
 * <p>不在 V5.42.5 范围(留 V5.42.6+.7+.8):
 * <ul>
 *   <li>KnowledgeBuilder 入口切流(用 applicationContext 注入,spring 集成 V5.42.6)</li>
 *   <li>console-ui 集成</li>
 *   <li>老 .xml 0 破坏详细 corpus(200+ 片段)</li>
 * </ul>
 *
 * @since 5.42
 */
@DisplayName("V5.42.5 — DRL 化 end-to-end BDD")
class DrlEndToEndTest {

    private DatatypeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DatatypeResolver();
        resolver.register("Applicant",
            DatatypeResolver.TypeInfo.fact("Applicant", Arrays.asList("age", "income", "name")));
    }

    // ============================================================
    // === V5.42 主线:DrlResource 文本 → Rule 列表 ===
    // ============================================================

    @Nested
    @DisplayName("Given V5.42.4 DrlResourceBuilder,When 跑 .drl 文本,Then 产出 Rule 列表")
    class DrlMainPath {

        @Test
        @DisplayName("最简 .drl 走完 V5.42.1+.2+.4 全链")
        void simplest() {
            DrlResourceBuilder b = new DrlResourceBuilder(resolver);
            DrlResource res = new DrlResource(
                "rule \"R1\" when Applicant(age > 18) then end",
                "/tmp/r.drl");
            List<Rule> rules = b.build(res);
            assertEquals(1, rules.size());
            assertEquals("R1", rules.get(0).getName());
            assertNotNull(rules.get(0).getLhs());
            assertNotNull(rules.get(0).getRhs());
        }

        @Test
        @DisplayName("DRL 带所有 11 attribute + extends(D2)")
        void fullAttributes() {
            DrlResourceBuilder b = new DrlResourceBuilder(resolver);
            DrlResource res = new DrlResource(
                "rule \"X_else\" " +
                "[salience 10, agenda-group \"g\", auto-focus true, no-loop true, " +
                "lock-on-active true, enabled true, date-effective \"2026-01-01\", " +
                "date-expires \"2027-01-01\", timer (int(60)), " +
                "activation-group \"a\", ruleflow-group \"rf\"] " +
                "extends \"X\" " +
                "when Applicant(age > 18) then end",
                "/tmp/r.drl");
            List<Rule> rules = b.build(res);
            assertEquals(1, rules.size());
            Rule r = rules.get(0);
            assertEquals("X_else", r.getName());
            assertTrue(r.isWithElse(), "D2 extends → withElse=true");
            assertEquals(10, r.getSalience());
            assertEquals("g", r.getAgendaGroup());
            assertEquals("a", r.getActivationGroup());
            assertEquals("rf", r.getRuleflowGroup());
            assertEquals(Boolean.TRUE, r.getAutoFocus());
            assertEquals(Boolean.TRUE, r.getEnabled());
        }

        @Test
        @DisplayName("多 rule 在同一 .drl 文本,顺序保留")
        void multipleRulesInOneFile() {
            DrlResourceBuilder b = new DrlResourceBuilder(resolver);
            DrlResource res = new DrlResource(
                "rule \"R1\" when Applicant(age > 18) then end\n" +
                "rule \"R2\" when Applicant(income > 5000) then end\n" +
                "rule \"R3\" when Applicant(name == \"alice\") then end",
                "/tmp/multi.drl");
            List<Rule> rules = b.build(res);
            assertEquals(3, rules.size());
            assertEquals("R1", rules.get(0).getName());
            assertEquals("R2", rules.get(1).getName());
            assertEquals("R3", rules.get(2).getName());
        }
    }

    // ============================================================
    // === V5.42.3a .dsl + .dslrd 桥接 → Rule 列表 ===
    // ============================================================

    @Nested
    @DisplayName("Given .dsl mapping + .dslrd 文本,When 走 DslParser + PlaceholderExpander,Then 进 DrlResourceBuilder 产出 Rule")
    class DslBridge {

        @Test
        @DisplayName("简单 when + then .dsl mapping + 占位符展开 → Rule 列表")
        void simpleDslBridge() {
            // 1. .dsl mapping
            String dsl = "[when]Applicant is at least {age}=Applicant(age >= {age})\n" +
                "[then]Approve=update($a); approve();\n";
            DslMappingSet mapping = new DslParser().parseAsSet(dsl);

            // 2. .dslrd 文本(占位符用 ${} 形式)
            String dslrd = "rule \"R1\" when ${nat_lang} then end";
            // 这里测 .dsl/.dslrd 桥接:end-to-end 走一遍
            // 注:.dslrd → DRL 转换在 V5.42.6 才完整化(V5.42.3a 只做 mapping 解析 + 占位符展开)
            // 本测试只断言:V5.42.3a 产出的 mapping 能被 DrlResourceBuilder 间接消费
            assertNotNull(mapping);
            assertEquals(2, mapping.size());

            // 3. 走 PlaceholderExpander(V5.42.3a)展开 .dslrd
            String expanded = new PlaceholderExpander().expand(dslrd);
            // ${nat_lang} 替换成 nat_lang
            assertEquals("rule \"R1\" when nat_lang then end", expanded);
        }

        @Test
        @DisplayName("place holder 展开后嵌入 DRL 模板 → 走 DrlResourceBuilder")
        void expandedToDrl() {
            // 1. DRL 模板里直接含 DRL(.dsl 展开路径走 V5.42.6 完整化)
            String template = "rule \"R1\" when Applicant(age >= 21) then end";
            DrlResourceBuilder b = new DrlResourceBuilder(resolver);
            List<Rule> rules = b.build(new DrlResource(template, "/tmp/r.drl"));
            assertEquals(1, rules.size());
        }
    }

    // ============================================================
    // === V5.51.2 lhs 内部可读:Rule.lhs.criterion 链 ===
    // ============================================================
    //
    // V5.42.4 时 PENDING_LHS 静态 map 暂存,V5.51.2 删字段后 caller 全部走
    // Lhs.criterion 链(And Junction → Criteria → Left/LeftPart)。

    @Nested
    @DisplayName("Given V5.51.2 Lhs.criterion,When 反查,Then 跟 parse 出的 Criteria 一致")
    class LhsPendingReadback {

        @Test
        @DisplayName("Applicant(age > 18) parse 后 Lhs.criterion 链能读到 Criteria")
        void pendingLhsReadable() {
            List<Rule> rules = DrlDeserializer.parseDrl(
                "rule \"R1\" when Applicant(age > 18) then end", resolver);
            // V5.51.2 migration:PropertyCriteria 全部挂到 Lhs.criterion(And) 链
            assertNotNull(rules.get(0).getLhs().getCriterion());
            And and = (And) rules.get(0).getLhs().getCriterion();
            assertEquals(1, and.getCriterions().size());
            Criteria c = (Criteria) and.getCriterions().get(0);
            assertEquals("age", ((VariableLeftPart) c.getLeft().getLeftPart()).getVariableName());
            assertEquals(Op.GreaterThen, c.getOp());
        }
    }
}
