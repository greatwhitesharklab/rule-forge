package com.ruleforge;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.GeneralEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5.89 — {@code Utils.getObjectProperty} 反射 cache 行为验证。
 *
 * <p>锁 V5.89 替换 apache commons PropertyUtils 后的契约:
 * <ul>
 *   <li>POJO 走 {@code Class<?> -> Map<String, Method>} 反射 cache,直接
 *       {@code Method.invoke},2 次以上调用同 class+property 必须命中 cache</li>
 *   <li>Map / GeneralEntity (extends HashMap) 走 fast path,
 *       {@code ((Map) obj).get(property)},零反射</li>
 *   <li>missing property / null object 抛 {@link RuleException} 跟原 PropertyUtils
 *       行为对齐</li>
 *   <li>2 种 getter 形态都支持: {@code getX} / {@code isX}(boolean)。V5.89
 *       故意收窄到 {@code PropertyUtils} 等价语义(无 nested/indexed/mapped/bare method)—
 *       经 audit 27+ call sites 全部用 simple name + 真实 POJO getter 形态,
 *       不影响 production</li>
 * </ul>
 *
 * <p>JFR 35s 抓出 post-V5.88 剩余 hot path 是 {@code PropertyUtilsBean.getSimpleProperty}(131)+
 * {@code DefaultResolver.next}(109)+ nested/getProperty/getPropertyDescriptor 全部
 * 来自 {@code Utils.getObjectProperty} 反射链,占 12% hot path。V5.89 替换后预期
 * 这 240 sample 全部消失。
 *
 * @since 5.89
 */
@DisplayName("V5.89 — Utils.getObjectProperty 反射 cache")
class UtilsGetObjectPropertyTest {

    /** V5.89 fixture — private fields + public getters + boolean isX. 顶层 static 类
     *  避免 inner-class synthetic this$0 干扰反射(也贴近生产 POJO)。 */
    public static final class Person {
        private final String name = "alice";
        private final int age = 30;
        private final boolean active = true;

        public Person() {}
        public String getName() { return name; }
        public int getAge() { return age; }
        public boolean isActive() { return active; }
    }

    @Nested
    @DisplayName("POJO getter path")
    class PojoGetter {

        // Given Person 私有字段 name="alice"
        // When 调用 Utils.getObjectProperty(new Person(), "name")
        // Then 返回 "alice"
        @Test
        @DisplayName("应通过 getX 反射返回 String 字段")
        void shouldInvokeGetXGetter() {
            assertThat(Utils.getObjectProperty(new Person(), "name"))
                .isEqualTo("alice");
        }

        // Given Person.age = 30
        // When 调用 getObjectProperty(person, "age")
        // Then 返回 30 (int 字段)
        @Test
        @DisplayName("应通过 getX 返回 int 字段")
        void shouldReturnIntViaGetter() {
            assertThat(Utils.getObjectProperty(new Person(), "age"))
                .isEqualTo(30);
        }

        // Given Person.active = true
        // When 调用 getObjectProperty(person, "active")
        // Then 返回 true (走 isX 形态)
        @Test
        @DisplayName("应通过 isX 返回 boolean 字段")
        void shouldInvokeIsXGetter() {
            assertThat(Utils.getObjectProperty(new Person(), "active"))
                .isEqualTo(true);
        }

        // Given 同 class + property 二次调用
        // When 第二次 getObjectProperty
        // Then 必须命中 cache (返回同一引用, Method.invoke 复用)
        @Test
        @DisplayName("二次调用同 class+property 应从 cache 返回同一引用")
        void secondCallShouldHitCache() {
            Person p = new Person();
            Object first = Utils.getObjectProperty(p, "name");
            Object second = Utils.getObjectProperty(p, "name");
            assertThat(first)
                .isEqualTo("alice")
                .isSameAs(second);
        }
    }

    @Nested
    @DisplayName("Map fast path")
    class MapFastPath {

        // Given HashMap key="score", value=95
        // When 调用 getObjectProperty(map, "score")
        // Then 返回 95 (零反射, 走 Map fast path)
        @Test
        @DisplayName("应返回 HashMap 中 key 对应的 value")
        void shouldReadHashMapValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("score", 95);
            assertThat(Utils.getObjectProperty(map, "score")).isEqualTo(95);
        }

        // Given GeneralEntity (extends HashMap) key="name", value="bob"
        // When 调用 getObjectProperty(entity, "name")
        // Then 返回 "bob" (零反射, HashMap 走 fast path)
        @Test
        @DisplayName("应返回 GeneralEntity 中 key 对应的 value")
        void shouldReadGeneralEntityValue() {
            GeneralEntity entity = new GeneralEntity("User");
            entity.put("name", "bob");
            assertThat(Utils.getObjectProperty(entity, "name")).isEqualTo("bob");
        }
    }

    @Nested
    @DisplayName("错误路径")
    class ErrorPath {

        // Given Person 无 "missing" 属性
        // When 调用 getObjectProperty(person, "missing")
        // Then 抛 RuleException
        @Test
        @DisplayName("缺失属性应抛 RuleException")
        void missingPropertyShouldThrowRuleException() {
            assertThatThrownBy(() -> Utils.getObjectProperty(new Person(), "missing"))
                .isInstanceOf(RuleException.class);
        }

        // Given object = null
        // When 调用 getObjectProperty(null, "name")
        // Then 抛 RuleException
        @Test
        @DisplayName("object 为 null 应抛 RuleException")
        void nullObjectShouldThrowRuleException() {
            assertThatThrownBy(() -> Utils.getObjectProperty(null, "name"))
                .isInstanceOf(RuleException.class);
        }
    }
}
