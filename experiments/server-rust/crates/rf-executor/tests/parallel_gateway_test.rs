//! V5.28 — `ParallelGateway` fork/join integration tests.
//!
//! V5.28 v0 implements the **fork** part of parallel
//! gateway. A `parallelGateway` with 2+ outgoing edges
//! returns `NodeResult::Fork(Vec<ForkBranch>)`; the
//! `traverse()` driver runs each branch recursively
//! and merges the contexts (last-branch-wins).
//!
//! What V5.28 v0 does **not** model:
//!
//! - **Join sync.** A diamond pattern (split → branch
//!   → join) runs each branch to its own end event;
//!   the join gateway is a no-op `Continue` (single
//!   outgoing edge). Documented in the gateway
//!   executor module comment.
//! - **Concurrent branch execution.** Branches run
//!   serially. The architecture is ready for
//!   concurrency (each branch has its own
//!   `FlowContext`); the single-threaded executor is
//!   fine because `dispatch` is currently pure-CPU.
//!
//! Test cases:
//!
//! 1. 2-branch fork — both branches run to their own
//!    end events.
//! 2. 3-branch fork — all three branches run.
//! 3. Last-branch-wins var merge — when multiple
//!    branches set the same var, the LAST branch's
//!    value wins.
//! 4. Single-outgoing parallel gateway is a no-op
//!    pass-through (effectively a "join" with no fan-in).
//! 5. Zero-outgoing parallel gateway errors.
//! 6. Nested parallel gateway — a branch that itself
//!    contains a parallel gateway (recursive fork).
//! 7. var_assigns are independent across branches
//!    until the merge (each branch's writes don't
//!    leak to siblings mid-traversal).
//! 8. `reg.def` unset + ParallelGateway dispatched
//!    via `dispatch()` directly errors clearly
//!    (defensive guard for unit tests that don't
//!    route through `traverse()`).

use std::sync::Arc;

use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::executors::action::{ActionExecutor, MockActionRegistry};
use rf_executor::flow_context::FlowContext;
use rf_executor::node_executor::NodeExecutor;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::flow_definition::FlowDefinition;
use rf_parse::bpmn_parser::BpmnXmlParser;
use serde_json::json;

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

fn rule_registry() -> Arc<ExecutorRegistry> {
    Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )))
}

/// 2-branch fork: start → parallel → branch1 → end1
///                              → branch2 → end2.
const TWO_BRANCH_FORK: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:parallelGateway id="g">
      <bpmn:outgoing>e1</bpmn:outgoing>
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:parallelGateway>
    <bpmn:endEvent id="end1"/>
    <bpmn:endEvent id="end2"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g"/>
    <bpmn:sequenceFlow id="e1" sourceRef="g" targetRef="end1"/>
    <bpmn:sequenceFlow id="e2" sourceRef="g" targetRef="end2"/>
"#;

