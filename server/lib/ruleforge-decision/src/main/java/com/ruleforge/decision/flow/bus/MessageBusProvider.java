package com.ruleforge.decision.flow.bus;

/**
 * V5.39 A0 — Bus 多实现优先级语义。
 *
 * <p>为 {@link MessageBus} 加 {@link #priority()} 钩子,让
 * {@link MessageBusRegistry} 在多个 bus 注入时按优先级选主(primary)。
 *
 * <p>语义参考阿里 compileflow 的 {@code @ExtensionRealization(priority = N)}
 * (越大越优先;同 priority 不保证顺序)。沿用项目"不用 ServiceLoader,Spring
 * {@code List<Interface>} 注入"的惯例 — 注入由
 * {@link MessageBusRegistry} 在 {@code @PostConstruct} 阶段完成。
 *
 * <p>约定:
 * <ul>
 *   <li>默认 {@code 0} — 单一实现不显式 override 即默认优先级</li>
 *   <li>调试 / 旁路 / 测试 bus 用负值,保证被生产 bus 压制</li>
 *   <li>同一 bean class 不允许重复注册(由 Spring 保证)</li>
 * </ul>
 */
public interface MessageBusProvider {

    /**
     * 优先级,越大越优先。
     *
     * @return 优先级整数;默认 0
     */
    default int priority() {
        return 0;
    }
}
