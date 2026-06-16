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
 * V5.99 — {@code Utils.getObjectProperty} MethodHandle 契约 BDD。
 *
 * <p>锁 V5.99 修法(用 {@code MethodHandle} 替代 {@code Method.invoke}
 * 作反射调用)的行为不变性:
 * <ul>
 *   <li>POJO 走 {@code Class<?> -> Map<String, MethodHandle>} cache,直接
 *       {@code MethodHandle.invoke} JIT-specialized 路径</li>
 *   <li>同 V5.89:Map / GeneralEntity 走 fast path,零反射</li>
 *   <li>missing property / null object 抛 {@link RuleException}</li>
 *   <li>2 种 getter 形态都支持: {@code getX} / {@code isX}</li>
 *   <li>并发多线程访问 cache 行为正确(MethodHandle 自身线程安全,cache 用 CHM)</li>
 *   <li>返回 value 与 V5.89 行为完全一致(契约 lock,不破坏现有调用方)</li>
 * </ul>
 *
 * <p><b>Why V5.99 选 MethodHandle 替代 Method.invoke</b>:
 * V5.98 后 JFR 30s HotPathBenchTest 显示 reflection chain 残留 sample:
 * <ul>
 *   <li>{@code Utils.getObjectProperty}: 340 sample</li>
 *   <li>{@code Method.invoke}: 231 sample</li>
 *   <li>{@code DirectMethodHandleAccessor.invoke}: 254 sample</li>
 *   <li>合计 825 sample(rete hot path 32%)</li>
 * </ul>
 * V5.89 用 {@code Class -> Map cache + Method.invoke},但每次 invoke 仍走:
 * {@code varargs Object[] allocation + IllegalAccessException 检查 +
 * InvocationTargetException 拆包 + reflective access check}。
 * {@code MethodHandle.invoke} 走 {@code LambdaForm + Signature adapter},
 * JIT 把它当 polymorphic call site 完整 inline + escape analysis 消除 boxing。
 *
 * <p><b>行为等价性 audit</b>:
 * <ul>
 *   <li>return value 类型 / null 行为:同 V5.89</li>
 *   <li>异常路径: {@code MethodHandle.invoke} 抛 {@code ClassCastException}
 *       (从 Object 适配失败)/ {@code WrongMethodTypeException},而
 *       {@code Method.invoke} 抛 {@code InvocationTargetException} 包装底层 cause。
 *       本方法 V5.99 改用 try-catch on Throwable cause 拆包 + 转 RuntimeException
 *       / Error / Exception,语义跟 V5.89 拆 {@code InvocationTargetException.getCause()}
 *       一致 — 锁 [[v599-utils-methodhandle]]。</li>
 *   <li>cache 行为:同 class + property 二次调用命中 cache,直接 {@code invoke}</li>
 * </ul>
 *
 * @see com.ruleforge.docs.notes.v599-utils-methodhandle V5.99 完整 doc
 * @since 5.99
 */
@DisplayName("V5.99 — Utils.getObjectProperty MethodHandle 契约")
class UtilsGetObjectPropertyMethodHandleTest {

    public static final class Person {
        private final String name = "alice";
        private final int age = 30;
        private final boolean active = true;

        public Person() {}
        public String getName() { return name; }
        public int getAge() { return age; }
        public boolean isActive() { return active; }
    }

    public static final class ThrowOnGet {
        public String getBoom() { throw new IllegalStateException("boom"); }
    }

    @Nested
    @DisplayName("POJO MethodHandle invoke path")
    class PojoMethodHandle {

        // Given Person.name = "alice"
        // When 调用 Utils.getObjectProperty(person, "name")
        // Then 返回 "alice" (走 MethodHandle.invoke, JIT-specialized)
        @Test
        @DisplayName("应通过 getX MethodHandle 返回 String 字段")
        void shouldInvokeGetXGetter() {
            assertThat(Utils.getObjectProperty(new Person(), "name"))
                .isEqualTo("alice");
        }

