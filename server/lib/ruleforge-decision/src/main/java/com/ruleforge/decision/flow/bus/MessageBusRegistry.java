package com.ruleforge.decision.flow.bus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * V5.39 A0 — Bus 多实现选择器。
 *
 * <p>在 Spring 启动时把 {@code List<MessageBus>} 按 {@link MessageBus#priority()}
 * 排序,挑出 {@link #primary()} 作为默认 bus(供 {@link MessageBusPublisher} 用)。
 *
 * <p>设计要点:
 * <ul>
 *   <li>空注册列表 → 启动期 fail-fast({@link IllegalStateException}),
 *       避免运行时 {@code NoSuchElementException}</li>
 *   <li>同 priority 不保证顺序 — bean 注入顺序由 Spring 决定</li>
 *   <li>{@link #all()} 留作 V5.40+ broadcast 场景:发一条 message 同时打到所有 bus
 *       (如日志 bus + 主 bus + shadow bus)</li>
 *   <li>不动 bus 实现 — bus 之间彼此不感知,registry 是唯一的多实现协调点</li>
 * </ul>
 *
 * <p>不是 {@code @Primary} 而走独立 registry 的原因:
 * {@code @Primary} 是 bean 级别的"哪注入哪优先",没法同时暴露 {@code all()}
 * 给 broadcast 场景。registry 显式列出"我有 N 个 bus"的语义更清晰。
 */
@Slf4j
@Component
public class MessageBusRegistry {

    private final List<MessageBus> buses;
    private final MessageBus primary;

    public MessageBusRegistry(List<MessageBus> buses) {
        if (buses == null || buses.isEmpty()) {
            throw new IllegalStateException(
                "No MessageBus bean registered. At least one MessageBus implementation "
                    + "(e.g. InMemoryMessageBus) must be on the Spring classpath.");
        }
        this.buses = List.copyOf(buses);
        this.primary = this.buses.stream()
            .max(Comparator.comparingInt(MessageBus::priority))
            .orElseThrow();  // 不可能(empty case 已抛)
        log.info("[BUS-REG] initialized with {} bus(es); primary={} (priority={})",
            this.buses.size(), primary, primary.priority());
    }

    /**
     * 优先级最高的 bus(默认 publish 走这里)。
     */
    public MessageBus primary() {
        return primary;
    }

    /**
     * 所有已注册的 bus(原顺序)。供 V5.40+ broadcast 用。
     */
    public List<MessageBus> all() {
        return buses;
    }
}
