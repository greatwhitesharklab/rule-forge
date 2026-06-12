package com.ruleforge.decision.flow;

import com.ruleforge.decision.exception.FlowExecutionException;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V5.35 A5 — IntermediateEvent 节点种类(SEALED 模拟 Rust enum)。
 *
 * <p>Mirror Rust V5.32 {@code intermediate_event.rs} IntermediateEventKind 7 variant 契约:
 * <ul>
 *   <li>{@link None} — 无 eventType,透传(同 startEvent/endEvent 行为)</li>
 *   <li>{@link Message} — messageCatch (eventType=message + eventName)</li>
 *   <li>{@link Signal} — signalCatch (eventType=signal + eventName)</li>
 *   <li>{@link Timer} — timerCatch (eventType=timer + eventDuration ISO 8601)</li>
 *   <li>{@link Conditional} — conditionalCatch (eventType=conditional + condition UEL)</li>
 *   <li>{@link LinkThrow} — linkThrow (eventType=linkThrow + linkName → BRANCH 跳到 linkCatch)</li>
 *   <li>{@link LinkCatch} — linkCatch (eventType=linkCatch + linkName → 透传)</li>
 * </ul>
 *
 * <p>{@code fromAttrs} 工厂规则:
 * <ul>
 *   <li>无 {@code ruleforge:eventType} → None</li>
 *   <li>已知 eventType 缺必填字段 → 抛 {@link FlowExecutionException}</li>
 *   <li>未知 eventType → 抛 {@link FlowExecutionException}(早 fail)</li>
 * </ul>
 */