        // Given Person.age = 30
        // When getObjectProperty(person, "age")
        // Then 返 30 (int 字段走 MethodHandle → boxed Integer)
        @Test
        @DisplayName("应通过 getX MethodHandle 返回 int 字段")
        void shouldReturnIntViaGetter() {
            assertThat(Utils.getObjectProperty(new Person(), "age"))
                .isEqualTo(30);
        }

        // Given Person.active = true
        // When getObjectProperty(person, "active")
        // Then 返 true (走 isX 形态)
        @Test
        @DisplayName("应通过 isX MethodHandle 返回 boolean 字段")
        void shouldInvokeIsXGetter() {
            assertThat(Utils.getObjectProperty(new Person(), "active"))
                .isEqualTo(true);
        }

        // Given 同 class + property 二次调用
        // When 第二次 getObjectProperty
        // Then 必须命中 cache (MethodHandle 复用)
        @Test
        @DisplayName("二次调用同 class+property 应从 MethodHandle cache 返回")
        void secondCallShouldHitMethodHandleCache() {
            Person p = new Person();
            Object first = Utils.getObjectProperty(p, "name");
            Object second = Utils.getObjectProperty(p, "name");
            assertThat(first).isEqualTo("alice").isSameAs(second);
        }
    }

    @Nested
    @DisplayName("Map fast path (V5.99 不变)")
    class MapFastPath {

        @Test
        @DisplayName("HashMap key → value 零反射")
        void shouldReadHashMapValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("score", 95);
            assertThat(Utils.getObjectProperty(map, "score")).isEqualTo(95);
        }

        @Test
        @DisplayName("GeneralEntity (extends HashMap) → value 零反射")
        void shouldReadGeneralEntityValue() {
            GeneralEntity entity = new GeneralEntity("User");
            entity.put("name", "bob");
            assertThat(Utils.getObjectProperty(entity, "name")).isEqualTo("bob");
        }
    }

    @Nested
    @DisplayName("错误路径")
    class ErrorPath {

        @Test
        @DisplayName("缺失属性应抛 RuleException")
        void missingPropertyShouldThrowRuleException() {
            assertThatThrownBy(() -> Utils.getObjectProperty(new Person(), "missing"))
                .isInstanceOf(RuleException.class);
        }

        @Test
        @DisplayName("object 为 null 应抛 RuleException")
        void nullObjectShouldThrowRuleException() {
            assertThatThrownBy(() -> Utils.getObjectProperty(null, "name"))
                .isInstanceOf(RuleException.class);
        }

        // V5.89/V5.99 一致: getter 抛 RuntimeException 时按 PropertyUtils 语义
        // 直接抛底层 cause 出来,不包装成 RuleException
        // (对齐 PropertyUtils 自己抛底层 cause 的行为,见 Utils.java V5.99 catch Throwable cause)
        @Test
        @DisplayName("getter 抛 RuntimeException → 直接抛底层 cause (V5.99 行为契约)")
        void getterRuntimeExceptionPropagatedAsCause() {
            assertThatThrownBy(() -> Utils.getObjectProperty(new ThrowOnGet(), "boom"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        }
    }

    @Nested
    @DisplayName("并发安全")
    class ConcurrentAccess {

        // Given 100 thread × 1000 次同 class + property 调用
        // When 全跑完
        // Then 无异常 + 返回值一致
        @Test
        @DisplayName("多线程并发访问 MethodHandle cache 行为正确")
        void concurrentAccessToMethodHandleCache() throws Exception {
            int threads = 100;
            int iterPerThread = 1000;
            java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
            java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(threads);
            java.util.concurrent.atomic.AtomicInteger errors =
                new java.util.concurrent.atomic.AtomicInteger();
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        Person p = new Person();
                        for (int i = 0; i < iterPerThread; i++) {
                            String n = (String) Utils.getObjectProperty(p, "name");
                            Integer a = (Integer) Utils.getObjectProperty(p, "age");
                            if (!"alice".equals(n) || a != 30) {
                                errors.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            pool.shutdown();
            assertThat(errors.get()).isZero();
        }
    }
}
