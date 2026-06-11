//! V5.30 — `BoundaryEvent` error-handler chain to
//! `ErrorEnd` / `EscalationEnd`.
//!
//! V5.27 P0 (with V5.28 P1 "really attached")
//! routes an activity's `thrown_error` to the
//! attached boundary's outgoing — the *handler
//! path*. V5.30 closes the loop: the handler
//! path's terminal node can be an `ErrorEnd` /
//! `EscalationEnd` that turns the (otherwise
//! successful) handler run into a `Failed`
//! terminal state. The carried `ctx.thrown_error`
//! from the original activity is overwritten with
//! the `errorRef` declared on the `ErrorEnd` node
//! (for observability).
//!
//! ## V5.30 v0 contract
//!
//! - `s → action(throws "boom") → error_boundary(errorRef="boom") →
//!   error_end(errorRef="loan_rejected")` →
//!   `TraverseOutcome::Failed(_, "action error: flow reached
//!   error end: loan_rejected")`. The `errorRef` on
//!   the `ErrorEnd` (not the activity's thrown ref)
//!   is the one that surfaces in the message and in
//!   `ctx.thrown_error`.
//! - Same shape but the `ErrorEnd` is replaced by
//!   `EscalationEnd(escalationRef="manual_review")`
//!   → `Failed(_, "action error: flow reached
//!   escalation end: manual_review")`.
//! - An activity that throws but the handler
//!   path terminates in a *normal* end →
//!   `Completed` (the boundary's handler path is a
//!   successful recovery; the original throw is
//!   "consumed" by the boundary's routing). The
//!   `ErrorEnd` is **only** reached if the handler
//!   path explicitly walks into one.

use std::sync::Arc;

use async_trait::async_trait;
use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::flow_context::FlowContext;
use rf_executor::node_executor::NodeExecutor;
use rf_executor::node_result::NodeResult;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::flow_definition::FlowDefinition;
use rf_ir::flow_node::FlowNode;
use rf_parse::bpmn_parser::BpmnXmlParser;
use serde_json::json;

/// V5.30 test helper — same shape as
/// `ThrowActionExecutor` in
/// `boundary_event_attached_test.rs`. The
/// `MockActionRegistry::ActionFn` is
/// `fn(&mut Vars) -> Result<(), String>`, which
/// can't write `ctx.thrown_error` (a `FlowContext`
/// field, not a `Vars` field). This executor
/// replaces the action slot and writes
/// `thrown_error` directly when the node id
/// matches.
struct ThrowActionExecutor {
    throws_for: String,
    thrown_ref: String,
}

#[async_trait]
impl NodeExecutor for ThrowActionExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, rf_executor::error::FlowError> {
        if node.node_id == self.throws_for {
            ctx.thrown_error = Some(self.thrown_ref.clone());
            ctx.vars
                .insert("__throw_ran__".to_string(), json!(true));
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

fn registry_with_throw(
    throws_for: &str,
    thrown_ref: &str,
) -> Arc<ExecutorRegistry> {
    let mut r = ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    ));
    r.action = Arc::new(ThrowActionExecutor {
        throws_for: throws_for.to_string(),
        thrown_ref: thrown_ref.to_string(),
    });
    Arc::new(r)
}

// ----- 1: throw → boundary → error_end -----

const BPMN_BOUNDARY_TO_ERROR_END: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
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
    <bpmn:serviceTask id="normal_end" ruleforge:taskType="action"
                      ruleforge:method="noop"/>
    <bpmn:endEvent id="error_end" ruleforge:endType="error"
                   ruleforge:errorRef="loan_rejected"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="task"/>
    <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
    <bpmn:sequenceFlow id="e_err" sourceRef="b" targetRef="error_end"/>
"#;

#[test]
fn boundary_error_handler_to_error_end_chain() {
    let def = parse(&bpmn("p", BPMN_BOUNDARY_TO_ERROR_END));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Failed(t, err) => {
            // V5.30 — the ErrorEnd's
            // `errorRef="loan_rejected"` is
            // what surfaces in the message,
            // NOT the activity's thrown
            // ref "boom". The boundary
            // matches on "boom" (its
            // `errorRef`); the error end
            // declares the *failure* ref
            // the flow exits with.
            let msg = format!("{err}");
            assert!(
                msg.contains("flow reached error end: loan_rejected"),
                "expected error-end message, got: {msg}"
            );
            // The ErrorEnd wrote the
            // `errorRef` to `thrown_error`
            // (observability — overrides
            // whatever the activity set).
            assert_eq!(t.ctx.thrown_error.as_deref(), Some("loan_rejected"));
            // The activity ran (the
            // throw action executor set
            // this marker before the
            // boundary routing kicked in).
            assert_eq!(
                t.ctx.vars.get("__throw_ran__").cloned(),
                Some(json!(true))
            );
            // The flow's terminal node is
            // the error end, NOT the
            // normal end.
            assert_eq!(
                t.ctx.current_node_id.as_deref(),
                Some("error_end"),
                "flow should terminate at error_end"
            );
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

// ----- 2: throw → boundary → escalation_end -----

const BPMN_BOUNDARY_TO_ESCALATION_END: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
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
    <bpmn:endEvent id="esc_end" ruleforge:endType="escalation"
                   ruleforge:escalationRef="manual_review"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="task"/>
    <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="b"/>
    <bpmn:sequenceFlow id="e_err" sourceRef="b" targetRef="esc_end"/>
"#;

#[test]
fn boundary_error_handler_to_escalation_end_chain() {
    let def = parse(&bpmn("p", BPMN_BOUNDARY_TO_ESCALATION_END));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Failed(t, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("flow reached escalation end: manual_review"),
                "expected escalation-end message, got: {msg}"
            );
            assert_eq!(t.ctx.thrown_error.as_deref(), Some("manual_review"));
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

// ----- 3: regression — boundary → normal end (handler succeeds) -----

const BPMN_BOUNDARY_TO_NORMAL_END: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
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
    <bpmn:endEvent id="recovery_end"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="task"/>
    <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="b"/>
    <bpmn:sequenceFlow id="e_err" sourceRef="b" targetRef="recovery_end"/>
"#;

#[test]
fn boundary_error_handler_to_normal_end_completes() {
    let def = parse(&bpmn("p", BPMN_BOUNDARY_TO_NORMAL_END));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    // The boundary catches the throw and
    // routes to its outgoing → a normal end.
    // The flow is `Completed` (the boundary's
    // handler is a successful recovery; the
    // ErrorEnd is NOT auto-injected).
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx.current_node_id.as_deref(),
                Some("recovery_end"),
                "handler path should reach normal end (recovery)"
            );
            assert!(
                t.ctx.thrown_error.is_none(),
                "normal end should NOT carry thrown_error"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}
