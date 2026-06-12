package com.ruleforge.decision.flow.ir;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V5.37 B0 — Participant 3 字段不可变。
 *
 * <p>2 BDD:字段读出 + processRef 必填。
 */
@DisplayName("Participant 不可变 IR")
class ParticipantTest {

    @Test
    @DisplayName("Given id+name+processRef,When builder,Then 字段读出")
    void fields_read_back() {
        Participant p = new Participant("p1", "Credit Pool", "Process_Credit");
        assertEquals("p1", p.getId());
        assertEquals("Credit Pool", p.getName());
        assertEquals("Process_Credit", p.getProcessRef());
    }

    @Test
    @DisplayName("Given processRef=null,When builder,Then 抛 NPE")
    void processRef_is_required() {
        assertThrows(NullPointerException.class,
            () -> new Participant("p1", "name", null));
    }
}
