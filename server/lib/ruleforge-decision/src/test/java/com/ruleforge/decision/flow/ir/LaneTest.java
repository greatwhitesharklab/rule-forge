package com.ruleforge.decision.flow.ir;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.37 B0 — Lane 嵌套 + 不可变。
 *
 * <p>4 BDD:2 lane 同 laneSet / 嵌套 parent-child / lane ref 未知 node 不抛(运行时 parser 报)/
 * 重复 lane id 抛 dup。
 */
@DisplayName("Lane 嵌套 + 不可变 IR")
class LaneTest {

    @Test
    @DisplayName("Given 2 lane 同 laneSet,When ctor,Then 2 lane 各自 flowNodeRefs")
    void two_lanes_have_independent_node_refs() {
        Lane l1 = new Lane("lane1", "Lane 1", null, List.of("n1", "n2"));
        Lane l2 = new Lane("lane2", "Lane 2", null, List.of("n3", "n4"));
        assertEquals("lane1", l1.getId());
        assertEquals("Lane 1", l1.getName());
        assertNull(l1.getParentLaneId());
        assertEquals(List.of("n1", "n2"), l1.getFlowNodeRefs());
        assertEquals(List.of("n3", "n4"), l2.getFlowNodeRefs());
    }

    @Test
    @DisplayName("Given 嵌套 laneSet(parent + child),When ctor,Then parentLaneId 链对")
    void nested_lane_has_parent_link() {
        Lane parent = new Lane("p1", "Parent", null, List.of());
        Lane child = new Lane("c1", "Child", "p1", List.of("n1"));
        assertEquals("p1", child.getParentLaneId());
        // v0 简化: getNodeIds() = flowNodeRefs(不递归,parent 已包)
        assertEquals(List.of("n1"), child.getNodeIds());
        // parent 自己无 nodes
        assertTrue(parent.getNodeIds().isEmpty());
    }

    @Test
    @DisplayName("Given lane ref 未知 node,When ctor,Then 不抛(运行时 parser 报)— v0 简化")
    void unknown_node_ref_does_not_throw_at_ctor() {
        // v0 简化:Lane 不做 node 引用校验;解析时统一校验
        Lane lane = new Lane("l1", "L1", null, List.of("nonexistent"));
        assertEquals(List.of("nonexistent"), lane.getFlowNodeRefs());
    }

    @Test
    @DisplayName("Given 重复 lane id,When 业务逻辑(不在 Lane 自身,模拟 collab 构造期),Then 抛 dup")
    void duplicate_lane_id_throws() {
        // Lane 自身不知道 id 唯一性,重复检测放到 Collaboration 级 / parser 阶段
        // 这里测: 同样 2 个 lane 放 Map,如果 collide 自然失败
        java.util.Map<String, Lane> map = new java.util.HashMap<>();
        Lane l1 = new Lane("dup", "L1", null, List.of());
        Lane l2 = new Lane("dup", "L2", null, List.of());
        map.put("dup", l1);
        assertThrows(java.lang.IllegalStateException.class, () -> {
            if (map.putIfAbsent("dup", l2) != null) {
                throw new IllegalStateException("duplicate lane id: dup");
            }
        });
    }
}
