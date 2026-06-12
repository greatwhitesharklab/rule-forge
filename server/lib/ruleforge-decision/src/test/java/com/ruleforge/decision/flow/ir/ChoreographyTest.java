package com.ruleforge.decision.flow.ir;

import com.ruleforge.decision.exception.FlowExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.37 B1 — Choreography 顶层 IR 行为 + 跟 Collaboration 交叉校验。
 *
 * <p>5 BDD:
 * <ul>
 *   <li>空 choreography:索引可查 + findTask 返空</li>
 *   <li>2 task 索引 + findTask 命中</li>
 *   <li>重复 task id 抛错</li>
 *   <li>validateMessageFlowRefs:已知 mf 静默通过</li>
 *   <li>validateMessageFlowRefs:未知 mfRef 抛错</li>
 * </ul>
 */
@DisplayName("Choreography 顶层 IR 行为")
class ChoreographyTest {

    private ChoreographyTask task(String id, String mf) {
        return new ChoreographyTask(id, null, "p_a", "p_a", "p_b", mf, null);
    }

    @Nested
    @DisplayName("索引 + findTask")
    class Lookup {

        @Test
        @DisplayName("Given 2 task, when ctor, then 索引可查 + findTask 命中 + 顺序保留")
        void two_tasks_indexed() {
            ChoreographyTask t1 = task("CT1", "MF1");
            ChoreographyTask t2 = task("CT2", "MF2");
            Choreography c = new Choreography("ch1", "loan-orchestration", List.of(t1, t2));
            assertEquals(List.of(t1, t2), c.getTasks());
            assertSame(t1, c.findTask("CT1").orElseThrow());
            assertSame(t2, c.findTask("CT2").orElseThrow());
        }

        @Test
        @DisplayName("Given 0 task + 未知 id, when findTask, then 返空 + 不抛")
        void empty_or_unknown_returns_empty() {
            Choreography c = new Choreography("ch1", null, List.of());
            assertTrue(c.getTasks().isEmpty());
            assertTrue(c.findTask("CT_X").isEmpty());
        }
    }

    @Nested
    @DisplayName("重复检测")
    class Deduplication {

        @Test
        @DisplayName("Given 重复 task id, when ctor, then 抛 FlowExecutionException")
        void duplicate_task_id_throws() {
            ChoreographyTask t1 = task("CT1", null);
            ChoreographyTask t2 = task("CT1", null);
            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> new Choreography("ch1", null, List.of(t1, t2)));
            assertTrue(ex.getMessage().contains("duplicate task id CT1"));
        }
    }

    @Nested
    @DisplayName("跟 Collaboration 交叉校验")
    class CrossValidation {

        @Test
        @DisplayName("Given choreo task messageFlowId 在 collab 内存在, when validate, then 不抛")
        void known_message_flow_ref_passes() {
            ChoreographyTask t1 = task("CT1", "MF1");
            Choreography c = new Choreography("ch1", null, List.of(t1));
            // 造一个含 MF1 的 collab
            Participant p1 = new Participant("p_credit", "信贷", "Process_Credit");
            Participant p2 = new Participant("p_uw", "审核", "Process_UW");
            MessageFlow mf1 = new MessageFlow("MF1", null,
                "p_credit", "send", "p_uw", "recv");
            Collaboration collab = new Collaboration("c1", null, List.of(p1, p2), List.of(mf1));
            c.validateMessageFlowRefs(collab);  // 不抛
        }

        @Test
        @DisplayName("Given choreo task messageFlowId 找不到, when validate, then 抛 FlowExecutionException")
        void unknown_message_flow_ref_throws() {
            ChoreographyTask t1 = task("CT1", "MF_DOES_NOT_EXIST");
            Choreography c = new Choreography("ch1", null, List.of(t1));
            Participant p1 = new Participant("p_credit", "信贷", "Process_Credit");
            Collaboration collab = new Collaboration("c1", null, List.of(p1), List.of());
            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> c.validateMessageFlowRefs(collab));
            assertTrue(ex.getMessage().contains("MF_DOES_NOT_EXIST"));
            assertTrue(ex.getMessage().contains("not in collaboration"));
        }

        @Test
        @DisplayName("Given collab=null(独立 choreography), when validate, then 静默通过(无 collab 不校验)")
        void no_collab_skips_validation() {
            ChoreographyTask t1 = task("CT1", "MF1");
            Choreography c = new Choreography("ch1", null, List.of(t1));
            c.validateMessageFlowRefs(null);  // 不抛
        }
    }
}
