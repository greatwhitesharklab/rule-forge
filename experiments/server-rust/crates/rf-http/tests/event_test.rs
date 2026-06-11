//! BDD integration tests for `POST /ruleforge/flow/event`.
//!
//! Closes the V5.26 P1 suspend→resume loop for the
//! IntermediateCatchEvent executor: the HTTP wire now delivers
//! message/signal events to flows that are suspended on them.
//!
//! ## Scenarios
//!
//! ### Scenario: message catch resumes on matching event name
//! - **Given** a flow is suspended at
//!   `intermediateCatchEvent(eventType=message, eventName=approval_received)`
//! - **When** POST /ruleforge/flow/event
//!   `{"flowRunId": "...", "eventName": "approval_received", "payload": {...}}`
//! - **Then** response 200 with `{"result": "COMPLETED", "vars": {...}}`
//! - **And** `vars.approval_received == <payload>`
//!
//! ### Scenario: signal catch resumes
//! - Same shape, `eventType=signal`.
//!
//! ### Scenario: mismatched event name returns 409
//! - **Given** suspended at `eventName=approval_received`
//! - **When** POST with `eventName=different_event`
//! - **Then** 409 + error mentions both names
//!
//! ### Scenario: unknown flowRunId returns 404
//!
//! ### Scenario: timer-suspended flow returns 409
//! - **Given** suspended at `eventType=timer` (AsyncTask wait_type)
//! - **When** POST /ruleforge/flow/event
//! - **Then** 409 + error mentions timer / recovery loop
//!
//! ### Scenario: missing payload field defaults to null
//! - The handler accepts requests with no `payload`; the value
//!   attached is `null`. Resumes normally.

use std::sync::Arc;

use axum::body::{to_bytes, Body};
use axum::http::{Request, StatusCode};
use axum::routing::post;
use axum::Router;
use rf_executor::dispatch::ExecutorRegistry;
use rf_http::flow_def_repo::{FlowDefinitionRepo, StubFlowLoader};
use rf_http::routes::evaluate::evaluate;
use rf_http::routes::event::deliver;
use rf_http::state::AppState;
use serde_json::{json, Value};
use tower::ServiceExt;

const MESSAGE_CATCH_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="message_catch_flow">
    <bpmn:startEvent id="s"><bpmn:outgoing>e1</bpmn:outgoing></bpmn:startEvent>
    <bpmn:intermediateCatchEvent id="ic" ruleforge:eventType="message"
                                 ruleforge:eventName="approval_received">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
    <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="end"/>
    <bpmn:endEvent id="end"/>
  </bpmn:process>
</bpmn:definitions>"#;

const SIGNAL_CATCH_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="signal_catch_flow">
    <bpmn:startEvent id="s"><bpmn:outgoing>e1</bpmn:outgoing></bpmn:startEvent>
    <bpmn:intermediateCatchEvent id="ic" ruleforge:eventType="signal"
                                 ruleforge:eventName="broadcast_xyz">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
    <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="end"/>
    <bpmn:endEvent id="end"/>
  </bpmn:process>
</bpmn:definitions>"#;

const TIMER_CATCH_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="timer_catch_flow">
    <bpmn:startEvent id="s"><bpmn:outgoing>e1</bpmn:outgoing></bpmn:startEvent>
    <bpmn:intermediateCatchEvent id="ic" ruleforge:eventType="timer"
                                 ruleforge:timerDuration="PT5S">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
    <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="end"/>
    <bpmn:endEvent id="end"/>
  </bpmn:process>
</bpmn:definitions>"#;

fn build_state_with(loader: StubFlowLoader) -> AppState {
    let repo = Arc::new(FlowDefinitionRepo::new(Arc::new(loader)));
    let registry = Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )));
    AppState::new(repo, registry, "test-worker", "http://localhost:8180", "")
}

fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/ruleforge/evaluate", post(evaluate))
        .route("/ruleforge/flow/event", post(deliver))
        .with_state(state)
}

async fn body_json(resp: axum::response::Response) -> Value {
    let body = to_bytes(resp.into_body(), 1024 * 1024).await.unwrap();
    serde_json::from_slice(&body).unwrap()
}

async fn suspend_a_flow(app: &Router, flow_id: &str) -> String {
    let resp = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/evaluate")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({ "flow_id": flow_id })).unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "PENDING", "expected PENDING, got {body}");
    body["flow_run_id"].as_str().unwrap().to_string()
}

