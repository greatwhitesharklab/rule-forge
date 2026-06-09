package com.ruleforge.datasource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Vars — 变量容器")
class VarsTest {

    @Nested
    @DisplayName("Scenario: put / get")
    class PutGet {

        @Test
        @DisplayName("put + getStr 返原值")
        void shouldPutAndGetString() {
            Vars v = new Vars().put("name", "alice");
            assertThat(v.getStr("name")).isEqualTo("alice");
        }

        @Test
        @DisplayName("getStr 缺失 key 返 null(不抛)")
        void shouldReturnNullForMissingKey() {
            assertThat(new Vars().getStr("nope")).isNull();
        }

        @Test
        @DisplayName("put null key 抛 NPE")
        void shouldRejectNullKey() {
            assertThatThrownBy(() -> new Vars().put(null, "x"))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Scenario: 类型化 accessor")
    class TypedAccessors {

        @Test
        @DisplayName("getInt 处理 Integer / Long / Double / String 数字")
        void shouldCoerceInt() {
            Vars v = new Vars()
                .put("a", 42)
                .put("b", 42L)
                .put("c", 42.7)
                .put("d", "99");
            assertThat(v.getInt("a")).isEqualTo(42);
            assertThat(v.getInt("b")).isEqualTo(42);
            assertThat(v.getInt("c")).isEqualTo(42);  // double 截断
            assertThat(v.getInt("d")).isEqualTo(99);
        }

        @Test
        @DisplayName("getInt 不可解析字符串返 null")
        void shouldReturnNullForUnparseableInt() {
            Vars v = new Vars().put("bad", "not a number");
            assertThat(v.getInt("bad")).isNull();
        }

        @Test
        @DisplayName("getBool 接受 true/false/Y/N/1/0 字符串")
        void shouldParseBool() {
            Vars v = new Vars();
            v.put("a", "true"); v.put("b", "FALSE"); v.put("c", "Y");
            v.put("d", "n"); v.put("e", "1"); v.put("f", "0");
            assertThat(v.getBool("a")).isTrue();
            assertThat(v.getBool("b")).isFalse();
            assertThat(v.getBool("c")).isTrue();
            assertThat(v.getBool("d")).isFalse();
            assertThat(v.getBool("e")).isTrue();
            assertThat(v.getBool("f")).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario: 合并 + 容量")
    class MergeAndSize {

        @Test
        @DisplayName("putAll 把另一 Vars 合并进来")
        void shouldMerge() {
            Vars a = new Vars().put("x", 1);
            Vars b = new Vars().put("y", 2);
            a.putAll(b);
            assertThat(a.size()).isEqualTo(2);
            assertThat(a.getInt("y")).isEqualTo(2);
        }

        @Test
        @DisplayName("putAll(null) 不抛")
        void shouldTolerateNullInPutAll() {
            Vars a = new Vars().put("x", 1);
            a.putAll(null);  // 不抛
            assertThat(a.size()).isEqualTo(1);
        }
    }
}
