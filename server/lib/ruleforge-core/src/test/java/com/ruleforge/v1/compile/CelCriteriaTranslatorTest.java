package com.ruleforge.v1.compile;

import com.ruleforge.action.AbstractAction;
import com.ruleforge.action.ActionType;
import com.ruleforge.action.ActionValue;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.engine.Context;
import com.ruleforge.engine.RuleExecutionResponse;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Act;
import com.ruleforge.model.library.variable.CategoryType;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.library.variable.VariableLibrary;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.builder.ReteBuilder;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.Or;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.SchemaField;
import com.ruleforge.v1.ast.V1DataType;
import com.ruleforge.v1.cel.CelConditionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V7.0.0 W1-3 — CEL → RETE criteria 翻译器 BDD(critical path)。
 *
 * <p>锁契约:CEL 条件经 {@link CelCriteriaTranslator} 翻译成 Criteria/And/Or 树,
 * 喂 ReteBuilder 编进 RETE,assert fact 后 fireRules 命中行为正确。
 * 这证明"CEL 编出的 criteria 跟手写 RETE criteria 等价"(design doc W1-3 验收)。
 *
 * <p>每个用例:CEL expr → translate → build rete → insert fact → fire → 断言 fired。
 */
@DisplayName("V7.0.0 W1-3 — CEL → RETE criteria 翻译器")
class CelCriteriaTranslatorTest {

    public static class LoanFact {
        public int age;
        public int score;
        public boolean blacklisted;
        public int getAge() { return age; }
        public int getScore() { return score; }
        public boolean isBlacklisted() { return blacklisted; }
    }

    private static Schema loanSchema() {
        Schema s = new Schema();
        s.setName("LoanApplication");
        s.setFields(Arrays.asList(
                new SchemaField("age", V1DataType.NUMBER),
                new SchemaField("score", V1DataType.NUMBER),
                new SchemaField("blacklisted", V1DataType.BOOLEAN)));
        return s;
    }

    @BeforeAll
    static void wire() throws Exception {
        EngineContextWirer.wire();
    }