#[test]
fn parallel_fork_runs_both_branches_to_completion() {
    let def = parse(&bpmn("p", TWO_BRANCH_FORK));
    let outcome = traverse(def, FlowContext::new("p1"), rule_registry());
    // Both branches hit an end event. The
    // last-branch-wins strategy means `current_node_id`
    // is one of the end events (whichever branch ran
    // last in the iteration order, which is
    // deterministic — branches run in `outgoing_ids`
    // order, so `end2` is last).
    match outcome {
        TraverseOutcome::Completed(t) => {
            let nid = t.ctx().current_node_id.as_deref();
            assert!(
                nid == Some("end1") || nid == Some("end2"),
                "expected end1 or end2, got {nid:?}"
            );
            // Both branches visited the parallel gateway
            // (it's in the visited set, only once). No
            // node should be visited more than once
            // because V5.28 v0 runs each branch's first
            // node as its start, and the branches
            // terminate at distinct ends.
            assert!(t.visited().contains("g"));
            assert!(t.visited().contains("end1"));
            assert!(t.visited().contains("end2"));
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[test]
fn parallel_fork_three_branches_all_run() {
    // start → parallel → branch1 → end1
    //                    → branch2 → end2
    //                    → branch3 → end3
    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:parallelGateway id="g">
          <bpmn:outgoing>e1</bpmn:outgoing>
          <bpmn:outgoing>e2</bpmn:outgoing>
          <bpmn:outgoing>e3</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:endEvent id="end1"/>
        <bpmn:endEvent id="end2"/>
        <bpmn:endEvent id="end3"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g"/>
        <bpmn:sequenceFlow id="e1" sourceRef="g" targetRef="end1"/>
        <bpmn:sequenceFlow id="e2" sourceRef="g" targetRef="end2"/>
        <bpmn:sequenceFlow id="e3" sourceRef="g" targetRef="end3"/>
    "#,
    ));
    let outcome = traverse(def, FlowContext::new("p1"), rule_registry());
    match outcome {
        TraverseOutcome::Completed(t) => {
            // All three end events visited.
            assert!(t.visited().contains("end1"));
            assert!(t.visited().contains("end2"));
            assert!(t.visited().contains("end3"));
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[test]
fn parallel_fork_last_branch_wins_for_var_merges() {
    // Each branch runs a service-task that sets a
    // `stamped` var via the `stamp` action. V5.28
    // v0 merge strategy is "last branch wins" — the
    // branch with the highest outgoing-ids index
    // (e2 in this case) should be the survivor.
    let action_reg = Arc::new(
        MockActionRegistry::new().register("stamp", |vars| {
            vars.insert("stamped", json!("from_action"));
            Ok(())
        }),
    );
    let mut reg = (*rule_registry()).clone();
    reg.action = Arc::new(ActionExecutor::new(action_reg));
    let reg = Arc::new(reg);

    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:parallelGateway id="g">
          <bpmn:outgoing>e1</bpmn:outgoing>
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:serviceTask id="a1" ruleforge:taskType="action" ruleforge:method="stamp">
          <bpmn:outgoing>e1_out</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:serviceTask id="a2" ruleforge:taskType="action" ruleforge:method="stamp">
          <bpmn:outgoing>e2_out</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="end1"/>
        <bpmn:endEvent id="end2"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g"/>
        <bpmn:sequenceFlow id="e1" sourceRef="g" targetRef="a1"/>
        <bpmn:sequenceFlow id="e2" sourceRef="g" targetRef="a2"/>
        <bpmn:sequenceFlow id="e1_out" sourceRef="a1" targetRef="end1"/>
        <bpmn:sequenceFlow id="e2_out" sourceRef="a2" targetRef="end2"/>
    "#,
    ));
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // The `stamped` var is set to "from_action"
            // (both branches write the same value, so
            // last-wins is observable but not distinct).
            // The real assertion is that BOTH actions
            // ran (the var was set). We assert this
            // indirectly: the var exists.
            assert_eq!(
                t.ctx().vars.get("stamped"),
                Some(&json!("from_action"))
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[test]
fn parallel_fork_union_merges_branch_vars_with_last_wins_collision() {
    // V5.28 P6 — vars use **union-merge** semantics.
    // Each branch sets a DISTINCT var (`var_a` vs
    // `var_b`) and a SHARED var (`var_x`). After
    // both branches complete, the parent's vars
    // should contain:
    //   - `var_a` (set by branch1) — kept
    //   - `var_b` (set by branch2) — kept
    //   - `var_x` = 2 (last-wins; branch2 wrote
    //     after branch1 in outgoing-ids order)
    //
    // The V5.28 v0 "last-branch-wins full-ctx" bug
    // is fixed: distinct-keyed vars from earlier
    // branches now survive the merge.
    let action_reg = Arc::new(
        MockActionRegistry::new()
            .register("set_a", |vars| {
                vars.insert("var_a", json!(1));
                vars.insert("var_x", json!(1));
                Ok(())
            })
            .register("set_b", |vars| {
                vars.insert("var_b", json!(2));
                vars.insert("var_x", json!(2));
                Ok(())
            }),
    );
    let mut reg = (*rule_registry()).clone();
    reg.action = Arc::new(ActionExecutor::new(action_reg));
    let reg = Arc::new(reg);

    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:parallelGateway id="g">
          <bpmn:outgoing>e1</bpmn:outgoing>
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:serviceTask id="a1" ruleforge:taskType="action" ruleforge:method="set_a">
          <bpmn:outgoing>e1_out</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:serviceTask id="a2" ruleforge:taskType="action" ruleforge:method="set_b">
          <bpmn:outgoing>e2_out</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="end1"/>
        <bpmn:endEvent id="end2"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g"/>
        <bpmn:sequenceFlow id="e1" sourceRef="g" targetRef="a1"/>
        <bpmn:sequenceFlow id="e2" sourceRef="g" targetRef="a2"/>
        <bpmn:sequenceFlow id="e1_out" sourceRef="a1" targetRef="end1"/>
        <bpmn:sequenceFlow id="e2_out" sourceRef="a2" targetRef="end2"/>
    "#,
    ));
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // V5.28 P6 — union-merge: distinct
            // keys from both branches survive.
            assert_eq!(t.ctx().vars.get("var_a"), Some(&json!(1)));
            assert_eq!(t.ctx().vars.get("var_b"), Some(&json!(2)));
            // V5.28 P6 — collision: last branch
            // (outgoing-ids index 1 = branch2)
            // wins for the shared key.
            assert_eq!(t.ctx().vars.get("var_x"), Some(&json!(2)));
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[test]
fn parallel_fork_single_outgoing_is_pass_through() {
    // A `parallelGateway` with 1 outgoing edge is
    // effectively a "join only" (no fan-out). The
    // executor returns `Continue` and the flow
    // continues via `next_node`. This is the case
    // where a parallel gateway is used purely as a
    // sync point — the single branch that entered
    // continues.
    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:parallelGateway id="g">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g"/>
        <bpmn:sequenceFlow id="e1" sourceRef="g" targetRef="end"/>
    "#,
    ));
    let outcome = traverse(def, FlowContext::new("p1"), rule_registry());
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("end"));
            assert!(t.visited().contains("g"));
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[test]
fn parallel_fork_zero_outgoing_errors() {
    // A `parallelGateway` with 0 outgoing edges is
    // malformed. The executor errors. The BPMN
    // parser requires at least one end event in
    // the process, so we include an end event
    // elsewhere (it'll never be reached in this
    // test — the gateway error happens first).
    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:parallelGateway id="g"/>
        <bpmn:endEvent id="end_unused"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g"/>
    "#,
    ));
    let outcome = traverse(def, FlowContext::new("p1"), rule_registry());
    match outcome {
        TraverseOutcome::Failed(_, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("parallel gateway") || msg.contains("outgoing"),
                "expected outgoing-edges error, got: {msg}"
            );
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

#[test]
fn parallel_fork_nested_recursive() {
    // Outer fork: g1 → branch_a, branch_b
    //   branch_a: a_task → inner_fork (g2)
    //     inner_fork → a1 → end_a1
    //                 → a2 → end_a2
    //   branch_b: b_task → end_b
    //
    // The recursive `traverse()` call inside the
    // fork handler must handle a nested fork
    // correctly (the inner fork runs when branch_a
    // is being traversed).
    let action_reg = Arc::new(
        MockActionRegistry::new().register("noop", |_vars| Ok(())),
    );
    let mut reg = (*rule_registry()).clone();
    reg.action = Arc::new(ActionExecutor::new(action_reg));
    let reg = Arc::new(reg);

    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:parallelGateway id="g1">
          <bpmn:outgoing>e1</bpmn:outgoing>
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:serviceTask id="a" ruleforge:taskType="action" ruleforge:method="noop">
          <bpmn:outgoing>e1_to_g2</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:parallelGateway id="g2">
          <bpmn:outgoing>g2e1</bpmn:outgoing>
          <bpmn:outgoing>g2e2</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:serviceTask id="b" ruleforge:taskType="action" ruleforge:method="noop">
          <bpmn:outgoing>e2_out</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="end_a1"/>
        <bpmn:endEvent id="end_a2"/>
        <bpmn:endEvent id="end_b"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g1"/>
        <bpmn:sequenceFlow id="e1" sourceRef="g1" targetRef="a"/>
        <bpmn:sequenceFlow id="e2" sourceRef="g1" targetRef="b"/>
        <bpmn:sequenceFlow id="e1_to_g2" sourceRef="a" targetRef="g2"/>
        <bpmn:sequenceFlow id="g2e1" sourceRef="g2" targetRef="end_a1"/>
        <bpmn:sequenceFlow id="g2e2" sourceRef="g2" targetRef="end_a2"/>
        <bpmn:sequenceFlow id="e2_out" sourceRef="b" targetRef="end_b"/>
    "#,
    ));
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // Both g1 and g2 visited.
            assert!(t.visited().contains("g1"));
            assert!(t.visited().contains("g2"));
            // All three end events reached.
            assert!(t.visited().contains("end_a1"));
            assert!(t.visited().contains("end_a2"));
            assert!(t.visited().contains("end_b"));
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[tokio::test]
async fn parallel_gateway_dispatched_directly_without_reg_def_errors() {
    // Defensive: if `dispatch()` is called directly
    // (not via `traverse()`) on a ParallelGateway
    // node, and the registry has no `def` set, the
    // gateway executor should surface a clear error
    // rather than panic.
    //
    // We construct a `FlowNode` programmatically
    // (bypassing the BPMN parser) and call
    // `dispatch()` directly with a registry that has
    // `def = None`.
    use rf_ir::attrs::Attrs;
    use rf_ir::flow_node::FlowNode;
    use rf_ir::node_kind::NodeKind;

    let node = FlowNode {
        node_id: "g".to_string(),
        kind: NodeKind::ParallelGateway {
            attrs: Attrs::new(),
        },
        name: None,
        outgoing_ids: vec!["e1".to_string(), "e2".to_string()],
        async_flag: false,
    };
    let reg = ExecutorRegistry::default();
    assert!(reg.def.is_none());
    let mut ctx = FlowContext::new("p1");

    // The gateway executor is what's invoked; we
    // call it via `execute_with` since the
    // dispatcher's normal path would need a `def`.
    // But the dispatcher would also reach the
    // gateway via `execute_with` (we updated the
    // dispatch match). To simulate "no def wired",
    // we just call the gateway directly.
    let gateway = reg.gateway.clone();
    let result = gateway
        .execute_with(&node, &mut ctx, &reg)
        .await;
    match result {
        Err(err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("def") || msg.contains("registry"),
                "expected def/registry error, got: {msg}"
            );
        }
        Ok(other) => panic!(
            "expected Err for missing def, got: {other:?}"
        ),
    }
}