#[tokio::test]
async fn given_suspended_message_catch_when_event_delivered_then_completed() {
    let loader = StubFlowLoader::with_flow("message_catch_flow", MESSAGE_CATCH_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());
    let flow_run_id = suspend_a_flow(&app, "message_catch_flow").await;

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "approval_received",
                        "payload": { "approved": true, "reviewer": "alice" }
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "COMPLETED");
    // vars.approval_received is the payload the handler wrote
    assert_eq!(body["vars"]["approval_received"]["approved"], json!(true));
    assert_eq!(
        body["vars"]["approval_received"]["reviewer"],
        json!("alice")
    );
    // inflight should be cleared on completion
    assert_eq!(state.inflight.len().await, 0);
}

#[tokio::test]
async fn given_suspended_signal_catch_when_event_delivered_then_completed() {
    let loader = StubFlowLoader::with_flow("signal_catch_flow", SIGNAL_CATCH_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state);
    let flow_run_id = suspend_a_flow(&app, "signal_catch_flow").await;

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "broadcast_xyz",
                        "payload": "ok"
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "COMPLETED");
    assert_eq!(body["vars"]["broadcast_xyz"], json!("ok"));
}

#[tokio::test]
async fn given_mismatched_event_name_when_deliver_then_409() {
    let loader = StubFlowLoader::with_flow("message_catch_flow", MESSAGE_CATCH_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());
    let flow_run_id = suspend_a_flow(&app, "message_catch_flow").await;

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "different_event",
                        "payload": {}
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::CONFLICT);
    let body = body_json(resp).await;
    let msg = body["error"].as_str().unwrap();
    assert!(msg.contains("approval_received"), "got: {msg}");
    assert!(msg.contains("different_event"), "got: {msg}");
    // Still suspended — the inflight entry was NOT updated.
    assert_eq!(state.inflight.len().await, 1);
}

#[tokio::test]
async fn given_unknown_flow_run_id_when_deliver_then_404() {
    let loader = StubFlowLoader::with_flow("message_catch_flow", MESSAGE_CATCH_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state);
    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": "ghost",
                        "eventName": "approval_received",
                        "payload": {}
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn given_timer_suspended_when_deliver_then_409() {
    // Timer catch suspends with WaitType::AsyncTask; /flow/event is
    // for AsyncData (message/signal) only. The recovery loop
    // re-drives AsyncTask waits.
    let loader = StubFlowLoader::with_flow("timer_catch_flow", TIMER_CATCH_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());
    let flow_run_id = suspend_a_flow(&app, "timer_catch_flow").await;

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "any_name",
                        "payload": {}
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::CONFLICT);
    let body = body_json(resp).await;
    let msg = body["error"].as_str().unwrap();
    assert!(
        msg.contains("timer") || msg.contains("AsyncTask"),
        "expected timer-related error, got: {msg}"
    );
}

#[tokio::test]
async fn given_message_catch_when_event_delivered_without_payload_then_completed() {
    // The handler treats `payload` as optional (default = null).
    // A bare `null` is still `is_some()` for the executor's resume
    // check, so the flow completes.
    let loader = StubFlowLoader::with_flow("message_catch_flow", MESSAGE_CATCH_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state);
    let flow_run_id = suspend_a_flow(&app, "message_catch_flow").await;

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "approval_received"
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "COMPLETED");
    assert_eq!(body["vars"]["approval_received"], Value::Null);
}

#[tokio::test]
async fn given_message_catch_when_event_resumes_then_chained_user_task_can_suspend_again() {
    // After the event resumes, the next userTask should suspend and
    // /flow/event should NOT match (event name doesn't match the
    // userTask's decisionField). The inflight entry should still be
    // there waiting on the userTask.
    const CHAIN_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="chain_flow">
    <bpmn:startEvent id="s"><bpmn:outgoing>e1</bpmn:outgoing></bpmn:startEvent>
    <bpmn:intermediateCatchEvent id="ic" ruleforge:eventType="message"
                                 ruleforge:eventName="ready">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:intermediateCatchEvent>
    <bpmn:userTask id="u1" ruleforge:decisionField="approve">
      <bpmn:outgoing>e_yes</bpmn:outgoing>
      <bpmn:outgoing>e_no</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
    <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="u1"/>
    <bpmn:sequenceFlow id="e_yes" sourceRef="u1" targetRef="end_yes"
                       ruleforge:decisionValue="yes"/>
    <bpmn:sequenceFlow id="e_no" sourceRef="u1" targetRef="end_no"
                       ruleforge:decisionValue="no"/>
    <bpmn:endEvent id="end_yes"/>
    <bpmn:endEvent id="end_no"/>
  </bpmn:process>
</bpmn:definitions>"#;
    let loader = StubFlowLoader::with_flow("chain_flow", CHAIN_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());
    let flow_run_id = suspend_a_flow(&app, "chain_flow").await;

    // Deliver the event; should re-suspend at the userTask.
    let resp = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "ready",
                        "payload": "ok"
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "PENDING");
    assert_eq!(body["wait_ref"], "approve");
    // Still inflight (one entry).
    assert_eq!(state.inflight.len().await, 1);
}

