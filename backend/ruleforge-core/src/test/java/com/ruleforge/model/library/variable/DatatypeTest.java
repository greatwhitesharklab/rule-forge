package com.ruleforge.model.library.variable;

import com.ruleforge.model.library.Datatype;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Datatype - 数据类型转换")
class DatatypeTest {

    @Nested
    @DisplayName("字符串转目标类型")
    class StringConvert {

        // Given Datatype.Integer 和字符串 "42"
        // When 调用 convert("42")
        // Then 应返回 Integer 类型的 42
        @Test
        @DisplayName("字符串应转为 Integer")
        void shouldConvertStringToInteger() {
            Object result = Datatype.Integer.convert("42");
            assertThat(result).isInstanceOf(Integer.class).isEqualTo(42);
        }

        // Given Datatype.Double 和字符串 "3.14"
        // When 调用 convert("3.14")
        // Then 应返回 Double 类型的 3.14
        @Test
        @DisplayName("字符串应转为 Double")
        void shouldConvertStringToDouble() {
            Object result = Datatype.Double.convert("3.14");
            assertThat(result).isInstanceOf(Double.class).isEqualTo(3.14);
        }

        // Given Datatype.Boolean 和字符串 "true"
        // When 调用 convert("true")
        // Then 应返回 Boolean.TRUE
        @Test
        @DisplayName("字符串应转为 Boolean")
        void shouldConvertStringToBoolean() {
            Object result = Datatype.Boolean.convert("true");
            assertThat(result).isEqualTo(Boolean.TRUE);
        }

        // Given Datatype.String 和字符串 "hello"
        // When 调用 convert("hello")
        // Then 应返回原字符串
        @Test
        @DisplayName("字符串类型应原样返回")
        void shouldReturnStringAsIs() {
            Object result = Datatype.String.convert("hello");
            assertThat(result).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("对象转字符串")
    class ObjectToString {

        // Given Datatype.Integer 和 Integer 值 42
        // When 调用 convertObjectToString(42)
        // Then 应返回字符串 "42"
        @Test
        @DisplayName("Integer 应转为字符串")
        void shouldConvertIntegerToString() {
            String result = Datatype.Integer.convertObjectToString(42);
            assertThat(result).isEqualTo("42");
        }

        // Given Datatype.Boolean 和 Boolean.TRUE
        // When 调用 convertObjectToString(Boolean.TRUE)
        // Then 应返回字符串 "true"
        @Test
        @DisplayName("Boolean 应转为字符串")
        void shouldConvertBooleanToString() {
            String result = Datatype.Boolean.convertObjectToString(true);
            assertThat(result).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("枚举值")
    class EnumValues {

        // Given Datatype 枚举
        // When 检查枚举值
        // Then 应包含 String, Integer, Double, Boolean, List, Set, Map, Date
        @Test
        @DisplayName("应包含所有预期枚举值")
        void shouldContainAllExpectedValues() {
            assertThat(Datatype.values()).contains(
                    Datatype.String,
                    Datatype.Integer,
                    Datatype.Double,
                    Datatype.Boolean,
                    Datatype.List,
                    Datatype.Set,
                    Datatype.Map,
                    Datatype.Date
            );
        }
    }
}
