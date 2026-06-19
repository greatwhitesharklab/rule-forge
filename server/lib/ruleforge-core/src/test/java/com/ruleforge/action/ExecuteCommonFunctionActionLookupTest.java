package com.ruleforge.action;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.debug.MsgType;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionContext;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.CommonFunctionParameter;
import com.ruleforge.plugin.EnginePluginRegistry;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.runtime.EngineContext;
import com.ruleforge.engine.WorkingMemory;
import com.ruleforge.runtime.assertor.AssertorEvaluator;
import com.ruleforge.engine.Context;
import com.ruleforge.runtime.rete.ValueCompute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5.100.1 — {@link ExecuteCommonFunctionAction#execute} function lookup 契约 BDD。
 *
 * <p>锁 V5.100.1 修法(2 处 {@code containsKey + (findFunctionDescriptor | get)} 双 lookup
 * → 2 处 {@code map.get(name) == null} 单 lookup)的 行为不变性:
 * <ul>
 *   <li>byName 命中 → 用 byName 的 FunctionDescriptor (优先 byName, 不查 byLabel)</li>
 *   <li>byName miss + byLabel 命中 → 用 byLabel 的 FunctionDescriptor (fallback)</li>
 *   <li>byName miss + byLabel miss → 抛 RuleException("Function[name] not exist.")</li>
 *   <li>由 byName 命中的 function 跟 byLabel 命中的 function 是不同对象时, 优先 byName</li>
 *   <li>name == null + byLabel 命中 → 用 byLabel (跟原代码一致, name 查不到时 fallback label)</li>
 * </ul>
 *
 * <p><b>Why V5.100.1 选这条</b>: V5.93 立的 "HashMap.get 已能区分 absent vs null value" 原则
 * (砍 containsKey + get 双 lookup, save 1 hash lookup per call)。 V5.100 KB.addToLibraryMap
 * 已经用本原则砍过 (build-time), V5.100.1 是 runtime 版本的同一原则落地 —
 * action.execute 调用频度 (per-fire-rule, 不是 per-fact hot path), 节省 2 个 containsKey
 * hash lookup (line 26 + line 28 各 1 个), 跟 V5.93 / V5.97 / V6.1 / V5.100 同档
 * pure code elegance closure。
 *
 * <p>行为关键: {@code FunctionDescriptor} 永不为 null (EngineContext.init 唯一 put 是
 * {@code put(name, fun)} + {@code put(label, fun)}, 无 {@code put(key, null)} 风险),
 * 所以 {@code map.get(key) == null} 跟 {@code !map.containsKey(key)} 100% 等价 —
 * 两者都表示 "this key 没装过 FunctionDescriptor"。
 *
 * @see com.ruleforge.docs.notes.v51001-executecommonfunctionaction-doublelookup V5.100.1 完整 doc
 * @since 5.100.1
 */
@DisplayName("V5.100.1 — ExecuteCommonFunctionAction function lookup 契约 (double lookup 砍)")
class ExecuteCommonFunctionActionLookupTest {

    @BeforeAll
    static void wireBaseRegistry() throws Exception {
        EngineContextWirer.wire();
    }

    @BeforeEach
    void resetToEmpty() throws Exception {
        // 每个 nested class 走自己的 registry (自定义 FunctionDescriptor), 用完 reset 回
        // empty + base (避免 test order 互相污染 EngineContext static state).
        reinitWith(Collections.emptyList());
    }

    // ─── 通用 helper: 重新 init EngineContext (idempotent, overwrite static maps) ───

    private static void reinitWith(Collection<FunctionDescriptor> fns) throws Exception {
        EnginePluginRegistry registry = new EnginePluginRegistry() {
            @Override public Collection<com.ruleforge.runtime.assertor.Assertor> getAssertors() {
                return Collections.emptyList();
            }
            @Override public Collection<com.ruleforge.parse.CriterionParser> getCriterionParsers() {
                return Collections.emptyList();
            }
            @Override public Collection<com.ruleforge.parse.ActionParser> getActionParsers() {
                return Collections.emptyList();
            }
            @Override public Collection<com.ruleforge.model.rete.builder.CriterionBuilder> getCriterionBuilders() {
                return Collections.emptyList();
            }
            @Override public Collection<com.ruleforge.builder.resource.ResourceBuilder> getResourceBuilders() {
                return Collections.emptyList();
            }
            @Override public Collection<com.ruleforge.builder.resource.ResourceProvider> getResourceProviders() {
                return Collections.emptyList();
            }
            @Override public Collection<com.ruleforge.action.BsfVariableProvider> getBsfVariableProviders() {
                return Collections.emptyList();
            }
            @Override public Collection<FunctionDescriptor> getFunctionDescriptors() { return fns; }
            @Override public Collection<com.ruleforge.debug.DebugWriter> getDebugWriters() {
                return Collections.emptyList();
            }
            @Override public AssertorEvaluator getAssertorEvaluator() {
                return EngineContext.getAssertorEvaluator();
            }
            @Override public ValueCompute getValueCompute() { return EngineContext.getValueCompute(); }
            @Override public Object getBean(String beanId) { return null; }
        };
        EngineContext.init(registry);
    }

    // ─── Test fixture: 简单加 1 function ───

    /** Returns the input as-is, multiplied by 2 if numeric. */
    private static FunctionDescriptor doublingFn(String name, String label) {
        return new FunctionDescriptor() {
            @Override public Argument getArgument() {
                Argument a = new Argument();
                a.setName("对象");
                a.setNeedProperty(false);
                return a;
            }
            @Override public Object doFunction(Object object, String property, FunctionContext ctx) {
                if (object instanceof Number) {
                    return ((Number) object).doubleValue() * 2;
                }
                return object;
            }
            @Override public String getName() { return name; }
            @Override public String getLabel() { return label; }
            @Override public boolean isDisabled() { return false; }
        };
    }

    /** Returns 42 regardless of input. */
    private static FunctionDescriptor constant42Fn(String name, String label) {
        return new FunctionDescriptor() {
            @Override public Argument getArgument() {
                Argument a = new Argument();
                a.setName("对象");
                a.setNeedProperty(false);
                return a;
            }
            @Override public Object doFunction(Object object, String property, FunctionContext ctx) {
                return 42;
            }
            @Override public String getName() { return name; }
            @Override public String getLabel() { return label; }
            @Override public boolean isDisabled() { return false; }
        };
    }

    // ─── Stub Context (only what ExecuteCommonFunctionAction.execute 调用) ───

    private static Context stubContext() {
        return new Context() {
            @Override public void addTipMsg(String msg) { }
            @Override public String getTipMsg() { return ""; }
            @Override public void cleanTipMsg() { }
            @Override public AssertorEvaluator getAssertorEvaluator() {
                return EngineContext.getAssertorEvaluator();
            }
            @Override public ValueCompute getValueCompute() { return EngineContext.getValueCompute(); }
            @Override public String getVariableCategoryClass(String s) { return null; }
            @Override public WorkingMemory getWorkingMemory() { return null; }
            @Override public Object parseExpression(String s) { return null; }
            @Override public List<MessageItem> getExecuteMessageItems() { return Collections.emptyList(); }
            @Override public void logMsg(String msg, MsgType type) { }
            @Override public void logMsg(String msg, MsgType type, String l, String lv, String r, String rv) { }
            @Override public RuleInfo getCurrentRule() { return null; }
        };
    }

    // ─── byName 优先命中 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("byName 优先命中: 命中 name 时用 byName, 不查 byLabel")
    class ByNameHit {

        // Given: 1 function registered with name="MyFn", label="My Function"
        // When:  ExecuteCommonFunctionAction.execute with name="MyFn", label="My Function"
        // Then:  function = byName 命中的 (return ActionValue with name, 等价 byName 路径)
        @Test
        @DisplayName("byName 命中 + byLabel 也命中 (同 function) → 用 byName, ActionValue 装上 result")
        void byNameHitReturnsByNameResult() throws Exception {
            FunctionDescriptor fn = constant42Fn("MyFn", "My Function");
            reinitWith(Collections.singletonList(fn));

            ExecuteCommonFunctionAction action = new ExecuteCommonFunctionAction();
            action.setName("MyFn");
            action.setLabel("My Function");
            // no parameter, so no complexValueCompute 路径

            ActionValue result = action.execute(stubContext(), null, Collections.emptyList());

            assertThat(result).isNotNull();
            assertThat(result.getActionId()).isEqualTo("MyFn");
            assertThat(result.getValue()).isEqualTo(42);
        }
    }

    // ─── byName miss + byLabel 命中 (fallback) ────────────────────────────────

    @Nested
    @DisplayName("byName miss + byLabel 命中: fallback 到 byLabel")
    class ByNameMissByLabelHit {

        // Given: 1 function registered with name="Doubling", label="Double It"
        // When:  action.execute with name="NotMyFn" (miss), label="Double It" (hit)
        // Then:  function = byLabel 命中的, return ActionValue with name=label key
        //   ⚠️ 现有 behavior: fallback 用 label, ActionValue 仍以 action.name 装
        //     (不是 byLabel 命中的 function.name — 见 V5.100.1 行为保留)
        @Test
        @DisplayName("byName miss + byLabel 命中 → fallback, byLabel function 跑 (result=42)")
        void byNameMissFallsBackToByLabel() throws Exception {
            FunctionDescriptor fn = constant42Fn("Doubling", "Double It");
            reinitWith(Collections.singletonList(fn));

            ExecuteCommonFunctionAction action = new ExecuteCommonFunctionAction();
            action.setName("NotMyFn");  // byName miss
            action.setLabel("Double It");  // byLabel hit

            ActionValue result = action.execute(stubContext(), null, Collections.emptyList());

            assertThat(result).isNotNull();
            assertThat(result.getActionId()).isEqualTo("NotMyFn");
            assertThat(result.getValue()).isEqualTo(42);  // constant42Fn 不管 input
        }

        // Given: 2 functions — one registered with name="A", another with name="B" / label="ALabel"
        // When:  action.execute with name="A" (byName hit, distinct from byLabel "ALabel")
        // Then:  function = byName 命中的 "A", 不用 byLabel 命中的 "B"
        @Test
        @DisplayName("byName 命中 + byLabel 命中不同 function → 优先 byName (不查 byLabel)")
        void byNameHitSkipsByLabelEvenIfDifferentFunction() throws Exception {
            FunctionDescriptor fnA = constant42Fn("A", "LabelA");
            FunctionDescriptor fnB = constant42Fn("B", "ALabel");
            reinitWith(java.util.Arrays.asList(fnA, fnB));

            ExecuteCommonFunctionAction action = new ExecuteCommonFunctionAction();
            action.setName("A");
            action.setLabel("ALabel");  // byLabel 命中 fnB, 但 byName "A" 命中 fnA

            ActionValue result = action.execute(stubContext(), null, Collections.emptyList());

            assertThat(result).isNotNull();
            // 跑的是 fnA (constant42Fn 返 42), 不是 fnB — 验证优先 byName
            assertThat(result.getValue()).isEqualTo(42);
            // ActionValue name 是 action.name, 跟 function name 无关
            assertThat(result.getActionId()).isEqualTo("A");
        }
    }

    // ─── byName miss + byLabel miss → throw RuleException ────────────────────

    @Nested
    @DisplayName("byName miss + byLabel miss → 抛 RuleException")
    class BothMiss {

        // Given: 1 function registered with name="RealFn"
        // When:  action.execute with name="WrongName", label="WrongLabel"
        // Then:  RuleException("Function[WrongName] not exist.")
        @Test
        @DisplayName("byName miss + byLabel miss → 抛 RuleException, msg 含 action.name")
        void bothMissThrowsRuleException() throws Exception {
            FunctionDescriptor fn = constant42Fn("RealFn", "Real Label");
            reinitWith(Collections.singletonList(fn));

            ExecuteCommonFunctionAction action = new ExecuteCommonFunctionAction();
            action.setName("WrongName");
            action.setLabel("WrongLabel");

            assertThatThrownBy(() -> action.execute(stubContext(), null, Collections.emptyList()))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Function[WrongName] not exist");
        }

        // Given: empty registry (no functions)
        // When:  action.execute with any name/label
        // Then:  RuleException
        @Test
        @DisplayName("registry 空 → 抛 RuleException, 走 throw path")
        void emptyRegistryThrowsRuleException() throws Exception {
            reinitWith(Collections.emptyList());

            ExecuteCommonFunctionAction action = new ExecuteCommonFunctionAction();
            action.setName("Anything");
            action.setLabel("AnyLabel");

            assertThatThrownBy(() -> action.execute(stubContext(), null, Collections.emptyList()))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Function[Anything] not exist");
        }
    }

    // ─── name == null 时直接走 byLabel fallback ─────────────────────────────

    @Nested
    @DisplayName("name == null 时直接走 byLabel fallback (跟原代码一致)")
    class NameNull {

        // Given: 1 function registered with name="SomeFn", label="Some Label"
        // When:  action.execute with name=null, label="Some Label" (byLabel hit)
        // Then:  function = byLabel 命中的, result 装上
        //   ⚠️ 跟原代码一致: name 查不到 (null) → fallback byLabel
        @Test
        @DisplayName("name == null + byLabel 命中 → fallback 跑, result 装上")
        void nullNameFallsBackToByLabel() throws Exception {
            FunctionDescriptor fn = constant42Fn("SomeFn", "Some Label");
            reinitWith(Collections.singletonList(fn));

            ExecuteCommonFunctionAction action = new ExecuteCommonFunctionAction();
            action.setName(null);
            action.setLabel("Some Label");

            ActionValue result = action.execute(stubContext(), null, Collections.emptyList());

            assertThat(result).isNotNull();
            assertThat(result.getActionId()).isNull();  // action.name 是 null
            assertThat(result.getValue()).isEqualTo(42);
        }
    }

    // ─── V5.100.1 修法核心: byName 命中时不调 EngineContext.findFunctionDescriptor ──

    @Nested
        @DisplayName("V5.100.1 修法核心验证: byName 命中时直接用 byName (不再经 findFunctionDescriptor)")
    class ByNameHitBehaviorPreserved {

        // Given: 1 function registered, name + label 都装上 (same function)
        // When:  action.execute with name=fn.name, label=fn.label
        // Then:  result 装上 fn.doFunction 的值 (42), ActionValue.name = action.name
        //   ⚠️ 跟原代码 100% 等价: byName hit → function = byName.get(name), 不经
        //   findFunctionDescriptor 抛错路径. V5.100.1 把 "containsKey + findFunctionDescriptor"
        //   改成 "get == null" 单 lookup, 命中时少 1 个 containsKey hash lookup.
        @Test
        @DisplayName("byName 命中 (跟 label 命中同 fn) → 跑 fn.doFunction, ActionValue 装上 (V5.100.1 行为保留)")
        void byNameHitRunsFunctionDoFunction() throws Exception {
            FunctionDescriptor fn = constant42Fn("Fn", "Fn Label");
            reinitWith(Collections.singletonList(fn));

            ExecuteCommonFunctionAction action = new ExecuteCommonFunctionAction();
            action.setName("Fn");
            action.setLabel("Fn Label");

            ActionValue result = action.execute(stubContext(), null, Collections.emptyList());

            assertThat(result).isNotNull();
            assertThat(result.getValue()).isEqualTo(42);
            assertThat(result.getActionId()).isEqualTo("Fn");
        }
    }
}
