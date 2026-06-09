//! Phase 4 userTask suspend+resume tests.
//!
//! Java expresses "suspend at a user task" by `throw new
//! AsyncNodeSuspendException(...)`; the Rust port returns
//! `NodeResult::Suspend(SuspendInfo)` as data. The traverser hands
//! the `SuspendInfo` to the caller, which persists it to the state
//! row (Phase 6) and returns `PENDING` to the HTTP client. The user
//! clicks a decision; the system POSTs to `/flow/decision`; the
//! resume path writes the decision into vars and re-runs the
//! traverser from the suspended node.
//!
//! This file tests the in-memory half of that contract — no DB, no
//! HTTP, just `Traverser<Suspended> → Traverser<Running> → Completed`.

use std::sync::Arc;

use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::flow_context::FlowContext;
use rf_executor::node_result::WaitType;
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

fn test_registry() -> Arc<ExecutorRegistry> {
    Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )))
}

const BINARY_DECISION_FLOW: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="u1" ruleforge:decisionField="approve">
      <bpmn:outgoing>e_yes</bpmn:outgoing>
      <bpmn:outgoing>e_no</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="u1"/>
    <bpmn:sequenceFlow id="e_yes" sourceRef="u1" targetRef="end_yes"
                       ruleforge:decisionValue="yes"/>
    <bpmn:sequenceFlow id="e_no" sourceRef="u1" targetRef="end_no"
                       ruleforge:decisionValue="no"/>
    <bpmn:endEvent id="end_yes"/>
    <bpmn:endEvent id="end_no"/>
"#;

#[test]
fn user_task_returns_suspend_outcome() {
    let def = parse(&bpmn("p", BINARY_DECISION_FLOW));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            assert_eq!(info.wait_type, WaitType::UserTask);
            assert_eq!(info.wait_ref, "approve");
            // Payload is what the frontend renders: nodeId + decision
            // type/field so the right form is shown.
            assert_eq!(info.payload["node_id"], json!("u1"));
            assert_eq!(info.payload["decision_field"], json!("approve"));
        }
        _ => panic!("expected Suspended, got something else"),
    }
}

#[test]
fn user_task_sets_current_awaiting_field_for_next_gateway() {
    // After the userTask step, ctx.current_awaiting_field must equal
    // the decisionField so the next gateway can route on it.
    let def = parse(&bpmn("p", BINARY_DECISION_FLOW));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    let ctx = outcome.context();
    assert_eq!(ctx.current_awaiting_field.as_deref(), Some("approve"));
}

#[test]
fn user_task_records_current_node_id_in_context() {
    // Phase 6 (state persistence) reads ctx.current_node_id to write
    // it to the state row's current_node_id column. Verify the
    // suspended step recorded u1.
    let def = parse(&bpmn("p", BINARY_DECISION_FLOW));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    assert_eq!(outcome.context().current_node_id.as_deref(), Some("u1"));
}

#[test]
fn resume_with_yes_decision_routes_to_end_yes() {
    // After suspending, simulate the HTTP /flow/decision callback:
    // write decision into vars, set current_awaiting_value, then
    // re-traverse. (In production, the HTTP handler does this and
    // calls .resume() to drive from the suspended node forward.)
    let def = parse(&bpmn("p", BINARY_DECISION_FLOW));
    let outcome = traverse(def.clone(), FlowContext::new("r"), test_registry());
    let (suspended, _info) = match outcome {
        TraverseOutcome::Suspended(s, i) => (s, i),
        _ => panic!("expected Suspended"),
    };

    let mut ctx = suspended.ctx;
    // Simulate HTTP /flow/decision writing the user's choice:
    ctx.vars.insert("approve", json!("yes"));
    ctx.current_awaiting_value = Some(json!("yes"));
    ctx.current_awaiting_field = Some("approve".to_string());

    // Re-traverse: the routing in next_node reads current_awaiting_value
    // and the userTask step is now skipped (current_node_id is still
    // "u1" but the gateway routing will pick the right edge).
    let outcome = traverse(def, ctx, test_registry());
    let ctx = outcome.into_context();
    assert_eq!(ctx.current_node_id.as_deref(), Some("end_yes"));
}

#[test]
fn resume_with_no_decision_routes_to_end_no() {
    let def = parse(&bpmn("p", BINARY_DECISION_FLOW));
    let outcome = traverse(def.clone(), FlowContext::new("r"), test_registry());
    let (suspended, _info) = match outcome {
        TraverseOutcome::Suspended(s, i) => (s, i),
        _ => panic!("expected Suspended"),
    };
    let mut ctx = suspended.ctx;
    ctx.vars.insert("approve", json!("no"));
    ctx.current_awaiting_value = Some(json!("no"));
    ctx.current_awaiting_field = Some("approve".to_string());

    let outcome = traverse(def, ctx, test_registry());
    let ctx = outcome.into_context();
    assert_eq!(ctx.current_node_id.as_deref(), Some("end_no"));
}

#[test]
fn user_task_without_decision_field_errors() {
    // Missing ruleforge:decisionField is a programmer error — caught
    // at runtime by UserTaskExecutor, becomes FlowError::UserTaskRequiredField.
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:userTask id="u1">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:userTask>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="u1"/>
           <bpmn:sequenceFlow id="e2" sourceRef="u1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    assert!(matches!(outcome, TraverseOutcome::Failed(_, _)));
}
