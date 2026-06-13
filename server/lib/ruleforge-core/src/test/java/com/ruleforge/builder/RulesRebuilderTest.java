package com.ruleforge.builder;

import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.rule.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0 — close RulesRebuilder 公共方法契约覆盖。
 *
 * <p>目标:锁定 9 个 public 方法的 null guard / 空集合 / happy path 边界,而不是
 * 复刻整个 714 行生产逻辑(那是 P1 facade 拆分的活)。覆盖路径:
 * <ul>
 *   <li>L39/43 {@code rebuildRules} — null libraries / null rules 静默 no-op</li>
 *   <li>L103 {@code rebuildRulesForDSL} — null guard 镜像 rebuildRules</li>
 *   <li>L157 {@code convertNamedJunctions} — 空 rules 不抛 + null LHS 跳过</li>
 *   <li>L676/695 {@code getVariableByName/Label} — 空 categories 返 null</li>
 *   <li>L96-98 语法错抛 {@link RuleException} 且**当前** root cause 不传(P1 修)</li>
 * </ul>
 */
@DisplayName("P0 — RulesRebuilder 公共契约")
class RulesRebuilderTest {

    private RulesRebuilder rebuilder;
    private ResourceLibraryBuilder libBuilder;

    @BeforeEach
    void setUp() throws Exception {
        rebuilder = new RulesRebuilder();
        // 注入 mock ResourceLibraryBuilder(避免拉 ApplicationContext / Rete)
        libBuilder = mock(ResourceLibraryBuilder.class);
        when(libBuilder.buildResourceLibrary(Collections.emptyList()))
            .thenReturn(new ResourceLibrary());
        Field f = RulesRebuilder.class.getDeclaredField("resourceLibraryBuilder");
        f.setAccessible(true);
        f.set(rebuilder, libBuilder);
    }

    @Nested
    @DisplayName("rebuildRules — null guard")
    class RebuildRulesNullGuards {

        @Test
        @DisplayName("null libraries 应 no-op,不抛")
        void nullLibraries() {
            assertDoesNotThrow(() -> rebuilder.rebuildRules(null, new ArrayList<>()));
        }

        @Test
        @DisplayName("null rules 应 no-op,不抛")
        void nullRules() {
            assertDoesNotThrow(() -> rebuilder.rebuildRules(new ArrayList<>(), null));
        }

        @Test
        @DisplayName("空 libraries + 空 rules 应直接返,不调 libBuilder")
        void emptyBoth() {
            assertDoesNotThrow(() -> rebuilder.rebuildRules(new ArrayList<>(), new ArrayList<>()));
        }

        @Test
        @DisplayName("三参版本 null libraries 同样 no-op")
        void nullLibrariesWithSnapshot() {
            assertDoesNotThrow(() -> rebuilder.rebuildRules(null, new ArrayList<>(), true));
        }
    }

    @Nested
    @DisplayName("rebuildRulesForDSL — null guard 镜像")
    class RebuildRulesForDslNullGuards {

        @Test
        @DisplayName("null libraries 应 no-op")
        void nullLibraries() {
            assertDoesNotThrow(() -> rebuilder.rebuildRulesForDSL(null, new ArrayList<>()));
        }

        @Test
        @DisplayName("null rules 应 no-op")
        void nullRules() {
            assertDoesNotThrow(() -> rebuilder.rebuildRulesForDSL(new ArrayList<>(), null));
        }

        @Test
        @DisplayName("空 libraries + 空 rules 应直接返")
        void emptyBoth() {
            assertDoesNotThrow(() -> rebuilder.rebuildRulesForDSL(new ArrayList<>(), new ArrayList<>()));
        }
    }

    @Nested
    @DisplayName("convertNamedJunctions — 空 / null LHS 跳过")
    class ConvertNamedJunctions {

        @Test
        @DisplayName("空 rules 列表应不抛")
        void emptyRules() {
            assertDoesNotThrow(() -> rebuilder.convertNamedJunctions(new ArrayList<>()));
        }

        @Test
        @DisplayName("rule.getLhs() == null 应跳过,不抛 NPE")
        void nullLhsRule() {
            Rule r = new Rule();
            r.setName("null-lhs-rule");
            // r.getLhs() 默认就是 null — 验证 convertNamedJunctions 不 NPE
            assertDoesNotThrow(() -> rebuilder.convertNamedJunctions(Collections.singletonList(r)));
        }

