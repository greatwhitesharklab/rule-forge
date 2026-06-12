package com.ruleforge.decision.flow.ir;

import com.ruleforge.decision.exception.FlowExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.37 B0 — Collaboration 索引 + lookup。
 *
 * <p>4 BDD:2 participant + 1 mf 索引 / 0 participant 空 list / 重复 participant id 抛错 /
 * findOutgoing source 命中。
 */
@DisplayName("Collaboration 不可变 + lookup 索引")
class CollaborationTest {

    @Test
    @DisplayName("Given 2 participants + 1 message flow,When ctor,Then 索引可查 + findParticipant 命中")
    void two_participants_indexed() {
        Participant p1 = new Participant("p1", "Credit", "Process_Credit");
        Participant p2 = new Participant("p2", "Underwriting", "Process_UW");
        MessageFlow mf = new MessageFlow(
            "mf1", "loan_approved",
            "p1", "sendNode1",
            "p2", "recvNode1");

        Collaboration coll = new Collaboration("collab1", "Loan Collab", List.of(p1, p2), List.of(mf));

        // 顺序保留
        assertEquals(2, coll.getParticipants().size());
        assertEquals(1, coll.getMessageFlows().size());

        // findParticipant
        Optional<Participant> hit1 = coll.findParticipant("p1");
        assertTrue(hit1.isPresent());
        assertSame(p1, hit1.get());

        Optional<Participant> hit2 = coll.findParticipant("p2");
        assertTrue(hit2.isPresent());
        assertSame(p2, hit2.get());

        // 不存在
        assertTrue(coll.findParticipant("nonexistent").isEmpty());

        // findIncoming(targetNodeId) — target 端查
        List<MessageFlow> incoming = coll.findIncoming("recvNode1");
        assertEquals(1, incoming.size());
        assertSame(mf, incoming.get(0));
    }

    @Test
    @DisplayName("Given 0 participants,When ctor,Then 空 list + findParticipant 返空")
    void empty_collaboration() {
        Collaboration coll = new Collaboration("c0", "Empty", List.of(), List.of());
        assertTrue(coll.getParticipants().isEmpty());
        assertTrue(coll.getMessageFlows().isEmpty());
        assertTrue(coll.findParticipant("anything").isEmpty());
        assertTrue(coll.findIncoming("anyNode").isEmpty());
        assertTrue(coll.findOutgoing("anyNode").isEmpty());
    }

    @Test
    @DisplayName("Given 重复 participant id,When ctor,Then 抛 FlowExecutionException")
    void duplicate_participant_id_throws() {
        Participant p1 = new Participant("dup", "P1", "Process_1");
        Participant p2 = new Participant("dup", "P2", "Process_2");
        assertThrows(FlowExecutionException.class,
            () -> new Collaboration("c1", "C1", List.of(p1, p2), List.of()));
    }

    @Test
    @DisplayName("Given 已知 outgoing source,When findOutgoing(sourceNodeId),Then 命中")
    void find_outgoing_by_source() {
        MessageFlow mf1 = new MessageFlow(
            "mf1", "loan_approved",
            "credit", "sendNode1",
            "underwriting", "recvNode1");
        MessageFlow mf2 = new MessageFlow(
            "mf2", "loan_rejected",
            "credit", "sendNode2",
            "underwriting", "recvNode2");

        Collaboration coll = new Collaboration("c1", "C1", List.of(), List.of(mf1, mf2));

        List<MessageFlow> outgoing = coll.findOutgoing("sendNode1");
        assertEquals(1, outgoing.size());
        assertSame(mf1, outgoing.get(0));

        // 2 message flow 都从 credit 出,fineOutgoing("credit") 不命中(按 nodeId 查)
        assertTrue(coll.findOutgoing("credit").isEmpty());
    }
}
