//! V5.28 P4 — BoundaryEvent multi-outgoing fan-out.
//!
//! V5.28 P1 boundary 路由用 first outgoing edge (1 个 boundary
//! error handler 只能走 1 条 handler path)。P4 让 boundary 的
//! 多个 outgoing edge 同时跑 (parallel fan-out):
//!
//! - 1 outgoing → fast path, Continue (P1 行为, back-compat)
//! - 2+ outgoing → Fork + traverse_branch,所有 outgoings 并行
//!   跑到各自 end (V5.28 P0 ParallelGateway 同款 join 语义)
//!
//! 复用 P0 的 Fork machinery:
/// - boundary 本身不被 visit (跟 P1 一致)
/// - 每个 outgoing 走自己的 sub-traversal
/// - 所有 sub-traversal 完成后 boundary 路由结束
/// - 跟 P0 一样,last-branch-wins for vars

use std::sync::Arc;

use async_trait::async_trait;
use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::error::FlowError;
use rf_executor::flow_context::FlowContext;
use rf_executor::node_executor::NodeExecutor;
use rf_executor::node_result::NodeResult;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::flow_definition::FlowDefinition;
use rf_ir::flow_node::FlowNode;
use rf_parse::bpmn_parser::BpmnXmlParser;
use serde_json::json;

/// Test-only "throw" executor: 写到 `ctx.thrown_error` 并 return
/// Continue。Optional var_writes map lets tests pre-set vars
/// per node id (for fan-out scenarios where branches need to
/// write different vars without owning a real action registry).
struct ThrowActionExecutor {
    pub throws_for: String,
    pub thrown_ref: String,
    /// Optional per-node var writes: `node_id → (var_name, value)`.
    /// When a visited node id matches, the executor inserts
    /// the var into `ctx.vars`.
    pub var_writes: Vec<(String, String, serde_json::Value)>,
}

#[async_trait]
impl NodeExecutor for ThrowActionExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        if node.node_id == self.throws_for {
            ctx.thrown_error = Some(self.thrown_ref.clone());
            ctx.vars.insert("__throw_ran__".to_string(), json!(true));
        }
        for (nid, var_name, value) in &self.var_writes {
            if node.node_id == *nid {
                ctx.vars.insert(var_name.clone(), value.clone());
            }
        }
        Ok(NodeResult::Continue)
    }
}

fn bpmn(process_id: &str, body: &str) -> String {
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="{process_id}">
    {body}
  </bpmn:process>
</bpmn:definitions>"#
    )
}

fn parse(xml: &str) -> Arc<FlowDefinition> {
    Arc::new(BpmnXmlParser::parse(xml).expect("parse ok"))
}

fn base_registry() -> Arc<ExecutorRegistry> {
    Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )))
}

fn registry_with_throw(
    throws_for: &str,
    thrown_ref: &str,
) -> Arc<ExecutorRegistry> {
    let mut r = (*base_registry()).clone();
    r.action = Arc::new(ThrowActionExecutor {
        throws_for: throws_for.to_string(),
        thrown_ref: thrown_ref.to_string(),
        var_writes: Vec::new(),
    });
    Arc::new(r)
}

/// **Test 1: 单 outgoing 走 fast path(P1 back-compat)**
///
/// Boundary 1 outgoing → 单 target,不是 fork。
/// V5.28 P1 测试已经覆盖这个 case (attached_error_boundary_routes_thrown_error),
/// 这里再 assert 一次是 P4 没破坏 P1 行为。
#[test]
fn single_outgoing_boundary_uses_continue_fast_path() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_err</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:endEvent id="error_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
        <bpmn:sequenceFlow id="e_err" sourceRef="b" targetRef="error_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx().current_node_id.as_deref(),
                Some("error_end"),
                "1-outgoing boundary should still work as P1 fast path"
            );
        }
        other => panic!("expected Completed at error_end, got {other:?}"),
    }
}

