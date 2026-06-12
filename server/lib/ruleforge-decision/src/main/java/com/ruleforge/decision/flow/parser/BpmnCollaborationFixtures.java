package com.ruleforge.decision.flow.parser;

/**
 * V5.37 B0 — BPMN 2.0 §12 collaboration / lane / messageFlow 测试 fixture XML 串。
 *
 * <p>提供 5 套 XML,覆盖:
 * <ul>
 *   <li>{@link #SINGLE_PROCESS_XML} — 单 process,向后兼容</li>
 *   <li>{@link #TWO_POOL_LOAN_XML} — 2 pool + 1 message flow(主流 happy path)</li>
 *   <li>{@link #TWO_POOL_WITH_LANES_XML} — 2 pool + laneSet</li>
 *   <li>{@link #MISSING_SOURCE_REF_XML} — collab 但 messageFlow 缺 sourceRef,应抛</li>
 *   <li>{@link #DUP_PARTICIPANT_XML} — collab 但 participant id 重复,应抛</li>
 * </ul>
 */
public final class BpmnCollaborationFixtures {

    private BpmnCollaborationFixtures() {}

    /** 单 process — 老 caller 用,验证向后兼容。 */
    public static final String SINGLE_PROCESS_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
        "             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "             xmlns:ruleforge=\"http://ruleforge.com/schema\"\n" +
        "             targetNamespace=\"http://ruleforge.com/test\">\n" +
        "  <process id=\"Process_1\" name=\"Single Process\">\n" +
        "    <startEvent id=\"start1\" name=\"Start\"/>\n" +
        "    <serviceTask id=\"task1\" name=\"Task 1\"/>\n" +
        "    <endEvent id=\"end1\" name=\"End\"/>\n" +
        "    <sequenceFlow id=\"s1\" sourceRef=\"start1\" targetRef=\"task1\"/>\n" +
        "    <sequenceFlow id=\"s2\" sourceRef=\"task1\" targetRef=\"end1\"/>\n" +
        "  </process>\n" +
        "</definitions>\n";

    /**
     * 2 pool + 1 message flow:
     *   Pool Credit: startCredit → scriptCheck → sendLoanDecision (END + messageFlowId=MF1)
     *   Pool UW:     recvLoanDecision (START + messageFlowId=MF1) → processLoan → endUW
     *   MF1: p_credit/sendLoanDecision → p_uw/recvLoanDecision, name="loan_approved"
     */
    public static final String TWO_POOL_LOAN_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
        "             xmlns:ruleforge=\"http://ruleforge.com/schema\"\n" +
        "             targetNamespace=\"http://ruleforge.com/test\">\n" +
        "  <collaboration id=\"Collab_Loan\" name=\"Loan Collaboration\">\n" +
        "    <participant id=\"p_credit\" name=\"Credit Pool\" processRef=\"Process_Credit\"/>\n" +
        "    <participant id=\"p_uw\" name=\"Underwriting Pool\" processRef=\"Process_UW\"/>\n" +
        "    <messageFlow id=\"MF1\" name=\"loan_approved\"\n" +
        "                 sourceRef=\"sendLoanDecision\" targetRef=\"recvLoanDecision\"/>\n" +
        "  </collaboration>\n" +
        "  <process id=\"Process_Credit\" name=\"Credit Process\">\n" +
        "    <startEvent id=\"startCredit\" name=\"Credit Start\"/>\n" +
        "    <scriptTask id=\"scriptCheck\" name=\"Check Credit\"/>\n" +
        "    <endEvent id=\"sendLoanDecision\" name=\"Send Decision\">\n" +
        "      <extensionElements>\n" +
        "        <ruleforge:messageFlowRef id=\"MF1\"/>\n" +
        "      </extensionElements>\n" +
        "    </endEvent>\n" +
        "    <sequenceFlow id=\"sc1\" sourceRef=\"startCredit\" targetRef=\"scriptCheck\"/>\n" +
        "    <sequenceFlow id=\"sc2\" sourceRef=\"scriptCheck\" targetRef=\"sendLoanDecision\"/>\n" +
        "  </process>\n" +
        "  <process id=\"Process_UW\" name=\"UW Process\">\n" +
        "    <startEvent id=\"recvLoanDecision\" name=\"Recv Decision\">\n" +
        "      <extensionElements>\n" +
        "        <ruleforge:messageFlowRef id=\"MF1\"/>\n" +
        "      </extensionElements>\n" +
        "    </startEvent>\n" +
        "    <scriptTask id=\"processLoan\" name=\"Process Loan\"/>\n" +
        "    <endEvent id=\"endUW\" name=\"UW End\"/>\n" +
        "    <sequenceFlow id=\"uw1\" sourceRef=\"recvLoanDecision\" targetRef=\"processLoan\"/>\n" +
        "    <sequenceFlow id=\"uw2\" sourceRef=\"processLoan\" targetRef=\"endUW\"/>\n" +
        "  </process>\n" +
        "</definitions>\n";