public sealed interface IntermediateEventKind permits IntermediateEventKind.None,
                                                    IntermediateEventKind.Message,
                                                    IntermediateEventKind.Signal,
                                                    IntermediateEventKind.Timer,
                                                    IntermediateEventKind.Conditional,
                                                    IntermediateEventKind.LinkThrow,
                                                    IntermediateEventKind.LinkCatch {

    /** 默认透传(Runner 走节点默认 out 推进)。 */
    final class None implements IntermediateEventKind {
        public static final None INSTANCE = new None();
        private None() {}
    }

    /** messageCatch:eventType=message + eventName=name;executor 抛 AsyncNodeSuspendException(waitRef=message:&lt;name&gt;)。 */
    final class Message implements IntermediateEventKind {
        private final String name;
        public Message(String name) { this.name = name; }
        public String name() { return name; }
    }

    /** signalCatch:eventType=signal + eventName=name;executor 抛 Suspend(waitRef=signal:&lt;name&gt;)。 */
    final class Signal implements IntermediateEventKind {
        private final String name;
        public Signal(String name) { this.name = name; }
        public String name() { return name; }
    }

    /** timerCatch:eventType=timer + eventDuration=ISO 8601(PT5S/PT1M/PT2H/PT1D/PT1W)。 */
    final class Timer implements IntermediateEventKind {
        private final Duration duration;
        public Timer(Duration duration) { this.duration = duration; }
        public Duration duration() { return duration; }
    }

    /** conditionalCatch:eventType=conditional + condition=UEL expr;executor 抛 Suspend(payload.condition=expr)。 */
    final class Conditional implements IntermediateEventKind {
        private final String expr;
        public Conditional(String expr) { this.expr = expr; }
        public String expr() { return expr; }
    }

    /** linkThrow:eventType=linkThrow + linkName=midway;executor 返回 BranchTransition(targetNodeId=linkCatch)。 */
    final class LinkThrow implements IntermediateEventKind {
        private final String linkName;
        public LinkThrow(String linkName) { this.linkName = linkName; }
        public String linkName() { return linkName; }
    }

    /** linkCatch:eventType=linkCatch + linkName=midway;executor 透传 Continue。 */
    final class LinkCatch implements IntermediateEventKind {
        private final String linkName;
        public LinkCatch(String linkName) { this.linkName = linkName; }
        public String linkName() { return linkName; }
    }

    // -------- factory --------

    /**
     * 从 BPMN 节点的 extensionAttrs 解析 IntermediateEventKind。
     *
     * @param attrs {@code key=value} map,key 形式如 {@code ruleforge:eventType} / {@code ruleforge:eventName} / {@code ruleforge:linkName}
     * @return 7 variant 之一
     * @throws FlowExecutionException 缺必填字段或不识别 eventType
     */
    static IntermediateEventKind fromAttrs(Map<String, String> attrs) {
        if (attrs == null) return None.INSTANCE;
        String eventType = attrs.get("ruleforge:eventType");
        if (eventType == null || eventType.isBlank()) {
            return None.INSTANCE;
        }
        return switch (eventType) {
            case "message" -> {
                String name = attrs.get("ruleforge:eventName");
                if (name == null || name.isBlank()) {
                    throw new FlowExecutionException(
                        "IntermediateCatch eventType=message requires non-empty ruleforge:eventName");
                }
                yield new Message(name);
            }
            case "signal" -> {
                String name = attrs.get("ruleforge:eventName");
                if (name == null || name.isBlank()) {
                    throw new FlowExecutionException(
                        "IntermediateCatch eventType=signal requires non-empty ruleforge:eventName");
                }
                yield new Signal(name);
            }
            case "timer" -> {
                String dur = attrs.get("ruleforge:eventDuration");
                if (dur == null || dur.isBlank()) {
                    throw new FlowExecutionException(
                        "IntermediateCatch eventType=timer requires non-empty ruleforge:eventDuration");
                }
                yield new Timer(parseIsoDuration(dur));
            }
            case "conditional" -> {
                String expr = attrs.get("ruleforge:condition");
                if (expr == null || expr.isBlank()) {
                    throw new FlowExecutionException(
                        "IntermediateCatch eventType=conditional requires non-empty ruleforge:condition");
                }
                yield new Conditional(expr);
            }
            case "linkThrow" -> {
                String name = attrs.get("ruleforge:linkName");
                if (name == null || name.isBlank()) {
                    throw new FlowExecutionException(
                        "IntermediateCatch eventType=linkThrow requires non-empty ruleforge:linkName");
                }
                yield new LinkThrow(name);
            }
            case "linkCatch" -> {
                String name = attrs.get("ruleforge:linkName");
                if (name == null || name.isBlank()) {
                    throw new FlowExecutionException(
                        "IntermediateCatch eventType=linkCatch requires non-empty ruleforge:linkName");
                }
                yield new LinkCatch(name);
            }
            default -> throw new FlowExecutionException(
                "Unknown IntermediateCatch eventType=" + eventType);
        };
    }

    /**
     * V5.35 A5 — 解析 ISO 8601 duration(简化版,Rust V5.32 同语义)。
     * 接受格式:
     * <ul>
     *   <li>{@code PT<n>S} — 秒</li>
     *   <li>{@code PT<n>M} — 分</li>
     *   <li>{@code PT<n>H} — 时</li>
     *   <li>{@code PT<n>D} — 天</li>
     *   <li>{@code PT<n>W} — 周(=7天)</li>
     *   <li>复合 {@code PT1H30M}(按 unit order: H, M, S, D, W)</li>
     * </ul>
     * <b>不</b>接受 {@code 5S} / {@code P1D} / {@code PTxyz} 等非 PT 前缀或不识别 unit。
     *
     * @param s 待解析字符串(必须以 "PT" 开头)
     * @return Duration
     * @throws FlowExecutionException 格式错误
     */
    static Duration parseIsoDuration(String s) {
        if (s == null || s.isBlank()) {
            throw new FlowExecutionException("ISO 8601 duration is empty");
        }
        if (!s.startsWith("PT")) {
            throw new FlowExecutionException("ISO 8601 duration must start with 'PT', got: " + s);
        }
        // 解析 "PT1H30M" 这种 — 按 unit 拆
        String body = s.substring(2);
        if (body.isEmpty()) {
            throw new FlowExecutionException("ISO 8601 duration has no value, got: " + s);
        }
        // 匹配 (数字)(unit) 对 — 顺序: H / M / S / D / W
        Pattern p = Pattern.compile("(\\d+)([HMSDW])");
        Matcher m = p.matcher(body);
        long seconds = 0L;
        boolean matched = false;
        int lastEnd = 0;
        while (m.find()) {
            if (m.start() != lastEnd) {
                throw new FlowExecutionException("ISO 8601 duration has unrecognized segment, got: " + s);
            }
            long n = Long.parseLong(m.group(1));
            String unit = m.group(2);
            switch (unit) {
                case "H" -> seconds += n * 3600L;
                case "M" -> seconds += n * 60L;
                case "S" -> seconds += n;
                case "D" -> seconds += n * 86400L;
                case "W" -> seconds += n * 7L * 86400L;
                default -> throw new FlowExecutionException("Unknown ISO 8601 unit: " + unit);
            }
            lastEnd = m.end();
            matched = true;
        }
        if (!matched) {
            throw new FlowExecutionException("ISO 8601 duration has no (number+unit) pair, got: " + s);
        }
        if (lastEnd != body.length()) {
            throw new FlowExecutionException("ISO 8601 duration has trailing garbage, got: " + s);
        }
        return Duration.ofSeconds(seconds);
    }
}
