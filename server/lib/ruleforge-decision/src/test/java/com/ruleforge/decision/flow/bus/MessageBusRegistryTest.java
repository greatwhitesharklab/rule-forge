package com.ruleforge.decision.flow.bus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.39 A0 — MessageBusRegistry 行为规范。
 *
 * <p>5 BDD 分 4 组:优先级选主 / 启动期 fail-fast / 同 priority 接受任一 /
 * all() 列表。设计的语义参考阿里 compileflow 的 {@code @ExtensionRealization(priority=N)}
 * 优先级语义,但走 Spring {@code List<MessageBus>} 注入(项目惯例,不用 ServiceLoader)。
 */
@DisplayName("MessageBusRegistry 行为")
class MessageBusRegistryTest {

    /**
     * Test fixture:最小 {@link MessageBus} 实现,priority 由 ctor 决定。
     * 真实 publish/subscribe 路径走内部 {@link InMemoryMessageBus}。
     */
    private static final class PriorityBus implements MessageBus {
        private final int priority;
        private final InMemoryMessageBus delegate = new InMemoryMessageBus();

        PriorityBus(int priority) {
            this.priority = priority;
        }

        @Override public int priority() { return priority; }
        @Override public int publish(Message message) { return delegate.publish(message); }
        @Override public Subscription subscribe(String c, MessageHandler h) { return delegate.subscribe(c, h); }
        @Override public int subscriberCount(String c) { return delegate.subscriberCount(c); }
        @Override public Set<String> knownChannels() { return delegate.knownChannels(); }

        @Override public String toString() { return "PriorityBus[p=" + priority + "]"; }
    }

    @Nested
    @DisplayName("Group 1 — 优先级选主")
    class PrioritySelection {

        @Test
        @DisplayName("Given 多个 bus,When 构造,Then priority 最大的胜出")
        void primary_picks_highest_priority() {
            // Given: 3 个 bus,priority 0 / 5 / 10,顺序乱
            PriorityBus low  = new PriorityBus(0);
            PriorityBus high = new PriorityBus(10);
            PriorityBus mid  = new PriorityBus(5);

            // When: 构造 registry
            MessageBusRegistry registry = new MessageBusRegistry(List.of(low, mid, high));

            // Then: primary 是 high
            assertSame(high, registry.primary());
        }

        @Test
        @DisplayName("Given 单一 bus(priority=0 默认值),When 构造,Then primary 就是它")
        void single_bus_with_default_zero_priority_works() {
            InMemoryMessageBus bus = new InMemoryMessageBus();
            MessageBusRegistry registry = new MessageBusRegistry(List.of(bus));
            assertSame(bus, registry.primary());
            // 默认 priority=0
            assertEquals(0, bus.priority());
        }

        @Test
        @DisplayName("Given 负 priority(保留语义),When 构造,Then 仍按数字大小选主")
        void negative_priority_supported_for_fallback_role() {
            // 设计语义:负值用于"调试 / 旁路"等需要被压制的 bus
            PriorityBus main  = new PriorityBus(10);
            PriorityBus debug = new PriorityBus(-1);
            MessageBusRegistry registry = new MessageBusRegistry(List.of(main, debug));
            assertSame(main, registry.primary());
        }
    }

    @Nested
    @DisplayName("Group 2 — 启动期 fail-fast")
    class FailFast {

        @Test
        @DisplayName("Given 空 bus 列表,When 构造,Then 抛 IllegalStateException(且消息提示 MessageBus)")
        void empty_registry_fails_fast() {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new MessageBusRegistry(Collections.emptyList()));
            assertTrue(ex.getMessage().contains("MessageBus"),
                () -> "异常消息应提到 MessageBus 关键词,实际: " + ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Group 3 — 同 priority 接受任一")
    class SamePriority {

        @Test
        @DisplayName("Given 两个 bus 同 priority,When 构造,Then primary 是其中一个(bean 顺序不保证)")
        void same_priority_picks_any() {
            PriorityBus a = new PriorityBus(5);
            PriorityBus b = new PriorityBus(5);
            MessageBusRegistry registry = new MessageBusRegistry(List.of(a, b));
            MessageBus primary = registry.primary();
            assertTrue(primary == a || primary == b,
                () -> "primary 必须是其中之一,实际: " + primary);
        }
    }

    @Nested
    @DisplayName("Group 4 — all() 列表(供 V5.40+ broadcast 用)")
    class AllBuses {

        @Test
        @DisplayName("Given N 个 bus,When all(),Then 返回所有 N 个(原顺序)")
        void all_returns_all_buses_in_registration_order() {
            PriorityBus a = new PriorityBus(0);
            PriorityBus b = new PriorityBus(1);
            PriorityBus c = new PriorityBus(2);
            MessageBusRegistry registry = new MessageBusRegistry(List.of(a, b, c));
            List<MessageBus> all = registry.all();
            assertEquals(3, all.size());
            assertEquals(List.of(a, b, c), all);
        }
    }
}