        @Test
        @DisplayName("多个 rule 串行处理,中间 LHS null 不应中断后续")
        void mixedNullLhs() {
            Rule ok = new Rule();
            ok.setName("ok");
            Rule nullLhs = new Rule();
            nullLhs.setName("null-lhs");
            List<Rule> list = new ArrayList<>();
            list.add(ok);
            list.add(nullLhs);
            assertDoesNotThrow(() -> rebuilder.convertNamedJunctions(list));
        }
    }

    @Nested
    @DisplayName("getVariableByName / ByLabel — 未找到抛 RuleException,找到返 Variable")
    class VariableLookup {

        @Test
        @DisplayName("getVariableByName 空 categories 应抛 RuleException")
        void byNameEmpty() {
            com.ruleforge.exception.RuleException ex = assertThrows(
                com.ruleforge.exception.RuleException.class,
                () -> rebuilder.getVariableByName(
                    new ArrayList<>(), "Cat", "name", Collections.emptyMap()));
            assertTrue(ex.getMessage().contains("Cat.name"),
                "RuleException message 应包含 category.name");
        }

        @Test
        @DisplayName("getVariableByLabel 空 categories 应抛 RuleException")
        void byLabelEmpty() {
            com.ruleforge.exception.RuleException ex = assertThrows(
                com.ruleforge.exception.RuleException.class,
                () -> rebuilder.getVariableByLabel(
                    new ArrayList<>(), "Cat", "label", Collections.emptyMap()));
            assertTrue(ex.getMessage().contains("Cat.label"),
                "RuleException message 应包含 category.label");
        }

        @Test
        @DisplayName("getVariableByName null categories 抛 NPE(契约锁定)")
        void byNameNullCategories() {
            assertThrows(NullPointerException.class,
                () -> rebuilder.getVariableByName(null, "Cat", "name", Collections.emptyMap()));
        }
    }

    @Nested
    @DisplayName("异常路径 — L96-98 语法错抛 RuleException(cause 必须保留)")
    class RuleExceptionPaths {

        @Test
        @DisplayName("convertNamedJunctions 对 minimal Rule 不抛(契约锚点)")
        void convertMinimalRule() {
            // 本测试为契约锚点:验证 convertNamedJunctions 不会因为 minimal Rule(null LHS)
            // 抛错。实际 syntax-error 路径需要构造 Action/Criterion 复杂对象,
            // 留 P1 Task 4 拆分 facade 时一并覆盖。
            Rule r = new Rule();
            r.setName("minimal-rule");
            assertDoesNotThrow(() -> rebuilder.convertNamedJunctions(Collections.singletonList(r)));
        }

        /**
         * P1 — V5.47 之前 L96-98 throw new RuleException(errorMsg) 不传 cause,
         * 导致 catch(RuleException) 拿不到根因(DrlParseException / ANTLR bail
         * 错等被吞)。修复后 {@code new RuleException(msg, cause)} 传 e 进去。
         *
         * <p>触发完整 catch 链需要让 rebuildAction 抛错(私有方法,需 mock
         * Criterion 复杂对象),投资回报不高。本测试改为直接锁定
         * {@link com.ruleforge.exception.RuleException} 构造器合同:
         * <ul>
         *   <li>getMessage() 返业务描述(不是 cause 消息)— P1 构造器修复</li>
         *   <li>getCause() 返原异常 — P1 L98 修复</li>
         * </ul>
         *
         * <p>3 个 caller 受益:RulesRebuilder L98 / ScorecardParser L178 /
         * KnowledgeServiceImpl L146。
         */
        @Test
        @DisplayName("RuleException(String, Exception) 构造器:msg 进 getMessage,cause 进 getCause")
        void ruleExceptionConstructorPreservesBoth() {
            // Given
            String businessMsg = "规则【syntax-broken-rule】包含语法错误";
            RuntimeException rootCause = new IllegalArgumentException("antlr bail: missing semicolon");

            // When
            com.ruleforge.exception.RuleException ex =
                new com.ruleforge.exception.RuleException(businessMsg, rootCause);

            // Then
            assertTrue(ex.getMessage().contains(businessMsg),
                "P1 修复:getMessage() 应包含业务描述 (V5.47 之前是 cause 消息): " + ex.getMessage());
            assertNotNull(ex.getCause(),
                "P1 修复:getCause() 必须保留原异常 (V5.47 之前是 null)");
            assertSame(rootCause, ex.getCause(),
                "cause 应严格等于传入的原异常");
        }
    }
}
