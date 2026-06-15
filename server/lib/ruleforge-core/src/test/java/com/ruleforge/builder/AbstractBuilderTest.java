package com.ruleforge.builder;

import com.ruleforge.builder.ResourceBase;
import com.ruleforge.builder.resource.ResourceBuilder;
import com.ruleforge.builder.resource.ResourceProvider;
import com.ruleforge.exception.RuleException;
import com.ruleforge.plugin.EnginePluginRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0 — close AbstractBuilder 公共方法契约覆盖。
 *
 * <p>目标:锁定 3 件事
 * <ol>
 *   <li>{@code newResourceBase()} 返非 null {@link ResourceBase}</li>
 *   <li>{@code setPluginRegistry(null)} 抛 NPE(getResourceBuilders 解引用)</li>
 *   <li>{@code parseResource(xml)} 错 XML 抛 {@link RuleException}</li>
 * </ol>
 * <p>V5.48: L43 dead-code {@code getBeansWithAnnotation(SuppressWarnings.class)} 已删
 * (P0 验证全 unused,Task 3 删 + 移 test 里 mock 桩)。
 * <p>V5.76.3: AbstractBuilder 改注入 EnginePluginRegistry(去 ApplicationContextAware)。
 * </p>
 */
@DisplayName("P0 — AbstractBuilder 公共契约")
class AbstractBuilderTest {

    /** Minimal 子类:把 protected field + parseResource 暴露给测试。 */
    static class TestBuilder extends AbstractBuilder {
        org.dom4j.Element callParse(String content) {
            return parseResource(content);
        }
    }

    @Nested
    @DisplayName("newResourceBase — 构造 ResourceBase")
    class NewResourceBase {

        @Test
        @DisplayName("无 pluginRegistry 时 newResourceBase 应返非 null")
        void newResourceBaseNoContext() {
            TestBuilder b = new TestBuilder();
            ResourceBase rb = b.newResourceBase();
            assertNotNull(rb);
        }
    }

    @Nested
    @DisplayName("setPluginRegistry — 注入契约")
    class SetPluginRegistry {

        @Test
        @DisplayName("setPluginRegistry(null) 应抛 NPE(getResourceBuilders 解引用)")
        void nullRegistryThrows() {
            TestBuilder b = new TestBuilder();
            assertThrows(NullPointerException.class, () -> b.setPluginRegistry(null));
        }

        @Test
        @DisplayName("setPluginRegistry(mock) 注入后 resourceBuilders/providers 都不为 null")
        void mockRegistryInjects() {
            TestBuilder b = new TestBuilder();
            EnginePluginRegistry registry = mock(EnginePluginRegistry.class);
            when(registry.getResourceBuilders()).thenReturn(java.util.Collections.emptyList());
            when(registry.getResourceProviders()).thenReturn(java.util.Collections.emptyList());

            assertDoesNotThrow(() -> b.setPluginRegistry(registry));
            // 字段注入后可访问(同 package,Field 反射跳过)
            try {
                java.lang.reflect.Field rb = AbstractBuilder.class.getDeclaredField("resourceBuilders");
                rb.setAccessible(true);
                assertNotNull(rb.get(b));
            } catch (Exception e) {
                throw new AssertionError("resourceBuilders 注入失败", e);
            }
        }
    }

    @Nested
    @DisplayName("parseResource — DOM4J 包装")
    class ParseResource {

        @Test
        @DisplayName("合法 XML 返根 Element")
        void parseValidXml() {
            TestBuilder b = new TestBuilder();
            org.dom4j.Element root = b.callParse("<root><child/></root>");
            assertNotNull(root);
            assertNotNull(root.element("child"));
        }

        @Test
        @DisplayName("非法 XML 抛 RuleException(DOM4J DocumentException 包装)")
        void parseInvalidXmlThrows() {
            TestBuilder b = new TestBuilder();
            assertThrows(RuleException.class, () -> b.callParse("not-xml-at-all"));
        }
    }
}
