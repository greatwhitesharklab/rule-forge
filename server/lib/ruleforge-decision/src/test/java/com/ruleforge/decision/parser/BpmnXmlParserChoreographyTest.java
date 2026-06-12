package com.ruleforge.decision.parser;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.ir.BpmnDefinition;
import com.ruleforge.decision.flow.ir.Choreography;
import com.ruleforge.decision.flow.parser.BpmnCollaborationFixtures;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.37 B1 — Parser 解析 §11 Choreography。
 *
 * <p>8 BDD(覆盖独立 choreography / collab 内嵌 choreography / 校验失败路径):
 * <ul>
 *   <li>独立 choreography 根 + 单 task</li>
 *   <li>独立 choreography:多 task + outgoing 顺序保留</li>
 *   <li>独立 choreography:messageFlowRef 未知 — 不抛(独立时无 collab 校验)</li>
 *   <li>独立 choreography:无 task 时 list 为空</li>
 *   <li>collab 内嵌 choreography:task 命中 + messageFlowRef 校验通过</li>
 *   <li>collab 内嵌 choreography:messageFlowRef 找不到 → 抛错</li>
 *   <li>collab 无 choreography:collaboration 不变 + choreography=null</li>
 *   <li>单 process 路径:choreography 仍为 null(向后兼容)</li>
 * </ul>
 */
@DisplayName("BpmnXmlParser — Choreography 解析")
class BpmnXmlParserChoreographyTest {

    private BpmnXmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new BpmnXmlParser();
    }

    @Nested
    @DisplayName("独立 <choreography> 根")
    class StandaloneChoreography {

        @Test
        @DisplayName("Given STANDALONE_CHOREO_XML, when parse, then choreography 不为 null + 1 task")
        void standalone_with_one_task() {
            BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.STANDALONE_CHOREO_XML);
            Choreography choreo = bpmn.choreography();
            assertNotNull(choreo, "独立 choreography 根 → bpmn.choreography() != null");
            assertEquals(1, choreo.getTasks().size());
            assertEquals("CT1", choreo.getTasks().get(0).getId());
            assertEquals("p_a", choreo.getTasks().get(0).getInitiatingParticipantId());
            // 独立时无 collab
            assertNull(bpmn.collaboration());
            assertTrue(bpmn.processes().isEmpty());
        }

        @Test
        @DisplayName("Given 独立 choreography 2 task + outgoing, when parse, then outgoing 顺序保留 + findTask 命中")
        void two_tasks_with_outgoing() {
            BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.STANDALONE_CHOREO_TWO_TASK_XML);
            Choreography choreo = bpmn.choreography();
            assertNotNull(choreo);
            assertEquals(2, choreo.getTasks().size());
            assertEquals(List.of("Flow_CT1_CT2"), choreo.getTasks().get(0).getOutgoingTaskIds());
            assertNotNull(choreo.findTask("CT2").orElse(null));
        }

        @Test
        @DisplayName("Given 独立 choreography + 未知 messageFlowRef, when parse, then 不抛(独立无 collab 校验)")
        void standalone_skips_message_flow_validation() {
            // STANDALONE_CHOREO_XML 里 messageFlowRef="MF_NOT_IN_COLLAB" — 独立时无 collab 不校验
            BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.STANDALONE_CHOREO_XML);
            assertNotNull(bpmn.choreography());
            assertEquals("MF_NOT_IN_COLLAB",
                bpmn.choreography().getTasks().get(0).getMessageFlowId());
        }

        @Test
        @DisplayName("Given 独立 choreography 无 task, when parse, then 0 task + choreography 不为 null")
        void empty_choreography() {
            BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.STANDALONE_CHOREO_EMPTY_XML);
            assertNotNull(bpmn.choreography());
            assertTrue(bpmn.choreography().getTasks().isEmpty());
        }
    }

    @Nested
    @DisplayName("Collaboration 内嵌 Choreography")
    class ChoreographyInsideCollaboration {

        @Test
        @DisplayName("Given COLLAB_WITH_CHOREO_XML, when parse, then choreography 嵌入 + messageFlowRef 命中 MF1")
        void collab_with_choreo_passes_validation() {
            BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.COLLAB_WITH_CHOREO_XML);
            assertNotNull(bpmn.collaboration());
            Choreography choreo = bpmn.choreography();
            assertNotNull(choreo);
            assertEquals(1, choreo.getTasks().size());
            // MF1 在 collab 里存在 → 校验通过
            assertEquals("MF1", choreo.getTasks().get(0).getMessageFlowId());
        }

        @Test
        @DisplayName("Given COLLAB_WITH_BAD_CHOREO_XML(task 引用未知 MF), when parse, then 抛 FlowExecutionException")
        void collab_with_choreo_fails_validation() {
            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> parser.parse(BpmnCollaborationFixtures.COLLAB_WITH_BAD_CHOREO_XML));
            assertTrue(ex.getMessage().contains("unknown messageFlowId"));
        }

        @Test
        @DisplayName("Given 纯 collab XML(TWO_POOL_LOAN_XML,无 choreography), when parse, then choreography=null + collab 不变")
        void collab_without_choreo() {
            BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.TWO_POOL_LOAN_XML);
            assertNotNull(bpmn.collaboration());
            assertNull(bpmn.choreography(), "collab 自身没 choreography 子元素时为 null");
        }
    }

    @Nested
    @DisplayName("向后兼容")
    class BackwardCompat {

        @Test
        @DisplayName("Given 单 process XML, when parse, then choreography=null + collaboration=null")
        void single_process_keeps_choreo_null() {
            BpmnDefinition bpmn = parser.parse(BpmnCollaborationFixtures.SINGLE_PROCESS_XML);
            assertNull(bpmn.collaboration());
            assertNull(bpmn.choreography());
        }
    }
}
