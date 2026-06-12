package com.ruleforge.decision.flow.bus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * V5.38 C0 — Bus publish 的 ergonomic sugar。
 *
 * <p>调用方(SendTask / B0 跨池 flow end / C2 throwEvent)不直接构造
 * {@link Message} — 用 3 个高阶方法,自动填 sourcePool / sourceNodeId / timestamp。
 *
 * <p>v0 sourcePool 总是 null(单池场景);B0 跨池会把 pool 信息塞进 payload
 * 而不是 Message.sourcePool(因为 pool 概念属于 BPMN 协作层,不属于 bus 通用数据)。
 */
@Component
@RequiredArgsConstructor
public class MessageBusPublisher {

    private final MessageBusRegistry registry;

    /**
     * C1 SendTask 用:发一条 {@code message:<name>}。
     *
     * @return 实际派发的 handler 数(0 = 无订阅)
     */
    public int publishMessage(String name, Map<String, Object> payload,
                               String sourceFlowRunId, String sourceNodeId) {
        Message m = Message.builder()
            .name(name)
            .channel(MessageKind.channelFor(MessageKind.Message.INSTANCE, name))
            .payload(payload)
            .sourceNodeId(sourceNodeId)
            .timestamp(Instant.now())
            .build();
        return registry.primary().publish(m);
    }

    /**
     * C2 intermediate throw signal 用(transport 层实际走 {@link FlowResumer},
     * 但 channel 命名仍走这里给日志/审计用)— v0 signal **不**经 bus publish,
     * 这个方法保留是给未来扩展:如果需要 bus 转发 signal 给外部 bus adapter
     * (Kafka topic "signal:xxx"),可以走这个入口。
     */
    public int publishSignal(String name, Map<String, Object> payload,
                              String sourceFlowRunId, String sourceNodeId) {
        Message m = Message.builder()
            .name(name)
            .channel(MessageKind.channelFor(MessageKind.Signal.INSTANCE, name))
            .payload(payload)
            .sourceNodeId(sourceNodeId)
            .timestamp(Instant.now())
            .build();
        return registry.primary().publish(m);
    }

    /**
     * B0 跨池 message flow 用:从 fromPool 池发到 toPool 池的 message。
     * channel = {@code pool:<fromPool>_to_<toPool>:<name>}。
     */
    public int publishPoolMessage(String fromPool, String toPool, String name,
                                   Map<String, Object> payload,
                                   String sourceFlowRunId, String sourceNodeId) {
        MessageKind kind = new MessageKind.PoolMessage(fromPool, toPool);
        Message m = Message.builder()
            .name(name)
            .channel(MessageKind.channelFor(kind, name))
            .payload(payload)
            .sourcePool(fromPool)
            .sourceNodeId(sourceNodeId)
            .timestamp(Instant.now())
            .build();
        return registry.primary().publish(m);
    }
}
