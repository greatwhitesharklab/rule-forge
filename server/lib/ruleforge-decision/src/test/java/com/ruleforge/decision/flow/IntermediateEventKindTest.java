package com.ruleforge.decision.flow;

import com.ruleforge.decision.exception.FlowExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.35 A5 — IntermediateEventKind 工厂 + parseIsoDuration 行为规范。
 *
 * <p>Mirror Rust V5.32 {@code intermediate_event.rs} IntermediateEventKind 7 variant 契约:
 * <ul>
 *   <li>{@code None} — 无 eventType,默认 pass-through</li>
 *   <li>{@code Message{name}} — messageCatch,name 必须非空</li>
 *   <li>{@code Signal{name}} — signalCatch,name 必须非空</li>
 *   <li>{@code Timer{duration}} — timerCatch,ISO 8601 duration (PT5S/PT1M/PT2H/PT1D/PT1W)</li>
 *   <li>{@code Conditional{expr}} — conditionalCatch,expr 允许空(factory 不报错,executor 校验)</li>
 *   <li>{@code LinkThrow{linkName}} — linkThrow,linkName 必须非空</li>
 *   <li>{@code LinkCatch{linkName}} — linkCatch,linkName 必须非空</li>
 * </ul>
 */
@DisplayName("IntermediateEventKind — 7 variant 工厂 + parseIsoDuration")
class IntermediateEventKindTest {

    @Test
    @DisplayName("Given 无 ruleforge:eventType,When fromAttrs,Then None")
    void from_attrs_none_when_no_event_type() {
        IntermediateEventKind kind = IntermediateEventKind.fromAttrs(Map.of());
        assertInstanceOf(IntermediateEventKind.None.class, kind);
    }

    @Test
    @DisplayName("Given null attrs,When fromAttrs,Then None(空 attrMap 兜底)")
    void from_attrs_null_attrs_returns_none() {
        IntermediateEventKind kind = IntermediateEventKind.fromAttrs(null);
        assertInstanceOf(IntermediateEventKind.None.class, kind);
    }

    @Nested
    @DisplayName("Message")
    class Message {

