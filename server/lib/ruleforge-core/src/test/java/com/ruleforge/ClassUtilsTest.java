package com.ruleforge;

import com.ruleforge.model.Label;
import com.ruleforge.model.library.Datatype;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V6.9.24 + V6.9.26 — {@link ClassUtils} 静态方法行为契约 BDD。
 *
 * <p>锁 V6.9.24 (L108-125 {@code getPropertyAnnotationLabel} while state machine →
 * explicit for-loop walk up superclass chain, V5.96 skip 模式) + V6.9.26
 * (L147-150 {@code getDateType} 重复 {@code if (Date.class.isAssignableFrom(type))}
 * → 删第二次) 的行为不变性。
 *
 * <p><b>Why V6.9.24 + V6.9.26 batch</b>: 都是 ClassUtils cold path 微调, build-time
 * annotation lookup / type 推断, JFR 0 sample, 同一个 file 共 batch 1 commit。
 */
@DisplayName("V6.9.24+V6.9.26 — ClassUtils 行为契约")
class ClassUtilsTest {

    // ====== V6.9.24 — getPropertyAnnotationLabel 走 superclass chain ======

    @Nested
    @DisplayName("V6.9.24 — getPropertyAnnotationLabel 走 superclass chain")
    class WalkSuperclass {

        static class ChildNoAnnotation {
            @Label("child-level")
            private String childField;
        }

        static class ParentAnnotated {
            @Label("parent-level")
            private String parentField;
        }

        static class ChildWithoutOwnField extends ParentAnnotated {
            // parentField 不在 child 重声明, 触发 walk-up 到父类
        }

        static class NoLabel {
            private String noLabelField;
        }

        static class NoLabelChild extends NoLabel {
            // noLabelField 不在 child 重声明, 触发 walk-up, 父类也无 @Label
        }

        static class ParentWithField {
            private String missingInChild;
        }

        static class ChildMissingField extends ParentWithField {
            // missingInChild 不在 child 重声明, 父类有
        }

        @Test
        @DisplayName("自身有 @Label → 直接返回")
        void selfAnnotated() throws Exception {
            String label = (String) invokeGetPropertyAnnotationLabel(ChildNoAnnotation.class, "childField");
            assertThat(label).isEqualTo("child-level");
        }

        @Test
        @DisplayName("子无字段, 父类有 @Label → 走 superclass chain 返回父类 label")
        void walksToParent() throws Exception {
            String label = (String) invokeGetPropertyAnnotationLabel(ChildWithoutOwnField.class, "parentField");
            assertThat(label).isEqualTo("parent-level");
        }

        @Test
        @DisplayName("字段在所有层级都无 @Label → 返回 null (子无字段, 父无 annotation)")
        void noAnnotationAnywhereReturnsNull() throws Exception {
            Object label = invokeGetPropertyAnnotationLabel(NoLabelChild.class, "noLabelField");
            assertThat(label).isNull();
        }

        @Test
        @DisplayName("字段在所有层级都不存在 → 抛 NoSuchFieldException (走到 Object.class rethrow)")
        void fieldMissingOnAllChainThrows() {
            assertThatThrownBy(() -> invokeGetPropertyAnnotationLabel(ChildMissingField.class, "nonexistent"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(NoSuchFieldException.class);
        }
    }

    // ====== V6.9.26 — getDateType 重复 Date 分支 dedup ======

    @Nested
    @DisplayName("V6.9.26 — getDateType 行为不变性 (含 Date dedup)")
    class GetDateType {

        @Test
        @DisplayName("Date → Datatype.Date (V6.9.26 删第二次重复分支, 行为不变)")
        void dateReturnsDate() {
            assertThat(invokeGetDateType(Date.class)).isEqualTo(Datatype.Date);
        }

        @Test
        @DisplayName("String → Datatype.String")
        void stringReturnsString() {
            assertThat(invokeGetDateType(String.class)).isEqualTo(Datatype.String);
        }

        @Test
        @DisplayName("int → Datatype.Integer")
        void intReturnsInteger() {
            assertThat(invokeGetDateType(int.class)).isEqualTo(Datatype.Integer);
        }

        @Test
        @DisplayName("List → Datatype.List")
        void listReturnsList() {
            assertThat(invokeGetDateType(List.class)).isEqualTo(Datatype.List);
        }

        @Test
        @DisplayName("Map → Datatype.Map")
        void mapReturnsMap() {
            assertThat(invokeGetDateType(Map.class)).isEqualTo(Datatype.Map);
        }

        @Test
        @DisplayName("Set → Datatype.Set")
        void setReturnsSet() {
            assertThat(invokeGetDateType(Set.class)).isEqualTo(Datatype.Set);
        }

        @Test
        @DisplayName("Object → Datatype.Object (fallback)")
        void objectReturnsObject() {
            assertThat(invokeGetDateType(Object.class)).isEqualTo(Datatype.Object);
        }

        @Test
        @DisplayName("StringBuilder (非任何 primitive/wrapper 集合类型) → Datatype.Object (fallback)")
        void stringBuilderReturnsObject() {
            assertThat(invokeGetDateType(StringBuilder.class)).isEqualTo(Datatype.Object);
        }
    }

    // ====== Reflection helper (private static method) ======

    private static Object invokeGetPropertyAnnotationLabel(Class<?> cls, String fieldName) {
        try {
            java.lang.reflect.Method m = ClassUtils.class.getDeclaredMethod(
                "getPropertyAnnotationLabel", Class.class, String.class);
            m.setAccessible(true);
            return m.invoke(null, cls, fieldName);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap and rethrow the cause to preserve test assertion semantics
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Datatype invokeGetDateType(Class<?> type) {
        try {
            java.lang.reflect.Method m = ClassUtils.class.getDeclaredMethod(
                "getDateType", Class.class);
            m.setAccessible(true);
            return (Datatype) m.invoke(null, type);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