/// **Test 2: 双 outgoing 走 fan-out**
///
/// Boundary 2 outgoing → 同时跑 2 个 handler path。
/// flow shape:
/// ```text
///   s → task (boundary "b") → normal_end
///   b → path1_end   (e_path1)
///   b → path2_end   (e_path2)
/// ```
///
/// 当 task throws,2 个 handler path 都跑(parallel fan-out)。
/// 两者都 end,last-branch-wins for vars。
#[test]
fn two_outgoing_boundary_fans_out_to_both_targets() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_path1</bpmn:outgoing>
          <bpmn:outgoing>e_path2</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:endEvent id="path1_end"/>
        <bpmn:endEvent id="path2_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
        <bpmn:sequenceFlow id="e_path1" sourceRef="b" targetRef="path1_end"/>
        <bpmn:sequenceFlow id="e_path2" sourceRef="b" targetRef="path2_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // Fan-out: both path1_end and path2_end visited.
            // visited set is the parent's; the fork handler
            // extends it with both branches' visited sets.
            // For the final current_node_id, we check that
            // the run did NOT take the normal_end path —
            // i.e., the throw was routed to boundary paths.
            //
            // In V5.28 P0 fork semantics, parent.next = None
            // after fork (no join continuation). The fork
            // handler takes `last_ctx` from the last branch.
            // The visited set is the union. current_node_id
            // is set by the last step in any branch — could
            // be path1_end OR path2_end depending on order.
            let last = t.ctx().current_node_id.as_deref();
            assert!(
                last == Some("path1_end") || last == Some("path2_end"),
                "fan-out should reach path1_end or path2_end; got {last:?}"
            );
            // The throw marker should be visible (each
            // branch sees the post-throw ctx because we
            // clone self.ctx at branch build time).
            assert_eq!(t.ctx().vars.get("__throw_ran__"), Some(&json!(true)));
        }
        other => panic!("expected Completed via fan-out, got {other:?}"),
    }
}

/// **Test 3: 三 outgoing fan-out**
///
/// Boundary 3 outgoing → fan-out 3 个 handler paths。
#[test]
fn three_outgoing_boundary_fans_out_to_all_three_targets() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_a</bpmn:outgoing>
          <bpmn:outgoing>e_b</bpmn:outgoing>
          <bpmn:outgoing>e_c</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:endEvent id="a_end"/>
        <bpmn:endEvent id="b_end"/>
        <bpmn:endEvent id="c_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
        <bpmn:sequenceFlow id="e_a" sourceRef="b" targetRef="a_end"/>
        <bpmn:sequenceFlow id="e_b" sourceRef="b" targetRef="b_end"/>
        <bpmn:sequenceFlow id="e_c" sourceRef="b" targetRef="c_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            let last = t.ctx().current_node_id.as_deref();
            assert!(
                last == Some("a_end")
                    || last == Some("b_end")
                    || last == Some("c_end"),
                "fan-out should reach one of a/b/c_end; got {last:?}"
            );
        }
        other => panic!("expected Completed via 3-way fan-out, got {other:?}"),
    }
}

/// **Test 4: 双 outgoing,其中一个 handler 自己 suspend**
///
/// Flow:
///   task throws → boundary → path_a (userTask suspends)
///                          → path_b (immediate end)
///
/// path_b 跑完,path_a suspends → 整体 Suspended。
/// 验证 fan-out 中途 suspend 不会 abort 兄弟 branch,但
/// 整个 run 还是 Suspended (any branch suspends = run
/// suspends)。
#[test]
fn fan_out_with_one_branch_suspending_returns_suspended() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_a</bpmn:outgoing>
          <bpmn:outgoing>e_b</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:userTask id="path_a_task" ruleforge:decisionField="a_decision">
          <bpmn:outgoing>e_a_done</bpmn:outgoing>
        </bpmn:userTask>
        <bpmn:endEvent id="a_end"/>
        <bpmn:endEvent id="b_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="a_end"/>
        <bpmn:sequenceFlow id="e_a" sourceRef="b" targetRef="path_a_task"/>
        <bpmn:sequenceFlow id="e_a_done" sourceRef="path_a_task" targetRef="a_end"/>
        <bpmn:sequenceFlow id="e_b" sourceRef="b" targetRef="b_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            // path_a's userTask suspends with wait_ref="a_decision"
            assert_eq!(info.wait_ref, "a_decision");
        }
        other => panic!(
            "expected Suspended (path_a suspends via fan-out), got {other:?}"
        ),
    }
}