        @Test
        @DisplayName("Given eventType=message + name=foo,When fromAttrs,Then Message{name=foo}")
        void from_attrs_message_with_name() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "message");
            attrs.put("ruleforge:eventName", "loan_approved");
            IntermediateEventKind kind = IntermediateEventKind.fromAttrs(attrs);
            assertInstanceOf(IntermediateEventKind.Message.class, kind);
            assertEquals("loan_approved", ((IntermediateEventKind.Message) kind).name());
        }

        @Test
        @DisplayName("Given eventType=message 缺 name,When fromAttrs,Then 抛 FlowExecutionException")
        void from_attrs_message_requires_name() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "message");
            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> IntermediateEventKind.fromAttrs(attrs));
            assertTrue(ex.getMessage().toLowerCase().contains("message")
                    && ex.getMessage().toLowerCase().contains("name"),
                "msg should mention message+name, got: " + ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Signal")
    class Signal {

        @Test
        @DisplayName("Given eventType=signal + name=bar,When fromAttrs,Then Signal{name=bar}")
        void from_attrs_signal_with_name() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "signal");
            attrs.put("ruleforge:eventName", "fraud_alert");
            IntermediateEventKind kind = IntermediateEventKind.fromAttrs(attrs);
            assertInstanceOf(IntermediateEventKind.Signal.class, kind);
            assertEquals("fraud_alert", ((IntermediateEventKind.Signal) kind).name());
        }

        @Test
        @DisplayName("Given eventType=signal 缺 name,When fromAttrs,Then 抛错")
        void from_attrs_signal_requires_name() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "signal");
            assertThrows(FlowExecutionException.class,
                () -> IntermediateEventKind.fromAttrs(attrs));
        }
    }

    @Nested
    @DisplayName("Timer")
    class Timer {

        @Test
        @DisplayName("Given eventType=timer + duration=PT5S/1M/2H/1D/1W,When fromAttrs,Then 5 unit 全解析对")
        void from_attrs_timer_parses_iso_5_units() {
            assertEquals(Duration.ofSeconds(5),
                ((IntermediateEventKind.Timer) IntermediateEventKind.fromAttrs(Map.of(
                    "ruleforge:eventType", "timer",
                    "ruleforge:eventDuration", "PT5S"))).duration());
            assertEquals(Duration.ofMinutes(1),
                ((IntermediateEventKind.Timer) IntermediateEventKind.fromAttrs(Map.of(
                    "ruleforge:eventType", "timer",
                    "ruleforge:eventDuration", "PT1M"))).duration());
            assertEquals(Duration.ofHours(2),
                ((IntermediateEventKind.Timer) IntermediateEventKind.fromAttrs(Map.of(
                    "ruleforge:eventType", "timer",
                    "ruleforge:eventDuration", "PT2H"))).duration());
            assertEquals(Duration.ofDays(1),
                ((IntermediateEventKind.Timer) IntermediateEventKind.fromAttrs(Map.of(
                    "ruleforge:eventType", "timer",
                    "ruleforge:eventDuration", "PT1D"))).duration());
            // 1W = 7 days
            assertEquals(Duration.ofDays(7),
                ((IntermediateEventKind.Timer) IntermediateEventKind.fromAttrs(Map.of(
                    "ruleforge:eventType", "timer",
                    "ruleforge:eventDuration", "PT1W"))).duration());
        }

        @Test
        @DisplayName("Given duration 格式错(5S / PTxyz / P1D),When fromAttrs,Then 抛错")
        void from_attrs_timer_rejects_bad_duration() {
            // 5S 没 PT 前缀
            assertThrows(FlowExecutionException.class,
                () -> IntermediateEventKind.fromAttrs(Map.of(
                    "ruleforge:eventType", "timer",
                    "ruleforge:eventDuration", "5S")));
            // PTxyz 不识别 unit
            assertThrows(FlowExecutionException.class,
                () -> IntermediateEventKind.fromAttrs(Map.of(
                    "ruleforge:eventType", "timer",
                    "ruleforge:eventDuration", "PTxyz")));
            // P1D 不是 PT
            assertThrows(FlowExecutionException.class,
                () -> IntermediateEventKind.fromAttrs(Map.of(
                    "ruleforge:eventType", "timer",
                    "ruleforge:eventDuration", "P1D")));
        }

        @Test
        @DisplayName("Given eventType=timer 缺 duration,When fromAttrs,Then 抛错")
        void from_attrs_timer_requires_duration() {
            assertThrows(FlowExecutionException.class,
                () -> IntermediateEventKind.fromAttrs(Map.of(
                    "ruleforge:eventType", "timer")));
        }
    }

    @Nested
    @DisplayName("Conditional")
    class Conditional {

        @Test
        @DisplayName("Given eventType=conditional + expr=approved==true,When fromAttrs,Then Conditional{expr=...}")
        void from_attrs_conditional_with_expr() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "conditional");
            attrs.put("ruleforge:condition", "approved == true");
            IntermediateEventKind kind = IntermediateEventKind.fromAttrs(attrs);
            assertInstanceOf(IntermediateEventKind.Conditional.class, kind);
            assertEquals("approved == true", ((IntermediateEventKind.Conditional) kind).expr());
        }

        @Test
        @DisplayName("Given eventType=conditional 缺 expr,When fromAttrs,Then 抛错")
        void from_attrs_conditional_requires_expr() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "conditional");
            assertThrows(FlowExecutionException.class,
                () -> IntermediateEventKind.fromAttrs(attrs));
        }
    }

    @Nested
    @DisplayName("Link")
    class Link {

        @Test
        @DisplayName("Given eventType=linkThrow + linkName=midway,When fromAttrs,Then LinkThrow{name=midway}")
        void from_attrs_link_throw_with_name() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "linkThrow");
            attrs.put("ruleforge:linkName", "midway");
            IntermediateEventKind kind = IntermediateEventKind.fromAttrs(attrs);
            assertInstanceOf(IntermediateEventKind.LinkThrow.class, kind);
            assertEquals("midway", ((IntermediateEventKind.LinkThrow) kind).linkName());
        }

        @Test
        @DisplayName("Given eventType=linkThrow 缺 linkName,When fromAttrs,Then 抛错")
        void from_attrs_link_throw_requires_name() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "linkThrow");
            assertThrows(FlowExecutionException.class,
                () -> IntermediateEventKind.fromAttrs(attrs));
        }

        @Test
        @DisplayName("Given eventType=linkCatch + linkName=midway,When fromAttrs,Then LinkCatch{name=midway}")
        void from_attrs_link_catch_with_name() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "linkCatch");
            attrs.put("ruleforge:linkName", "midway");
            IntermediateEventKind kind = IntermediateEventKind.fromAttrs(attrs);
            assertInstanceOf(IntermediateEventKind.LinkCatch.class, kind);
            assertEquals("midway", ((IntermediateEventKind.LinkCatch) kind).linkName());
        }
    }

    @Test
    @DisplayName("Given 未知 eventType,When fromAttrs,Then 抛错(早 fail)")
    void from_attrs_unknown_event_type_throws() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("ruleforge:eventType", "unicorn");
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
            () -> IntermediateEventKind.fromAttrs(attrs));
        assertTrue(ex.getMessage().toLowerCase().contains("unknown")
                || ex.getMessage().toLowerCase().contains("eventtype"),
            "msg should mention unknown/eventType, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("parseIsoDuration — 5 unit 各自正确解析")
    void parse_iso_duration_units_covered() {
        assertEquals(Duration.ofSeconds(5), IntermediateEventKind.parseIsoDuration("PT5S"));
        assertEquals(Duration.ofMinutes(1), IntermediateEventKind.parseIsoDuration("PT1M"));
        assertEquals(Duration.ofHours(2), IntermediateEventKind.parseIsoDuration("PT2H"));
        assertEquals(Duration.ofDays(1), IntermediateEventKind.parseIsoDuration("PT1D"));
        assertEquals(Duration.ofDays(7), IntermediateEventKind.parseIsoDuration("PT1W"));
    }

    @Test
    @DisplayName("parseIsoDuration — 复合值 PT1H30M 也接受")
    void parse_iso_duration_compound() {
        assertEquals(Duration.ofMinutes(90), IntermediateEventKind.parseIsoDuration("PT1H30M"));
    }

    @Test
    @DisplayName("parseIsoDuration — 错误格式抛 FlowExecutionException(不抛 NumberFormatException)")
    void parse_iso_duration_throws_flow_exception() {
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
            () -> IntermediateEventKind.parseIsoDuration("PTxyz"));
        assertNotNull(ex.getMessage());
    }
}
