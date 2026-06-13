package com.ruleforge.ir.drl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.42.2 — {@link DatatypeResolver} 独立 BDD。
 *
 * <p>3 组用例:
 * <ul>
 *   <li>TypeInfo factory(fact / declared)区分 fact 标志</li>
 *   <li>register / resolve / isKnown 正常路径</li>
 *   <li>异常路径:空 name 抛 IllegalArgumentException;unknown type 抛 DrlParseException + 错误信息含 D4 + 解法(declare/register)</li>
 *   <li>大小跟 immutable semantics:register 多次同名,后者覆盖</li>
 * </ul>
 *
 * @since 5.42
 */
@DisplayName("V5.42.2 — DatatypeResolver 独立 BDD")
class DatatypeResolverTest {

    private DatatypeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DatatypeResolver();
    }

    // ============================================================
    // === TypeInfo factory ===
    // ============================================================

    @Nested
    @DisplayName("Given TypeInfo factory,When 区分 fact vs declared,Then isFact 标志正确")
    class TypeInfoFactory {

        @Test
        @DisplayName("fact() → isFact = true")
        void factFlagTrue() {
            DatatypeResolver.TypeInfo info = DatatypeResolver.TypeInfo.fact("Applicant",
                Arrays.asList("age", "income"));
            assertEquals("Applicant", info.getName());
            assertTrue(info.isFact());
            assertEquals(2, info.getFields().size());
        }

        @Test
        @DisplayName("declared() → isFact = false")
        void declaredFlagFalse() {
            DatatypeResolver.TypeInfo info = DatatypeResolver.TypeInfo.declared("Order",
                Arrays.asList("amount"));
            assertFalse(info.isFact());
        }

        @Test
        @DisplayName("空 fields list 也接受 — V5.42.2 API 不强制最小字段数")
        void emptyFieldsAccepted() {
            DatatypeResolver.TypeInfo info = DatatypeResolver.TypeInfo.fact("Empty", Collections.emptyList());
            assertNotNull(info.getFields());
            assertEquals(0, info.getFields().size());
        }
    }

    // ============================================================
    // === register / resolve / isKnown happy path ===
    // ============================================================

    @Nested
    @DisplayName("Given register 入口,When caller 用 resolve / isKnown,Then 行为正确")
    class RegisterHappyPath {

        @Test
        @DisplayName("register + resolve 同一 type:返回相同 TypeInfo 实例")
        void registerResolveSame() {
            DatatypeResolver.TypeInfo registered = DatatypeResolver.TypeInfo.fact("Loan",
                Arrays.asList("amount"));
            resolver.register("Loan", registered);
            DatatypeResolver.TypeInfo resolved = resolver.resolve("Loan");
            assertEquals("Loan", resolved.getName());
            assertEquals(1, resolved.getFields().size());
            assertTrue(resolved.isFact());
        }

        @Test
        @DisplayName("isKnown 区分 known / unknown,不抛异常")
        void isKnownNonThrowing() {
            resolver.register("Known", DatatypeResolver.TypeInfo.fact("Known", Collections.emptyList()));
            assertTrue(resolver.isKnown("Known"));
            assertFalse(resolver.isKnown("Unknown"));
            // 大小写敏感 — V5.42.2 不做 normalization
            assertFalse(resolver.isKnown("known"));
        }

        @Test
        @DisplayName("构造器接受预填 Map — 模拟 console-ui 推送 type registry")
        void constructorWithInitialMap() {
            HashMap<String, DatatypeResolver.TypeInfo> initial = new HashMap<>();
            initial.put("Pre", DatatypeResolver.TypeInfo.fact("Pre", Arrays.asList("x")));
            DatatypeResolver r = new DatatypeResolver(initial);
            assertTrue(r.isKnown("Pre"));
            assertEquals(1, r.size());
        }

        @Test
        @DisplayName("size 反映已注册 type 数")
        void sizeReflectsRegistry() {
            assertEquals(0, resolver.size());
            resolver.register("A", DatatypeResolver.TypeInfo.fact("A", Collections.emptyList()));
            assertEquals(1, resolver.size());
            resolver.register("B", DatatypeResolver.TypeInfo.fact("B", Collections.emptyList()));
            assertEquals(2, resolver.size());
        }
    }

    // ============================================================
    // === 异常路径 ===
    // ============================================================

    @Nested
    @DisplayName("Given 异常路径,When 触发,Then 错误信息结构稳定")
    class ExceptionPath {

        @Test
        @DisplayName("unknown type → DrlParseException 含 4 部分:type 名 / D4 决定 / 解法 / 已知列表")
        void unknownTypeError() {
            resolver.register("Applicant",
                DatatypeResolver.TypeInfo.fact("Applicant", Arrays.asList("age")));
            DrlParseException ex = assertThrows(DrlParseException.class,
                () -> resolver.resolve("Unknown"));
            String msg = ex.getMessage();
            assertTrue(msg.contains("Unknown"),
                "应包含 type 名,实际:" + msg);
            assertTrue(msg.contains("D4"),
                "应点 V5.42 D4 决定,实际:" + msg);
            assertTrue(msg.contains("import"),
                "应说明 import 不支持,实际:" + msg);
            assertTrue(msg.contains("declare") || msg.contains("register"),
                "应给解法(declare 段 / 预 register),实际:" + msg);
            assertTrue(msg.contains("Applicant"),
                "应列出已知 types 帮助调试,实际:" + msg);
        }

        @Test
        @DisplayName("空 typeName register → IllegalArgumentException")
        void emptyTypeNameRejected() {
            assertThrows(IllegalArgumentException.class,
                () -> resolver.register("", DatatypeResolver.TypeInfo.fact("X", Collections.emptyList())));
            assertThrows(IllegalArgumentException.class,
                () -> resolver.register(null, DatatypeResolver.TypeInfo.fact("X", Collections.emptyList())));
        }

        @Test
        @DisplayName("DrlParseException 无 ctx → line/column 为 null")
        void exceptionWithoutPosition() {
            DrlParseException ex = new DrlParseException("oops");
            assertNull(ex.getLine());
            assertNull(ex.getColumn());
        }
    }

    // ============================================================
    // === 多次 register 同名 ===
    // ============================================================

    @Nested
    @DisplayName("Given 多次 register 同名,When 后注册覆盖,Then resolve 拿到最新")
    class OverwriteSemantics {

        @Test
        @DisplayName("后 register 覆盖先 register")
        void overwrite() {
            resolver.register("X", DatatypeResolver.TypeInfo.fact("X", Arrays.asList("a")));
            resolver.register("X", DatatypeResolver.TypeInfo.fact("X", Arrays.asList("a", "b")));
            assertEquals(2, resolver.resolve("X").getFields().size());
            // size 不变(同名覆盖)
            assertEquals(1, resolver.size());
        }
    }
}