    /**
     * 2 pool + 1 pool 含 laneSet(单 lane 简单 case):
     *   Pool Credit: 1 lane "lane_analyst" 含 scriptCheck 节点
     *   Pool UW: 简单 flow
     */
    public static final String TWO_POOL_WITH_LANES_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
        "             xmlns:ruleforge=\"http://ruleforge.com/schema\"\n" +
        "             targetNamespace=\"http://ruleforge.com/test\">\n" +
        "  <collaboration id=\"Collab_Lanes\" name=\"Lanes Collab\">\n" +
        "    <participant id=\"p_credit\" name=\"Credit\" processRef=\"Process_Credit\"/>\n" +
        "    <participant id=\"p_uw\" name=\"UW\" processRef=\"Process_UW\"/>\n" +
        "    <messageFlow id=\"MF1\" name=\"loan_approved\"\n" +
        "                 sourceRef=\"sendLoanDecision\" targetRef=\"recvLoanDecision\"/>\n" +
        "  </collaboration>\n" +
        "  <process id=\"Process_Credit\" name=\"Credit Process\">\n" +
        "    <laneSet id=\"ls_credit\">\n" +
        "      <lane id=\"lane_analyst\" name=\"Analyst\">\n" +
        "        <flowNodeRef>scriptCheck</flowNodeRef>\n" +
        "      </lane>\n" +
        "    </laneSet>\n" +
        "    <startEvent id=\"startCredit\" name=\"Credit Start\"/>\n" +
        "    <scriptTask id=\"scriptCheck\" name=\"Check Credit\"/>\n" +
        "    <endEvent id=\"sendLoanDecision\" name=\"Send Decision\"/>\n" +
        "    <sequenceFlow id=\"sc1\" sourceRef=\"startCredit\" targetRef=\"scriptCheck\"/>\n" +
        "    <sequenceFlow id=\"sc2\" sourceRef=\"scriptCheck\" targetRef=\"sendLoanDecision\"/>\n" +
        "  </process>\n" +
        "  <process id=\"Process_UW\" name=\"UW Process\">\n" +
        "    <startEvent id=\"recvLoanDecision\" name=\"Recv Decision\"/>\n" +
        "    <endEvent id=\"endUW\" name=\"UW End\"/>\n" +
        "    <sequenceFlow id=\"uw1\" sourceRef=\"recvLoanDecision\" targetRef=\"endUW\"/>\n" +
        "  </process>\n" +
        "</definitions>\n";

    /** messageFlow 缺 sourceRef — 应抛。 */
    public static final String MISSING_SOURCE_REF_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
        "             targetNamespace=\"http://ruleforge.com/test\">\n" +
        "  <collaboration id=\"Collab_Bad\">\n" +
        "    <participant id=\"p1\" name=\"P1\" processRef=\"Process_1\"/>\n" +
        "    <participant id=\"p2\" name=\"P2\" processRef=\"Process_2\"/>\n" +
        "    <messageFlow id=\"MF1\" name=\"loan\" targetRef=\"recv1\"/>\n" +
        "  </collaboration>\n" +
        "  <process id=\"Process_1\" name=\"P1\">\n" +
        "    <startEvent id=\"start1\"/>\n" +
        "    <endEvent id=\"end1\"/>\n" +
        "    <sequenceFlow id=\"s1\" sourceRef=\"start1\" targetRef=\"end1\"/>\n" +
        "  </process>\n" +
        "  <process id=\"Process_2\" name=\"P2\">\n" +
        "    <startEvent id=\"recv1\"/>\n" +
        "    <endEvent id=\"end2\"/>\n" +
        "    <sequenceFlow id=\"s2\" sourceRef=\"recv1\" targetRef=\"end2\"/>\n" +
        "  </process>\n" +
        "</definitions>\n";

    /** participant id 重复 — 应抛。 */
    public static final String DUP_PARTICIPANT_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
        "             targetNamespace=\"http://ruleforge.com/test\">\n" +
        "  <collaboration id=\"Collab_Dup\">\n" +
        "    <participant id=\"p1\" name=\"P1\" processRef=\"Process_1\"/>\n" +
        "    <participant id=\"p1\" name=\"P1Dup\" processRef=\"Process_2\"/>\n" +
        "  </collaboration>\n" +
        "  <process id=\"Process_1\" name=\"P1\">\n" +
        "    <startEvent id=\"start1\"/>\n" +
        "    <endEvent id=\"end1\"/>\n" +
        "    <sequenceFlow id=\"s1\" sourceRef=\"start1\" targetRef=\"end1\"/>\n" +
        "  </process>\n" +
        "  <process id=\"Process_2\" name=\"P2\">\n" +
        "    <startEvent id=\"start2\"/>\n" +
        "    <endEvent id=\"end2\"/>\n" +
        "    <sequenceFlow id=\"s2\" sourceRef=\"start2\" targetRef=\"end2\"/>\n" +
        "  </process>\n" +
        "</definitions>\n";
}
