package com.ruleforge.decision.flow.bus;

import java.util.Set;

/**
 * V5.38 C0 — Bus 传输层 SPI(generic,flow-agnostic)。
 *
 * <p>Bus 不知道 flow / engine / FlowContext,只懂 channel + handler。
 * Flow 那边用 {@code FlowResumer}({@code Message → engine.resume} 桥接)
 * 做"知道 flow 的事"。
 *
 * <p>v0 实现 {@link InMemoryMessageBus} 用 {@code ConcurrentHashMap + CopyOnWriteArrayList}
 * 走同步 iterate。future external bus(Kafka / NATS / Postgres-LISTEN)做 impl 切换,
 * consumer 端不感知。
 *
 * <p>设计要点:
 * <ul>
 *   <li>同步 publish — handler 在发布线程上跑,delivered 计数 = handler 跑完次数(异常不计入)</li>
 *   <li>Subscription dedup — 同一 handler 重复 subscribe 同 channel 只一份</li>
 *   <li>handler 异常隔离 — 不冒泡,不挂兄弟 handler,不挂 publish 调用方</li>
 *   <li>channel 命名 — 走 {@link MessageKind#channelFor} 集中拼,bus 不假定 prefix 格式</li>
 * </ul>
 */
public interface MessageBus extends MessageBusProvider {

    /**
     * 同步发布一条 message 到其 channel。
     *
     * @param message 不可变消息,channel 决定路由
     * @return 实际 handler 跑完的次数(异常被 catch 不算;0 = 该 channel 无订阅)
     */
    int publish(Message message);

    /**
     * 订阅 channel。
     *
     * <p>返回的 {@link Subscription} 必须调 {@link Subscription#close()} 退订(idempotent)。
     * 典型用法:try-with-resources。
     *
     * <p>同 handler 重复 subscribe 同 channel 只一份(delivered 不 double-fire)。
     *
     * @param channel 完整 channel 名(已用 {@link MessageKind#channelFor} 拼好)
     * @param handler 同步回调
     */
    Subscription subscribe(String channel, MessageHandler handler);

    /**
     * 便利重载:按 kind + name 拼 channel 后订阅。
     */
    default Subscription subscribe(MessageKind kind, String name, MessageHandler handler) {
        return subscribe(MessageKind.channelFor(kind, name), handler);
    }

    /** 诊断:某 channel 的订阅者数量(测试用,非 v0 contract 强制)。 */
    int subscriberCount(String channel);

    /** 诊断:当前已注册的所有 channel(测试用)。 */
    Set<String> knownChannels();

    /**
     * 订阅句柄 — try-with-resources 友好。
     * 调 {@link #close()} 退订;close 是 idempotent,重复调不抛。
     */
    interface Subscription extends AutoCloseable {
        @Override void close();
        String channel();
    }
}