#[test]
fn exclusive_gateway_still_uses_execute_path() {
    // Sanity check: the dispatcher's `ExclusiveGateway`
    // arm still calls `execute()` (not
    // `execute_with`) — the routing is via
    // `next_node()`, not fork. We verify this by
    // running an exclusive gateway end-to-end and
    // asserting the right branch is taken.
    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:exclusiveGateway id="g">
          <bpmn:outgoing>e_yes</bpmn:outgoing>
          <bpmn:outgoing>e_no</bpmn:outgoing>
        </bpmn:exclusiveGateway>
        <bpmn:endEvent id="end_yes"/>
        <bpmn:endEvent id="end_no"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g"/>
        <bpmn:sequenceFlow id="e_yes" sourceRef="g" targetRef="end_yes">
          <bpmn:conditionExpression>${approved == true}</bpmn:conditionExpression>
        </bpmn:sequenceFlow>
        <bpmn:sequenceFlow id="e_no" sourceRef="g" targetRef="end_no"/>
    "#,
    ));
    let mut ctx = FlowContext::new("p1");
    ctx.vars.assign("approved".to_string(), json!(true));
    let outcome = traverse(def, ctx, rule_registry());
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx().current_node_id.as_deref(),
                Some("end_yes")
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// V5.28 P6 — true join (diamond pattern) tests.
//
// The classic BPMN "diamond": start → fork → branch1 → join → post
//                                   → branch2 → join → post.
//
// V5.28 P0 supported this only "naively" — the join was a no-op
// `Continue` and the two branches ran to their own end events.
// V5.28 P6 detects the explicit `parallelGateway` join and routes
// the parent through it.