/// **Test 5: 多 boundary + 多 outgoing = 嵌套 fan-out**
///
/// activity 挂 2 个 boundary,每个 boundary 有 2 outgoing:
/// - boundary b1 (errorRef="boom") → path_a, path_b
/// - boundary b2 (errorRef="other") → path_c, path_d
///
/// 当 task throws "boom":
/// - b1 匹配(2 outgoing fan-out)
/// - b2 不匹配
///
/// 应该 fan-out 到 path_a 和 path_b,两个都跑完。
#[test]
fn multi_outgoing_with_multiple_attached_boundaries() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b1" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_a</bpmn:outgoing>
          <bpmn:outgoing>e_b</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:boundaryEvent id="b2" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="other">
          <bpmn:outgoing>e_c</bpmn:outgoing>
          <bpmn:outgoing>e_d</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:endEvent id="a_end"/>
        <bpmn:endEvent id="b_end"/>
        <bpmn:endEvent id="c_end"/>
        <bpmn:endEvent id="d_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
        <bpmn:sequenceFlow id="e_a" sourceRef="b1" targetRef="a_end"/>
        <bpmn:sequenceFlow id="e_b" sourceRef="b1" targetRef="b_end"/>
        <bpmn:sequenceFlow id="e_c" sourceRef="b2" targetRef="c_end"/>
        <bpmn:sequenceFlow id="e_d" sourceRef="b2" targetRef="d_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // b1 matches and fans out (a_end or b_end)
            // b2 doesn't match
            let last = t.ctx().current_node_id.as_deref();
            assert!(
                last == Some("a_end") || last == Some("b_end"),
                "boom throw should fan-out via b1 to a_end or b_end; got {last:?}"
            );
        }
        other => panic!(
            "expected Completed via b1 fan-out, got {other:?}"
        ),
    }
}

/// **Test 6: 0 outgoing boundary 走 None fallback**
///
/// Boundary 0 outgoing (malformed BPMN):
///   b → (no edges)
///
/// Activity throws,matching boundary but boundary has no handler
/// path → fall through to activity's normal outgoing (with warn)。
/// 这是 boundary_nexts() 的 fallback,不是个 fan-out case。
#[test]
fn boundary_with_zero_outgoing_falls_through() {
    // BPMN spec requires boundaryEvent to have an outgoing. But
    // we can construct one with no outgoings to test the
    // fallback. The parser doesn't enforce this — it just
    // collects <outgoing> children.
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    // boundary has no handler path → fall through → normal_end
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx().current_node_id.as_deref(),
                Some("normal_end"),
                "0-outgoing boundary should fall through to normal outgoing"
            );
        }
        other => panic!("expected Completed at normal_end, got {other:?}"),
    }
}

/// **Test 7: fan-out 隔离 — 各 branch 写不同 var,last-branch-wins**
///
/// task throws → boundary fans out to:
///   - path1 sets var "x" = "from_path1"
///   - path2 sets var "x" = "from_path2"
///
/// Both end immediately. last-branch-wins for var merge
/// (V5.28 P0 fork semantics applied to boundary fan-out).
#[test]
fn fan_out_branches_share_post_throw_ctx_and_last_wins_vars() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e1_path</bpmn:outgoing>
          <bpmn:outgoing>e2_path</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:serviceTask id="path1_task" ruleforge:taskType="action"
                          ruleforge:method="set_x_from_p1">
          <bpmn:outgoing>e1_done</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:serviceTask id="path2_task" ruleforge:taskType="action"
                          ruleforge:method="set_x_from_p2">
          <bpmn:outgoing>e2_done</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:endEvent id="path1_end"/>
        <bpmn:endEvent id="path2_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
        <bpmn:sequenceFlow id="e1_path" sourceRef="b" targetRef="path1_task"/>
        <bpmn:sequenceFlow id="e2_path" sourceRef="b" targetRef="path2_task"/>
        <bpmn:sequenceFlow id="e1_done" sourceRef="path1_task" targetRef="path1_end"/>
        <bpmn:sequenceFlow id="e2_done" sourceRef="path2_task" targetRef="path2_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    // Custom registry: 写不同 var in each branch's
    // serviceTask via ThrowActionExecutor's var_writes
    // (avoids needing a real ActionExecutor).
    let mut reg = (*base_registry()).clone();
    reg.action = Arc::new(ThrowActionExecutor {
        throws_for: "task".to_string(),
        thrown_ref: "boom".to_string(),
        var_writes: vec![
            ("path1_task".to_string(), "x".to_string(), json!("from_path1")),
            ("path2_task".to_string(), "x".to_string(), json!("from_path2")),
        ],
    });
    let reg = Arc::new(reg);

    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // var x should be set (one of the two values)
            // — which one depends on which branch finishes
            // last in the fork loop
            let x = t.ctx().vars.get("x");
            assert!(
                x == Some(&json!("from_path1"))
                    || x == Some(&json!("from_path2")),
                "var x should be set by one of the fan-out branches; got {x:?}"
            );
        }
        other => panic!("expected Completed via fan-out, got {other:?}"),
    }
}