package com.ruleforge.runtime;

import com.ruleforge.exception.RuleException;
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
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.runtime.rete.ReteInstance;
import com.ruleforge.runtime.rete.ReteInstanceUnit;
import com.ruleforge.rete.test.EngineContextWirer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5.100.6 — {@link KnowledgeSessionImpl#activeRule(String, String)} +
 * {@link KnowledgeSessionImpl#activeAgendaGroup(String)} group-existence 契约 BDD。
 *
 * <p>锁 V5.100.6 修法 (2 处 {@code containsKey + get} 双 lookup → {@code get == null} 单 lookup)
 * 的行为不变性:
 * <ul>
 *   <li>group 不存在 (map 无 key) → 抛 RuleException("...group [...] not exist!")</li>
 *   <li>group 存在 (map 有 key, 即便空 list) → 不抛 (loop no-op on empty list, clean())</li>
 *   <li>activeRule / activeAgendaGroup 两个方法同档 (同 V5.93 模式)</li>
 * </ul>
 *
 * <p><b>Why V5.100.6 选这条</b>: V5.93 原则系列 (V5.100.0-5) 的最后一处安全 containsKey+get。
 * value 永为 {@code List<ReteInstanceUnit>} (非 null, Rete.buildGroupRetesInstance 用
 * {@code computeIfAbsent(k -> new ArrayList<>())} 装入, 无 put(key, null) 风险)。 低频
 * (用户显式激活 rule group, 不是 per-fact hot path), JFR noise level 预期。
 *
 * <p><b>不动内层 labeled loop</b>: activeRule 的 {@code Iterator var4 + label42} + activeAgendaGroup
 * 的 {@code Iterator var3 + while(true) do-while} 是 V5.96 显式 skip 的 state machine (runtime
 * hot path, 需独立 characterization test 投资). V5.100.6 只砍 containsKey, 内层 100% 保留。
 *
 * <p><b>Test fixture</b>: 用最小 KnowledgePackage (Foo rule, 跟 SingleRuleFiresBDD 同) 构造
 * session (initContext 设好 evaluationContext), 再用反射往 activationReteInstancesMap /
 * agendaReteInstancesMap 装测试 entry (生产时这俩 map 由 evaluationRete 内 putAll 填)。
 *
 * @see com.ruleforge.docs.notes.v51006-knowledgesessionimpl-activegroup-containskey V5.100.6 完整 doc
 * @since 5.100.6
 */
@DisplayName("V5.100.6 — KnowledgeSessionImpl activeRule/activeAgendaGroup group-existence 契约")
class KnowledgeSessionImplActiveGroupTest {

    private KnowledgeSessionImpl session;
    private Map<String, List<ReteInstanceUnit>> activationMap;
    private Map<String, List<ReteInstanceUnit>> agendaMap;
    private Rete rete;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        // ReteBuilder.buildRete 需要 criterionBuilders (CriteriaBuilder + AndBuilder) 装配
        EngineContextWirer.wire();
    }

    @BeforeEach
    void setUp() throws Exception {
        // 最小 KnowledgePackage: 1 rule Foo(name == "alice") — 只为让 KnowledgeSessionImpl 构造
        // 跑通 initContext (设 evaluationContext), active* 测试不依赖 rule 内容.
        Rule r = new Rule();
        r.setName("R1");
        r.setSalience(0);
        And and = new And();
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory("Foo");
        part.setVariableName("name");
        part.setVariableLabel("name");
        part.setDatatype(Datatype.String);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue();
        sv.setContent("alice");
        c.setValue(sv);
        and.addCriterion(c);
        Lhs lhs = new Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);

        VariableLibrary lib = new VariableLibrary();
        VariableCategory cat = new VariableCategory();
        cat.setName("Foo");
        cat.setType(CategoryType.Clazz);
        cat.setClazz(Foo.class.getName());
        Variable v = new Variable();
        v.setName("name");
        v.setLabel("name");
        v.setType(Datatype.String);
        v.setAct(Act.In);
        cat.setVariables(Collections.singletonList(v));
        lib.addVariableCategory(cat);
        ResourceLibrary rl = new ResourceLibrary(Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());

        rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
        KnowledgePackage kp = new KnowledgeBase(rete).getKnowledgePackage();
        session = new KnowledgeSessionImpl(kp);

        // 反射拿 session 的 2 个 map (生产由 evaluationRete 内 putAll 填, 这里手动装测试 entry)
        activationMap = getMapField("activationReteInstancesMap");
        agendaMap = getMapField("agendaReteInstancesMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<ReteInstanceUnit>> getMapField(String name) throws Exception {
        Field f = KnowledgeSessionImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        return (Map<String, List<ReteInstanceUnit>>) f.get(session);
    }

    // ─── activeRule: group 不存在 → 抛 ─────────────────────────────────────

    @Nested
    @DisplayName("activeRule: activation group 不存在 → 抛 RuleException")
    class ActiveRuleGroupNotExist {

        // Given: session, activationReteInstancesMap 空 (无任何 group)
        // When:  activeRule("nonexistent", "R1")
        // Then:  RuleException("Activation group [nonexistent] not exist!")
        @Test
        @DisplayName("group 不存在 → 抛 RuleException, msg 含 group name")
        void groupNotExistThrows() {
            assertThatThrownBy(() -> session.activeRule("nonexistent", "R1"))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Activation group [nonexistent] not exist");
        }
    }

    // ─── activeRule: group 存在 (空 list) → 不抛 ───────────────────────────

    @Nested
    @DisplayName("activeRule: activation group 存在 (空 list) → 不抛 (loop no-op + clean)")
    class ActiveRuleGroupExist {

        // Given: activationReteInstancesMap 装 "g1" → 空 list
        // When:  activeRule("g1", "R1")
        // Then:  不抛 (空 list → Iterator var4 loop no-op → evaluationContext.clean() 跑通)
        @Test
        @DisplayName("group 存在 + 空 list → 不抛")
        void groupExistEmptyListNoThrow() {
            activationMap.put("g1", new ArrayList<>());

            assertThatCode(() -> session.activeRule("g1", "R1"))
                    .doesNotThrowAnyException();
        }

        // Given: activationReteInstancesMap 装 "g1" → 空 list, "g2" 不在 map
        // When:  activeRule("g2", "R1")
        // Then:  抛 RuleException (即便 "g1" 存在, 查的是 "g2")
        @Test
        @DisplayName("g1 存在但查 g2 → 抛 (查的 key 决定)")
        void differentGroupStillThrows() {
            activationMap.put("g1", new ArrayList<>());

            assertThatThrownBy(() -> session.activeRule("g2", "R1"))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Activation group [g2] not exist");
        }
    }

    // ─── activeAgendaGroup: group 不存在 → 抛 ──────────────────────────────

    @Nested
    @DisplayName("activeAgendaGroup: agenda group 不存在 → 抛 RuleException")
    class ActiveAgendaGroupNotExist {

        // Given: session, agendaReteInstancesMap 空
        // When:  activeAgendaGroup("nonexistent")
        // Then:  RuleException("Agenda group [nonexistent] not exist!")
        @Test
        @DisplayName("group 不存在 → 抛 RuleException")
        void groupNotExistThrows() {
            assertThatThrownBy(() -> session.activeAgendaGroup("nonexistent"))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Agenda group [nonexistent] not exist");
        }
    }

    // ─── activeAgendaGroup: group 存在 (空 list) → 不抛 ────────────────────

    @Nested
    @DisplayName("activeAgendaGroup: agenda group 存在 (空 list) → 不抛")
    class ActiveAgendaGroupExist {

        // Given: agendaReteInstancesMap 装 "ag1" → 空 list
        // When:  activeAgendaGroup("ag1")
        // Then:  不抛 (空 list → while(true) do-while !hasNext 立即 return, 不进 rete enter)
        @Test
        @DisplayName("group 存在 + 空 list → 不抛")
        void groupExistEmptyListNoThrow() {
            agendaMap.put("ag1", new ArrayList<>());

            assertThatCode(() -> session.activeAgendaGroup("ag1"))
                    .doesNotThrowAnyException();
        }

        // Given: agendaReteInstancesMap 装 "ag1" → 空 list
        // When:  activeAgendaGroup("ag2") (不在 map)
        // Then:  抛 RuleException
        @Test
        @DisplayName("ag1 存在但查 ag2 → 抛")
        void differentGroupStillThrows() {
            agendaMap.put("ag1", new ArrayList<>());

            assertThatThrownBy(() -> session.activeAgendaGroup("ag2"))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Agenda group [ag2] not exist");
        }
    }

    // ─── V5.100.6 修法核心: 两个 map 互不干扰 ───────────────────────────────

    @Nested
    @DisplayName("V5.100.6 修法核心: activation map + agenda map 互不干扰")
    class MapsIsolated {

        // Given: activationMap 装 "shared" → 空 list, agendaMap 空
        // When:  activeAgendaGroup("shared") (查 agendaMap, 不是 activationMap)
        // Then:  抛 RuleException (agendaMap 没 "shared", 即便 activationMap 有)
        @Test
        @DisplayName("activationMap 有 \"shared\" 但 agendaMap 没 → activeAgendaGroup 仍抛")
        void activationEntryDoesNotSatisfyAgendaLookup() {
            activationMap.put("shared", new ArrayList<>());

            assertThatThrownBy(() -> session.activeAgendaGroup("shared"))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Agenda group [shared] not exist");
        }

        // Given: agendaMap 装 "shared" → 空 list, activationMap 空
        // When:  activeRule("shared", "R1") (查 activationMap)
        // Then:  抛 RuleException (activationMap 没 "shared")
        @Test
        @DisplayName("agendaMap 有 \"shared\" 但 activationMap 没 → activeRule 仍抛")
        void agendaEntryDoesNotSatisfyActivationLookup() {
            agendaMap.put("shared", new ArrayList<>());

            assertThatThrownBy(() -> session.activeRule("shared", "R1"))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Activation group [shared] not exist");
        }
    }

    // ─── V5.100.7: 非空 list 真实 ReteInstanceUnit 锁 loop body 行为 ────────

    @Nested
    @DisplayName("V5.100.7: 非空 list 真实 ReteInstanceUnit → 锁 active* loop body 行为")
    class NonEmptyLoopBody {

        // Build a fresh ReteInstanceUnit from the Foo rete (valid: 无 effective/expires 日期
        // → isWithinValidPeriod=true, isNotYetEffective=false, isExpired=false).
        private ReteInstanceUnit unit(String ruleName) {
            ReteInstance ins = rete.newReteInstance();
            return new ReteInstanceUnit(ins, ruleName);
        }

        // Given: activationMap 装 "g1" → [unit(ruleName="R1")] (valid), allFactsList 空
        // When:  activeRule("g1", "R1") — unit 匹配 + 有效
        // Then:  不抛 (loop body 跑: rete enter over 空 allFactsList no-op + clean())
        //   ⚠️ V5.100.7 flatten (Iterator var4 + label42 → enhanced for) 保留这个 loop body.
        @Test
        @DisplayName("activeRule 匹配 + 有效 unit → 跑 loop body, 不抛 (V5.100.7 flatten 保留)")
        void activeRuleMatchingValidUnitRunsLoopBody() {
            activationMap.put("g1", Collections.singletonList(unit("R1")));

            assertThatCode(() -> session.activeRule("g1", "R1"))
                    .doesNotThrowAnyException();
        }

        // Given: activationMap 装 "g1" → [unit(ruleName="R1")], allFactsList 空
        // When:  activeRule("g1", "OTHER") — ruleName 不匹配
        // Then:  不抛 (loop 跑遍 unit, 但 ruleName 不匹配 → 内层 block skip → clean())
        @Test
        @DisplayName("activeRule ruleName 不匹配 → loop 遍历但 skip 内层 block, 不抛")
        void activeRuleNonMatchingRuleNameSkipsInnerBlock() {
            activationMap.put("g1", Collections.singletonList(unit("R1")));

            assertThatCode(() -> session.activeRule("g1", "OTHER"))
                    .doesNotThrowAnyException();
        }

        // Given: activationMap 装 "g1" → 3 unit (R1 valid + R2 valid + R3 valid)
        // When:  activeRule("g1", "R2") — 只有 R2 匹配
        // Then:  不抛 (loop 遍历 3 unit, 只对 R2 跑 rete enter)
        @Test
        @DisplayName("activeRule 3 unit 只匹配 1 个 → loop 遍历全, 只跑匹配的, 不抛")
        void activeRuleMultipleUnitsOnlyMatchingProcessed() {
            List<ReteInstanceUnit> units = new ArrayList<>();
            units.add(unit("R1"));
            units.add(unit("R2"));
            units.add(unit("R3"));
            activationMap.put("g1", units);

            assertThatCode(() -> session.activeRule("g1", "R2"))
                    .doesNotThrowAnyException();
        }

        // Given: agendaMap 装 "ag1" → [unit(valid)], allFactsList 空
        // When:  activeAgendaGroup("ag1")
        // Then:  不抛 (loop body 跑: rete enter over 空 allFactsList + "__*__" no-op)
        //   ⚠️ V5.100.7 flatten (while(true) do-while-find-valid → enhanced for + 2 continue)
        //   保留: skip not-effective + skip expired + 跑有效 unit.
        @Test
        @DisplayName("activeAgendaGroup 有效 unit → 跑 loop body, 不抛 (V5.100.7 flatten 保留)")
        void activeAgendaGroupValidUnitRunsLoopBody() {
            agendaMap.put("ag1", Collections.singletonList(unit("R1")));

            assertThatCode(() -> session.activeAgendaGroup("ag1"))
                    .doesNotThrowAnyException();
        }

        // Given: agendaMap 装 "ag1" → 3 unit 全 valid (无日期), allFactsList 空
        // When:  activeAgendaGroup("ag1")
        // Then:  不抛 (loop 遍历 3 unit 全有效, 各跑 rete enter no-op)
        @Test
        @DisplayName("activeAgendaGroup 3 unit 全有效 → loop 全跑, 不抛")
        void activeAgendaGroupMultipleValidUnitsAllProcessed() {
            List<ReteInstanceUnit> units = new ArrayList<>();
            units.add(unit("R1"));
            units.add(unit("R2"));
            units.add(unit("R3"));
            agendaMap.put("ag1", units);

            assertThatCode(() -> session.activeAgendaGroup("ag1"))
                    .doesNotThrowAnyException();
        }
    }

    /** Simple POJO for Foo category (跟 SingleRuleFiresBDD.Foo 同). */
    public static class Foo {
        private String name;
        public Foo() {}
        public Foo(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