// V5.28 P5 — namespaced `current_awaiting_field` regression test.
//
// The `IntermediateEventExecutor` (V5.28 P2) namespaces the
// `current_awaiting_field` it writes at suspend time:
//   message catch → "message:<event_name>"
//   signal catch  → "signal:<event_name>"
//
// The HTTP `/flow/event` handler must match the client's
// unprefixed `eventName` against either namespace. This test
// pins that contract: a flow suspended at
// `intermediateCatchEvent(eventType=message, eventName=foo)` must
// complete when the client delivers `eventName=foo` (and a
// future flow suspended at `signal:foo` would also complete
// against the same `eventName=foo` — they're independent runs).

#[tokio::test]
async fn v5_28_p2_namespaced_awaiting_field_matches_unprefixed_event_name() {
    // The PENDING body from /evaluate exposes the namespaced
    // wait_ref ("message:approval_received") but the existing
    // 4 tests above exercise the resume path implicitly.
    // This test pins the contract at the suspend side AND
    // the resume side in a single flow.
    let loader = StubFlowLoader::with_flow("message_catch_flow", MESSAGE_CATCH_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());
    let flow_run_id = suspend_a_flow(&app, "message_catch_flow").await;

    // The inflight entry's `current_awaiting_field` is the
    // namespaced form — this is the V5.28 P2 contract that
    // the handler must respect. We peek the in-memory store
    // directly. (If we're using the pg-backed store, this
    // field isn't directly readable — but the test uses
    // MemInflightStore, so it is.)
    let stored = state.inflight.get(&flow_run_id).await.expect("inflight entry");
    assert_eq!(
        stored.ctx.current_awaiting_field.as_deref(),
        Some("message:approval_received"),
        "V5.28 P2: current_awaiting_field is namespaced"
    );

    // Resume: the client sends an unprefixed name; the handler
    // must accept by trying the `message:` and `signal:`
    // prefixes.
    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "approval_received",
                        "payload": { "ok": true }
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "COMPLETED");
    assert_eq!(body["vars"]["approval_received"]["ok"], json!(true));
}

#[tokio::test]
async fn v5_28_p2_mismatched_kind_returns_409() {
    // A flow suspended at `message:foo` must NOT be resumable
    // by a delivery that asks for the same name but with a
    // different prefix. The handler tries both prefixes —
    // for `message:foo` (the actual field) the match succeeds
    // (we're testing the OPPOSITE: the field is `message:foo`
    // and the request is `eventName=foo` — but the contract
    // here is that the unprefixed name matches EITHER
    // prefix). The true negative case: a flow suspended at
    // `message:foo` and a request with `eventName=bar` —
    // the namespaced form `message:bar` doesn't match
    // `message:foo`. We already have
    // `given_mismatched_event_name_when_deliver_then_409`
    // for that. This test pins the error message contains
    // the namespaced `awaiting_event` so observability
    // dashboards see the actual key.
    let loader = StubFlowLoader::with_flow("message_catch_flow", MESSAGE_CATCH_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state);
    let flow_run_id = suspend_a_flow(&app, "message_catch_flow").await;

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "completely_different_event",
                        "payload": {}
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::CONFLICT);
    let body = body_json(resp).await;
    // The error body includes the namespaced `awaiting_event`
    // so dashboards can show "the flow is waiting for
    // message:approval_received".
    assert_eq!(
        body["awaiting_event"], "message:approval_received",
        "V5.28 P2: 409 body surfaces the namespaced awaiting field"
    );
}
