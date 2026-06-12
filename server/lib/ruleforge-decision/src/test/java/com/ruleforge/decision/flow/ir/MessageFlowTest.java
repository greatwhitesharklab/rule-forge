package com.ruleforge.decision.flow.ir;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.37 B0 — MessageFlow 4-tuple + channel name 派生。
 *
 * <p>4 BDD:有 name 时的 channel / name=null fallback / matchesSource / matchesTarget。
 */
@DisplayName("MessageFlow 4-tuple + channel 派生")
class MessageFlowTest {

    @Test
    @DisplayName("Given 4-tuple + name,When channelName,Then pool:src_to_tgt:name")
    void channel_name_uses_explicit_name() {
        MessageFlow mf = new MessageFlow(
            "mf1", "loan_approved",
            "credit", "sendNode1",
            "underwriting", "recvNode1");
        assertEquals("pool:credit_to_underwriting:loan_approved", mf.channelName());
    }

    @Test
    @DisplayName("Given name=null,When channelName,Then 用 sourceNode+'_to_'+targetNode fallback")
    void channel_name_falls_back_to_node_pair() {
        MessageFlow mf = new MessageFlow(
            "mf1", null,
            "credit", "sendNode1",
            "underwriting", "recvNode1");
        assertEquals("pool:credit_to_underwriting:sendNode1_to_recvNode1", mf.channelName());
    }

    @Test
    @DisplayName("Given matchesSource(pool, node),When 调用,Then true/false 正确(2-case)")
    void matches_source() {
        MessageFlow mf = new MessageFlow(
            "mf1", "loan_approved",
            "credit", "sendNode1",
            "underwriting", "recvNode1");
        assertTrue(mf.matchesSource("credit", "sendNode1"));
        assertFalse(mf.matchesSource("underwriting", "recvNode1"));
        assertFalse(mf.matchesSource("credit", "wrongNode"));
        assertFalse(mf.matchesSource("wrongPool", "sendNode1"));
    }

    @Test
    @DisplayName("Given matchesTarget(pool, node),When 调用,Then true/false 正确(2-case)")
    void matches_target() {
        MessageFlow mf = new MessageFlow(
            "mf1", "loan_approved",
            "credit", "sendNode1",
            "underwriting", "recvNode1");
        assertTrue(mf.matchesTarget("underwriting", "recvNode1"));
        assertFalse(mf.matchesTarget("credit", "sendNode1"));
        assertFalse(mf.matchesTarget("underwriting", "wrongNode"));
        assertFalse(mf.matchesTarget("wrongPool", "recvNode1"));
    }
}
