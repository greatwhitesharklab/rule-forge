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
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.0.0 W1-5 — 硬编码 condition → RETE → fire 冒烟测试。
 *
 * <p>不依赖 CEL(W1-3 翻译器),直接手写 Criteria → ReteBuilder.buildRete → insert fact → fireRules,
 * 验证 V1 用 ruleforge-core RETE 引擎的完整链路通(这是 W1-3 翻译器的目标运行时)。
 *
 * <p>锁契约:
 * <ul>
 *   <li>VariableLibrary + VariableCategory(Clazz) + Variable(In) 注册 fact 字段</li>
 *   <li>Criteria(VariableLeftPart(category, name, datatype) + Op + SimpleValue) 命中 fact</li>
 *   <li>命中后 Rhs action 执行</li>
 *   <li>fireRules 返回 firedRules</li>
 * </ul>
 *
 * <p>照搬 SingleRuleFiresBDD / KnowledgeSessionImplActiveGroupTest 的确认模式
 * (EngineContextWirer.wire() 装配 criterionBuilders,CategoryType.Clazz + clazz=POJO 名)。
 *
 * <p>V1 fact 模型:V1 fact 是 schema 定义的动态字段。MVP 冒烟用固定 POJO 验证引擎链路;
 * W2 Flow runner 再定 GeneralEntity(Map-backed)动态 fact 模型。
 */
@DisplayName("V7.0.0 W1-5 — 硬编码 condition → RETE → fire 冒烟")
class V1ReteSmokeTest {

    /** 冒烟 fact POJO(V1 schema "LoanApplication" 的固定投影,验证引擎链路)。 */
    public static class LoanFact {
        public int age;
        public int score;
        public boolean blacklisted;

        public int getAge() { return age; }
        public int getScore() { return score; }
        public boolean isBlacklisted() { return blacklisted; }
    }

    @BeforeAll
    static void wireEngineContext() throws Exception {
        // ReteBuilder.buildRete 需要 criterionBuilders(CriteriaBuilder + AndBuilder)装配
        EngineContextWirer.wire();
    }

    private KnowledgeSessionImpl buildSession(Rule rule, String category, Class<?> factClass, List<VarDef> vars) {
        VariableLibrary lib = new VariableLibrary();
        VariableCategory cat = new VariableCategory();
        cat.setName(category);
        cat.setType(CategoryType.Clazz);
        cat.setClazz(factClass.getName());
        List<Variable> variables = new ArrayList<>();
        for (VarDef v : vars) {
            Variable var = new Variable();
            var.setName(v.name);
            var.setLabel(v.name);
            var.setType(v.type);
            var.setAct(v.act);
            variables.add(var);
        }
        cat.setVariables(variables);
        lib.addVariableCategory(cat);
        ResourceLibrary rl = new ResourceLibrary(Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());
        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(rule), rl);
        KnowledgePackage kp = new KnowledgeBase(rete).getKnowledgePackage();
        return new KnowledgeSessionImpl(kp);
    }

    /** 构造 age >= 18 的 Criteria。 */
    private Criteria ageCriteria(String category) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(category);
        part.setVariableName("age");
        part.setVariableLabel("age");
        part.setDatatype(Datatype.Integer);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(Op.GreaterThenEquals);
        SimpleValue sv = new SimpleValue();
        sv.setContent("18");
        c.setValue(sv);
        return c;
    }

    private static class VarDef {
        final String name;
        final Datatype type;
        final Act act;
        VarDef(String name, Datatype type, Act act) {
            this.name = name;
            this.type = type;
            this.act = act;
        }
    }

    @Test
    @DisplayName("Given age=20 fact + age>=18 rule When fireRules Then rule fires")
    void age_ge_18_命中_30_岁_fire() {
        // Given age>=18 rule + action(设 fired flag)
        AtomicBoolean fired = new AtomicBoolean(false);
        Rule rule = new Rule();
        rule.setName("adult-rule");
        And and = new And();
        and.addCriterion(ageCriteria("LoanApplication"));
        Lhs lhs = new Lhs();
        lhs.setCriterion(and);
        rule.setLhs(lhs);
        Rhs rhs = new Rhs();
        List<com.ruleforge.action.Action> actions = new ArrayList<>();
        actions.add(new AbstractAction() {
            private final ActionType actionType = ActionType.ConsolePrint;

            @Override
            public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
                fired.set(true);
                return null;
            }

            @Override
            public ActionType getActionType() {
                return actionType;
            }
        });
        rhs.setActions(actions);
        rule.setRhs(rhs);

        // When build session + insert age=20 fact + fire
        KnowledgeSessionImpl session = buildSession(rule, "LoanApplication", LoanFact.class,
                Collections.singletonList(new VarDef("age", Datatype.Integer, Act.In)));
        LoanFact fact = new LoanFact();
        fact.age = 20;
        session.insert(fact);
        RuleExecutionResponse resp = session.fireRules();

        // Then rule fires
        assertThat(fired).isTrue();
        assertThat(resp.getFiredRules()).hasSize(1);
        assertThat(resp.getFiredRules().get(0).getName()).isEqualTo("adult-rule");
    }

    @Test
    @DisplayName("Given age=15 fact + age>=18 rule When fireRules Then rule 不 fire")
    void age_15_不命中_不_fire() {
        // Given age>=18 rule
        AtomicBoolean fired = new AtomicBoolean(false);
        Rule rule = new Rule();
        rule.setName("adult-rule");
        And and = new And();
        and.addCriterion(ageCriteria("LoanApplication"));
        Lhs lhs = new Lhs();
        lhs.setCriterion(and);
        rule.setLhs(lhs);
        Rhs rhs = new Rhs();
        rhs.setActions(Collections.singletonList(new AbstractAction() {
            private final ActionType actionType = ActionType.ConsolePrint;

            public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
                fired.set(true);
                return null;
            }

            public ActionType getActionType() {
                return actionType;
            }
        }));
        rule.setRhs(rhs);

        // When insert age=15
        KnowledgeSessionImpl session = buildSession(rule, "LoanApplication", LoanFact.class,
                Collections.singletonList(new VarDef("age", Datatype.Integer, Act.In)));
        LoanFact fact = new LoanFact();
        fact.age = 15;
        session.insert(fact);
        session.fireRules();

        // Then 不 fire
        assertThat(fired).isFalse();
    }
}
