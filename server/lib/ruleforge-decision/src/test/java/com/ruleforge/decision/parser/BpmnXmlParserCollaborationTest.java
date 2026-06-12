package com.ruleforge.decision.parser;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.parser.BpmnCollaborationFixtures;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import com.ruleforge.decision.flow.ir.BpmnDefinition;
import com.ruleforge.decision.flow.ir.Collaboration;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.Lane;
import com.ruleforge.decision.flow.ir.MessageFlow;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.ir.Participant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.37 B0 — BpmnXmlParser 升级到返 BpmnDefinition。
 *
 * <p>8 BDD:2-pool 解析 / messageFlow 端点 attach / 单 process 向后兼容 / lane 解析 /
 * missing sourceRef / 重复 participant / parseSingleProcess 便利方法。
 */
@DisplayName("BpmnXmlParser 解析 collaboration / lane / messageFlow")
class BpmnXmlParserCollaborationTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    @Test
    @DisplayName("Given TWO_POOL_LOAN_XML,When parse,Then BpmnDefinition + 2 process + 1 message flow + collab 索引对")
    void two_pool_loan_xml_parses_to_collaboration() {
        BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.TWO_POOL_LOAN_XML);

        assertTrue(bpmn.isMultiPool());
        Collaboration coll = bpmn.collaboration();
        assertNotNull(coll);
        assertEquals("Collab_Loan", coll.getId());
        assertEquals(2, coll.getParticipants().size());
        assertEquals(1, coll.getMessageFlows().size());

        // participant 索引
        Participant p1 = coll.findParticipant("p_credit").orElseThrow();
        assertEquals("Process_Credit", p1.getProcessRef());
        Participant p2 = coll.findParticipant("p_uw").orElseThrow();
        assertEquals("Process_UW", p2.getProcessRef());

        // 2 process
        assertEquals(2, bpmn.processes().size());
        assertNotNull(bpmn.requireProcess("Process_Credit"));
        assertNotNull(bpmn.requireProcess("Process_UW"));

        // 1 message flow,channel 对
        MessageFlow mf = coll.getMessageFlows().get(0);
        assertEquals("MF1", mf.getId());
        assertEquals("p_credit", mf.getSourceParticipantId());
        assertEquals("p_uw", mf.getTargetParticipantId());
        assertEquals("pool:p_credit_to_p_uw:loan_approved", mf.channelName());
    }

    @Test
    @DisplayName("Given TWO_POOL_LOAN_XML,When parse,Then START 节点带 messageFlowId + END 节点带 messageFlowId")
    void message_flow_endpoints_attached_to_nodes() {
        BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.TWO_POOL_LOAN_XML);

        // Pool Credit 的 EndEvent "sendLoanDecision" 应带 messageFlowId="MF1"
        FlowDefinition credit = bpmn.requireProcess("Process_Credit");
        FlowNode endNode = credit.getNode("sendLoanDecision");
        assertNotNull(endNode);
        assertEquals(NodeType.END_EVENT, endNode.getType());
        assertEquals("MF1", endNode.getMessageFlowId());

        // Pool UW 的 StartEvent "recvLoanDecision" 应带 messageFlowId="MF1"
        FlowDefinition uw = bpmn.requireProcess("Process_UW");
        FlowNode startNode = uw.getNode("recvLoanDecision");
        assertNotNull(startNode);
        assertEquals(NodeType.START_EVENT, startNode.getType());
        assertEquals("MF1", startNode.getMessageFlowId());
    }

    @Test
    @DisplayName("Given SINGLE_PROCESS_XML,When parse,Then BpmnDefinition.collaboration=null + 1 process")
    void single_process_xml_has_null_collaboration() {
        BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.SINGLE_PROCESS_XML);

        assertFalse(bpmn.isMultiPool());
        assertNull(bpmn.collaboration());
        assertEquals(1, bpmn.processes().size());
        assertNotNull(bpmn.requireProcess("Process_1"));
    }

    @Test
    @DisplayName("Given SINGLE_PROCESS_XML,When parse,Then 所有 node.messageFlowId=null")
    void single_process_nodes_have_null_message_flow_id() {
        BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.SINGLE_PROCESS_XML);

        FlowDefinition def = bpmn.requireProcess("Process_1");
        for (FlowNode n : def.getNodes().values()) {
            assertNull(n.getMessageFlowId(),
                "单 process 不应有 messageFlowId, 实际: " + n.getNodeId() + "=" + n.getMessageFlowId());
        }
    }

    @Test
    @DisplayName("Given 带 laneSet 的 process,When parse,Then FlowNode.laneId 写到对应 lane")
    void lane_set_resolves_to_node_lane_id() {
        BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.TWO_POOL_WITH_LANES_XML);

        FlowDefinition credit = bpmn.requireProcess("Process_Credit");
        // verify lane 索引
        assertTrue(credit.getLanes().containsKey("lane_analyst"),
            "lane_analyst 应在 FlowDefinition.lanes");
        Lane analystLane = credit.getLanes().get("lane_analyst");
        assertEquals(List.of("scriptCheck"), analystLane.getFlowNodeRefs());

        // scriptCheck 节点应带 laneId
        FlowNode sc = credit.getNode("scriptCheck");
        assertNotNull(sc);
        assertEquals("lane_analyst", sc.getLaneId());
    }

    @Test
    @DisplayName("Given collab XML 但 missing sourceRef,When parse,Then 抛 FlowExecutionException")
    void message_flow_missing_source_ref_throws() {
        assertThrows(FlowExecutionException.class,
            () -> parser.parse(BpmnCollaborationFixtures.MISSING_SOURCE_REF_XML));
    }

    @Test
    @DisplayName("Given collab XML 但重复 participant id,When parse,Then 抛 FlowExecutionException")
    void duplicate_participant_id_throws() {
        assertThrows(FlowExecutionException.class,
            () -> parser.parse(BpmnCollaborationFixtures.DUP_PARTICIPANT_XML));
    }

    @Test
    @DisplayName("Given 旧 caller 用 parseSingleProcess,When 调,Then 返 FlowDefinition(向后兼容)")
    void parse_single_process_returns_flow_definition_for_backward_compat() {
        FlowDefinition def = parser.parseSingleProcess(BpmnCollaborationFixtures.SINGLE_PROCESS_XML);

        assertNotNull(def);
        assertEquals("Process_1", def.getProcessId());
        assertNotNull(def.getStartNodeId());
    }
}
