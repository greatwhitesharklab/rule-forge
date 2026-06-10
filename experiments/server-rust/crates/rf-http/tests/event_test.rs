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