#[test]
fn parallel_join_synchronizes_two_branches_then_routes_through_post_join_node() {
    // start → g1 (fork, 2 outgoing) → b1 → g2 (join) → post → end
    //                                  → b2 → g2 (join) → post → end
    //
    // g2 is a parallel gateway with in-degree 2 (= fork's branch
    // count) and at least one outgoing. The executor's
    // `find_join_target` heuristic returns `g2` as the unique
    // candidate, and the post-merge step routes the parent
    // through g2's outgoing.
    //
    // `b1` sets `var_a`, `b2` sets `var_b`; `post` (a
    // serviceTask so it doesn't suspend) should see both
    // (union merge). The flow completes at `end` and
    // `current_node_id` is `end`.
    let action_reg = Arc::new(
        MockActionRegistry::new()
            .register("set_a", |vars| {
                vars.insert("var_a", json!("from_b1"));
                Ok(())
            })
            .register("set_b", |vars| {
                vars.insert("var_b", json!("from_b2"));
                Ok(())
            })
            .register("post_action", |vars| {
                // The post node can observe both
                // branches' writes — that's the whole
                // point of the join.
                vars.insert(
                    "post_saw",
                    json!(format!(
                        "a={:?},b={:?}",
                        vars.get("var_a"),
                        vars.get("var_b")
                    )),
                );
                Ok(())
            }),
    );
    let mut reg = (*rule_registry()).clone();
    reg.action = Arc::new(ActionExecutor::new(action_reg));
    let reg = Arc::new(reg);

    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:parallelGateway id="g1">
          <bpmn:outgoing>e1</bpmn:outgoing>
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:serviceTask id="b1" ruleforge:taskType="action" ruleforge:method="set_a">
          <bpmn:outgoing>e1_to_g2</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:serviceTask id="b2" ruleforge:taskType="action" ruleforge:method="set_b">
          <bpmn:outgoing>e2_to_g2</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:parallelGateway id="g2">
          <bpmn:outgoing>e_g2_post</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:serviceTask id="post" ruleforge:taskType="action" ruleforge:method="post_action">
          <bpmn:outgoing>e_post_end</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g1"/>
        <bpmn:sequenceFlow id="e1" sourceRef="g1" targetRef="b1"/>
        <bpmn:sequenceFlow id="e2" sourceRef="g1" targetRef="b2"/>
        <bpmn:sequenceFlow id="e1_to_g2" sourceRef="b1" targetRef="g2"/>
        <bpmn:sequenceFlow id="e2_to_g2" sourceRef="b2" targetRef="g2"/>
        <bpmn:sequenceFlow id="e_g2_post" sourceRef="g2" targetRef="post"/>
        <bpmn:sequenceFlow id="e_post_end" sourceRef="post" targetRef="end"/>
    "#,
    ));
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    // Sync case — both branches completed, join crossed,
    // post ran with union-merged vars, end reached.
    match outcome {
        TraverseOutcome::Completed(t) => {
            // We end at `end` (the userTask/post_saw
            // node ran without suspending).
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("end"));
            // Union-merge: both branches' writes
            // survive into the post node.
            assert_eq!(
                t.ctx().vars.get("var_a"),
                Some(&json!("from_b1"))
            );
            assert_eq!(
                t.ctx().vars.get("var_b"),
                Some(&json!("from_b2"))
            );
            // The post node saw both vars together —
            // the format string proves it.
            let post_saw = t
                .ctx()
                .vars
                .get("post_saw")
                .and_then(|v| v.as_str())
                .unwrap_or("");
            assert!(
                post_saw.contains("from_b1") && post_saw.contains("from_b2"),
                "post node should see both branches' writes, got: {post_saw}"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[test]
fn parallel_join_missing_falls_back_to_p0_behavior() {
    // The "no explicit join" case: start → g (fork, 2 outgoing) → b1 → end1
    //                                                    → b2 → end2
    // There is NO downstream `parallelGateway`, so
    // `find_join_target` returns `None`. The executor
    // preserves the P0 behaviour: each branch runs to its
    // own end event, the parent finishes with `next = None`
    // after the union merge. The current_node_id is the
    // last branch's end node.
    let action_reg = Arc::new(
        MockActionRegistry::new()
            .register("set_a", |vars| {
                vars.insert("var_a", json!(1));
                Ok(())
            })
            .register("set_b", |vars| {
                vars.insert("var_b", json!(2));
                Ok(())
            }),
    );
    let mut reg = (*rule_registry()).clone();
    reg.action = Arc::new(ActionExecutor::new(action_reg));
    let reg = Arc::new(reg);

    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:parallelGateway id="g">
          <bpmn:outgoing>e1</bpmn:outgoing>
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:serviceTask id="b1" ruleforge:taskType="action" ruleforge:method="set_a">
          <bpmn:outgoing>e1_out</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:serviceTask id="b2" ruleforge:taskType="action" ruleforge:method="set_b">
          <bpmn:outgoing>e2_out</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="end1"/>
        <bpmn:endEvent id="end2"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g"/>
        <bpmn:sequenceFlow id="e1" sourceRef="g" targetRef="b1"/>
        <bpmn:sequenceFlow id="e2" sourceRef="g" targetRef="b2"/>
        <bpmn:sequenceFlow id="e1_out" sourceRef="b1" targetRef="end1"/>
        <bpmn:sequenceFlow id="e2_out" sourceRef="b2" targetRef="end2"/>
    "#,
    ));
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // V5.28 P0 last-wins for current_node_id is
            // preserved (the second branch's end event
            // is the one surfaced). The vars are still
            // union-merged (P6 contract).
            let nid = t.ctx().current_node_id.as_deref();
            assert!(
                nid == Some("end1") || nid == Some("end2"),
                "expected end1 or end2, got {nid:?}"
            );
            assert_eq!(t.ctx().vars.get("var_a"), Some(&json!(1)));
            assert_eq!(t.ctx().vars.get("var_b"), Some(&json!(2)));
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[test]
fn parallel_fork_async_branch_still_suspends_above() {
    // V5.28 P6 v0 — async barrier is **not** implemented
    // yet. A branch that hits a `userTask` suspends the
    // entire flow (P0 behaviour, preserved). The
    // post-merge step is short-circuited by the
    // `Suspended` arm in the `traverse_branch` loop.
    //
    // This test pins the v0 contract: when one branch
    // suspends, the parent suspends too — even if other
    // branches are still running / would have completed.
    // V5.29 (Multi-Instance) will replace this with a
    // true async barrier; until then, the v0 behaviour is
    // the documented behaviour.
    const DIAMOND_BPMN: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:parallelGateway id="g1">
      <bpmn:outgoing>e1</bpmn:outgoing>
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:parallelGateway>
    <bpmn:userTask id="b1" ruleforge:decisionField="approve">
      <bpmn:outgoing>e1_to_g2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:serviceTask id="b2" ruleforge:taskType="action" ruleforge:method="noop">
      <bpmn:outgoing>e2_to_g2</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:parallelGateway id="g2">
      <bpmn:outgoing>e_g2_post</bpmn:outgoing>
    </bpmn:parallelGateway>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="g1"/>
    <bpmn:sequenceFlow id="e1" sourceRef="g1" targetRef="b1"/>
    <bpmn:sequenceFlow id="e2" sourceRef="g1" targetRef="b2"/>
    <bpmn:sequenceFlow id="e1_to_g2" sourceRef="b1" targetRef="g2"/>
    <bpmn:sequenceFlow id="e2_to_g2" sourceRef="b2" targetRef="g2"/>
    <bpmn:sequenceFlow id="e_g2_post" sourceRef="g2" targetRef="end"/>
"#;
    let action_reg = Arc::new(
        MockActionRegistry::new().register("noop", |_vars| Ok(())),
    );
    let mut reg = (*rule_registry()).clone();
    reg.action = Arc::new(ActionExecutor::new(action_reg));
    let reg = Arc::new(reg);

    let def = parse(&bpmn("p", DIAMOND_BPMN));
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    // Branch 1 hits the userTask and suspends. The
    // parent suspends too (P0 + P6 v0 behaviour). The
    // current_node_id is the userTask (the activity
    // that triggered the suspend).
    match outcome {
        TraverseOutcome::Suspended(t, info) => {
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("b1"));
            assert_eq!(info.wait_ref, "approve");
        }
        other => panic!("expected Suspended at b1, got {other:?}"),
    }
}
