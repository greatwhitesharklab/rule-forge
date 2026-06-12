package com.ruleforge.decision.flow.ir;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.37 B1 — ChoreographyTask IR 行为规范。
 *
 * <p>6 BDD(原计划 2,补 4):
 * <ul>
 *   <li>字段读写</li>
 *   <li>三个角色必填 NPE</li>
 *   <li>initiating 必须是 first/second 之一(否则 IAE)</li>
 *   <li>outgoing null → empty list(防御性)</li>
 *   <li>outgoing 内容读出</li>
 *   <li>messageFlowId 可空</li>
 * </ul>
 */
@DisplayName("ChoreographyTask IR 行为")
class ChoreographyTaskTest {

    @Nested
    @DisplayName("字段读写")
    class FieldAccess {

        @Test
        @DisplayName("Given 完整 7 字段, when ctor, then 字段读出正确")
        void all_fields_roundtrip() {
            ChoreographyTask t = new ChoreographyTask(
                "CT1", "credit-notify-uw",
                "p_credit", "p_credit", "p_uw",
                "MF1", List.of("Flow_CT2"));
            assertEquals("CT1", t.getId());
            assertEquals("credit-notify-uw", t.getName());
            assertEquals("p_credit", t.getInitiatingParticipantId());
            assertEquals("p_credit", t.getFirstParticipantId());
            assertEquals("p_uw", t.getSecondParticipantId());
            assertEquals("MF1", t.getMessageFlowId());
            assertEquals(List.of("Flow_CT2"), t.getOutgoingTaskIds());
        }

        @Test
        @DisplayName("Given messageFlowId=null, when ctor, then 字段为 null(独立 choreography 允许)")
        void message_flow_id_nullable() {
            ChoreographyTask t = new ChoreographyTask(
                "CT1", null, "p_a", "p_a", "p_b", null, null);
            assertNull(t.getMessageFlowId());
            assertTrue(t.getOutgoingTaskIds().isEmpty());
        }
    }

    @Nested
    @DisplayName("不变量")
    class Invariants {

        @Test
        @DisplayName("Given initiatingParticipantId=null, when ctor, then 抛 NPE")
        void initiating_required() {
            assertThrows(NullPointerException.class, () ->
                new ChoreographyTask("CT1", null, null, "p_a", "p_b", null, null));
        }

        @Test
        @DisplayName("Given firstParticipantId=null, when ctor, then 抛 NPE")
        void first_required() {
            assertThrows(NullPointerException.class, () ->
                new ChoreographyTask("CT1", null, "p_a", null, "p_b", null, null));
        }

        @Test
        @DisplayName("Given secondParticipantId=null, when ctor, then 抛 NPE")
        void second_required() {
            assertThrows(NullPointerException.class, () ->
                new ChoreographyTask("CT1", null, "p_a", "p_b", null, null, null));
        }

        @Test
        @DisplayName("Given initiating 不在 first/second 集合, when ctor, then 抛 IAE")
        void initiating_must_be_one_of_first_or_second() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new ChoreographyTask("CT1", null, "p_outsider", "p_a", "p_b", null, null));
            assertTrue(ex.getMessage().contains("initiatingParticipantRef"));
            assertTrue(ex.getMessage().contains("must be one of"));
        }
    }
}
