//! V5.26 IntermediateEvent suspend + resume tests.
//!
//! Mirrors the userTask pattern: the executor returns
//! `NodeResult::Suspend(SuspendInfo)` with a wait_type that
//! matches the event kind (AsyncData for message/signal,
//! AsyncTask for timer). The resume path mirrors userTask: the
//! HTTP /flow/event handler writes `current_awaiting_field`
//! and `current_awaiting_value`, then re-traverses.
//!
//! Phase 4+ will add the `/flow/event` HTTP handler; this file
//! tests the in-memory contract only.

use std::sync::Arc;

use chrono::Utc;
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

const MESSAGE_CATCH_FLOW: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:intermediateCatchEvent id="ic" ruleforge:eventType="message"
                                 ruleforge:eventName="approval_received">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
    <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="end"/>
    <bpmn:endEvent id="end"/>
"#;

const SIGNAL_CATCH_FLOW: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:intermediateCatchEvent id="ic" ruleforge:eventType="signal"
                                 ruleforge:eventName="broadcast_xyz">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
    <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="end"/>
    <bpmn:endEvent id="end"/>
"#;

const TIMER_CATCH_FLOW: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:intermediateCatchEvent id="ic" ruleforge:eventType="timer"
                                 ruleforge:timerDuration="PT5S">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
    <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="end"/>
    <bpmn:endEvent id="end"/>
"#;

const NO_TYPE_FLOW: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:intermediateCatchEvent id="ic">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
    <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="end"/>
    <bpmn:endEvent id="end"/>
"#;

#[test]
fn message_catch_suspends_with_async_data() {
    let def = parse(&bpmn("p", MESSAGE_CATCH_FLOW));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            assert_eq!(info.wait_type, WaitType::AsyncData);
            // V5.28 P2 — wait_ref is namespaced with
            // `message:` prefix to avoid collisions
            // with signal/timer in the same run.
            assert_eq!(info.wait_ref, "message:approval_received");
            assert_eq!(info.payload["event_type"], json!("message"));
            assert_eq!(info.payload["event_name"], json!("approval_received"));
        }
        _ => panic!("expected Suspended"),
    }
}

#[test]
fn signal_catch_suspends_with_async_data() {
    let def = parse(&bpmn("p", SIGNAL_CATCH_FLOW));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            assert_eq!(info.wait_type, WaitType::AsyncData);
            // V5.28 P2 — namespaced with `signal:` prefix.
            assert_eq!(info.wait_ref, "signal:broadcast_xyz");
            assert_eq!(info.payload["event_type"], json!("signal"));
        }
        _ => panic!("expected Suspended"),
    }
}

#[test]
fn timer_catch_suspends_with_async_task_and_retry_at() {
    let def = parse(&bpmn("p", TIMER_CATCH_FLOW));
    let before = Utc::now();
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            assert_eq!(info.wait_type, WaitType::AsyncTask);
            assert!(info.next_retry_at.is_some(), "timer must set next_retry_at");
            // next_retry_at is roughly now + 5s (PT5S).
            let retry = info.next_retry_at.unwrap();
            let diff = (retry - before).num_seconds();
            assert!(diff >= 4 && diff <= 6, "expected ~5s, got {diff}s");
            assert_eq!(info.payload["event_type"], json!("timer"));
            assert_eq!(info.payload["duration_seconds"], json!(5));
        }
        _ => panic!("expected Suspended"),
    }
}

#[test]
fn no_event_type_passes_through_as_continue() {
    // IntermediateThrowEvent (or a catch with no eventType set) is
    // a no-op — the flow should run to end_yes, not suspend.
    let def = parse(&bpmn("p", NO_TYPE_FLOW));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("end"));
        }
        _ => panic!("expected Completed (no eventType = pass-through)"),
    }
}

#[test]
fn message_catch_resume_with_awaiting_value_continues() {
    // Simulate the HTTP /flow/event handler: set
    // current_awaiting_field to the namespaced event ref
    // (`message:<name>` per V5.28 P2) and provide a value.
    // The executor treats it as a continuation and returns Continue.
    let def = parse(&bpmn("p", MESSAGE_CATCH_FLOW));
    let outcome = traverse(def.clone(), FlowContext::new("r"), test_registry());
    let (suspended, _info) = match outcome {
        TraverseOutcome::Suspended(s, i) => (s, i),
        _ => panic!("expected Suspended"),
    };
    let mut ctx = suspended.ctx;
    ctx.current_awaiting_field = Some("message:approval_received".to_string());
    ctx.current_awaiting_value = Some(json!({"approved": true, "reviewer": "alice"}));

    let outcome = traverse(def, ctx, test_registry());
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("end"));
        }
        _ => panic!("expected Completed after resume"),
    }
}

#[test]
fn message_catch_with_wrong_awaiting_field_does_not_resume() {
    // If the awaiting field doesn't match the event name, the
    // executor does NOT treat it as a resume — it suspends again
    // with the (new) event name. This is the "different event
    // arrived" case.
    let def = parse(&bpmn("p", MESSAGE_CATCH_FLOW));
    let outcome = traverse(def.clone(), FlowContext::new("r"), test_registry());
    let (suspended, _info) = match outcome {
        TraverseOutcome::Suspended(s, i) => (s, i),
        _ => panic!("expected Suspended"),
    };
    let mut ctx = suspended.ctx;
    // Wrong field name (different event).
    ctx.current_awaiting_field = Some("different_event".to_string());
    ctx.current_awaiting_value = Some(json!({"foo": 1}));

    let outcome = traverse(def, ctx, test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            // Still suspended, still waiting for the original event.
            // V5.28 P2 — namespaced wait_ref.
            assert_eq!(info.wait_ref, "message:approval_received");
        }
        _ => panic!("expected Suspended (mismatched field = no resume)"),
    }
}

#[test]
fn message_catch_without_event_name_errors() {
    // Missing ruleforge:eventName is a malformed event — caught at
    // runtime, becomes FlowError::Action. Tests the FromAttrs
    // error path.
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:intermediateCatchEvent id="ic" ruleforge:eventType="message">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:intermediateCatchEvent>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
           <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Failed(_, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("eventName"),
                "expected error to mention eventName, got: {msg}"
            );
        }
        _ => panic!("expected Failed (missing eventName)"),
    }
}

#[test]
fn timer_catch_with_bad_duration_errors() {
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:intermediateCatchEvent id="ic" ruleforge:eventType="timer"
                                        ruleforge:timerDuration="garbage">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:intermediateCatchEvent>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
           <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    assert!(matches!(outcome, TraverseOutcome::Failed(_, _)));
}