    /** 把一个 CEL 条件编成 RETE + 插 fact + fire,返回是否 fire。 */
    private boolean fires(String celCondition, LoanFact fact) {
        AtomicBoolean fired = new AtomicBoolean(false);
        Rule rule = new Rule();
        rule.setName("cel-rule");
        rule.setLhs(CelCriteriaTranslator.translateToLhs(celCondition, loanSchema()));
        Rhs rhs = new Rhs();
        rhs.setActions(Collections.singletonList(new AbstractAction() {
            private final ActionType t = ActionType.ConsolePrint;
            public ActionValue execute(Context c, Object m, List<Object> all) { fired.set(true); return null; }
            public ActionType getActionType() { return t; }
        }));
        rule.setRhs(rhs);

        VariableLibrary lib = new VariableLibrary();
        VariableCategory cat = new VariableCategory();
        cat.setName("LoanApplication");
        cat.setType(CategoryType.Clazz);
        cat.setClazz(LoanFact.class.getName());
        List<Variable> vars = new ArrayList<>();
        for (String n : Arrays.asList("age", "score", "blacklisted")) {
            Variable v = new Variable();
            v.setName(n); v.setLabel(n);
            v.setType(n.equals("blacklisted") ? Datatype.Boolean : Datatype.Integer);
            v.setAct(Act.In);
            vars.add(v);
        }
        cat.setVariables(vars);
        lib.addVariableCategory(cat);
        ResourceLibrary rl = new ResourceLibrary(Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());
        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(rule), rl);
        KnowledgePackage kp = new KnowledgeBase(rete).getKnowledgePackage();
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        session.insert(fact);
        RuleExecutionResponse resp = session.fireRules();
        return fired.get();
    }

    @Nested
    @DisplayName("Given 单字段比较 CEL When translate+fire Then 命中正确")
    class SingleComparison {

        @Test
        void age_ge_18_命中_20_岁() {
            // Given "age >= 18" + age=20 When fire Then fires
            LoanFact f = new LoanFact(); f.age = 20;
            assertThat(fires("age >= 18", f)).isTrue();
        }

        @Test
        void age_ge_18_不命中_15_岁() {
            LoanFact f = new LoanFact(); f.age = 15;
            assertThat(fires("age >= 18", f)).isFalse();
        }

        @Test
        void blacklisted_eq_true_命中() {
            // Given "blacklisted == true" + blacklisted=true When fire Then fires
            LoanFact f = new LoanFact(); f.blacklisted = true;
            assertThat(fires("blacklisted == true", f)).isTrue();
        }

        @Test
        void blacklisted_eq_true_不命中_false() {
            LoanFact f = new LoanFact(); f.blacklisted = false;
            assertThat(fires("blacklisted == true", f)).isFalse();
        }

        @Test
        void 字段在右_18_le_age_翻转_命中() {
            // Given "18 <= age" (字段在右)+ age=20 When fire Then fires (翻转成 age >= 18)
            LoanFact f = new LoanFact(); f.age = 20;
            assertThat(fires("18 <= age", f)).isTrue();
        }
    }

    @Nested
    @DisplayName("Given 布尔字段裸引用/取反 When translate+fire Then 命中正确")
    class BooleanField {

        @Test
        void 裸布尔字段_blacklisted_当_true() {
            // Given "blacklisted" + blacklisted=true When fire Then fires (== true)
            LoanFact f = new LoanFact(); f.blacklisted = true;
            assertThat(fires("blacklisted", f)).isTrue();
        }

        @Test
        void 裸布尔字段_blacklisted_false_不命中() {
            LoanFact f = new LoanFact(); f.blacklisted = false;
            assertThat(fires("blacklisted", f)).isFalse();
        }

        @Test
        void 取反_not_blacklisted_false_命中() {
            // Given "!blacklisted" + blacklisted=false When fire Then fires (== false)
            LoanFact f = new LoanFact(); f.blacklisted = false;
            assertThat(fires("!blacklisted", f)).isTrue();
        }

        @Test
        void 取反_not_blacklisted_true_不命中() {
            LoanFact f = new LoanFact(); f.blacklisted = true;
            assertThat(fires("!blacklisted", f)).isFalse();
        }
    }

    @Nested
    @DisplayName("Given 复合逻辑 CEL When translate+fire Then 命中正确")
    class Compound {

        @Test
        void and_两条件都满足_命中() {
            // Given "score > 600 && !blacklisted" + score=700/blacklisted=false When fire Then fires
            LoanFact f = new LoanFact(); f.score = 700; f.blacklisted = false;
            assertThat(fires("score > 600 && !blacklisted", f)).isTrue();
        }

        @Test
        void and_blacklisted_true_不命中() {
            LoanFact f = new LoanFact(); f.score = 700; f.blacklisted = true;
            assertThat(fires("score > 600 && !blacklisted", f)).isFalse();
        }

        @Test
        void and_score低_不命中() {
            LoanFact f = new LoanFact(); f.score = 500; f.blacklisted = false;
            assertThat(fires("score > 600 && !blacklisted", f)).isFalse();
        }

        @Test
        void or_任一满足_命中() {
            // Given "age < 25 || score > 800"
            LoanFact young = new LoanFact(); young.age = 20; young.score = 500;
            assertThat(fires("age < 25 || score > 800", young)).isTrue();
            LoanFact highScore = new LoanFact(); highScore.age = 30; highScore.score = 900;
            assertThat(fires("age < 25 || score > 800", highScore)).isTrue();
            LoanFact neither = new LoanFact(); neither.age = 30; neither.score = 700;
            assertThat(fires("age < 25 || score > 800", neither)).isFalse();
        }
    }

    @Nested
    @DisplayName("Given 翻译产出结构 When 检查 Then 翻译成正确的 Criteria/Junction 类型")
    class TranslationShape {

        @Test
        void 单比较_翻译成_Criteria() {
            // Given "age >= 18" When translate Then 是 Criteria(非 junction)
            assertThat(CelCriteriaTranslator.translate("age >= 18", loanSchema()))
                    .isInstanceOf(Criteria.class);
        }

        @Test
        void and_翻译成_And_junction() {
            assertThat(CelCriteriaTranslator.translate("age >= 18 && score > 600", loanSchema()))
                    .isInstanceOf(And.class);
        }

        @Test
        void or_翻译成_Or_junction() {
            assertThat(CelCriteriaTranslator.translate("age < 25 || score > 800", loanSchema()))
                    .isInstanceOf(Or.class);
        }
    }

    @Nested
    @DisplayName("Given 不支持的 CEL When translate Then 抛 CelConditionException")
    class Unsupported {

        @Test
        void 算术_on_field_被拒() {
            // Given "age + 1 >= 18" (算术) When translate Then 抛(用 CEL-eval 路径)
            assertThatThrownBy(() -> CelCriteriaTranslator.translate("age + 1 >= 18", loanSchema()))
                    .isInstanceOf(CelConditionException.class);
        }

        @Test
        void 嵌套_not_被拒() {
            // Given "!(age >= 18)" When translate Then 抛(展开成显式比较)
            assertThatThrownBy(() -> CelCriteriaTranslator.translate("!(age >= 18)", loanSchema()))
                    .isInstanceOf(CelConditionException.class);
        }
    }
}
