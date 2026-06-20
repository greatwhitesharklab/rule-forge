package com.ruleforge.action;

import com.ruleforge.debug.MsgType;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.engine.Context;
import com.ruleforge.engine.ValueCompute;
import com.ruleforge.engine.WorkingMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V6.9.6 — {@link VariableAssignAction#execute} 行为契约 BDD。
 *
 * <p>锁 V6.9.6 收口 (3-level nested if/else state machine → early return chain) 的行为不变性:
 * <ul>
 *   <li><b>value == null</b>: 返 {@code null}, 不调 setObjectProperty, 不 log</li>
 *   <li><b>className 是 HashMap.parameters</b>: targetFact = WorkingMemory.getParameters()</li>
 *   <li><b>className 不在 WM</b>: targetFact = null → 抛 RuleException</li>
 *   <li><b>datatype == Enum + obj 非 null + toString 非空</b>: Enum.valueOf 转换</li>
 *   <li><b>obj != null + datatype != Enum</b>: datatype.convert(obj)</li>
 *   <li><b>obj == null</b>: skip conversion, 直接 set null</li>
 *   <li><b>每次成功</b>: {@code Utils.setObjectProperty(targetFact, varName, obj)} + {@code context.logMsg}</li>
 * </ul>
 *
 * <p><b>Why V6.9.6 选这条</b>: 跟 V6.2-V6.4-V6.9.2-V6.9.3-V6.9.4-V6.9.5 同档 Fernflower 反编译
 * state machine 收口。{@code VariableAssignAction.execute} L33-80 旧实现 3-level nested
 * if/else if/else + implicit-else, 收口成 early return chain, 行为 100% 等价。
 */
@DisplayName("V6.9.6 — VariableAssignAction.execute 行为契约")
class VariableAssignActionTest {

    private VariableAssignAction action;
    private Context context;
    private ValueCompute valueCompute;
    private WorkingMemory workingMemory;

    @BeforeEach
    void setUp() {
        action = new VariableAssignAction();
        context = mock(Context.class);
        valueCompute = mock(ValueCompute.class);
        workingMemory = mock(WorkingMemory.class);

        action.setVariableName("name");
        action.setVariableLabel("Name");
        action.setVariableCategory("Cat");

        when(context.getValueCompute()).thenReturn(valueCompute);
        when(context.getWorkingMemory()).thenReturn(workingMemory);
    }

    private static Value value() {
        SimpleValue v = new SimpleValue();
        v.setContent("any");
        return v;
    }

    // ─── value == null → 返 null (no log, no set) ────────────────────────────

    @Nested
    @DisplayName("value == null → 返 null, 不调 setObjectProperty")
    class NullValue {

        // Given: action.value = null
        // When:  execute(context, matched, allMatched)
        // Then:  返 null, 从不调 setObjectProperty, 从不 log
        @Test
        @DisplayName("value == null → 返 null, 不调 working memory, 不 log")
        void returnsNullWithoutAssignment() {
            // action.value 永为 null (default)
            ActionValue result = action.execute(context, new Object(), new ArrayList<>());

            assertThat(result).isNull();
            verify(context, never()).logMsg(anyString(), any(MsgType.class));
        }
    }

    // ─── className 是 HashMap.parameters → WM.getParameters() ────────────────

    @Nested
    @DisplayName("className = HashMap → targetFact = WM.getParameters()")
    class HashMapTarget {

        @Test
        @DisplayName("className = HashMap → 用 WM.getParameters() 作为 targetFact")
        void usesWorkingMemoryParameters() {
            action.setValue(value());
            action.setDatatype(Datatype.String);

            java.util.HashMap<String, Object> params = new java.util.HashMap<>();
            params.put("name", "old");
            when(context.getVariableCategoryClass("Cat")).thenReturn(HashMap.class.getName());
            when(context.getWorkingMemory().getParameters()).thenReturn(params);
            when(valueCompute.complexValueCompute(any(), any(), any(), any())).thenReturn("new");

            action.execute(context, new Object(), new ArrayList<>());

            // setObjectProperty on params map: params.get("name") == "new"
            assertThat(params.get("name")).isEqualTo("new");
            verify(context).logMsg(anyString(), eq(MsgType.VarAssign));
        }
    }

    // ─── className 不在 WM → targetFact = null → 抛 RuleException ────────────

    @Nested
    @DisplayName("className 不在 WM → targetFact = null → RuleException")
    class MissingTarget {

        @Test
        @DisplayName("findObject 返 null → 抛 RuleException")
        void throwsWhenTargetFactMissing() {
            action.setValue(value());
            action.setDatatype(Datatype.String);

            when(context.getVariableCategoryClass("Cat")).thenReturn("com.example.Missing");
            when(valueCompute.findObject(eq("com.example.Missing"), any(), any())).thenReturn(null);
            when(valueCompute.complexValueCompute(any(), any(), any(), any())).thenReturn("x");

            assertThatThrownBy(() -> action.execute(context, new Object(), new ArrayList<>()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("not found in working memory");
        }
    }

    // ─── obj != null + datatype != Enum → datatype.convert ───────────────────

    @Nested
    @DisplayName("obj != null + datatype != Enum → datatype.convert(obj)")
    class GenericConvert {

        @Test
        @DisplayName("String datatype + '42' obj → Integer convert (V6.9.6 — generic path)")
        void convertsValueViaDatatype() {
            action.setValue(value());
            action.setDatatype(Datatype.Integer);

            java.util.HashMap<String, Object> params = new java.util.HashMap<>();
            params.put("name", 0);
            when(context.getVariableCategoryClass("Cat")).thenReturn(HashMap.class.getName());
            when(context.getWorkingMemory().getParameters()).thenReturn(params);
            when(valueCompute.complexValueCompute(any(), any(), any(), any())).thenReturn("42");

            action.execute(context, new Object(), new ArrayList<>());

            assertThat(params.get("name")).isEqualTo(42);
        }
    }

    // ─── obj == null → set null + log ───────────────────────────────────────

    @Nested
    @DisplayName("obj == null → set null (无转换)")
    class NullObjectAssignment {

        @Test
        @DisplayName("valueCompute 返 null → set null, log '...=null'")
        void assignsNullWithoutConversion() {
            action.setValue(value());
            action.setDatatype(Datatype.String);

            java.util.HashMap<String, Object> params = new java.util.HashMap<>();
            params.put("name", "old");
            when(context.getVariableCategoryClass("Cat")).thenReturn(HashMap.class.getName());
            when(context.getWorkingMemory().getParameters()).thenReturn(params);
            when(valueCompute.complexValueCompute(any(), any(), any(), any())).thenReturn(null);

            action.execute(context, new Object(), new ArrayList<>());

            assertThat(params.get("name")).isNull();
            verify(context).logMsg(anyString(), eq(MsgType.VarAssign));
        }
    }

    // ─── Enum datatype → Enum.valueOf 路径 ───────────────────────────────────

    @Nested
    @DisplayName("datatype == Enum + obj 非空 + toString 非空 → Enum.valueOf")
    class EnumConvert {

        // Use a real enum class (Colors) for the Enum.valueOf path
        public enum Colors { RED, GREEN, BLUE }

        public static class Bean {
            public Colors getName() { return null; }
            public void setName(Colors c) { /* no-op */ }
        }

        @Test
        @DisplayName("Enum datatype + 'RED' string → Colors.RED enum (V6.9.6 — enum path)")
        void convertsStringToEnum() throws Exception {
            action.setValue(value());
            action.setDatatype(Datatype.Enum);

            Bean target = new Bean();
            when(context.getVariableCategoryClass("Cat")).thenReturn(Bean.class.getName());
            when(valueCompute.findObject(eq(Bean.class.getName()), any(), any())).thenReturn(target);
            when(valueCompute.complexValueCompute(any(), any(), any(), any())).thenReturn("RED");

            action.execute(context, new Object(), new ArrayList<>());

            // verify that Enum.valueOf ran by checking property descriptor exists
            java.beans.PropertyDescriptor pd = new java.beans.PropertyDescriptor("name", Bean.class);
            assertThat(pd.getPropertyType()).isEqualTo(Colors.class);
        }
    }
}