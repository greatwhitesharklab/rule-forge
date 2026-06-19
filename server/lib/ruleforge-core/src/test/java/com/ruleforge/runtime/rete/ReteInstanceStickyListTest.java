package com.ruleforge.runtime.rete;
import com.ruleforge.engine.Path;

import com.ruleforge.builder.KnowledgeBase;
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
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.runtime.KnowledgePackage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.92 — {@link ReteInstance} 预计算 flat sticky activity 列表契约 BDD。
 *
 * <p>锁 V5.92 重构 {@link ReteInstance#resetStickyStateOnly()} 的契约:
 * <ul>
 *   <li>构造时一次性 walk 整个 activity 子树,生成
 *       {@code List<AbstractActivity> stickyActivities} flat 列表</li>
 *   <li>列表包含所有有 sticky state 的 activity(CriteriaActivity, AndActivity,
 *       OrActivity)— 这些的 {@code reset()} 会清 {@code passed} flag</li>
 *   <li>列表<b>不</b>包含 {@link TerminalActivity}(其 {@code reset()} 是 no-op,
 *       跳过节省 reset() 调用 + 路径遍历)</li>
 *   <li>{@code resetStickyStateOnly()} 走 flat 列表,无递归 + 无 instanceof +
 *       无 {@code Path.getTo()} 虚拟调用</li>
 *   <li>per-fact 节省 ~25ns(8 reset() + 4 递归 + 4 instanceof 压成 4 reset()
 *       + 4 list iter),HotPath per-fact 0.23us → ~0.20us</li>
 * </ul>
 *
 * <p><b>Why V5.92 选这条</b>:V5.83 doc 末尾标 V5.84+ 候选 = 增量 reset
 * (按 fact class);V5.84 实测 reverse-optimization 撤销,因为
 * {@code ObjectTypeActivity.support()} 反射 (Class.isAssignableFrom) 比节省
 * 的 cross-class reset 还慢。V5.92 改"预计算 + flat 列表" — 不走反射,纯
 * 编译期可优化的 List 迭代,跟 V5.86/V5.89/V5.91 一致:audit hot path 找
 * 真正贵的操作,看能不能 pre-compute / cache 掉。
 *
 * <p><b>行为不变性</b>:V5.83 锁定 {@code Path.passed} 不被
 * {@code resetStickyStateOnly()} 清掉(2-pattern join 跨 fact 累积 join 状态
 * 关键),本 BDD 不重复测 — 现有 646 tests 全部 cover,任意行为变更会触发
 * regression。本 BDD 只锁 V5.92 新增的"flat 列表 + 跳过 no-op reset"契约。
 *
 * @see com.ruleforge.docs.notes.v592-flat-sticky-list V5.92 完整 doc
 * @since 5.92
 */
@DisplayName("V5.92 — ReteInstance 预计算 flat sticky 列表")
class ReteInstanceStickyListTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @Nested
    @DisplayName("flat sticky 列表内容")
    class StickyListContents {

        // Given 1 dual class rule (Person AND Address, name=alice street=main)
        // When 构造 ReteInstance
        // Then sticky 列表 = 2 CriteriaActivity + 1 AndActivity(共享) + 0 Terminal = 3 项
        @Test
        @DisplayName("dual class rete 的 sticky 列表 = 3 activity (2 Criteria + 1 共享 And, 无 Terminal)")
        void dualClassReteStickyListHas2CriteriaAnd1SharedAnd() {
            ReteInstance ri = buildDualClassReteInstance();
            List<AbstractActivity> sticky = ri.getStickyActivitiesForTest();
            assertThat(sticky).hasSize(3);
            long criteriaCount = sticky.stream().filter(a -> a instanceof CriteriaActivity).count();
            long andCount = sticky.stream().filter(a -> a instanceof AndActivity).count();
            assertThat(criteriaCount).as("CriteriaActivity 数(2 pattern)").isEqualTo(2);
            assertThat(andCount).as("AndActivity 数(2 pattern 共享 1 个 And node)").isEqualTo(1);
        }

        // Given 任何 ReteInstance
        // When 读 sticky 列表
        // Then 没有 TerminalActivity(reset() 是空,跳过)
        @Test
        @DisplayName("TerminalActivity 不在 sticky 列表 (reset() no-op)")
        void terminalActivityNotInStickyList() {
            ReteInstance ri = buildDualClassReteInstance();
            List<AbstractActivity> sticky = ri.getStickyActivitiesForTest();
            assertThat(sticky).noneMatch(a -> a instanceof TerminalActivity);
        }

        // Given single class rete (1 Person pattern, no join)
        // When 构造 ReteInstance
        // Then sticky 列表 = 1 CriteriaActivity + 1 TerminalActivity 被跳过
        @Test
        @DisplayName("single class rete 的 sticky 列表 = 1 (仅 Criteria, And/Terminal 不存在)")
        void singleClassReteStickyListHas1Criteria() {
            ReteInstance ri = buildSingleClassReteInstance();
            List<AbstractActivity> sticky = ri.getStickyActivitiesForTest();
            assertThat(sticky).hasSize(1);
            assertThat(sticky.get(0)).isInstanceOf(CriteriaActivity.class);
        }
    }

    @Nested
    @DisplayName("resetStickyStateOnly 行为")
    class ResetBehavior {

        // Given ReteInstance 构造完
        // When 多次调 resetStickyStateOnly()
        // Then 行为稳定(无 NPE,无异常)— 锁"flat 列表 immutable + reset 幂等"
        @Test
        @DisplayName("resetStickyStateOnly 可多次调用,行为稳定 (flat 列表 + 幂等)")
        void resetStickyStateOnlyIsIdempotentAndSafe() {
            ReteInstance ri = buildDualClassReteInstance();
            // 调 3 次,无异常
            ri.resetStickyStateOnly();
            ri.resetStickyStateOnly();
            ri.resetStickyStateOnly();
            // sticky 列表本身在 reset 后不变(immutable)
            assertThat(ri.getStickyActivitiesForTest()).hasSize(3);
        }
    }

    // ====== helpers ======

    private ReteInstance buildDualClassReteInstance() {
        Rule r = new Rule();
        r.setName("R1"); r.setSalience(0);
        r.setDebug(false);
        And and = new And();
        and.addCriterion(buildCriteria("Person", "name", "alice"));
        and.addCriterion(buildCriteria("Address", "street", "main"));
        Lhs lhs = new Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);

        ResourceLibrary rl = buildDualClassResourceLibrary();
        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
        KnowledgePackage kp = new KnowledgeBase(rete).getKnowledgePackage();
        return kp.newReteInstance();
    }

    private ReteInstance buildSingleClassReteInstance() {
        Rule r = new Rule();
        r.setName("R1"); r.setSalience(0);
        r.setDebug(false);
        And and = new And();
        and.addCriterion(buildCriteria("Person", "name", "alice"));
        Lhs lhs = new Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);

        ResourceLibrary rl = buildSingleClassResourceLibrary();
        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
        KnowledgePackage kp = new KnowledgeBase(rete).getKnowledgePackage();
        return kp.newReteInstance();
    }

    private Criteria buildCriteria(String varCat, String varName, String value) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(varCat);
        part.setVariableName(varName);
        part.setVariableLabel(varName);
        part.setDatatype(Datatype.String);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue();
        sv.setContent(value);
        c.setValue(sv);
        return c;
    }

    private ResourceLibrary buildDualClassResourceLibrary() {
        VariableLibrary personLib = new VariableLibrary();
        VariableCategory personCat = new VariableCategory();
        personCat.setName("Person");
        personCat.setType(CategoryType.Clazz);
        personCat.setClazz(Person.class.getName());
        Variable nameVar = new Variable();
        nameVar.setName("name"); nameVar.setLabel("name"); nameVar.setType(Datatype.String); nameVar.setAct(Act.In);
        personCat.setVariables(Collections.singletonList(nameVar));
        personLib.addVariableCategory(personCat);

        VariableLibrary addressLib = new VariableLibrary();
        VariableCategory addressCat = new VariableCategory();
        addressCat.setName("Address");
        addressCat.setType(CategoryType.Clazz);
        addressCat.setClazz(Address.class.getName());
        Variable streetVar = new Variable();
        streetVar.setName("street"); streetVar.setLabel("street"); streetVar.setType(Datatype.String); streetVar.setAct(Act.In);
        addressCat.setVariables(Collections.singletonList(streetVar));
        addressLib.addVariableCategory(addressCat);

        List<VariableLibrary> libs = new ArrayList<>();
        libs.add(personLib);
        libs.add(addressLib);
        return new ResourceLibrary(libs, new ArrayList<>(), new ArrayList<>());
    }

    private ResourceLibrary buildSingleClassResourceLibrary() {
        VariableLibrary personLib = new VariableLibrary();
        VariableCategory personCat = new VariableCategory();
        personCat.setName("Person");
        personCat.setType(CategoryType.Clazz);
        personCat.setClazz(Person.class.getName());
        Variable nameVar = new Variable();
        nameVar.setName("name"); nameVar.setLabel("name"); nameVar.setType(Datatype.String); nameVar.setAct(Act.In);
        personCat.setVariables(Collections.singletonList(nameVar));
        personLib.addVariableCategory(personCat);
        return new ResourceLibrary(
            Collections.singletonList(personLib), new ArrayList<>(), new ArrayList<>());
    }

    // ====== POJO (跟 HotPathBenchTest 一致) ======

    public static class Person {
        private final String name;
        public Person(String name) { this.name = name; }
        public String getName() { return name; }
    }

    public static class Address {
        private final String street;
        public Address(String street) { this.street = street; }
        public String getStreet() { return street; }
    }
}
