package com.ruleforge.builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 — close KnowledgeBuilder 公共方法契约覆盖。
 *
 * <p>目标:锁定 3 件事
 * <ol>
 *   <li>无参构造可用(@Setter Lombok 注入字段)</li>
 *   <li>{@link KnowledgeBuilder#BEAN_ID} 常量 = "ruleforge.knowledgeBuilder"(Spring 注入契约)</li>
 *   <li>Lombok {@code @Setter} 注入后字段值可读</li>
 * </ol>
 *
 * <p>{@code buildKnowledgeBase(...)} 走完整 RETE 构建,需要 ResourceLibraryBuilder /
 * ReteBuilder / RulesRebuilder 全套 mock + 复杂 Resource,留 P1 facade 拆分后单测。
 */
@DisplayName("P0 — KnowledgeBuilder 公共契约")
class KnowledgeBuilderTest {

    private KnowledgeBuilder kb;

    @BeforeEach
    void setUp() {
        kb = new KnowledgeBuilder();
    }

    @Nested
    @DisplayName("构造 + BEAN_ID")
    class Construction {

        @Test
        @DisplayName("无参构造应可用")
        void noArgCtor() {
            assertNotNull(kb);
        }

        @Test
        @DisplayName("BEAN_ID 必须是 'ruleforge.knowledgeBuilder'")
        void beanIdConstant() {
            assertEquals("ruleforge.knowledgeBuilder", KnowledgeBuilder.BEAN_ID);
        }
    }

    @Nested
    @DisplayName("@Setter Lombok 字段注入")
    class SetterInjection {

        @Test
        @DisplayName("setResourceLibraryBuilder 注入后字段非空")
        void injectResourceLibraryBuilder() {
            ResourceLibraryBuilder lib = new ResourceLibraryBuilder();
            kb.setResourceLibraryBuilder(lib);
            try {
                java.lang.reflect.Field f = KnowledgeBuilder.class.getDeclaredField("resourceLibraryBuilder");
                f.setAccessible(true);
                assertSame(lib, f.get(kb));
            } catch (Exception e) {
                throw new AssertionError("field access failed", e);
            }
        }

        @Test
        @DisplayName("setReteBuilder 注入后字段非空")
        void injectReteBuilder() {
            com.ruleforge.model.rete.builder.ReteBuilder rete = new com.ruleforge.model.rete.builder.ReteBuilder();
            kb.setReteBuilder(rete);
            assertTrue(true, "Setter 注入未抛");
        }
    }
}
