//! V5.30 — Error / Escalation / Terminate end events.
//!
//! BPMN 2.0 `<bpmn:endEvent>` is the exit of every
//! flow. V5.27 treated it as a parameterless no-op;
//! V5.30 attaches `attrs` + an `end_kind`
//! discriminator that lets the same node model
//! "normal end", "error end", "escalation end",
//! and "terminate end". Error/escalation/terminate
//! ends return `NodeResult::Fail(String)` → the
//! traverser exits with `TraverseOutcome::Failed`
//! carrying the corresponding `FlowError` display
//! form (wrapped in `FlowError::Action` at the
//! traversal seam).
//!
//! ## V5.30 v0 contract
//!
//! - No `endType` (or `endType=""` / `endType="none"`)
//!   → `Continue` → `TraverseOutcome::Completed`
//!   (regression; matches V5.27 behaviour).
//! - `endType="error"` + `errorRef` (default
//!   `"error"`) → `Fail` → `Failed(_, err)` where
//!   `err.to_string()` starts with
//!   `"flow reached error end: <ref>"`. Also
//!   writes `ctx.thrown_error = Some(ref)` for
//!   observability.
//! - `endType="escalation"` + `escalationRef`
//!   (default `"escalation"`) → `Fail` →
//!   `Failed(_, err)` where `err.to_string()`
//!   starts with
//!   `"flow reached escalation end: <ref>"`.
//!   v0 same as Error (V5.31 refines).
//! - `endType="terminate"` → `Fail` →
//!   `Failed(_, err)` where `err.to_string()` is
//!   `"flow terminated"`. v0 same as Error
//!   (V5.31 P1 adds real token-kill).
//! - Unknown `endType` → `Failed(_, err)` with
//!   `err.to_string()` mentioning the bad value
//!   (caught at the dispatch site by
//!   `EndEventKind::from_attrs`).

use std::sync::Arc;

use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::executors::action::{ActionExecutor, MockActionRegistry};
use rf_executor::flow_context::FlowContext;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::flow_definition::FlowDefinition;
use rf_parse::bpmn_parser::BpmnXmlParser;

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

fn registry() -> Arc<ExecutorRegistry> {
    let actions = Arc::new(MockActionRegistry::new().register("noop", |_vars| Ok(())));
    let mut reg = ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    ));
    reg.action = Arc::new(ActionExecutor::new(actions));
    Arc::new(reg)
}

// ----- 1: error end -----

const BPMN_ERROR_END_DEFAULT: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="end" ruleforge:endType="error"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="end"/>
"#;

#[test]
fn error_end_returns_fail_outcome() {
    let def = parse(&bpmn("p", BPMN_ERROR_END_DEFAULT));
    let reg = registry();
    let ctx = FlowContext::new("p1");
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Failed(_, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("flow reached error end: error"),
                "expected error-end message, got: {msg}"
            );
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

#[test]
fn error_end_writes_thrown_error_for_observability() {
    let def = parse(&bpmn("p", BPMN_ERROR_END_DEFAULT));
    let reg = registry();
    let ctx = FlowContext::new("p1");
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Failed(t, _) => {
            // V5.30 — `ctx.thrown_error` carries the
            // error ref for observability even after
            // the flow has Failed.
            assert_eq!(
                t.ctx.thrown_error.as_deref(),
                Some("error"),
                "thrown_error should be set to the default error ref"
            );
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

#[test]
fn error_end_with_custom_error_ref() {
    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:endEvent id="end" ruleforge:endType="error"
                       ruleforge:errorRef="loan_rejected"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="end"/>
        "#,
    ));
    let reg = registry();
    let ctx = FlowContext::new("p1");
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Failed(t, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("flow reached error end: loan_rejected"),
                "expected custom ref in message, got: {msg}"
            );
            assert_eq!(t.ctx.thrown_error.as_deref(), Some("loan_rejected"));
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

// ----- 2: escalation end -----

#[test]
fn escalation_end_returns_fail_outcome() {
    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:endEvent id="end" ruleforge:endType="escalation"
                       ruleforge:escalationRef="manual_review"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="end"/>
        "#,
    ));
    let reg = registry();
    let ctx = FlowContext::new("p1");
    let outcome = traverse(def, ctx, reg);
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

// ----- 3: terminate end -----

#[test]
fn terminate_end_returns_fail_outcome() {
    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:endEvent id="end" ruleforge:endType="terminate"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="end"/>
        "#,
    ));
    let reg = registry();
    let ctx = FlowContext::new("p1");
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Failed(_, err) => {
            let msg = format!("{err}");
            // V5.30 — terminate end's `Fail`
            // payload is the `Display` form of
            // `FlowError::Terminated` ("flow
            // terminated"), wrapped in
            // `FlowError::Action` at the
            // traverser seam (which prepends
            // `"action error: "`).
            assert_eq!(msg, "action error: flow terminated");
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

// ----- 4: normal end (regression) -----

#[test]
fn normal_end_unchanged() {
    // No endType at all → V5.27 behaviour:
    // Continue → Completed.
    let body = BPMN_ERROR_END_DEFAULT.replace(
        r#"ruleforge:endType="error""#,
        "",
    );
    let def = parse(&bpmn("p", &body));
    let reg = registry();
    let ctx = FlowContext::new("p1");
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx.current_node_id.as_deref(),
                Some("end"),
                "flow should reach end node"
            );
            assert!(
                t.ctx.thrown_error.is_none(),
                "normal end should NOT write thrown_error"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// ----- 5: unknown end type -----

#[test]
fn error_end_unknown_type_rejected() {
    let def = parse(&bpmn(
        "p",
        r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:endEvent id="end" ruleforge:endType="banana"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="end"/>
        "#,
    ));
    let reg = registry();
    let ctx = FlowContext::new("p1");
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Failed(_, err) => {
            let msg = format!("{err}");
            // V5.30 — unknown endType → wrap
            // `EndEventError` into
            // `FlowError::Action` at the dispatch
            // site; message includes the bad
            // value.
            assert!(
                msg.contains("banana"),
                "expected bad endType in error, got: {msg}"
            );
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}
